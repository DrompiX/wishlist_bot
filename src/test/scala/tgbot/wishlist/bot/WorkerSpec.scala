package tgbot.wishlist.bot


import akka.actor.testkit.typed.Effect.Spawned
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.PrivateMethodTester._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.bot4s.telegram.models.{CallbackQuery, Chat, ChatType, Message, Update, User}
import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.PostgresProfile.api.Database
import tgbot.wishlist.db.{DBManager, UserWishesRow}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class WorkerSpec extends AnyFlatSpec with Matchers {
  import WorkerSpec._

  import scala.concurrent.ExecutionContext.Implicits.global

//  it should "" in {
//    val worker = BehaviorTestKit(new Worker(dbManager, bot).apply(0))
//    worker.run(BaseWorker.ProcessMessage(testMessage))
//    worker.returnedBehavior match {
//      case Behaviors.same => ???
//      case _ => ???
//    }
//  }

  it should "handle arguments correctly" in {
    val worker = new Worker(dbManager, bot)
    val handle = worker.handleArguments(testMessage, { msg => Some(Seq(msg.text.get, msg.messageId.toString)) })
    handle shouldBe Some("/add hello 0")
  }

}

object WorkerSpec {
  val testUser: User = User(0, isBot = false, firstName = "Dima")
  val testMessage: Message = Message(messageId = 0, date = 0, chat = Chat(0, ChatType.Private), text = Some("/add hello"))
  val testCallback: CallbackQuery = CallbackQuery(id = "id", from = testUser,
    chatInstance = "inst", message = Some(testMessage))
  val testUpdateWithMessage: Update = Update(0, message = Some(testMessage))
  val testUpdateWithCallback: Update = Update(0, callbackQuery = Some(testCallback))
  val config: Config = ConfigFactory.load()
  val db = Database.forConfig("testDB", config); db.close
  val dbManager = new MockDBManager(db)
  val bot = new WishListBot("", dbManager)
}

class MockDBManager(db: Database) extends DBManager(db) {
  override def getUserWishes(userId: Int): Future[Seq[UserWishesRow]] =
    Future.successful(Seq(UserWishesRow(Some(1), 0, "wish1"), UserWishesRow(Some(2), 0, "wish2")))

  override def insertWish(userId: Int, wish: Wish): Future[Option[Int]] = super.insertWish(userId, wish)

  override def deleteWish(rowId: Int): Future[Int] = super.deleteWish(rowId)
}

