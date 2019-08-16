package cn.lpx1233.personal_feed_backend

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Timers

import scala.concurrent.duration._

object CrawlScheduler {
  // props
  def props: Props = Props[CrawlScheduler]
  // messages
  case object Start
  case object Stop
  private case object CrawlHN
  // timer keys
  private case object HNKey
}

class CrawlScheduler extends Actor with ActorLogging with Timers {
  // Setup env
  import CrawlScheduler._
  val hnCrawler = context.actorOf(HNCrawler.props)
  // define core functionality
  def receive = {
    case Start =>
      self ! CrawlHN
      timers.startPeriodicTimer(HNKey, CrawlHN, 1.day)
    case Stop => timers.cancel(HNKey)
    case CrawlHN =>
      log.info("start hn crawler")
      hnCrawler ! HNCrawler.Start
  }
}
