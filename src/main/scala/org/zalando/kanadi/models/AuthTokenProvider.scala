package org.zalando.kanadi.models

import scala.concurrent.Future

final case class AuthTokenProvider(value: () => Future[AuthToken]) extends AnyVal
