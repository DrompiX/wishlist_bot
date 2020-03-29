package tgbot.wishlist

case class Wish(name: String, description: String, link: String)

case class WishList(wishList: List[Wish])
