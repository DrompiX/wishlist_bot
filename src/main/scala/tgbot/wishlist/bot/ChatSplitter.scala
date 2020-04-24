package tgbot.wishlist.bot

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.models.{CallbackQuery, Message, Update}
import slogging.StrictLogging

import scala.concurrent.Future

trait ChatSplitter
  extends ActorBroker
    with Commands[Future]
    with StrictLogging {

  val workerActor: BaseWorker
  val brokerActor: UpdateBroker

  implicit val system: ActorSystem[Update]// = ActorSystem(brokerActor(), "broker")
  override val broker: Option[ActorRef[Update]]// = Some(system)
}

trait UpdateBroker {
  type Sessions = Map[Long, ActorRef[BaseWorker.State]]

  def apply(): Behavior[Update] = processUpdates(Map.empty)
  def processUpdates(sessions: Sessions): Behavior[Update]
}

trait BaseWorker {
  import BaseWorker._
  def apply(chatId: Long): Behavior[State]
}

object BaseWorker {
  trait State
  case class ProcessMessage(msg: Message) extends State
  case class ProcessCallback(baseMsg: Message, cb: CallbackQuery) extends State
}


