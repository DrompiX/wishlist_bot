package tgbot.wishlist

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App {
  val TOKEN = sys.env("SCALA_BOT_TOKEN")
  val bot = new WishListBot(TOKEN)
  val eol = bot.run()
  println("Press [ENTER] to shutdown the bot, it may take a few seconds...")
  scala.io.StdIn.readLine()
  bot.shutdown()
  Await.result(eol, Duration.Inf)
}
