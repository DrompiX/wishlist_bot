package tgbot.wishlist.bot

import cats.MonadError
import com.bot4s.telegram.api.BotBase
import com.bot4s.telegram.future.BotExecutionContext
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.okhttp.OkHttpFutureBackend
import cats.instances.future._

import scala.concurrent.Future

object Utils {

}

trait CustomBot extends BotBase[Future] with BotExecutionContext {
  override val monad: MonadError[Future, Throwable] = MonadError[Future, Throwable]
}
