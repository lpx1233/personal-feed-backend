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
    deleted: Option[Boolean],
    itemType: Option[String],
    by: Option[String],
    time: Option[Long],
    test: Option[String],
    dead: Option[Boolean],
    parent: Option[Int],
    poll: Option[Int],
    kids: Option[List[Int]],
    url: Option[String],
    score: Option[Int],
    title: Option[String],
    parts: Option[List[Int]],
    descendants: Option[Int])
  // bson & json parser
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
      bson.getAs[Boolean]("deleted"),
      bson.getAs[String]("itemType"),
      bson.getAs[String]("by"),
      bson.getAs[Long]("time"),
      bson.getAs[String]("test"),
      bson.getAs[Boolean]("dead"),
      bson.getAs[Int]("parent"),
      bson.getAs[Int]("poll"),
      bson.getAs[List[Int]]("kids"),
      bson.getAs[String]("url"),
      bson.getAs[Int]("score"),
      bson.getAs[String]("title"),
      bson.getAs[List[Int]]("parts"),
      bson.getAs[Int]("descendants"))
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
  // json format
  implicit val itemFormat = jsonFormat15(HNItem)
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
              "https://hacker-news.firebaseio.com/v0/topstories.json"))
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
      items match {
        case item :: rest =>
          http.singleRequest(HttpRequest(uri =
              s"https://hacker-news.firebaseio.com/v0/item/$item.json"))
            .flatMap {
              case HttpResponse(StatusCodes.OK, _, e, _) => Unmarshal(e).to[HNItem]
              case HttpResponse(status, _, e, _) =>
                e.discardBytes()
                Future.failed(new Exception(s"service returned ${status.intValue()}"))
            }.onComplete((res) => {
              self ! GetItemsByTopStoriesIds(rest)
              res match {
                case Success(item: HNItem) => self ! StoreItem(item)
                case Failure(e) => log.error(
                    s"Hacker News Crawler: Get item[$item] error, ${e.getMessage()}")
              }
            })
        case Nil =>
      }
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