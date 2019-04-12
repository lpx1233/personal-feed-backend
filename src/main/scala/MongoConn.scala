package cn.lpx1233.personal_feed_backend

import scala.concurrent.ExecutionContext
import reactivemongo.api.MongoDriver
import reactivemongo.api.{ Cursor, DefaultDB }
import reactivemongo.bson.{
  BSONDocumentWriter, BSONDocumentReader, Macros, BSONDocument
}
import scala.concurrent.Future
import HNCrawler._

object MongoConn {
  import ExecutionContext.Implicits.global

  val driver = MongoDriver()
  val connection = driver.connection(List("localhost"))

  def getHNTopStories(): Future[List[Int]] = {
    connection.database("hacker_news")
      .map(_.collection("top_stories"))
      .flatMap(_.find(BSONDocument("_id" -> 0)).requireOne[BSONDocument])
      .map(_.getAs[List[Int]]("top_stories").get)
  }

  def getHNItemById(id: Int): Future[HNItem] = {
    connection.database("hacker_news")
      .map(_.collection("items"))
      .flatMap(_.find(BSONDocument("id" -> id)).requireOne[HNItem])
  }

  def getAllItemIds(): Future[List[Int]] = {
    val projection = BSONDocument("id" -> 1, "_id" -> 0)
    connection.database("hacker_news")
      .map(_.collection("items"))
      .map(_.find(BSONDocument(), projection).cursor[BSONDocument]())
      .flatMap(_.collect[List](-1, Cursor.FailOnError[List[BSONDocument]]()))
      .map(_.map(_.getAs[Int]("id").get))
  }
}