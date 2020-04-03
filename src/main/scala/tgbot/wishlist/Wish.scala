package tgbot.wishlist

case class Wish(name: String, link: Option[String] = None, description: Option[String] = None) {
  override def toString: String = {
    val linkPart = if (link.isDefined) s"\nLink: ${link.get}" else ""
    val descPart = if (description.isDefined) s"\nDescription: ${description.get}" else ""
    name + linkPart + descPart
  }
}

object Wish {
  val emptyWish: Wish = Wish("")
}
