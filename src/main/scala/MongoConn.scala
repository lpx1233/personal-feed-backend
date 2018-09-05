package cn.lpx1233.personal_feed_backend

import scala.concurrent.ExecutionContext
import reactivemongo.api.MongoDriver

object MongoConn {
  import ExecutionContext.Implicits.global
  val driver = MongoDriver()
  val connection = driver.connection(List("localhost"))
}