package tgbot.wishlist

import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.methods.SendPoll
import com.softwaremill.sttp.SttpBackend
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory}

import scala.util.Try
import scala.concurrent.Future

class WishListBot(val token: String)
    extends TelegramBot
    with Polling
    with Commands[Future] {

  LoggerConfig.factory = PrintLoggerFactory()
  LoggerConfig.level = LogLevel.TRACE

  implicit val backend: SttpBackend[Future, Nothing] = SttpBackends.default
  override val client: RequestHandler[Future] = new FutureSttpClient(token)

  onCommand("create") { implicit msg =>
    request(SendPoll(msg.chat.id, "Q1", Array("opt1", "opt2"))).void
  }
  
}
