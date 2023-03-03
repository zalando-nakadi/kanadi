package org.zalando.kanadi

import akka.http.scaladsl.coding._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpEntity, HttpHeader, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import com.typesafe.scalalogging.CanLog
import io.circe._
import io.circe.parser._
import org.zalando.kanadi.models.HttpHeaders.XFlowID
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
      List(RawHeader(XFlowID, flowId.value), `Accept-Encoding`(HttpEncodings.gzip, HttpEncodings.deflate))

    def decodeCompressed(response: HttpResponse): HttpResponse = {
      val decoder = response.encoding match {
        case HttpEncodings.gzip =>
          Coders.Gzip
        case HttpEncodings.deflate =>
          Coders.Deflate
        case _ =>
          Coders.NoCoding
      }

      decoder.decodeMessage(response)
    }
  }

  private[api] def toHeader(authToken: AuthToken)(implicit kanadiHttpConfig: HttpConfig): HttpHeader =
    if (kanadiHttpConfig.censorAuthToken)
      CensoredRawHeader("Authorization", s"Bearer ${authToken.value}", "Bearer <secret>")
    else RawHeader("Authorization", s"Bearer ${authToken.value}")

  private[api] def stripAuthToken(request: HttpRequest)(implicit kanadiHttpConfig: HttpConfig): HttpRequest = {
    val headers = request.headers.map {
      case Authorization(OAuth2BearerToken(token)) =>
        toHeader(AuthToken(token))
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

  def processNotSuccessful(request: HttpRequest, response: HttpResponse)(implicit
      materializer: Materializer,
      executionContext: ExecutionContext): Future[Nothing] =
    for {
      stringOrProblem <- unmarshalStringOrProblem(response.entity)
    } yield stringOrProblem match {
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

  private[kanadi] def maybeStringToProblem(string: String): Option[Problem] = {
    import org.zalando.kanadi.models.codec.ProblemCodec._
    if (string.isEmpty)
      None
    else {
      decode[Problem](string).toOption
    }
  }

  private[kanadi] def unmarshalStringOrProblem(entity: HttpEntity)(implicit
      materializer: Materializer,
      executionContext: ExecutionContext): Future[Either[String, Problem]] =
    for {
      asString <- Unmarshal(entity).to[String].recover { case Unmarshaller.NoContentException =>
                    ""
                  }
      tryDecodeAsProblem = maybeStringToProblem(asString)

    } yield tryDecodeAsProblem.toRight(asString)
}
