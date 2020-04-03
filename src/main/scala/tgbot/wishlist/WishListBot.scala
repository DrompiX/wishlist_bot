package tgbot.wishlist

import cats.instances.future._
import cats.syntax.functor._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.bot4s.telegram.api.{BotBase, RequestHandler}
import com.bot4s.telegram.api.declarative.{Args, Commands}
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.methods.{ParseMode, SendMessage}
import com.bot4s.telegram.models.{Message, Update, User}
import com.softwaremill.sttp.SttpBackend
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory}

import scala.util.Try
import scala.concurrent.{ExecutionContext, Future}

import tgbot.wishlist.BotMessages._


class WishListBot(val token: String)
    extends TelegramBot
    with Polling
    with Commands[Future]
    with ChatSplitter {

  LoggerConfig.factory = PrintLoggerFactory()
  LoggerConfig.level = LogLevel.DEBUG

  implicit val backend: SttpBackend[Future, Nothing] = SttpBackends.default
  override val client: RequestHandler[Future] = new FutureSttpClient(token)

  private def getIndexedWishes(implicit msg: Message): Future[List[(Int, UserWishesRow)]] = {
    msg.from match {
      case Some(user) =>
        DBManager.getUserWishes(user.id).map { wishes => List.range(1, wishes.length + 1).zip(wishes)}
      case _ =>
        Future.successful(List.empty[(Int, UserWishesRow)])
    }
  }

  onCommand('start) { implicit msg => reply(greetingText).void }

  onCommand('help) { implicit msg => reply(helpText).void }

  onCommand('list) { implicit msg =>
    withArgs { args =>
      for {
        wishes <- getIndexedWishes
      } yield {
        val itemCnt = args match { case Seq(Int(n)) if n > 0 => n; case _ => wishes.length }
        val wishesPretty = wishes.take(itemCnt).map { case (id, wish) =>
          s"🏷 $id. " + Wish('`' + wish.wishName + '`', wish.wishLink, wish.wishDesc)
        }
        val sep = List.fill(25)("–").mkString
        replyMd(wishesPretty.mkString(s"\n$sep\n")).void
      }
    }
  }
  //      msg.from match {
  //        case Some(user) =>
  //          val wishesFut = DBManager.getUserWishes(user.id)
  //          for {
  //            wishes <- wishesFut
  //          } yield {
  //            val itemCnt = args match { case Seq(Int(n)) if n > 0 => n; case _ => wishes.length }
  //            val indexedWishes = List.range(1, itemCnt + 1).zip(wishes)
  //            val wishesPretty = indexedWishes.map {
  //              id2wish =>
  //                val (id, wish) = id2wish
  //                s"🏷 $id. " + Wish('`' + wish.wishName + '`', wish.wishLink, wish.wishDesc)
  //            }
  //            val sep = List.fill(25)("–").mkString
  //            replyMd(wishesPretty.mkString(s"\n$sep\n")).void
  //          }
  //        case None => reply("You have no wishes yet! Type /add <wish_name> to create new one.").void
  //      }

  onCommand('delete) { implicit msg =>
    withArgs {
      case Seq(Int(n)) =>
        for {
          wishes <- getIndexedWishes
        } yield {
          if (n - 1 >= 0 && n - 1 < wishes.length)
            msg.from match {
              case Some(user) =>
                DBManager.deleteWish(user.id, wishes(n)._2)//.map { wishes => List.range(1, wishes.length + 1).zip(wishes)}
              case _ =>
                Future.successful(List.empty[(Int, UserWishesRow)])
            }
//            DBManager.deleteWish()
            wishes(n)
        }
      case _ => ???
    }
  }

  object Int {
    def unapply(s: String): Option[Int] = Try(s.toInt).toOption
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

  private implicit val ec: ExecutionContext = ExecutionContext.global
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
            case 198009316 | 89127286 =>
              val id = message.chat.id
              val handler = sessions.getOrElse(id, {
                val worker = ctx.spawn(Worker(), s"worker_$id")
                ctx.watch(worker)
                worker
              })
              handler ! Worker.ProcessMessage(message)
              processUpdates(sessions + (id -> handler))
            case _ =>
              println("ID: " + user.id)
              request(SendMessage(message.chat.id, "Sorry, this bot is under development."))
              Behaviors.same
          }
        }
      nextState.getOrElse(Behaviors.same)
    }

  }

  object Worker {
    sealed trait State
    case class ProcessMessage(msg: Message) extends State
    case class AddName(name: String) extends State
    case class AddLink(link: String) extends State
    case class AddDescription(desc: String) extends State

    def apply(): Behavior[State] = idleState()

    def idleState(): Behavior[State] = Behaviors.receive { (ctx, msg) =>
      val nextState: Option[Behavior[State]] = msg match {
        case ProcessMessage(message) =>
          println("Worker: " + ctx.self)
          for { com <- command(message) } yield {
            com.cmd match {
              case "add" =>
                handleArguments(message, commandArguments) match {
                  case Some(name) =>
                    request(SendMessage(message.chat.id, linkRequired, Some(ParseMode.Markdown)))
                    addWishLink(Wish(name))
                  case None =>
                    request(SendMessage(message.chat.id, nameRequired))
                    Behaviors.same
                }

//              case "delete" =>
//                handleArguments(message, commandArguments) match {
//                  case Some(id) => ???
//                  case None =>
//                    request(SendMessage(message.chat.id, nameRequired))
//                    Behaviors.same
//                }

              case _ => Behaviors.same
            }
          }

        case _ => Some(Behaviors.same)
      }
      nextState.getOrElse(Behaviors.same)
    }

    def addWishLink(wish: Wish): Behavior[State] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case ProcessMessage(message) => command(message) match {
          case Some(_) => // item creation was interrupted by another command
            request(SendMessage(message.chat.id, notFinishedWish))
            ctx.self ! ProcessMessage(message)
            idleState()
          case None =>
            request(SendMessage(message.chat.id, descRequired, Some(ParseMode.Markdown)))
            val link = handleArguments(message, textTokens)
            addWishDescription(Wish(wish.name, link))
        }

        case _ => ???
      }
    }

    def addWishDescription(wish: Wish): Behavior[State] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case ProcessMessage(message) => command(message) match {
          case Some(_) => // item creation was interrupted by another command
            request(SendMessage(message.chat.id, notFinishedWish))
            ctx.self ! ProcessMessage(message)
            idleState()
          case None =>
            val desc = handleArguments(message, textTokens)
            val finalWish = Wish(wish.name, wish.link, desc)
            println("Resulting wish:\n" + finalWish)
            val addFut = message.from match {
              case Some(user) => DBManager.insertWish(user.id, finalWish)
              case _ => Future.successful(None)
            }
            addFut.map {
              case Some(1) => request(SendMessage(message.chat.id, successfulCreation))
              case _       => request(SendMessage(message.chat.id, failedCreation))
            }
//            val isSuccessful = for { add <- addFut } yield { if (add.contains(1)) true else false }
//            isSuccessful.map {
//              case true  =>
//              case false => request(SendMessage(message.chat.id, failedCreation))
//            }


//            DBManager.insertWish(message.from.)
            idleState()
        }

        case _ => ???
      }
    }

    def handleArguments(m: Message, extractor: Message => Option[Args]): Option[String] =
      for {
        args <- extractor(m)
        if args.nonEmpty
      } yield { args.mkString(" ") }

  }
}
