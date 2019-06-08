import org.mdedetrich.webmodels.{OAuth2Token, OAuth2TokenProvider}

import scala.concurrent.Future

object TokenProvider {
  private def emptyStringToNone(string: String): Option[String] =
    if (string.trim.isEmpty)
      None
    else
      Some(string)

  def getDataFromEnv(id: String): String =
    sys.props
      .get(id)
      .flatMap(emptyStringToNone)
      .orElse(sys.env.get(id).flatMap(emptyStringToNone))
      .getOrElse(throw new IllegalArgumentException(s"Unable to get $id from the environment"))

  private val token = getDataFromEnv("TOKEN")

  lazy val environmentTokenProvider = Some(OAuth2TokenProvider(() => Future.successful(OAuth2Token(token))))
}
