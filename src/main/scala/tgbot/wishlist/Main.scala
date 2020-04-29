package tgbot.wishlist

import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import slick.jdbc.PostgresProfile.api.Database

import tgbot.wishlist.bot.WishListBot
import tgbot.wishlist.db.DBManager


object Main extends App {
  val conf = ConfigFactory.load()
  val database = Database.forConfig("myDB", conf)
  try {
    val dbManager = new DBManager(database)
    val TOKEN = conf.getString("TOKEN")
    val bot = new WishListBot(TOKEN, dbManager)
    val eol = bot.run()
    println("Press [ENTER] to shutdown the bot, it may take a few seconds...")
    scala.io.StdIn.readLine()
    bot.shutdown()
    Await.result(eol, Duration.Inf)
  } finally database.close
}
