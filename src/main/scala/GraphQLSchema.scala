package cn.lpx1233.personal_feed_backend

import sangria.schema._
import sangria.macros.derive._
import HNCrawler.HNItem
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import com.typesafe.scalalogging.LazyLogging

object GraphQLSchema {
  // define HNItem
  val HNItemType =
    deriveObjectType[Unit, HNItem](ObjectTypeDescription("Hacker News Item"))

  // define argument for item
  val itemID = Argument("id", IntType)

  // define argument for topstories
  val topstoriesFrom = Argument("from", IntType)
  val topstoriesLen = Argument("len", IntType)

  // define arguments for recommended
  val userID = Argument("userID", IntType)
  val recommendLen = Argument("len", IntType)
  val readBefore = Argument("readBefore", ListInputType(IntType))

  // define Query type
  val queryType = ObjectType(
    "Query",
    fields[HNItemRepo, Unit](
      Field(
        "item",
        OptionType(HNItemType),
        description = Some("Returns a item with specific `id`."),
        arguments = itemID :: Nil,
        resolve = c => c.ctx.item(c.arg(itemID))
      ),
      Field(
        "topstories",
        ListType(HNItemType),
        description = Some("Returns a list topstories for all users."),
        arguments = topstoriesFrom :: topstoriesLen :: Nil,
        resolve =
          c => c.ctx.topstories(c.arg(topstoriesFrom), c.arg(topstoriesLen))
      ),
      Field(
        "recommended",
        ListType(HNItemType),
        description =
          Some("Returns a list of recommended item for a specific user."),
        arguments = userID :: recommendLen :: readBefore :: Nil,
        resolve = c =>
          c.ctx
            .recommended(c.arg(userID), c.arg(recommendLen), c.arg(readBefore))
      )
    )
  )

  // define schema
  val schema = Schema(queryType)
}

class HNItemRepo extends LazyLogging {
  def item(id: Int): Future[HNItem] = MongoConn.getHNItemById(id)
  def topstories(from: Int, len: Int): Future[List[HNItem]] = {
    MongoConn
      .getHNTopStories()
      .flatMap { (topIDs: List[Int]) =>
        Future.sequence(topIDs.drop(from).take(len).map { (id: Int) =>
          MongoConn.getHNItemById(id)
        })
      }
  }
  // TODO: implement recommendation logic
  // userID: user identification for recommendation, 0 refers to global user
  // len: the number of fetched recommended item, default is 10
  // readBefore: the list of item read before, is used to prevent double
  //   recommendation. If userID is not 0 and server have a read history
  //   of this user, readBefore could be Nil.
  def recommended(
      userID: Int,
      len: Int,
      readBefore: Seq[Int]
  ): Future[List[HNItem]] = {
    val readSet = readBefore.toSet
    MongoConn
      .getAllItemIds()
      .map(_.toVector)
      .map { (itemIDs: Vector[Int]) =>
        def loop(res: List[Int]): List[Int] = {
          val idx = Random.nextInt(itemIDs.length)
          val id = itemIDs(idx)
          // logger.info(s"idx = ${idx}, id = ${id}")
          if (res.length == len) res
          else if (!readSet.contains(id) && !res.toSet.contains(id))
            loop(id :: res)
          else loop(res)
        }
        loop(List())
      }
      .flatMap { (itemIDs: List[Int]) =>
        Future.sequence(itemIDs.map { (id: Int) =>
          MongoConn.getHNItemById(id)
        })
      }
  }
}
