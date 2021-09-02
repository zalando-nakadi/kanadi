name := """kanadi"""

val akkaHttpVersion                    = "10.2.2"
val akkaStreamsJsonVersion             = "0.7.0"
val currentScalaVersion                = "2.12.11"
val scala213Version                    = "2.13.4"
val enumeratumCirceVersion             = "1.5.23"
val circeVersion                       = "0.13.0"
val akkaVersion                        = "2.6.10"
val specs2Version                      = "4.8.0"
val heikoseebergerAkkaHttpCirceVersion = "1.35.3"

ThisBuild / scalaVersion := currentScalaVersion

ThisBuild / crossScalaVersions := Seq(currentScalaVersion, scala213Version)

ThisBuild / versionScheme := Some(VersionScheme.EarlySemVer)

organization := "org.zalando"

Test / fork               := true
Test / parallelExecution  := true
Test / testForkedParallel := true

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8",                  // Specify character encoding used by source files.
  "-explaintypes",          // Explain type errors in more detail.
  "-feature",               // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros", // Allow macro definition (besides implementation and application)
  "-language:higherKinds",         // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
  "-unchecked",                    // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                   // Wrap field accessors to throw an exception on uninitialized access.
  //  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  "-Xfuture",                      // Turn on future language features.
  "-Xlint:adapted-args",           // Warn if an argument list is modified to match the receiver.
  "-Xlint:delayedinit-select",     // Selecting member of DelayedInit.
  "-Xlint:doc-detached",           // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",           // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",              // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",   // A string literal appears to be missing an interpolator id.
  "-Xlint:option-implicit",        // Option.apply used implicit view.
  "-Xlint:package-object-classes", // Class or object defined in package object.
  "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",         // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",            // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",  // A local type parameter shadows a type already in scope.
  //"-Yno-adapted-args", // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ywarn-dead-code",     // Warn when dead code is identified.
  "-Ywarn-numeric-widen", // Warn when numerics are widened.
  "-Ywarn-value-discard"  // Warn when non-Unit expression results are unused.
)

val flagsFor12 = Seq(
  "-Xlint:_",
  "-Ywarn-infer-any",
  "-Ypartial-unification",
  "-Ywarn-inaccessible",              // Warn about inaccessible types in method signatures.
  "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
  "-Xlint:unsound-match",             // Pattern match may not be typesafe.
  "-Ywarn-infer-any",                 // Warn when a type argument is inferred to be `Any`.
  "-Xlint:nullary-override",          // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit",              // Warn when nullary methods return Unit.
  "-opt-inline-from:<sources>"
)

val flagsFor13 = Seq(
  "-Xlint:_",
  "-opt-inline-from:<sources>"
)

scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, n)) if n == 13 =>
      flagsFor13
    case Some((2, n)) if n == 12 =>
      flagsFor12
  }
}

libraryDependencies ++= {
  Seq(
    "com.typesafe.akka"          %% "akka-http"           % akkaHttpVersion % Provided,
    "com.typesafe.akka"          %% "akka-slf4j"          % akkaVersion     % Provided,
    "com.typesafe.akka"          %% "akka-stream"         % akkaVersion     % Provided,
    "org.mdedetrich"             %% "censored-raw-header" % "0.5.0",
    "org.mdedetrich"             %% "webmodels"           % "0.8.1",
    "com.beachape"               %% "enumeratum-circe"    % enumeratumCirceVersion,
    "io.circe"                   %% "circe-parser"        % circeVersion,
    "org.mdedetrich"             %% "akka-stream-circe"   % akkaStreamsJsonVersion,
    "org.mdedetrich"             %% "akka-http-circe"     % akkaStreamsJsonVersion,
    "de.heikoseeberger"          %% "akka-http-circe"     % heikoseebergerAkkaHttpCirceVersion,
    "com.iheart"                 %% "ficus"               % "1.4.7",
    "com.typesafe.scala-logging" %% "scala-logging"       % "3.9.2",
    "ch.qos.logback"              % "logback-classic"     % "1.1.7",
    "org.specs2"                 %% "specs2-core"         % specs2Version   % Test
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, n)) if n == 13 =>
      Seq(
        "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0" % Test
      )
    case _ =>
      Seq.empty
  })
}

Test / scalacOptions ++= Seq("-Yrangepos")

homepage := Some(url("https://github.com/zalando-incubator/kanadi"))

scmInfo := Some(
  ScmInfo(url("https://github.com/zalando-incubator/kanadi"), "git@github.com:zalando-incubator/kanadi.git"))

developers := List(
  Developer("mdedetrich", "Matthew de Detrich", "mdedetrich@gmail.com", url("https://github.com/mdedetrich")),
  Developer("xjrk58", "Jiri Syrovy", "jiri.syrovy@zalando.de", url("https://github.com/xjrk58")),
  Developer("itachi3", "Balaji Sonachalam", "balajispsg@gmail.com", url("https://github.com/itachi3")),
  Developer("Deeds67", "Pierre Marais", "pierrem@live.co.za", url("https://github.com/Deeds67")),
  Developer("gouthampradhan",
            "Goutham Vidya Pradhan",
            "goutham.vidya.pradhan@gmail.com",
            url("https://github.com/gouthampradhan")),
  Developer("javierarrieta", "Javier Arrieta", "javier.arrieta@zalando.ie", url("https://github.com/javierarrieta")),
  Developer("pascalh", "Pascal Hof", "pascal.hof@zalando.de", url("https://github.com/pascalh")),
  Developer("gchudnov", "Grigorii Chudnov", "g.chudnov@gmail.com", url("https://github.com/gchudnov"))
)

licenses += ("MIT", url("https://opensource.org/licenses/MIT"))

publishMavenStyle := true

publishTo := sonatypePublishTo.value

def emptyStringToNone(string: String): Option[String] =
  if (string.trim.isEmpty)
    None
  else
    Some(string)

javaOptions ++= sys.props
  .get("TOKEN")
  .flatMap(emptyStringToNone)
  .map { token =>
    s"-DTOKEN=$token"
  }
  .toList

envVars ++= Map("TOKEN" -> sys.env.get("TOKEN").flatMap(emptyStringToNone)).collect { case (k, Some(v)) =>
  (k, v)
}

Test / publishArtifact := false

pomIncludeRepository := (_ => false)

resolvers += Resolver.jcenterRepo

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Run(
    List("echo ${GITHUB_TOKEN} | docker login docker.pkg.github.com -u ${GITHUB_ACTOR} --password-stdin"),
    name = Some("Login to Docker"),
    env = Map(
      "GITHUB_TOKEN" -> "${{ secrets.GITHUB_TOKEN }}"
    )
  ),
  WorkflowStep.Run(List("docker-compose up -d"), name = Some("Launch Nakadi")),
  WorkflowStep.Sbt(List("clean", "coverage", "test"), name = Some("Build project"))
)

ThisBuild / githubWorkflowBuildPostamble ++= Seq(
  // See https://github.com/scoverage/sbt-coveralls#github-actions-integration
  WorkflowStep.Sbt(
    List("coverageReport", "coverageAggregate", "coveralls"),
    name = Some("Upload coverage data to Coveralls"),
    env = Map(
      "COVERALLS_REPO_TOKEN" -> "${{ secrets.GITHUB_TOKEN }}",
      "COVERALLS_FLAG_NAME"  -> "Scala ${{ matrix.scala }}"
    )
  ),
  WorkflowStep.Run(
    List("docker-compose down"),
    name = Some("Shut down Nakadi")
  )
)

// This is causing problems with env variables being passed in, see
// https://github.com/sbt/sbt/issues/6468
ThisBuild / githubWorkflowUseSbtThinClient := false

ThisBuild / githubWorkflowPublishTargetBranches := Seq()

import ReleaseTransformations._

releaseCrossBuild := false
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepCommandAndRemaining("+test"),
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommand("sonatypeReleaseAll"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
