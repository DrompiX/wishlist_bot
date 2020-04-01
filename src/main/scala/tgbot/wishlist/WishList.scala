package tgbot.wishlist

case class Wish(name: String, link: String = "", description: String = "") {
  override def toString: String = s"Wish: $name\nLink: $link\nDescription: $description"
}

object Wish {
  val emptyWish: Wish = Wish("")
}

case class WishList(wishList: List[Wish])