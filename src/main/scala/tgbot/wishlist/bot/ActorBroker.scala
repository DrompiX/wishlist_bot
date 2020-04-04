package tgbot.wishlist.bot

import akka.actor.typed.ActorRef
import com.bot4s.telegram.api.BotBase
import com.bot4s.telegram.models.{Update, User}

import scala.concurrent.Future

trait ActorBroker extends BotBase[Future] {
  def broker: Option[ActorRef[Update]]

  override def receiveUpdate(u: Update, botUser: Option[User]): Future[Unit] = {
    broker.foreach(_ ! u)
    super.receiveUpdate(u, botUser)
  }
}
