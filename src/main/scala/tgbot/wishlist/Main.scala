package tgbot.wishlist

//import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import slick.jdbc.PostgresProfile.api.Database

import tgbot.wishlist.bot.WishListBot
import tgbot.wishlist.db.DBManager


object Main extends App {
  val database = Database.forConfig("myDB")
  try {
    val dbManager = new DBManager(database)
    val TOKEN = sys.env("SCALA_BOT_TOKEN")
    val bot = new WishListBot(TOKEN, dbManager)
    val eol = bot.run()
    println("Press [ENTER] to shutdown the bot, it may take a few seconds...")
    scala.io.StdIn.readLine()
    bot.shutdown()
    Await.result(eol, Duration.Inf)
  } finally database.close
}

//    val conf = ConfigFactory.load()
//    val TOKEN = conf.getString("TOKEN")
//    TOKEN = ${?SCALA_BOT_TOKEN} -- .conf
