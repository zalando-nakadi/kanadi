package org.zalando.kanadi.api

import java.net.URI

import defaults._
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, HttpRequest, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import org.mdedetrich.webmodels.{FlowId, OAuth2TokenProvider}
import org.mdedetrich.webmodels.RequestHeaders.`X-Flow-ID`
import org.zalando.kanadi.models._

import scala.concurrent.{ExecutionContext, Future}

case class Registry(baseUri: URI, oAuth2TokenProvider: Option[OAuth2TokenProvider] = None)(implicit
    kanadiHttpConfig: HttpConfig,
    http: HttpExt,
    materializer: Materializer)
    extends RegistryInterface {
  protected val logger: LoggerTakingImplicit[FlowId] = Logger.takingImplicit[FlowId](classOf[Registry])
  private val baseUri_                               = Uri(baseUri.toString)

  /** Lists all of the enrichment strategies supported by this Nakadi installation. Special or custom strategies besides
    * the defaults will be listed here.
    * @param flowId
    *   The flow id of the request, which is written into the logs and passed to called services. Helpful for
    *   operational troubleshooting and log analysis.
    * @return
    *   Returns a list of all enrichment strategies known to Nakadi
    */
  def enrichmentStrategies(implicit
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext): Future[List[String]] = {
    val uri =
      baseUri_.withPath(baseUri_.path / "registry" / "enrichment-strategies")

    val baseHeaders = List(RawHeader(`X-Flow-ID`, flowId.value))

    for {
      headers <- oAuth2TokenProvider match {
                   case None => Future.successful(baseHeaders)
                   case Some(futureProvider) =>
                     futureProvider.value().map { oAuth2Token =>
                       toHeader(oAuth2Token) +: baseHeaders
                     }
                 }
      request   = HttpRequest(HttpMethods.GET, uri, headers)
      _         = logger.debug(request.toString)
      response <- http.singleRequest(request)
      result <- {
        if (response.status.isSuccess()) {
          Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
            .to[List[String]]
        } else
          processNotSuccessful(request, response)
      }
    } yield result
  }

  /** Lists all of the partition resolution strategies supported by this installation of Nakadi. Special or custom
    * strategies besides the defaults will be listed here.
    *
    * Nakadi currently offers these inbuilt strategies:
    *
    * [[org.zalando.kanadi.api.PartitionStrategy.Random]]: Resolution of the target partition happens randomly (events
    * are evenly distributed on the topic's partitions). [[org.zalando.kanadi.api.PartitionStrategy.UserDefined]]:
    * Target partition is defined by the client. As long as the indicated partition exists, Event assignment will
    * respect this value. Correctness of the relative ordering of events is under the responsibility of the Producer.
    * Requires that the client provides the target partition on [[org.zalando.kanadi.api.Metadata.partition]] (See
    * [[org.zalando.kanadi.api.Metadata]]). Failure to do so will reject the publishing of the
    * [[org.zalando.kanadi.api.Event]]. [[org.zalando.kanadi.api.PartitionStrategy.Hash]]: Resolution of the partition
    * follows the computation of a hash from the value of the fields indicated in the EventType's
    * [[org.zalando.kanadi.api.EventType.partitionKeyFields]], guaranteeing that Events with same values on those fields
    * end in the same partition. Given the event type's category is DataChangeEvent, field path is considered relative
    * to "data".
    *
    * @param flowId
    *   The flow id of the request, which is written into the logs and passed to called services. Helpful for
    *   operational troubleshooting and log analysis.
    * @return
    *   Returns a list of all partitioning strategies known to Nakadi
    */
  def partitionStrategies(implicit
      flowId: FlowId = randomFlowId(),
      executionContext: ExecutionContext): Future[List[PartitionStrategy]] = {
    val uri =
      baseUri_.withPath(baseUri_.path / "registry" / "partition-strategies")

    val baseHeaders = List(RawHeader(`X-Flow-ID`, flowId.value))

    for {
      headers <- oAuth2TokenProvider match {
                   case None => Future.successful(baseHeaders)
                   case Some(futureProvider) =>
                     futureProvider.value().map { oAuth2Token =>
                       toHeader(oAuth2Token) +: baseHeaders
                     }
                 }
      request   = HttpRequest(HttpMethods.GET, uri, headers)
      _         = logger.debug(request.toString)
      response <- http.singleRequest(request)
      result <- {
        if (response.status.isSuccess()) {
          Unmarshal(response.entity.httpEntity.withContentType(ContentTypes.`application/json`))
            .to[List[PartitionStrategy]]
        } else
          processNotSuccessful(request, response)
      }
    } yield result
  }

}
