package tgbot.wishlist.db

import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

case class UserWishesRow(id: Option[Int] = None,
                         userId: Int,
                         wishName: String,
                         wishLink: Option[String] = None,
                         wishDesc: Option[String] = None) {
}

class UserWishes(tag: Tag) extends Table[UserWishesRow](tag, Some("my_schema"), "user_wishes") {
  val id = column[Int]("id", O.AutoInc, O.PrimaryKey)
  val userId = column[Int]("user_id")
  val wishName = column[String]("wish_name")
  val wishLink = column[Option[String]]("wish_link", O.Default(None))
  val wishDesc = column[Option[String]]("wish_desc", O.Default(None))

  override def * : ProvenShape[UserWishesRow] =
    (id.?, userId, wishName, wishLink, wishDesc) <> (UserWishesRow.tupled, UserWishesRow.unapply)
}
