name := "wishbot"

version := "0.1"

scalaVersion := "2.12.7"

libraryDependencies ++= Seq(
  "com.bot4s" %% "telegram-core" % "4.4.0-RC2",
  "com.bot4s" %% "telegram-akka" % "4.4.0-RC2",
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
//  "com.typesafe" % "config" % "1.4.0",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.4",
  "com.typesafe.akka" %% "akka-protobuf" % "2.6.4",
  "com.typesafe.akka" %% "akka-stream" % "2.6.4",
  "com.typesafe.slick" %% "slick" % "3.3.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.1",
  "org.postgresql" % "postgresql" % "42.2.5",
  "com.typesafe.slick" %% "slick-codegen" % "3.3.1",
  "com.softwaremill.sttp" %% "akka-http-backend" % "1.7.2"
)
