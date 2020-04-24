package tgbot.wishlist.bot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.bot4s.telegram.models.{Message, Update}

case class ChatBroker(worker: BaseWorker) extends UpdateBroker {

  def handleUpdateData(update: Update): Option[(Message, BaseWorker.State)] = {
    (update.message, update.callbackQuery) match {
      case (Some(msg), None) => Some((msg, BaseWorker.ProcessMessage(msg)))
      case (None, Some(cb))  => cb.message.map { msg => (msg, BaseWorker.ProcessCallback(msg, cb)) }
      case _                 => None // Don't process other types of updates
    }
  }

  def processUpdates(sessions: Sessions): Behavior[Update] = Behaviors.receive { (ctx, update) =>
    val nextState: Option[Behavior[Update]] =
      for {
        (msg, command) <- handleUpdateData(update)
      } yield {
        val chatId = msg.chat.id
        val handler = sessions.getOrElse(chatId, {
          val newWorker = ctx.spawn(worker(chatId), s"worker_$chatId")
          ctx.watch(newWorker)
          newWorker
        })

        handler ! command
        processUpdates(sessions + (chatId -> handler))
      }

    nextState.getOrElse(Behaviors.same)
  }

}
