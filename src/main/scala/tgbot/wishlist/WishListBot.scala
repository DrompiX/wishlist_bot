package tgbot.wishlist

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.{BotBase, RequestHandler}
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.methods.SendMessage
//import com.bot4s.telegram.methods.{SendMessage, SendPoll}
import com.bot4s.telegram.models.{Message, Update, User}
import com.softwaremill.sttp.SttpBackend
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory}
//import akka.actor.{Actor, ActorRef, FSM, Props, Terminated}

import scala.concurrent.Future

trait FSMState
trait FSMData

class WishListBot(val token: String)
    extends TelegramBot
    with Polling
    with Commands[Future]
    with ChatSplitter {

  LoggerConfig.factory = PrintLoggerFactory()
  LoggerConfig.level = LogLevel.DEBUG

  implicit val backend: SttpBackend[Future, Nothing] = SttpBackends.default
  override val client: RequestHandler[Future] = new FutureSttpClient(token)

  onCommand('start) { implicit msg =>
    reply("Greetings!\nTo add an item to the wishlist, please type /add").void
  }

}

trait ActorBroker extends BotBase[Future] {
  def broker: Option[ActorRef[Update]]

  override def receiveUpdate(u: Update, botUser: Option[User]): Future[Unit] = {
    broker.foreach(_ ! u)
    super.receiveUpdate(u, botUser)
  }
}

trait ChatSplitter extends ActorBroker with Commands[Future] {
  type Sessions = Map[Long, ActorRef[Worker.State]]

  implicit val system: ActorSystem[Update] = ActorSystem(ChatBroker(), "broker")
  override val broker: Option[ActorRef[Update]] = Some(system)

  object ChatBroker {
    def apply(): Behavior[Update] = processUpdates(Map.empty)

    def processUpdates(sessions: Sessions): Behavior[Update] = Behaviors.receive { (ctx, update) =>
      val nextState: Option[Behavior[Update]] =
        for {
          message <- update.message
          user <- message.from
        } yield {
          user.id match {
            case 198009316 =>
              val id = message.chat.id
              val handler = sessions.getOrElse(id, {
                val worker = ctx.spawn(Worker(), s"worker_$id")
                ctx.watch(worker)
                worker
              })
              handler ! Worker.ProcessMessage(message)
              processUpdates(sessions + (id -> handler))
            case _ =>
              request(SendMessage(message.chat.id, "Sorry, this bot is under development."))
              Behaviors.same
          }
        }
      nextState.getOrElse(Behaviors.same)
    }

  }


  object Worker {
    sealed trait State
//    object Idle extends State
    object Reset extends State
    case class ProcessMessage(msg: Message) extends State
    case class AddName(name: String) extends State
    case class AddLink(link: String) extends State
    case class AddDescription(desc: String) extends State

    def apply(): Behavior[State] = idleState()

    def idleState(): Behavior[State] = Behaviors.receive { (ctx, msg) =>
      val nextState: Option[Behavior[State]] = msg match {
        case ProcessMessage(message) =>
          for { com <- command(message) } yield {
            com.cmd match {
              case "add" =>
                handleArguments(message) match {
                  case Some(name) =>
                    ctx.self ! AddName(name)
                    addState()
                  case None =>
                    request(SendMessage(message.chat.id, "Please, specify the name of an item after /add"))
                    Behaviors.same
                }

              case _ => Behaviors.same
            }
          }

        case _ => Some(Behaviors.same)
      }
      nextState.getOrElse(Behaviors.same)
    }

    def addState(): Behavior[State] = Behaviors.receive { (ctx, msg) =>
      val nextState: Option[Behavior[State]] = msg match {
        case AddName(name) => ???
        case ProcessMessage(message) => ???
        case Reset => ???
        case _ => ???
      }

      nextState.getOrElse(Behaviors.same)
    }

    def handleArguments(m: Message): Option[String] =
      for {
        args <- commandArguments(m)
        if args.nonEmpty
      } yield { args.mkString(" ") }
    ////        command(m) match {
    ////          case Some(com) => com.cmd match {
    ////            case "add" =>
    ////              handleAddition(m) match {
    ////                case Some(name) =>
    ////                  println("N: " + name)
    ////                  self ! AddName(name)
    ////                  addProcess()
    ////                case None       =>
    ////                  request(SendMessage(m.chat.id, "Please, specify the name of an item after /add"))
    ////              }
    ////
    ////            case _ =>
    ////          }

  }
}

//trait PerChatRequests
//    extends ActorBroker
//    with Commands[Future]
//    with AkkaDefaults {
//
//  override val broker: Option[ActorRef] = Some(
//    system.actorOf(Props(new Broker), "broker")
//  )
//
//  class Broker extends Actor {
//    val chatActors: mutable.Map[Long, ActorRef] = collection.mutable.Map[Long, ActorRef]()
//
//    def receive: Receive = {
//      case u: Update =>
////        println("U: " + u)
//        u.message.foreach { m =>
////          for (user <- m.from)
////            user.id match {
////              case 198009315 =>
////              case _         =>
////            }
//
//          m.from match {
//            case Some(u: User) => u.id match {
//              case 198009316 =>
//                val id = m.chat.id
//                val handler = chatActors.getOrElseUpdate(m.chat.id, {
//                  val worker = system.actorOf(Props(new Worker), s"worker_$id")
//                  context.watch(worker)
//                  worker
//                })
//                handler ! m
//              case _ =>
//                request(SendMessage(m.chat.id, "Sorry, this bot is under development."))
//            }
//          }
//        }
//
//      case Terminated(worker) =>
//        chatActors.find(_._2 == worker).foreach {
//          case (k, _) => chatActors.remove(k)
//        }
//
//      case _ =>
//    }
//  }
//
//  // For every chat a new worker actor will be spawned.
//  // All requests will be routed through this worker actor; allowing to maintain a per-chat state.
//  class Worker {
////    import Worker._
////
////    def receive: Receive = {
////      case m: Message =>
////        println(m.text)
////        command(m) match {
////          case Some(com) => com.cmd match {
////            case "add" =>
////              handleAddition(m) match {
////                case Some(name) =>
////                  println("N: " + name)
////                  self ! AddName(name)
////                  addProcess()
////                case None       =>
////                  request(SendMessage(m.chat.id, "Please, specify the name of an item after /add"))
////              }
////
////            case _ =>
////          }
////        }
////
////      case _ =>
////    }
//
//  }
//
//  object Worker {
//    def addProcess(): Behavior[State] = Behaviors.receive { (ctx, msg) =>
//      msg match {
//        case AddName(name) =>
//          println("here: " + name)
//          Behaviors.same
//        case _ => Behaviors.same
//      }
//    }
//
//    def handleAddition(m: Message): Option[String] =
//      for {
//        args <- commandArguments(m)
//        if args.nonEmpty
//      } yield { args.mkString(" ") }
//  }
//
////  sealed trait BotCommand
////  object Start extends BotCommand
////  object Help extends BotCommand
////  object Add extends BotCommand
////  object Delete extends BotCommand
//
//  sealed trait State
//  object Idle extends State
//  object Reset extends State
//  case class AddName(name: String) extends State
//  case class AddLink(link: String) extends State
//  case class AddDescription(desc: String) extends State
//}
