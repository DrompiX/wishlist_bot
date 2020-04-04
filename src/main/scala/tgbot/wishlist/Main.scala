package tgbot.wishlist

import tgbot.wishlist.bot.WishListBot
import tgbot.wishlist.db.DBManager

import scala.concurrent.Await
import scala.concurrent.duration._


object Main extends App {

  try {
    val TOKEN = sys.env("SCALA_BOT_TOKEN")
    val bot = new WishListBot(TOKEN)
    val eol = bot.run()
    println("Press [ENTER] to shutdown the bot, it may take a few seconds...")
    scala.io.StdIn.readLine()
    bot.shutdown()
    Await.result(eol, Duration.Inf)
  } finally DBManager.db.close()

}
