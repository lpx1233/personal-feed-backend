package cn.lpx1233.personal_feed_backend

import akka.actor.ActorSystem

object Main extends App {
  println("Personal Feed Backend Start!")

  // start crawler actors
  val crawlerActorSystem = ActorSystem("crawler")
  val crawlerScheduler = crawlerActorSystem.actorOf(CrawlScheduler.props)
  crawlerScheduler ! CrawlScheduler.Start

  // start web server
  WebServer.startServer("0.0.0.0", 8080)
}
