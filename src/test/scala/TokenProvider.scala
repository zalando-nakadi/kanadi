import org.mdedetrich.webmodels.{OAuth2Token, OAuth2TokenProvider}

import scala.concurrent.Future

object TokenProvider {
  private val token = (sys.props.get("TOKEN") orElse sys.env.get("TOKEN")).getOrElse(
    throw new IllegalArgumentException("Expected token")
  )

  val environmentTokenProvider = Option(OAuth2TokenProvider(() => Future.successful(OAuth2Token(token))))
}
