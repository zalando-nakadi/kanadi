package org.zalando.kanadi.models

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

class HttpServiceError(val httpRequest: HttpRequest,
                       val httpResponse: HttpResponse,
                       val stringOrProblem: Either[String, Problem])
    extends Exception {
  override def getMessage: String = s"General error in server response, $toString"
  override def toString =
    s"Response content is $stringOrProblem, Request is $httpRequest, Response is ${httpResponse.toString()}"
}
