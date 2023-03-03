package org.zalando.kanadi.models

import java.net.URI

/** Problem
  *
  * https://nakadi.io/manual.html#definition_Problem
  */
final case class Problem(
    `type`: Option[URI],
    title: String,
    status: Int,
    detail: Option[String],
    instance: Option[String]
)
