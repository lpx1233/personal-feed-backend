package cn.lpx1233.personal_feed_backend

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Timers
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

import scala.concurrent.duration._
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
  // json format
  import DefaultJsonProtocol._
  implicit val itemFormat = jsonFormat15(HNItem)
  // messages
  case object Start
  private case object GetTopStoriesIDs
  private case class GetHeadItemByIDs(itemIDs: List[Int])
  private case object End
  private case object Retry
  // timer keys
  private case object RetryKey
}

class HNCrawler extends Actor with ActorLogging with SprayJsonSupport
    with DefaultJsonProtocol with Timers {
  // Setup env
  import akka.pattern.pipe
  import context.dispatcher
  import HNCrawler._
  final implicit val materializer: ActorMaterializer =
      ActorMaterializer(ActorMaterializerSettings(context.system))
  val http = Http(context.system)
  // internal states
  var inProgress: Boolean = false
  var retryTimes: Int = 5
  // define core functionality
  def receive = {
    case Start =>
      // start crawl hacker news
      log.info("HNCrawler: start running")
      Future.fromTry(
          Try(if (!inProgress) true else throw new Exception("in progress")))
        .onComplete {
          case Success(_) =>
            inProgress = true
            self ! GetTopStoriesIDs
          case Failure(e) =>
            log.info(s"HNCrawler: currently in progress")
        }
    case GetTopStoriesIDs =>
      // get top stories ids and store them into db
      log.info("HNCrawler: start fetching top stories IDs")
      http.singleRequest(
          HttpRequest(uri = "https://hacker-news.firebaseio.com/v0/topstories.json"))
        .flatMap {
          case HttpResponse(StatusCodes.OK, _, e, _) => Unmarshal(e).to[List[Int]]
          case HttpResponse(status, _, e, _) =>
            e.discardBytes()
            Future.failed(new Exception(s"service returned ${status.intValue()}"))
        }.flatMap { (itemIDs: List[Int]) =>
          val selector = BSONDocument("_id" -> 0)
          val updater = BSONDocument("top_stories" -> itemIDs)
          MongoConn.connection.database("hacker_news")
            .map(_.collection("top_stories"))
            .flatMap(_.update(selector, updater, upsert=true))
            .map((_, itemIDs))
        }.onComplete {
          case Success((storeRes, itemIDs)) => {
            self ! GetHeadItemByIDs(itemIDs)
            log.info(s"HNCrawler: get top stories ids success")
          }
          case Failure(e) =>
            log.error(s"HNCrawler: get top stories error, ${e.getMessage()}")
            self ! Retry
        }
    case GetHeadItemByIDs(itemIDs: List[Int]) =>
      // get item by id one by one
      itemIDs match {
        case itemID :: rest =>
          log.info(s"HNCrawler: start get item[${itemID}]")
          http.singleRequest(HttpRequest(uri =
              s"https://hacker-news.firebaseio.com/v0/item/${itemID}.json"))
            .flatMap {
              case HttpResponse(StatusCodes.OK, _, e, _) => Unmarshal(e).to[HNItem]
              case HttpResponse(status, _, e, _) =>
                e.discardBytes()
                Future.failed(new Exception(s"service returned ${status.intValue()}"))
            }.flatMap { (item) =>
              val selector = BSONDocument("id" -> item.id)
              MongoConn.connection.database("hacker_news")
                .map(_.collection("items"))
                .flatMap(_.update(selector, item, upsert=true))
            }.onComplete {
              case Success(res) =>
                log.info(s"HNCrawler: store item[${itemID}] success")
                self ! GetHeadItemByIDs(rest)
              case Failure(e) => 
                log.error(s"HNCrawler: Get item[${itemID}] error, ${e.getMessage()}")
                self ! Retry
            }
        case Nil =>
          log.info(s"HNCrawler: finished all item fetching")
          self ! End
      }
    case Retry =>
      // retry after 1 min for 4 times
      retryTimes -= 1
      if (retryTimes > 0)
        timers.startSingleTimer(RetryKey, GetTopStoriesIDs, 1.minute)
      else {
        log.error(s"HNCrawler: crawling failed 5 times, ending")
        self ! End
      }
    case End =>
      log.info("HNCrawler: crawling end")
      inProgress = false
      // TODO: inform sender
      // TODO: check data integrity
      // TODO: clean old items
  }
}