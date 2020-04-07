package org.zalando.kanadi.models

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.mdedetrich.webmodels.Problem

class HttpServiceError(val httpRequest: HttpRequest,
                       val httpResponse: HttpResponse,
                       val stringOrProblem: Either[String, Problem])
    extends Exception
