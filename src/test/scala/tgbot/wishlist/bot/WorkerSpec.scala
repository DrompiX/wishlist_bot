package tgbot.wishlist.bot

import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.TelegramBot
import com.bot4s.telegram.methods.{Request, SendMessage}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.bot4s.telegram.models.{CallbackQuery, Chat, ChatType, Message, User}
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import slick.jdbc.PostgresProfile.api.Database
import org.mockito.MockitoSugar

import scala.concurrent.Future
import tgbot.wishlist.db.DBManager
import tgbot.wishlist.bot.BotMessages._

class WorkerSpec extends AnyFlatSpec with MockitoSugar with Matchers {
  import WorkerSpec._

  import scala.concurrent.ExecutionContext.Implicits.global

  it should "handle arguments correctly" in {
    val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private), text = Some("/add hello"))
    val worker = new Worker(dbManager, bot)
    val handle = worker.handleArguments(testMessage, { msg => Some(Seq(msg.text.get, msg.messageId.toString)) })
    handle shouldBe Some("/add hello 0")
  }

  it should "correctly process add message in idle state" in {
    val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private), text = Some("/add hello"))
    val worker = new Worker(dbManager, bot)
    val wb = BehaviorTestKit(worker.idleState(0))
    wb.run(BaseWorker.ProcessMessage(testMessage))
    bot.req shouldBe SendMessage(0L, linkRequired, replyMarkup = worker.skipKeyboard)
  }

  it should "correctly process add message without argument in idle state" in {
    val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private), text = Some("/add"))
    val worker = new Worker(dbManager, bot)
    val wb = BehaviorTestKit(worker.idleState(0))
    wb.run(BaseWorker.ProcessMessage(testMessage))
    bot.req shouldBe SendMessage(0L, nameRequired)
  }

  it should "correctly process link message" in {
    val worker = new Worker(dbManager, bot)
    val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private), text = Some("www.google.com"))
    val prevMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private),
      text = Some(linkRequired), replyMarkup = worker.skipKeyboard)

    val wb = BehaviorTestKit(worker.addWishLink(0, Wish("wish"), Future.successful(prevMessage)))
    wb.run(BaseWorker.ProcessMessage(testMessage))
    bot.req shouldBe SendMessage(0L, descRequired, replyMarkup = worker.skipKeyboard)
  }

  it should "correctly process skip link message" in {
    val worker = new Worker(dbManager, bot)
    val prevMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private),
      text = Some(linkRequired), replyMarkup = worker.skipKeyboard)
    val testCallback: CallbackQuery = CallbackQuery(id = "id", from = testUser,
        chatInstance = "inst", message = Some(prevMessage), data = Some("skip"))

    val wb = BehaviorTestKit(worker.addWishLink(0, Wish("wish"), Future.successful(prevMessage)))
    wb.run(BaseWorker.ProcessCallback(prevMessage, testCallback))
    bot.req shouldBe SendMessage(0L, descRequired, replyMarkup = worker.skipKeyboard)
  }

  it should "cancel addition when another command is executed" in {
    val worker = new Worker(dbManager, bot)
    val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private), text = Some("/help"))
    val prevMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private),
      text = Some(linkRequired), replyMarkup = worker.skipKeyboard)

    val wb = BehaviorTestKit(worker.addWishLink(0, Wish("wish"), Future.successful(prevMessage)))
    wb.run(BaseWorker.ProcessMessage(testMessage))
    bot.req shouldBe SendMessage(0L, notFinishedWish)
  }

  it should "correctly pass correct user to save state" in {
    val worker = new Worker(dbManager, bot)
    val prevMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private),
      text = Some(descRequired), replyMarkup = worker.skipKeyboard)
    val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private),
      text = Some("Buy me google pls"), from = Some(testUser))

    val wish = Wish("I want google", Some("google.com"))
    val wb = BehaviorTestKit(worker.addWishDescription(0, wish, Future.successful(prevMessage)))
    wb.run(BaseWorker.ProcessMessage(testMessage))
    val message = wb.selfInbox().receiveMessage()
    message match {
      case Worker.SaveWish(Some(user)) => user shouldBe User(0, isBot = false, "Dima")
      case _ => fail("Incorrect user passed")
    }
  }

  it should "correctly construct wish through states" in {
    val worker = new Worker(dbManager, bot)
    val prevMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private),
      text = Some(descRequired), replyMarkup = worker.skipKeyboard)
    val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private),
      text = Some("Buy me google pls"), from = Some(testUser))

    val wish = Wish("I want google", Some("google.com"))
    val wb = BehaviorTestKit(worker.addWishDescription(0, wish, Future.successful(prevMessage)))
    wb.run(BaseWorker.ProcessMessage(testMessage))
    wb.run(Worker.SaveWish(Some(testUser)))

    println(dbManager.savedWish.name, dbManager.savedWish.link, dbManager.savedWish.description)
    dbManager.savedWish shouldBe Wish("I want google", Some("google.com"), Some("Buy me google pls"))
  }

}

/* P.S:
    /\/\         /\/\
    \  /         \  /
      \\  \\|//  //
       \\_(-_-)_//
          \ | /
           –o–
          / | \
          || ||
         _|| ||_
*/

object WorkerSpec extends MockitoSugar {
  val testUser: User = User(0, isBot = false, firstName = "Dima")
  val db = mock[Database]
  val dbManager = new MockDBManager2(db)
  val bot = new TestBot2("")
}

class TestBot2(token: String) extends TelegramBot with Commands[Future] {
  var req: Request[Message] = SendMessage(0L, "")

  class TestRequestHandler extends FutureSttpClient(token) {

    override def apply[R](request: Request[R]): Future[R] = {
      request match {
        case r: SendMessage => req = r
        case _ =>
      }
      super.apply(request)
    }
  }

  implicit val backend: SttpBackend[Future, Nothing] = AkkaHttpBackend()
  override val client: RequestHandler[Future] = new TestRequestHandler()
}

class MockDBManager2(db: Database) extends DBManager(db) {
  var savedWish: Wish = Wish("")

  override def insertWish(userId: Int, wish: Wish): Future[Option[Int]] = {
    savedWish = wish
    Future.successful(Some(1))
  }
}
