package tgbot.wishlist

case class Wish(name: String, link: String = "", description: String = "")

case class WishList(wishList: List[Wish])
