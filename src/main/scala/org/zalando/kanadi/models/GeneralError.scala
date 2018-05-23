package org.zalando.kanadi.models

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.mdedetrich.webmodels.Problem

class GeneralError(val problem: Problem) extends Exception {
  override def getMessage: String = s"Error from server, response is $problem"
}

case class OtherError(error: BasicServerError) extends Exception {
  override def getMessage: String = s"Error from server, response is $error"
}

class ExpectedHeader(headerName: String, request: HttpRequest, response: HttpResponse) extends Exception {
  override def getMessage: String =
    s"Expected header with name: $headerName, request is $request, response is $response"
}
