package org.zalando.kanadi

import akka.http.scaladsl.coding._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpEntity, HttpHeader, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import cats.syntax.either._
import com.typesafe.scalalogging.CanLog
import io.circe._
import org.mdedetrich.webmodels.RequestHeaders.`X-Flow-ID`
import org.mdedetrich.webmodels.{FlowId, OAuth2Token, Problem}
import org.mdedetrich.webmodels.circe._
import org.slf4j.MDC
import org.zalando.kanadi.models._

import scala.concurrent.{ExecutionContext, Future}

package object api {
  private[api] val xNakadiStreamIdHeader = "X-Nakadi-StreamId"
  @inline private[api] def randomFlowId() =
    FlowId(java.util.UUID.randomUUID().toString)

  object defaults {
    private[api] implicit val printer: Printer =
      Printer.noSpaces.copy(dropNullValues = true)

    def baseHeaders(flowId: FlowId) =
      List(RawHeader(`X-Flow-ID`, flowId.value), `Accept-Encoding`(HttpEncodings.gzip, HttpEncodings.deflate))

    def decodeCompressed(response: HttpResponse): HttpResponse = {
      val decoder = response.encoding match {
        case HttpEncodings.gzip =>
          Gzip
        case HttpEncodings.deflate =>
          Deflate
        case _ =>
          NoCoding
      }

      decoder.decodeMessage(response)
    }
  }

  private[api] def toHeader(oAuth2Token: OAuth2Token)(implicit kanadiHttpConfig: HttpConfig): HttpHeader =
    if (kanadiHttpConfig.censorOAuth2Token)
      CensoredRawHeader("Authorization", s"Bearer ${oAuth2Token.value}", "Bearer <secret>")
    else RawHeader("Authorization", s"Bearer ${oAuth2Token.value}")

  private[api] def stripAuthToken(request: HttpRequest)(implicit kanadiHttpConfig: HttpConfig): HttpRequest = {
    val headers = request.headers.map {
      case Authorization(OAuth2BearerToken(token)) =>
        toHeader(OAuth2Token(token))
      case rest => rest
    }
    request.withHeaders(headers)
  }

  private[kanadi] implicit final val canLogFlowId: CanLog[FlowId] = new CanLog[FlowId] {
    override def logMessage(originalMsg: String, flowId: FlowId): String = {
      MDC.put("flow_id", flowId.value)
      originalMsg
    }

    override def afterLog(flowId: FlowId): Unit =
      MDC.remove("flow_id")
  }

  def processNotSuccessful(request: HttpRequest, response: HttpResponse)(
      implicit materializer: Materializer,
      executionContext: ExecutionContext): Future[Nothing] =
    for {
      stringOrProblem <- unmarshalStringOrProblem(response.entity)
    } yield {
      stringOrProblem match {
        case Left(body) =>
          parser.parse(body).flatMap(_.as[BasicServerError]) match {
            case Left(_) =>
              throw new HttpServiceError(request, response, stringOrProblem)
            case Right(basicServerError) =>
              throw OtherError(basicServerError)
          }
        case Right(problem) =>
          throw new GeneralError(problem, request, response)
      }
    }

  private[kanadi] def maybeStringToProblem(string: String): Option[Problem] = {
    import org.mdedetrich.webmodels.circe._
    if (string.isEmpty)
      None
    else
      for {
        asJson    <- io.circe.parser.parse(string).right.toOption
        asProblem <- asJson.as[Problem].right.toOption
      } yield asProblem
  }

  private[kanadi] def unmarshalStringOrProblem(entity: HttpEntity)(
      implicit materializer: Materializer,
      executionContext: ExecutionContext): Future[Either[String, Problem]] =
    for {
      asString <- Unmarshal(entity).to[String].recover {
                   case Unmarshaller.NoContentException => ""
                 }
      tryDecodeAsProblem = maybeStringToProblem(asString)

    } yield tryDecodeAsProblem.toRight(asString)
}
