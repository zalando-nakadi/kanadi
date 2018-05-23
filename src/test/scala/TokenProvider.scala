import org.mdedetrich.webmodels.{OAuth2Token, OAuth2TokenProvider}

import scala.concurrent.Future

object TokenProvider {
  val oAuth2TokenProvider = Option(OAuth2TokenProvider(() => Future.successful(OAuth2Token(sys.props("TOKEN")))))
}
