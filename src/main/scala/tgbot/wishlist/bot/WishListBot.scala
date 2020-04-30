package tgbot.wishlist.bot

import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.{Commands, InlineQueries, RegexCommands, whenOrElse}
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.methods.ParseMode
import com.bot4s.telegram.models._
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory}
import tgbot.wishlist.bot.BotMessages._
import tgbot.wishlist.db.{DBManager, UserWishesRow}

import scala.concurrent.Future
import scala.util.Try


class WishListBot(val token: String, dbManager: DBManager)
    extends TelegramBot
    with Polling
    with Commands[Future]
    with InlineQueries[Future]
    with RegexCommands[Future]
    with ChatSplitter {

  LoggerConfig.factory = PrintLoggerFactory()
  LoggerConfig.level = LogLevel.TRACE

  override val workerActor: BaseWorker = new Worker(dbManager, this)
  override val brokerActor: UpdateBroker = ChatBroker(workerActor)

  implicit val backend: SttpBackend[Future, Nothing] = AkkaHttpBackend()
  override val client: RequestHandler[Future] = new FutureSttpClient(token)

  def nonEmptyQuery(iq: InlineQuery): Boolean = iq.query.nonEmpty

  private def getIndexedWishes(userOpt: Option[User]): Future[List[(Int, UserWishesRow)]] =
    userOpt match {
      case Some(user) =>
        dbManager.getUserWishes(user.id).map { wishes => List.range(1, wishes.length + 1).zip(wishes)}
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
            dbManager.deleteWish(wishes(normN)._2.id.getOrElse(-1)).map {
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

  onRegex(".*".r) { implicit msg => _ => reply(unknownCommand).void }

  object Int {
    def unapply(s: String): Option[Int] = Try(s.toInt).toOption
  }

}
