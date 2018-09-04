// module configuration
scalaVersion := "2.12.6"
name := "personal-feed-backend"
organization := "ch.epfl.scala"
version := "1.0"

// dependencies
libraryDependencies += "org.typelevel" %% "cats-core" % "latest.release"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "latest.release"
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "latest.release" 
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "latest.release"
libraryDependencies += "org.mongodb" %% "casbah" % "latest.release"