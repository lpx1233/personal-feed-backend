package cn.lpx1233.personal_feed_backend

import sangria.schema._
import sangria.macros.derive._
import HNCrawler.HNItem
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object GraphQLSchema {
  val HNItemType = deriveObjectType[Unit, HNItem](
    ObjectTypeDescription("Hacker News Item"))

  val itemID = Argument("id", IntType)

  val topstoriesLen = Argument("len", OptionInputType(IntType))

  val userID = Argument("userID", IntType)
  val recommendLen = Argument("len", IntType)

  val queryType = ObjectType("Query", fields[HNItemRepo, Unit](
    Field("item", OptionType(HNItemType),
      description = Some("Returns a item with specific `id`."),
      arguments = itemID :: Nil,
      resolve = c => c.ctx.item(c.arg(itemID))),
      
    Field("topstories", ListType(HNItemType),
      description = Some("Returns a list topstories for all users."),
      arguments = topstoriesLen :: Nil,
      resolve = c => c.ctx.topstories(c.arg(topstoriesLen))),

    Field("recommended", ListType(HNItemType),
      description = Some("Returns a list of recommended item for a specific user."),
      arguments = userID :: recommendLen :: Nil,
      resolve = c => c.ctx.recommended(c.arg(userID), c.arg(recommendLen)))))

  val schema = Schema(queryType)
}

class HNItemRepo {
  def item(id: Int): Future[HNItem] = MongoConn.getHNItemById(id)
  def topstories(len: Option[Int]): Future[List[HNItem]] = {
    MongoConn.getHNTopStories()
      .flatMap { (topIDs: List[Int]) =>
        val idsToFetch = len match {
          case Some(l) => topIDs.take(l)
          case None => topIDs
        }
        Future.sequence(idsToFetch.map { (id: Int) => MongoConn.getHNItemById(id) }
      )}
  }
  // TODO: implement recommendation logic
  def recommended(userID: Int, len: Int): Future[List[HNItem]] = ???
}