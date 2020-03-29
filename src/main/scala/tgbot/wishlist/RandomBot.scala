package tgbot.wishlist

import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.softwaremill.sttp.SttpBackend
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory}

import scala.util.Try
import scala.concurrent.Future


class RandomBot(val token: String) extends TelegramBot
  with Polling
  with Commands[Future] {

  LoggerConfig.factory = PrintLoggerFactory()
  LoggerConfig.level = LogLevel.TRACE

  implicit val backend: SttpBackend[Future, Nothing] = SttpBackends.default
  override val client: RequestHandler[Future] = new FutureSttpClient(token)

  val rng = new scala.util.Random(System.currentTimeMillis())
  onCommand("coin" or "flip") { implicit msg =>
    reply(if (rng.nextBoolean()) "Head!" else "Tail!").void
  }
  onCommand('real | 'double | 'float) { implicit msg =>
    reply(rng.nextDouble().toString).void
  }
  onCommand("/dice" | "roll") { implicit msg =>
    reply("⚀⚁⚂⚃⚄⚅" (rng.nextInt(6)).toString).void
  }
  onCommand("random" or "rnd") { implicit msg =>
    withArgs {
      case Seq(Int(n)) if n > 0 =>
        reply(rng.nextInt(n).toString).void
      case _ => reply("Invalid argumentヽ(ಠ_ಠ)ノ").void
    }
  }
  onCommand('choose | 'pick | 'select) { implicit msg =>
    withArgs { args =>
      replyMd(if (args.isEmpty) "No arguments provided." else args(rng.nextInt(args.size))).void
    }
  }

  // Int(n) extractor
  object Int {
    def unapply(s: String): Option[Int] = Try(s.toInt).toOption
  }
}