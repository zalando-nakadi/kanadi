package org.zalando.kanadi.models

trait HttpHeaders {
  val XFlowID: String = "X-Flow-ID"
}

object HttpHeaders extends HttpHeaders
