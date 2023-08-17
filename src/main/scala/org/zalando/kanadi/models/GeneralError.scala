package org.zalando.kanadi.models

import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}

class GeneralError(val problem: Problem, override val httpRequest: HttpRequest, override val httpResponse: HttpResponse)
    extends HttpServiceError(httpRequest, httpResponse, Right(problem)) {
  override def getMessage: String = s"Error from server, response is $problem"
}

final case class OtherError(error: BasicServerError) extends Exception {
  override def getMessage: String = s"Error from server, response is $error"
}

final case class SchemaNotFoundError(schemaString: String) extends Exception {
  override def getMessage: String = s"Schema with value $schemaString not found on server"
}

class ExpectedHeader(val headerName: String,
                     override val httpRequest: HttpRequest,
                     override val httpResponse: HttpResponse)
    extends HttpServiceError(httpRequest, httpResponse, Left("")) {
  override def getMessage: String =
    s"Expected header with name: $headerName, request is $httpRequest, response is $httpResponse"
}
