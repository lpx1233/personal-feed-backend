// module configuration
scalaVersion := "2.12.8"
name := "personal-feed-backend"
version := "0.1"

// dependencies
libraryDependencies += "org.typelevel" %% "cats-core" % "latest.release"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "latest.release"
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "latest.release" 
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "latest.release"
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % "latest.release"
libraryDependencies += "io.spray" %% "spray-json" % "latest.release"
libraryDependencies += "org.reactivemongo" %% "reactivemongo" % "latest.release"
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "latest.release"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "latest.release"
libraryDependencies += "ch.megard" %% "akka-http-cors" % "latest.release"
// GraphQL Support
libraryDependencies += "org.sangria-graphql" %% "sangria" % "latest.release"
libraryDependencies += "org.sangria-graphql" %% "sangria-spray-json" % "latest.release"

// test dependencies
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "latest.release" % Test
libraryDependencies += "com.typesafe.akka" %% "akka-http-testkit" % "latest.release" % Test
libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % "latest.release" % Test
