package tgbot.wishlist

import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

case class UserWishesRow(id: Option[Int] = None,
                         userId: Int,
                         wishName: String,
                         wishLink: Option[String] = None,
                         wishDesc: Option[String] = None)

class UserWishes(tag: Tag) extends Table[UserWishesRow](tag, Some("my_schema"), "user_wishes") {
  val id = column[Int]("id", O.AutoInc, O.PrimaryKey)
  val userId = column[Int]("user_id")
  val wishName = column[String]("wish_name")
  val wishLink = column[Option[String]]("wish_link", O.Default(None))
  val wishDesc = column[Option[String]]("wish_desc", O.Default(None))

  override def * : ProvenShape[UserWishesRow] =
    (id.?, userId, wishName, wishLink, wishDesc) <> (UserWishesRow.tupled, UserWishesRow.unapply)
}

//// AUTO-GENERATED Slick data model
//
///** Stand-alone Slick data model for immediate use */
//object Tables extends {
//  val profile = slick.jdbc.PostgresProfile
//} with Tables
//
///** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
//trait Tables {
//  val profile: slick.jdbc.JdbcProfile
//  import profile.api._
//  import slick.model.ForeignKeyAction
//  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
//  import slick.jdbc.{GetResult => GR}
//
//  /** DDL for all tables. Call .create to execute. */
//  lazy val schema: profile.SchemaDescription = UserWishes.schema
//  @deprecated("Use .schema instead of .ddl", "3.0")
//  def ddl = schema
//
//  /** Entity class storing rows of table UserWishes
//   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
//   *  @param userId Database column user_id SqlType(int4)
//   *  @param wishName Database column wish_name SqlType(text)
//   *  @param wishLink Database column wish_link SqlType(text), Default(None)
//   *  @param wishDesc Database column wish_desc SqlType(text), Default(None) */
//  case class UserWishesRow(id: Int, userId: Int, wishName: String, wishLink: Option[String] = None, wishDesc: Option[String] = None)
//  /** GetResult implicit for fetching UserWishesRow objects using plain SQL queries */
//  implicit def GetResultUserWishesRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Option[String]]): GR[UserWishesRow] = GR{
//    prs => import prs._
//    UserWishesRow.tupled((<<[Int], <<[Int], <<[String], <<?[String], <<?[String]))
//  }
//  /** Table description of table user_wishes. Objects of this class serve as prototypes for rows in queries. */
//  class UserWishes(_tableTag: Tag) extends profile.api.Table[UserWishesRow](_tableTag, Some("my_schema"), "user_wishes") {
//    def * = (id, userId, wishName, wishLink, wishDesc) <> (UserWishesRow.tupled, UserWishesRow.unapply)
//    /** Maps whole row to an option. Useful for outer joins. */
//    def ? = ((Rep.Some(id), Rep.Some(userId), Rep.Some(wishName), wishLink, wishDesc)).shaped.<>({r=>import r._; _1.map(_=> UserWishesRow.tupled((_1.get, _2.get, _3.get, _4, _5)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))
//
//    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
//    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
//    /** Database column user_id SqlType(int4) */
//    val userId: Rep[Int] = column[Int]("user_id")
//    /** Database column wish_name SqlType(text) */
//    val wishName: Rep[String] = column[String]("wish_name")
//    /** Database column wish_link SqlType(text), Default(None) */
//    val wishLink: Rep[Option[String]] = column[Option[String]]("wish_link", O.Default(None))
//    /** Database column wish_desc SqlType(text), Default(None) */
//    val wishDesc: Rep[Option[String]] = column[Option[String]]("wish_desc", O.Default(None))
//  }
//  /** Collection-like TableQuery object for table UserWishes */
//  lazy val UserWishes = new TableQuery(tag => new UserWishes(tag))
//}
