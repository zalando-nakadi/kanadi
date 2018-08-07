package org.zalando.kanadi

import java.net.URI
import io.circe.syntax._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv

class URIDecodingSpec(implicit ec: ExecutionEnv) extends Specification {
  override def is = sequential ^ s2"""
      Encode and decode an absolute URI $absoluteURI
      Encode and decode a relative URI $relativeURI
      """

  def absoluteURI = {
    val absoluteLink = "https://www.google.de/search?q=nyancat"

    val uri     = new URI(absoluteLink)
    val uriJson = uri.asJson
    val result  = uriJson.as[URI]

    result must beRight(uri)
  }

  def relativeURI = {
    val relativeLink = "/doggos?size=puppy"

    val uri     = new URI(relativeLink)
    val uriJson = uri.asJson
    val result  = uriJson.as[URI]

    result must beRight(uri)
  }
}
