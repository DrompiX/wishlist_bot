package tgbot.wishlist.bot

import com.bot4s.telegram.methods.ParseMode.ParseMode
import com.bot4s.telegram.models.{Chat, ChatType, Message, ReplyMarkup, Update, User}
import org.scalatest.PrivateMethodTester
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import slick.jdbc.PostgresProfile.api.Database
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import org.mockito.MockitoSugar

import tgbot.wishlist.db.{DBManager, UserWishesRow}
import tgbot.wishlist.bot.BotMessages._

class WishListBotSpec extends AnyFlatSpec with PrivateMethodTester with Matchers {
  import WishListBotSpec._

  it should "get indexed wishes correctly" in {
    val getter = PrivateMethod[Future[List[(Int, UserWishesRow)]]]('getIndexedWishes)
    val getFut = bot1 invokePrivate getter(Some(testUser))
    val result = Await.result(getFut, 4.seconds)
    result shouldBe List((1, UserWishesRow(Some(1), 0, "wish1")), (2, UserWishesRow(Some(2), 0, "wish2")))
  }

  it should "correctly handle successful delete" in {
    val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private),
      text = Some("/delete 1"), from = Some(testUser))
    val testUpdateWithMessage: Update = Update(0, message = Some(testMessage))
    Await.result(bot.receiveUpdate(testUpdateWithMessage, None), 4.seconds)
    bot.testMessageStorage shouldBe successfulRemoval
  }

  it should "correctly handle incorrect delete id" in {
    val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private),
      text = Some("/delete 3"), from = Some(testUser))
    val testUpdateWithMessage: Update = Update(0, message = Some(testMessage))
    Await.result(bot.receiveUpdate(testUpdateWithMessage, None), 4.seconds)
    bot.testMessageStorage shouldBe incorrectId(1, 2)
  }

  it should "correctly handle user without wishes" in {
    val testUser2: User = User(1, isBot = false, firstName = "Dima")
    val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private),
      text = Some("/list"), from = Some(testUser2))
    val testUpdateWithMessage: Update = Update(0, message = Some(testMessage))
    Await.result(bot.receiveUpdate(testUpdateWithMessage, None), 4.seconds)
    bot.testMessageStorage shouldBe emptyList
  }

  it should "correctly handle user with wishes" in {
    val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private),
      text = Some("/list"), from = Some(testUser))
    val testUpdateWithMessage: Update = Update(0, message = Some(testMessage))
    Await.result(bot.receiveUpdate(testUpdateWithMessage, None), 4.seconds)
    val getter = PrivateMethod[String]('getPrintableWishes)
    val expectedRes = bot1 invokePrivate getter(List((1, UserWishesRow(Some(1), 0, "wish1")), (2, UserWishesRow(Some(2), 0, "wish2"))))
    bot.testMessageStorage shouldBe expectedRes
  }

}

object WishListBotSpec extends MockitoSugar {
  val testUser: User = User(0, isBot = false, firstName = "Dima")
  val db = mock[Database]
  val dbManager = new MockDBManager(db)
  val bot1 = new WishListBot("", dbManager)
  val bot = new TestBot("", dbManager)
}

class MockDBManager(db: Database) extends DBManager(db) {
  override def getUserWishes(userId: Int): Future[Seq[UserWishesRow]] = userId match {
    case 0 => Future.successful(Seq(UserWishesRow(Some(1), 0, "wish1"), UserWishesRow(Some(2), 0, "wish2")))
    case _ => Future.successful(Seq())
  }

  override def insertWish(userId: Int, wish: Wish): Future[Option[Int]] = super.insertWish(userId, wish)

  override def deleteWish(rowId: Int): Future[Int] = Future.successful(1)
}

class TestBot(token: String, dbManager: DBManager) extends WishListBot(token, dbManager) {
  var testMessageStorage: String = ""

  override def reply(text: String,
                     parseMode: Option[ParseMode],
                     disableWebPagePreview: Option[Boolean],
                     disableNotification: Option[Boolean],
                     replyToMessageId: Option[Int],
                     replyMarkup: Option[ReplyMarkup])
                    (implicit message: Message): Future[Message] = {
    testMessageStorage = text
    super.reply(text, parseMode, disableWebPagePreview, disableNotification, replyToMessageId, replyMarkup)
  }

  override def replyMd(text: String,
                       disableWebPagePreview: Option[Boolean],
                       disableNotification: Option[Boolean],
                       replyToMessageId: Option[Int],
                       replyMarkup: Option[ReplyMarkup])
                      (implicit message: Message): Future[Message] = {
    testMessageStorage = text
    super.replyMd(text, disableWebPagePreview, disableNotification, replyToMessageId, replyMarkup)
  }

}
