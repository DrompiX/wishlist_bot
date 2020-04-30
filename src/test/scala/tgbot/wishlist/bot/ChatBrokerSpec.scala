package tgbot.wishlist.bot

import akka.actor.testkit.typed.Effect.Spawned
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.bot4s.telegram.models.{CallbackQuery, Chat, ChatType, Message, Update, User}
import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.PostgresProfile.api.Database

import tgbot.wishlist.db.DBManager


class ChatBrokerSpec extends AnyFlatSpec with Matchers {
  import ChatBrokerSpec._
  import scala.concurrent.ExecutionContext.Implicits.global

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
    val effect = broker.retrieveEffect()
    effect match {
      case Spawned(_, name, _) =>
        name shouldBe s"worker_${testUpdateWithMessage.message.get.chat.id}"
      case _ =>
        fail("Incorrect effect received, expected spawned worker.")
    }
  }

  it should "not spawn new worker for known chat" in {
    val worker = new Worker(dbManager, bot)
    val workerInbox = TestInbox[BaseWorker.State]()
    val broker = BehaviorTestKit(ChatBroker(worker).processUpdates(Map(0L -> workerInbox.ref)))
    broker.run(testUpdateWithMessage)

    broker.hasEffects() shouldBe false
    workerInbox.receiveMessage()
  }

  it should "send messages to correct worker" in {
    val workerProbe1 = TestInbox[BaseWorker.State]()
    val workerProbe2 = TestInbox[BaseWorker.State]()
    val worker = new Worker(dbManager, bot)
    val broker = BehaviorTestKit(ChatBroker(worker).processUpdates(Map(0L -> workerProbe1.ref, 1L -> workerProbe2.ref)))
    broker.run(testUpdateWithMessage)

    workerProbe1.receiveMessage()
    workerProbe2.hasMessages shouldBe false
  }

  it should "send correct types of messages to worker" in {
    val workerProbe = TestInbox[BaseWorker.State]()
    val worker = new Worker(dbManager, bot)
    val broker = BehaviorTestKit(ChatBroker(worker).processUpdates(Map(0L -> workerProbe.ref)))
    broker.run(testUpdateWithMessage)
    broker.run(testUpdateWithCallback)
    val Seq(message1, message2) = workerProbe.receiveAll()
    message1 match {
      case BaseWorker.ProcessMessage(msg) =>
        msg.chat.id shouldBe 0
        msg.text.getOrElse(":)") shouldBe "hello"
      case _ => fail(s"Incorrect message type.")
    }
    message2 match {
      case BaseWorker.ProcessCallback(msg, cb) =>
        msg.chat.id shouldBe 0
        msg.text.getOrElse(":)") shouldBe "hello"
        cb.from.firstName shouldBe "Dima"
      case _ => fail(s"Incorrect message type.")
    }
  }

}

object ChatBrokerSpec {
  val testUser: User = User(0, isBot = false, firstName = "Dima")
  val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private), text = Some("hello"))
  val testCallback: CallbackQuery = CallbackQuery(id = "id", from = testUser,
                                                  chatInstance = "inst", message = Some(testMessage))
  val testUpdateWithMessage: Update = Update(0, message = Some(testMessage))
  val testUpdateWithCallback: Update = Update(0, callbackQuery = Some(testCallback))
  val config: Config = ConfigFactory.load()
  val db = Database.forConfig("testDB", config); db.close
  val dbManager = new DBManager(db)
  val bot = new WishListBot("", dbManager)
}
