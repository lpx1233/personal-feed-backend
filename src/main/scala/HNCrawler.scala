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
  BSONDocumentWriter, BSONDocumentReader, Macros, BSONDocument
}

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

object HNCrawler {
  // props
  def props: Props = Props[HNCrawler]
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
  implicit object HNItemWriter extends BSONDocumentWriter[HNItem] {
    def write(item: HNItem): BSONDocument =
      BSONDocument(
        "id" -> item.id,
        "deleted" -> item.deleted,
        "itemType" -> item.itemType,
        "by" -> item.by,
        "time" -> item.time,
        "test" -> item.test,
        "dead" -> item.dead,
        "parent" -> item.parent,
        "poll" -> item.poll,
        "kids" -> item.kids,
        "url" -> item.url,
        "score" -> item.score,
        "title" -> item.title,
        "parts" -> item.parts,
        "descendants" -> item.descendants)
  }
  implicit object HNItemReader extends BSONDocumentReader[HNItem] {
    def read(bson: BSONDocument): HNItem = new HNItem(
      bson.getAs[Int]("id").get,
      bson.getAs[Boolean]("deleted").get,
      bson.getAs[String]("itemType").get,
      bson.getAs[String]("by").get,
      bson.getAs[Long]("time").get,
      bson.getAs[String]("test").get,
      bson.getAs[Boolean]("dead").get,
      bson.getAs[Int]("parent").get,
      bson.getAs[Int]("poll").get,
      bson.getAs[List[Int]]("kids").get,
      bson.getAs[String]("url").get,
      bson.getAs[Int]("score").get,
      bson.getAs[String]("title").get,
      bson.getAs[List[Int]]("parts").get,
      bson.getAs[Int]("descendants").get)
  }
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
    // TODO: implement retry method
    case Start =>
        // start crawl hacker news
        log.info("HNCrawler get Start")
        Future.fromTry(Try(
            if(!inProgress) true else throw new Exception("HNCrawler in progress")))
          .flatMap { (_) =>
            inProgress = true
            isTopStoriesStored = false
            itemsStored = 0
            http.singleRequest(HttpRequest(uri =
                "http://hacker-news.firebaseio.com/v0/topstories.json"))
          }.flatMap {
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
          (item, s"http://hacker-news.firebaseio.com/v0/item/$item.json"))
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
      val selector = BSONDocument("_id" -> 0)
      val updater = BSONDocument("top_stories" -> items)
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
      val selector = BSONDocument("id" -> item.id)
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