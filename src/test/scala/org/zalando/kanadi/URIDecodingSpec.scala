package org.zalando.kanadi

import java.net.URI
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class URIDecodingSpec extends AnyFreeSpec with Matchers with EitherValues {

  "Encode and decode an absolute URI" in {
    val absoluteLink = "https://www.google.de/search?q=nyancat"

    val uri     = new URI(absoluteLink)
    val uriJson = uri.asJson
    val result  = uriJson.as[URI]

    result.value mustEqual uri
  }

  "Encode and decode a relative URI" in {
    val relativeLink = "/doggos?size=puppy"

    val uri     = new URI(relativeLink)
    val uriJson = uri.asJson
    val result  = uriJson.as[URI]

    result.value mustEqual uri
  }
}
