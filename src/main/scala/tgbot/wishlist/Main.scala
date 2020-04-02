package tgbot.wishlist

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App {

  val db = Database.forConfig("myDB")

  try {
    val wishes = TableQuery[UserWishes]
    val newRow = UserWishesRow(None, 198009316, "hp2", Some("l2"), Some("d2"))
    val insertAction = wishes ++= Seq(newRow)
    val deleteAction = wishes.filter(_.id === 1).delete
    Await.result(db.run(deleteAction), 10.seconds)
    println("Wishes:")
    val resultFuture = db.run(wishes.result).map(_.foreach { println(_) })
    Await.result(resultFuture, 10.seconds)
  } finally db.close()

//  val TOKEN = sys.env("SCALA_BOT_TOKEN")
//  val bot = new WishListBot(TOKEN)
//  val eol = bot.run()
//  println("Press [ENTER] to shutdown the bot, it may take a few seconds...")
//  scala.io.StdIn.readLine()
//  bot.shutdown()
//  Await.result(eol, Duration.Inf)
}
