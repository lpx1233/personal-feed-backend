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
  val userID = Argument("userID", IntType)

  val queryType = ObjectType("Query", fields[HNItemRepo, Unit](
    Field("item", OptionType(HNItemType),
      description = Some("Returns a item with specific `id`."),
      arguments = itemID :: Nil,
      resolve = c => c.ctx.item(c arg itemID)),
      
    Field("topstories", ListType(HNItemType),
      description = Some("Returns a list topstories for all users."),
      resolve = _.ctx.topstories),

    Field("recommended", ListType(HNItemType),
      description = Some("Returns a list of recommended item for a specific user."),
      resolve = c => c.ctx.recommended(c arg userID))))

  val schema = Schema(queryType)
}

class HNItemRepo {
  def item(id: Int): Future[HNItem] = MongoConn.getHNItemById(id)
  def topstories: Future[List[HNItem]] = {
    MongoConn.getHNTopStories()
      .flatMap { (topIDs: List[Int]) =>
        Future.sequence(topIDs.map { (id: Int) =>
          MongoConn.getHNItemById(id)
        })
      }
  }
  def recommended(userID: Int): Future[List[HNItem]] = ???
}