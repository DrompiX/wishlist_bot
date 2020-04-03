package tgbot.wishlist

import cats.instances.future._
import cats.syntax.functor._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.bot4s.telegram.api.{BotBase, RequestHandler}
import com.bot4s.telegram.api.declarative.{Args, Commands, InlineQueries, whenOrElse}
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.methods.{ParseMode, SendMessage}
import com.bot4s.telegram.models.{InlineQuery, InlineQueryResultArticle, InputTextMessageContent, Message, Update, User}
import com.softwaremill.sttp.SttpBackend
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory}

import scala.util.Try
import scala.concurrent.{ExecutionContext, Future}
import tgbot.wishlist.BotMessages._


class WishListBot(val token: String)
    extends TelegramBot
    with Polling
    with Commands[Future]
    with InlineQueries[Future]
    with ChatSplitter {

  LoggerConfig.factory = PrintLoggerFactory()
  LoggerConfig.level = LogLevel.DEBUG

  implicit val backend: SttpBackend[Future, Nothing] = SttpBackends.default
  override val client: RequestHandler[Future] = new FutureSttpClient(token)

  private def getIndexedWishes(userOpt: Option[User]): Future[List[(Int, UserWishesRow)]] =
    userOpt match {
      case Some(user) =>
        DBManager.getUserWishes(user.id).map { wishes => List.range(1, wishes.length + 1).zip(wishes)}
      case _ =>
        Future.successful(List.empty[(Int, UserWishesRow)])
    }

  private def getPrintableWishes(wishes: List[(Int, UserWishesRow)]): String = {
    val wishesPretty = wishes.map { case (id, wish) =>
      s"🏷 $id. " + Wish('`' + wish.wishName + '`', wish.wishLink, wish.wishDesc)
    }
    val sep = List.fill(25)("–").mkString
    wishesPretty.mkString(s"\n$sep\n")
  }

  onCommand('start) { implicit msg => reply(greetingText).void }

  onCommand('help) { implicit msg => reply(helpText).void }

  onCommand('list) { implicit msg =>
    withArgs { args =>
      for {
        wishes <- getIndexedWishes(msg.from)
      } yield {
        val itemCnt = args match { case Seq(Int(n)) if n > 0 => n; case _ => wishes.length }
        replyMd(getPrintableWishes(wishes.take(itemCnt)))
      }
    }
  }

  onCommand('delete) { implicit msg =>
    withArgs {
      case Seq(Int(n)) =>
        val normN = n - 1
        for {
          wishes <- getIndexedWishes(msg.from)
        } yield {
          if (normN >= 0 && normN < wishes.length)
            DBManager.deleteWish(wishes(normN)._2.id.getOrElse(-1)).map {
              case 1 => reply(successfulRemoval)
              case _ => reply(failedRemoval)
            }
          else
            reply(incorrectId(1, wishes.length))
        }
      case _ => reply(idRequired).void
    }
  }

  def nonEmptyQuery(iq: InlineQuery): Boolean = iq.query.nonEmpty

  whenOrElse(onInlineQuery, nonEmptyQuery) { implicit inQuery =>
    inQuery.query match {
      case "share" =>
        for {
          wishes <- getIndexedWishes(Some(inQuery.from))
        } yield {
          val prettyWishes = InputTextMessageContent(getPrintableWishes(wishes))
          val result = InlineQueryResultArticle(id = "1", title = "Wishlist", prettyWishes)
          answerInlineQuery(Seq(result))
        }

      case _ => answerInlineQuery(Seq()).void
    }
  } /* empty query */ {
    answerInlineQuery(Seq())(_).void
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
