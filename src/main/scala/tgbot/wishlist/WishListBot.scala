package tgbot.wishlist

import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.{ActorBroker, AkkaDefaults, RequestHandler}
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.methods.{SendMessage, SendPoll}
import com.bot4s.telegram.models.{Message, Update}
import com.softwaremill.sttp.SttpBackend
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory}
import akka.actor.{Actor, ActorRef, Props, Terminated}

import scala.collection.mutable
import scala.concurrent.Future

class WishListBot(val token: String)
    extends TelegramBot
    with Polling
    with Commands[Future]
    with PerChatRequests {

  LoggerConfig.factory = PrintLoggerFactory()
  LoggerConfig.level = LogLevel.TRACE

  implicit val backend: SttpBackend[Future, Nothing] = SttpBackends.default
  override val client: RequestHandler[Future] = new FutureSttpClient(token)

  onCommand("start") { implicit msg =>
    reply("Greetings!\nTo add an item to the wishlist, please type /add").void
  }

  onCommand("create") { implicit msg =>
    request(SendPoll(msg.chat.id, "Q1", Array("opt1", "opt2"))).void
  }

}

trait PerChatRequests
    extends ActorBroker
    with Commands[Future]
    with AkkaDefaults {

  override val broker: Option[ActorRef] = Some(
    system.actorOf(Props(new Broker), "broker")
  )

  class Broker extends Actor {
    val chatActors: mutable.Map[Long, ActorRef] = collection.mutable.Map[Long, ActorRef]()

    def receive: Receive = {
      case u: Update =>
        u.message.foreach { m =>
          val id = m.chat.id
          val handler = chatActors.getOrElseUpdate(m.chat.id, {
            val worker = system.actorOf(Props(new Worker), s"worker_$id")
            context.watch(worker)
            worker
          })
          handler ! m
        }

      case Terminated(worker) =>
        // This should be faster
        chatActors.find(_._2 == worker).foreach {
          case (k, _) => chatActors.remove(k)
        }

      case _ =>
    }
  }

  // For every chat a new worker actor will be spawned.
  // All requests will be routed through this worker actor; allowing to maintain a per-chat state.
  class Worker extends Actor {
    def receive: Receive = {
      case m: Message =>
        println(m.text)
//        request(SendMessage(m.chat.id, self.toString))
      case _ =>
    }
  }
}
