package tgbot.wishlist

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.{ActorBroker, AkkaDefaults, RequestHandler}
import com.bot4s.telegram.api.declarative.{Args, Command, Commands}
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.methods.{SendMessage, SendPoll}
import com.bot4s.telegram.models.{Message, Update, User}
import com.softwaremill.sttp.SttpBackend
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory}
import akka.actor.{Actor, ActorRef, FSM, Props, Terminated}

import scala.collection.mutable
import scala.concurrent.Future

trait FSMState
trait FSMData

class WishListBot(val token: String)
    extends TelegramBot
    with Polling
    with Commands[Future]
    with PerChatRequests {

  LoggerConfig.factory = PrintLoggerFactory()
  LoggerConfig.level = LogLevel.DEBUG

  implicit val backend: SttpBackend[Future, Nothing] = SttpBackends.default
  override val client: RequestHandler[Future] = new FutureSttpClient(token)

//  override def receiveMessage(msg: Message): Future[Unit] = {
//    println("here! message -> " + msg)
//    super.receiveMessage(msg)
//  }
//
//  onCommand('start) { implicit msg =>
//    reply("Greetings!\nTo add an item to the wishlist, please type /add").void
//  }
//
//  onCommand('add) { implicit msg =>
//    request(SendPoll(msg.chat.id, "Q1", Array("opt1", "opt2"))).void
//  }

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
//        println("U: " + u)
        u.message.foreach { m =>
//          for (user <- m.from)
//            user.id match {
//              case 198009315 =>
//              case _         =>
//            }

          m.from match {
            case Some(u: User) => u.id match {
              case 198009316 =>
                val id = m.chat.id
                val handler = chatActors.getOrElseUpdate(m.chat.id, {
                  val worker = system.actorOf(Props(new Worker), s"worker_$id")
                  context.watch(worker)
                  worker
                })
                handler ! m
              case _ =>
                request(SendMessage(m.chat.id, "Sorry, this bot is under development."))
            }
          }
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
    import Worker._
    def receive: Receive = {
      case m: Message =>
        command(m) match {
          case Some(com) => com.cmd match {
            case "add" =>
              self ! Add
              handleAddition
          }
        }
//        println("M: " + m.text)
//        println(commandArguments(m))
//        request(SendMessage(m.chat.id, self.toString))
      case _ =>
    }

  }

  object Worker {
    def addProcess(args: Args): Behavior[State] = Behaviors.receive { (ctx, msg) => ??? }

    def handleAddition(implicit m: Message): Unit = commandArguments(m) match {
      case Some(args) => addProcess(args.mkString(" "))
      case None       => reply("")
    }
  }

//  sealed trait BotCommand
//  object Start extends BotCommand
//  object Help extends BotCommand
//  object Add extends BotCommand
//  object Delete extends BotCommand

  sealed trait State
  object Idle extends State
  case class Add() extends State
}
