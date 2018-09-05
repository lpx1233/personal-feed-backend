package cn.lpx1233.personal_feed_backend

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorLogging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.ByteString
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import reactivemongo.api.{ Cursor, DefaultDB }
import reactivemongo.bson.{
  BSONDocumentWriter, BSONDocumentReader, Macros, document
}

import scala.concurrent.Future
import scala.util.{ Failure, Success }

object HNCrawler {
  // internal data structure
  case class HNItem(
    id: Int,
    deleted: Boolean,
    itemType: String,
    by: String,
    time: Long,
    test: String,
    dead: Boolean,
    parent: Int,
    poll: Int,
    kids: List[Int],
    url: String,
    score: Int,
    title: String,
    parts: List[Int],
    descendants: Int)
  // messages
  case object Start
  private case class GetItemsByTopStoriesIds(items: List[Int])
  private case class StoreTopStoriesIds(items: List[Int])
  private case class StoreItem(item: HNItem)
  private case object Completed
}

class HNCrawler extends Actor with ActorLogging with SprayJsonSupport 
    with DefaultJsonProtocol {
  // Setup env
  import akka.pattern.pipe
  import context.dispatcher
  import HNCrawler._
  final implicit val materializer: ActorMaterializer =
      ActorMaterializer(ActorMaterializerSettings(context.system))
  val http = Http(context.system)
  // define json parser
  implicit val hNItemFormat = jsonFormat15(HNItem)
  // internal states
  var inProgress: Boolean = false
  var isTopStoriesStored: Boolean = false
  var itemsStored: Int = 0
  // define core functionality
  def receive = {
    case Start =>
        // start crawl hacker news
        log.info("HNCrawler get Start")
        inProgress = true
        isTopStoriesStored = false
        itemsStored = 0
        http.singleRequest(HttpRequest(uri =
            "https://hacker-news.firebaseio.com/v0/topstories.json"))
          .flatMap {
            case HttpResponse(StatusCodes.OK, _, e, _) => Unmarshal(e).to[List[Int]]
            case HttpResponse(status, _, e, _) =>
              e.discardBytes()
              Future.failed(new Exception(s"service returned ${status.intValue()}"))
          }.onComplete {
            case Success(items: List[Int]) => {
              self ! GetItemsByTopStoriesIds(items)
              self ! StoreTopStoriesIds(items)
            }
            case Failure(e) => log.error(
                s"Hacker News Crawler: Get top stories error, ${e.getMessage()}")
          }
    case GetItemsByTopStoriesIds(items: List[Int]) =>
      log.info("HNCrawler get GetItemsByTopStoriesIds")
      items.map((item: Int) =>
          (item, s"https://hacker-news.firebaseio.com/v0/item/$item.json"))
        .map { case (id: Int, uri: String) =>
            (id, http.singleRequest(HttpRequest(uri=uri)))}
        .foreach{ case (id: Int, responseFuture: Future[HttpResponse]) => {
          responseFuture.flatMap {
            case HttpResponse(StatusCodes.OK, _, e, _) => Unmarshal(e).to[HNItem]
            case HttpResponse(status, _, e, _) =>
              e.discardBytes()
              Future.failed(new Exception(s"service returned ${status.intValue()}"))
          }.onComplete {
            case Success(item: HNItem) => self ! StoreItem(item)
            case Failure(e) => log.error(
                s"Hacker News Crawler: Get item[$id] error, ${e.getMessage()}")
          }
        }}
    case StoreTopStoriesIds(items: List[Int]) =>
      // store the top stories ids in mongodb
      log.info("HNCrawler get StoreTopStoriesIds")
      val selector = document("_id" -> 0)
      val updater = document("top_stories" -> items)
      MongoConn.connection.database("hacker_news")
        .map(_.collection("top_stories"))
        .flatMap(_.update(selector, updater, upsert=true))
        .onComplete {
          case Success(res) =>
            log.info(s"Hacker News Crawler: store top stories ids success")
            isTopStoriesStored = true
            if(itemsStored == 500) self ! Completed
          case Failure(e) => log.error(
              s"Hacker News Crawler: store top stories ids error, ${e.getMessage()}")
        }
    case StoreItem(item: HNItem) =>
      // store a item using mongodb
      log.info("HNCrawler get StoreItem")
      val selector = document("id" -> item.id)
      implicit def itemsWriter: BSONDocumentWriter[HNItem] = Macros.writer[HNItem]
      MongoConn.connection.database("hacker_news")
        .map(_.collection("items"))
        .flatMap(_.update(selector, item, upsert=true))
        .onComplete {
          case Success(res) =>
            log.info(s"Hacker News Crawler: store item[${item.id}] success")
            itemsStored += 1
            if(isTopStoriesStored && itemsStored == 500) self ! Completed
          case Failure(e) => log.error(
              s"Hacker News Crawler: store item[${item.id}] error, ${e.getMessage()}")
        }
    case Completed =>
      log.info("HNCrawler get Completed")
      // progress completed
      inProgress = false
      isTopStoriesStored = false
      itemsStored = 0
      // TODO: inform sender
      // TODO: clean old items
  }
}