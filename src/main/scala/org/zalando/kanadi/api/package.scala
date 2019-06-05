package org.zalando.kanadi

import akka.http.scaladsl.coding._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{ContentTypes, HttpHeader, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.typesafe.scalalogging.CanLog
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
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

  def processNotSuccessful(response: HttpResponse)(implicit materializer: Materializer,
                                                   executionContext: ExecutionContext): Future[Nothing] =
    for {
      json <- Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
               .to[Json]
    } yield {
      json.as[Problem] match {
        case Left(_) =>
          json.as[BasicServerError] match {
            case Left(error) =>
              throw error
            case Right(basicServerError) =>
              throw OtherError(basicServerError)
          }
        case Right(problem) => throw new GeneralError(problem)
      }
    }
}
