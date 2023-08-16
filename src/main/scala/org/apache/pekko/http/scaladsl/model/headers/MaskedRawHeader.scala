package org.apache.pekko.http.scaladsl.model.headers

import org.apache.pekko.http.impl.util.{Rendering, _}
import org.apache.pekko.http.javadsl.{model => jm}
import org.apache.pekko.http.scaladsl.model.HttpHeader

/** A Header that replaces value with a mask when converted to a string Used to hide sensitive information (e.g. token
  * values) in logs
  */
final case class MaskedRawHeader(name: String, value: String, mask: String) extends jm.headers.RawHeader {
  override def renderInRequests      = true
  override def renderInResponses     = true
  override val lowercaseName: String = name.toRootLowerCase

  def render[R <: Rendering](r: R): r.type =
    r ~~ name ~~ ':' ~~ ' ' ~~ value

  private def renderWithMask[R <: Rendering](r: R): r.type =
    r ~~ name ~~ ':' ~~ ' ' ~~ mask

  override def toString(): String =
    renderWithMask(new StringRendering).get
}

object MaskedRawHeader {
  def unapply[H <: HttpHeader](header: H): Option[(String, String)] =
    Some(header.name -> header.value)
}
