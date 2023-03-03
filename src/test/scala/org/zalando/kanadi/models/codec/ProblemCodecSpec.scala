package org.zalando.kanadi.models.codec

import io.circe.parser._
import org.specs2.Specification
import org.specs2.matcher.MatchResult
import org.zalando.kanadi.models.Problem

import java.net.URI

final class ProblemCodecSpec extends Specification {
  import ProblemCodec._

  def is =
    s2"""
    decode problemA as $decodeProblemA
    decode problemB as $decodeProblemB
    decode problemC as $decodeProblemC
    decode problemD as $decodeProblemD
  """

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

  def decodeProblemA: String => MatchResult[Either[Throwable, Problem]] = (name: String) => {
    val expected = problemA
    val actual =
      for {
        parsed  <- parse(problemAstr)
        problem <- parsed.as[Problem]
      } yield problem

    actual must beRight(expected)
  }

  def decodeProblemB: String => MatchResult[Either[Throwable, Problem]] = (name: String) => {
    val expected = problemB
    val actual =
      for {
        parsed  <- parse(problemBstr)
        problem <- parsed.as[Problem]
      } yield problem

    actual must beRight(expected)
  }

  def decodeProblemC: String => MatchResult[Either[Throwable, Problem]] = (name: String) => {
    val expected = problemC
    val actual =
      for {
        parsed  <- parse(problemCstr)
        problem <- parsed.as[Problem]
      } yield problem

    actual must beRight(expected)
  }

  def decodeProblemD: String => MatchResult[Either[Throwable, Problem]] = (name: String) => {
    val expected = problemD
    val actual =
      for {
        parsed  <- parse(problemDstr)
        problem <- parsed.as[Problem]
      } yield problem

    actual must beRight(expected)
  }
}
