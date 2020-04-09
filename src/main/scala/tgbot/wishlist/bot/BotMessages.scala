package tgbot.wishlist.bot

object BotMessages {
  val nameRequired = "Please, specify the name of an item after /add command, e.x /add new AirPods."
  val linkRequired = "Please, specify a link (optional)"
  val descRequired = "Please, specify a description (optional)"
  val successfulCreation = "New wish was created!"
  val failedCreation = "Something went wrong, your wish was not added. Please, try again."
  val successfulRemoval = "Specified wish was successfully removed!"
  val failedRemoval = "Wish removal was unsuccessful."
  val notFinishedWish = "Previous wish was not finished."
  val emptyList = "Your wishlist is currently empty. To add a new wish use /add (e.g. /add AirPods)"

  val greetingText: String =
    """Greetings! I can help you to manage your wishlist \uD83D\uDE0A.
      |To see available commands, consider using /help.""".stripMargin

  val helpText: String =
    """I can help you to create and manage your wishlist.
      |
      |You can control me by sending such commands:
      |
      |/start – simple greeting
      |/help – helpful information about commands
      |
      |/list – print your full wishlist
      |/list <amount> – print some <amount> of first wishes from wishlist
      |
      |/add <wish_name> – add new wish to the list
      |/delete <wish_id> – delete a wish from the list with specific <wish_id> (can be found with /list command)""".stripMargin

  val idRequired: String =
    """Please, specify an index of the item after /delete
      |command (/delete <id> - you can find it using /list command).""".stripMargin

  def incorrectId(hi: Int, low: Int) = s"Item index is incorrect, should be in range [$hi; $low]."
}
