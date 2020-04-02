package tgbot.wishlist

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.okhttp.OkHttpFutureBackend

import scala.concurrent.Future

object Utils {

  object TableGenerator {
    val profile = "slick.jdbc.PostgresProfile"
    val jdbcDriver = "org.postgresql.Driver"
    val url = "jdbc:postgresql://localhost:5432/wishes"
    val outputFolder = "src/main/scala"
    val pkg = "tgbot.wishlist"
    val user = "dima"
    val password = "12345"

    def generate(): Unit = slick.codegen.SourceCodeGenerator.main(
      Array(profile, jdbcDriver, url, outputFolder, pkg, user, password)
    )
  }

}

object SttpBackends {
  val default: SttpBackend[Future, Nothing] = OkHttpFutureBackend()
}
