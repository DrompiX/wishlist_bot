name := "wishbot"

version := "0.1"

scalaVersion := "2.12.7"

libraryDependencies ++= Seq(
  "com.bot4s" %% "telegram-core" % "4.4.0-RC2",
  "com.bot4s" %% "telegram-akka" % "4.4.0-RC2",
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "com.github.pureconfig" %% "pureconfig" % "0.12.3"
)