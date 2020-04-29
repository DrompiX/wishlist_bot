package tgbot.wishlist.bot

import akka.actor.testkit.typed.Effect.Spawned
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, BehaviorTestKit}
import akka.actor.typed.DispatcherSelector
import cats.MonadError
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.Commands
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.bot4s.telegram.models.{CallbackQuery, Chat, ChatType, Message, Update, User}
import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.PostgresProfile.api.Database
import tgbot.wishlist.db.DBManager
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.{ExecutionContextExecutor, Future}

class ChatBrokerSpec extends AnyFlatSpec with Matchers {
  import ChatBrokerSpec._

//  val kit: ActorTestKit = ActorTestKit()
//  implicit val ec: ExecutionContextExecutor = kit.system.dispatchers.lookup(DispatcherSelector.default())

  it should "handle updates correctly" in {
    val broker: ChatBroker = ChatBroker(bot.workerActor)
    val correctMsgResult = Some((testMessage, BaseWorker.ProcessMessage(testMessage)))
    broker.handleUpdateData(testUpdateWithMessage) shouldBe correctMsgResult
  }

  it should "handle updates with callback correctly" in {
    val broker: ChatBroker = ChatBroker(bot.workerActor)
    val correctCbResult = Some((testMessage, BaseWorker.ProcessCallback(testMessage, testCallback)))
    broker.handleUpdateData(testUpdateWithCallback) shouldBe correctCbResult
  }

  it should "handle incorrect updates" in {
    val broker: ChatBroker = ChatBroker(bot.workerActor)
    broker.handleUpdateData(Update(0, channelPost = Some(testMessage))) shouldBe None
  }

  it should "spawn new worker for unknown chat" in {
    val worker = new Worker(dbManager, bot)
    val broker = BehaviorTestKit(ChatBroker(worker)())
    broker.run(testUpdateWithMessage)
    broker.expectEffect(Spawned(worker(0), "worker_0"))
  }

  //    val broker = kit.spawn(ChatBroker(worker)())
  //    broker ! testUpdateWithMessage

}

object ChatBrokerSpec {
  val testUser: User = User(0, isBot = false, firstName = "Dima")
  val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private))
  val testCallback: CallbackQuery = CallbackQuery(id = "id", from = testUser,
                                                  chatInstance = "inst", message = Some(testMessage))
  val testUpdateWithMessage: Update = Update(0, message = Some(testMessage))
  val testUpdateWithCallback: Update = Update(0, callbackQuery = Some(testCallback))
  val config: Config = ConfigFactory.load()
  val db = Database.forConfig("testDB", config); db.close
  val dbManager = new DBManager(db)
  val bot = new WishListBot("", dbManager)
}
