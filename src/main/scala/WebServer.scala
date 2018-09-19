package cn.lpx1233.personal_feed_backend

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.HttpApp
import akka.http.scaladsl.server.Route
import scala.concurrent.ExecutionContext
import reactivemongo.bson.BSONDocument
import ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

// Server definition
object WebServer extends HttpApp with SprayJsonSupport with DefaultJsonProtocol {
  import HNCrawler._
  override def routes: Route =
    cors() {
      get {
        pathSingleSlash {
          complete("Hello! This is My Personal Feed Backend~")
        } ~
        path("topstories") {
          onSuccess(getHNTopStories()) { topStories =>
            complete(topStories)
          }
        } ~
        path("id" / IntNumber) { id =>
          onSuccess(getHNItemById(id)) { item =>
            complete(item)
          }
        }
      }
    }
    
  def getHNTopStories(): Future[List[Int]] = {
    MongoConn.connection.database("hacker_news")
      .map(_.collection("top_stories"))
      .flatMap(_.find(BSONDocument("_id" -> 0)).requireOne[BSONDocument])
      .map(_.getAs[List[Int]]("top_stories").get)
  }
  def getHNItemById(id: Int): Future[HNItem] = {
    MongoConn.connection.database("hacker_news")
      .map(_.collection("items"))
      .flatMap(_.find(BSONDocument("id" -> id)).requireOne[HNItem])
  }
}
