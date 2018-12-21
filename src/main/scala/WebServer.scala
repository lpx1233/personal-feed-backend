package cn.lpx1233.personal_feed_backend

import akka.http.scaladsl.model._

import akka.http.scaladsl.server.HttpApp
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import reactivemongo.bson.BSONDocument

import sangria.marshalling.sprayJson._
import sangria.execution._
import sangria.ast._
import sangria.parser.QueryParser

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

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
        } ~
        (path("graphql") & entity(as[JsValue])) { requestJson =>
          // TODO: implement GraphQL
          val JsObject(fields) = requestJson
          val JsString(query) = fields("query")
          val operation = fields.get("operationName") collect {
            case JsString(op) => op
          }
          val vars = fields.get("variables") match {
            case Some(obj: JsObject) => obj
            case _ => JsObject.empty
          }
          QueryParser.parse(query) match {
            case Success(queryAst) =>
              complete(executeGraphQLQuery(queryAst, operation, vars))
            case Failure(error) =>
              complete(StatusCodes.BadRequest, JsObject("error" -> JsString(error.getMessage)))
          }
        } ~
        path("graphiql.html") {
          getFromResource("graphiql.html")
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
  def executeGraphQLQuery(query: Document, op: Option[String], vars: JsObject) =
    Executor.execute(
        schema, query, new ProductRepo, variables = vars, operationName = op)
      .map(StatusCodes.OK -> _)
      .recover {
        case error: QueryAnalysisError =>
          StatusCodes.BadRequest -> error.resolveError
        case error: ErrorWithResolver =>
          StatusCodes.InternalServerError -> error.resolveError
      }
}
