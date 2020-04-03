package tgbot.wishlist

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

object DBManager {
  val db = Database.forConfig("myDB")
  val wishes = TableQuery[UserWishes]

  def getUserWishes(userId: Int): Future[Seq[UserWishesRow]] =
    db.run(wishes.result).map(_.filter(_.userId == userId).sortBy(_.id))

  def insertWish(userId: Int, wish: Wish): Future[Option[Int]] = {
    val Wish(name, link, description) = wish
    // TODO: think about removing Options
    val insertQuery = wishes ++= Seq(UserWishesRow(None, userId, name, link, description))
    db.run(insertQuery)
  }

  def deleteWish(rowId: Int): Future[Int] = {
    val deleteQuery = wishes.filter(_.id === rowId).delete
    db.run(deleteQuery)
  }
}
