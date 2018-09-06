package cn.lpx1233.personal_feed_backend

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Timers

import scala.concurrent.duration._

object CrawlScheduler {
  // props
  def props(hnCrawler: ActorRef): Props = Props(new CrawlScheduler(hnCrawler))
  // messages
  case object Start
  case object Stop
  private case object CrawlHN
  // timer keys
  private case object HNKey
}

class CrawlScheduler(hnCrawler: ActorRef)
    extends Actor with ActorLogging with Timers {
  // Setup env
  import CrawlScheduler._
  // define core functionality
  def receive = {
    case Start => timers.startPeriodicTimer(HNKey, CrawlHN, 1 day)
    case Stop => timers.cancel(HNKey)
    case CrawlHN => hnCrawler ! HNCrawler.Start
  }
}