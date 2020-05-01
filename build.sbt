name := "wishbot"

version := "0.1"

scalaVersion := "2.12.7"

def akkaVersion: String = "2.6.4"

libraryDependencies ++= Seq(
  "com.bot4s" %% "telegram-core" % "4.4.0-RC2",
  "com.bot4s" %% "telegram-akka" % "4.4.0-RC2",
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
//  "com.typesafe" % "config" % "1.4.0",
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "com.typesafe.slick" %% "slick" % "3.3.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.1",
  "org.postgresql" % "postgresql" % "42.2.5",
  "com.typesafe.slick" %% "slick-codegen" % "3.3.1",
  "com.softwaremill.sttp" %% "akka-http-backend" % "1.7.2",
  "org.mockito" %% "mockito-scala" % "1.13.11" % Test
//  "org.mockito" %% "mockito-scala" % "1.14.0",
//  "org.mockito" %% "mockito-scala_scalatest" % "1.14.0" % Test
//  "org.mockito:mockito-scala_2.12.7:1.14.0",
//  "org.mockito:mockito-scala-scalatest_2.12.7:1.14.0"
)
