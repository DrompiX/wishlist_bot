package tgbot.wishlist.bot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.bot4s.telegram.api.declarative.{Args, Commands}
import com.bot4s.telegram.methods.{EditMessageText, ParseMode, SendMessage}
import com.bot4s.telegram.models.{InlineKeyboardButton, InlineKeyboardMarkup, Message, User}
import slogging.StrictLogging
import tgbot.wishlist.bot.BotMessages._
import tgbot.wishlist.db.DBManager

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Worker {
  import BaseWorker._
  case class AddName(name: String) extends State
  case class AddLink(link: String) extends State
  case class AddDescription(desc: String) extends State
  case class SaveWish(userOpt: Option[User]) extends State
}

class Worker(dbManager: DBManager, commands: Commands[Future])
            (implicit ec: ExecutionContext) extends BaseWorker with StrictLogging {
  import BaseWorker._
  import Worker._
  import commands._

  val skipButton: InlineKeyboardButton = InlineKeyboardButton("Skip", callbackData = Some("skip"))
  val skipKeyboard: Option[InlineKeyboardMarkup] = Some(InlineKeyboardMarkup.singleColumn(Seq(skipButton)))

  override def apply(chatId: Long): Behavior[State] = idleState(chatId)

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
          case Some(user) => dbManager.insertWish(user.id, wish).flatMap {
            case Some(1) => request(SendMessage(chatId, successfulCreation))
            case Some(0) => request(SendMessage(chatId, failedCreation))
            case _       => Future.successful(logger.error(s"More than one row was affected for user ${user.id}"))
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
