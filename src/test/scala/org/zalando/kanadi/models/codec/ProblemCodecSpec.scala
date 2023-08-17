package org.zalando.kanadi.models.codec

import io.circe.parser._
import org.scalatest.EitherValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.zalando.kanadi.models.Problem

import java.net.URI

final class ProblemCodecSpec extends AnyFreeSpec with Matchers with EitherValues {
  import ProblemCodec._

  private val problemAstr =
    """{"title":"Forbidden","status":403,"detail":"Access on READ subscription:e1eeeb6d-1819-48de-924e-ded2fe983c9e denied"}"""
  private val problemBstr =
    """{"title":"Conflict","status":409,"detail":"Resetting subscription cursors request is still in progress","instance":"test"}"""
  private val problemCstr = """{"title":"Unauthorized","status":401}"""
  private val problemDstr = """{"type":"about:blank","title":"Unauthorized","status":401}"""

  private val problemA = Problem(
    `type` = None,
    title = "Forbidden",
    status = 403,
    detail = Some("Access on READ subscription:e1eeeb6d-1819-48de-924e-ded2fe983c9e denied"),
    instance = None
  )

  private val problemB = Problem(
    `type` = None,
    title = "Conflict",
    status = 409,
    detail = Some("Resetting subscription cursors request is still in progress"),
    instance = Some("test")
  )

  private val problemC = Problem(
    `type` = None,
    title = "Unauthorized",
    status = 401,
    detail = None,
    instance = None
  )

  private val problemD = Problem(
    `type` = Some(new URI("about:blank")),
    title = "Unauthorized",
    status = 401,
    detail = None,
    instance = None
  )

  "decode problemA" in {
    val expected = problemA
    val actual =
      for {
        parsed  <- parse(problemAstr)
        problem <- parsed.as[Problem]
      } yield problem

    actual.value mustEqual expected
  }

  "decode problemB as" in {
    val expected = problemB
    val actual =
      for {
        parsed  <- parse(problemBstr)
        problem <- parsed.as[Problem]
      } yield problem

    actual.value mustEqual expected
  }

  "decode problemC as" in {
    val expected = problemC
    val actual =
      for {
        parsed  <- parse(problemCstr)
        problem <- parsed.as[Problem]
      } yield problem

    actual.value mustEqual expected
  }

  "decode problemD as" in {
    val expected = problemD
    val actual =
      for {
        parsed  <- parse(problemDstr)
        problem <- parsed.as[Problem]
      } yield problem

    actual.value mustEqual expected
  }
}
