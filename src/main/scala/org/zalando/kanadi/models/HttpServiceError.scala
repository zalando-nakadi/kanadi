package org.zalando.kanadi.models

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

class HttpServiceError(val httpRequest: HttpRequest, val httpResponse: HttpResponse) extends Exception
