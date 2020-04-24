package tgbot.wishlist.db

import java.io.File

import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._
import com.typesafe.config.{Config, ConfigFactory}


import tgbot.wishlist.bot.Wish


class DBManager(db: Database) { //(implicit ec: ExecutionContext) {
  val wishes = TableQuery[UserWishes]

  def getUserWishes(userId: Int): Future[Seq[UserWishesRow]] =
    db.run(wishes.result)//.map(_.filter(_.userId == userId).sortBy(_.id))

  def insertWish(userId: Int, wish: Wish): Future[Option[Int]] = {
    val Wish(name, link, description) = wish
    val insertQuery = wishes ++= Seq(UserWishesRow(None, userId, name, link, description))
    db.run(insertQuery)
  }

  def deleteWish(rowId: Int): Future[Int] = {
    val deleteQuery = wishes.filter(_.id === rowId).delete
    db.run(deleteQuery)
  }

  val close: Unit = db.close()
}
