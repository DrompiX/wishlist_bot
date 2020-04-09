package tgbot.wishlist.bot

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.okhttp.OkHttpFutureBackend

import scala.concurrent.Future

object Utils {

  object SttpBackends {
    val default: SttpBackend[Future, Nothing] = OkHttpFutureBackend()
  }

}
