package tgbot.wishlist.bot

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.{Args, Commands, InlineQueries, whenOrElse}
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.methods.{EditMessageText, ParseMode, SendMessage}
import com.bot4s.telegram.models._
import com.softwaremill.sttp.SttpBackend
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory, StrictLogging}
import tgbot.wishlist.bot.BotMessages._
import tgbot.wishlist.db.{DBManager, UserWishesRow}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


class WishListBot(val token: String)
    extends TelegramBot
    with Polling
    with Commands[Future]
    with InlineQueries[Future]
    with ChatSplitter {

  LoggerConfig.factory = PrintLoggerFactory()
  LoggerConfig.level = LogLevel.DEBUG

  implicit val backend: SttpBackend[Future, Nothing] = Utils.SttpBackends.default
  override val client: RequestHandler[Future] = new FutureSttpClient(token)

  def nonEmptyQuery(iq: InlineQuery): Boolean = iq.query.nonEmpty

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
        wishes.length match {
          case n if n > 0 => replyMd(getPrintableWishes(wishes.take(itemCnt)))
          case 0 => reply(emptyList)
          case _ => logger.error("Returned length of wishes from db is negative.")
        }
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

  whenOrElse(onInlineQuery, nonEmptyQuery) { implicit inQuery =>
    inQuery.query match {
      case "share" =>
        for {
          wishes <- getIndexedWishes(Some(inQuery.from))
        } yield {
          val prettyWishes = InputTextMessageContent(getPrintableWishes(wishes), parseMode = Some(ParseMode.Markdown))
          val result = InlineQueryResultArticle(id = s"${inQuery.from}", title = "Wishlist", prettyWishes)
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

trait ChatSplitter extends ActorBroker with Commands[Future] with StrictLogging {
  type Sessions = Map[Long, ActorRef[Worker.State]]

  private implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val system: ActorSystem[Update] = ActorSystem(ChatBroker(), "broker")
  override val broker: Option[ActorRef[Update]] = Some(system)

  object ChatBroker {
    def apply(): Behavior[Update] = processUpdates(Map.empty)

    def handleUpdateData(update: Update): Option[(Message, Worker.State)] = {
      (update.message, update.callbackQuery) match {
        case (Some(msg), None) => Some((msg, Worker.ProcessMessage(msg)))
        case (None, Some(cb))  => cb.message.map { msg => (msg, Worker.ProcessCallback(msg, cb)) }
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
            val worker = ctx.spawn(Worker(chatId), s"worker_$chatId")
            ctx.watch(worker)
            worker
          })
          handler ! command
          processUpdates(sessions + (chatId -> handler))
        }

      nextState.getOrElse(Behaviors.same)
    }

  }

  object Worker {
    sealed trait State
    case class ProcessMessage(msg: Message) extends State
    case class ProcessCallback(baseMsg: Message, cb: CallbackQuery) extends State
    case class AddName(name: String) extends State
    case class AddLink(link: String) extends State
    case class AddDescription(desc: String) extends State
    case class SaveWish(userOpt: Option[User]) extends State

    val skipButton: InlineKeyboardButton = InlineKeyboardButton("Skip", callbackData = Some("skip"))
    val skipKeyboard: Option[InlineKeyboardMarkup] = Some(InlineKeyboardMarkup.singleColumn(Seq(skipButton)))

    def apply(chatId: Long): Behavior[State] = idleState(chatId)

    def idleState(chatId: Long): Behavior[State] = Behaviors.receive { (_, msg) =>
      val nextState: Option[Behavior[State]] = msg match {
        case ProcessMessage(message) =>
          for {
            com <- command(message)
          } yield {
            com.cmd match {
              case "add" =>
                handleArguments(message, commandArguments) match {
                  case Some(name) =>
                    val linkMsgFut = request(SendMessage(chatId, linkRequired, replyMarkup = skipKeyboard))
                    addWishLink(chatId, Wish(name), linkMsgFut)
                  case None =>
                    request(SendMessage(chatId, nameRequired))
                    Behaviors.same
                }

              case _ => Behaviors.same
            }
          }

        case _ => Some(Behaviors.same)
      }
      nextState.getOrElse(Behaviors.same)
    }

    def addWishLink(chatId: Long, wish: Wish, msgFut: Future[Message]): Behavior[State] =
      Behaviors.receive { (ctx, msg) =>
        msg match {
          case ProcessMessage(message) =>
            removeMarkup(msgFut)
            command(message) match {
              case Some(_) => // item creation was interrupted by another command
                request(SendMessage(chatId, notFinishedWish))
                ctx.self ! ProcessMessage(message)
                idleState(chatId)
              case None =>
                val descMsgFut = request(SendMessage(chatId, descRequired, replyMarkup = skipKeyboard))
                val link = handleArguments(message, textTokens)
                addWishDescription(chatId, Wish(wish.name, link), descMsgFut)
            }

          case ProcessCallback(baseMsg, cb) => cb.data match {
            case Some("skip") =>
              request(getSkippedMessageEdit(baseMsg))
              val descMsgFut = request(SendMessage(chatId, descRequired, replyMarkup = skipKeyboard))
              addWishDescription(chatId, wish, descMsgFut)
            case _ => Behaviors.same
          }

          case _ => Behaviors.same
        }
      }

    def addWishDescription(chatId: Long, wish: Wish, msgFut: Future[Message]): Behavior[State] =
      Behaviors.receive { (ctx, msg) =>
        msg match {
          case ProcessMessage(message) =>
            removeMarkup(msgFut)
            command(message) match {
              case Some(_) => // item creation was interrupted by another command
                request(SendMessage(chatId, notFinishedWish))
                ctx.self ! ProcessMessage(message)
                idleState(chatId)
              case None =>
                val desc = handleArguments(message, textTokens)
                val finalWish = Wish(wish.name, wish.link, desc)
                ctx.self ! SaveWish(message.from)
                saveWish(chatId, finalWish)
            }

          case ProcessCallback(baseMsg, cb) => cb.data match {
            case Some("skip") =>
              request(getSkippedMessageEdit(baseMsg))
              ctx.self ! SaveWish(Some(cb.from))
              saveWish(chatId, wish)
            case _ => Behaviors.same
          }

          case _ => Behaviors.same
        }
      }

    def saveWish(chatId: Long, wish: Wish): Behavior[State] = Behaviors.receive { (_, msg) =>
      msg match {
        case SaveWish(userOpt) =>
          userOpt match {
            case Some(user) => DBManager.insertWish(user.id, wish).flatMap {
              case Some(1) => request(SendMessage(chatId, successfulCreation))
              case _       => request(SendMessage(chatId, failedCreation))
            }
            case _ => Future.successful(None)
          }
          idleState(chatId)

        case _ => Behaviors.same
      }
    }

    def removeMarkup(msgFut: Future[Message]): Unit = {
      msgFut.onComplete {
        case Success(msg) => request(getSkippedMessageEdit(msg, ""))
        case Failure(exception) => logger.error(s"Future failed with exception $exception")
      }
    }

    def getSkippedMessageEdit(msg: Message, bonusText: String = " -> <b>Skipped</b>."): EditMessageText = {
      val text = msg.text.getOrElse("")
      EditMessageText(Some(msg.chat.id), Some(msg.messageId), replyMarkup = None,
        text = text + bonusText, parseMode = Some(ParseMode.HTML))
    }

    def handleArguments(m: Message, extractor: Message => Option[Args]): Option[String] =
      for { args <- extractor(m) if args.nonEmpty } yield { args.mkString(" ") }
  }
}
