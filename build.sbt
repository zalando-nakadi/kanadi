name := """kanadi"""

val akkaHttpVersion        = "10.1.5"
val akkaStreamsJsonVersion = "0.1.0"
val currentScalaVersion    = "2.11.12"
val enumeratumCirceVersion = "1.5.12"
val circeVersion           = "0.9.3"
val akkaVersion            = "2.5.17"

scalaVersion in ThisBuild := currentScalaVersion

crossScalaVersions in ThisBuild := Seq(currentScalaVersion, "2.12.7")

organization := "org.zalando"

fork in Test := true
parallelExecution in Test := true
testForkedParallel in Test := true

updateOptions := updateOptions.value.withGigahorse(false)

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8", // Specify character encoding used by source files.
  "-explaintypes", // Explain type errors in more detail.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros", // Allow macro definition (besides implementation and application)
  "-language:higherKinds", // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
  "-unchecked",  // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
  //  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  "-Xfuture", // Turn on future language features.
  "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
  "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
  "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
  "-Xlint:option-implicit", // Option.apply used implicit view.
  "-Xlint:package-object-classes", // Class or object defined in package object.
  "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
  "-Xlint:unsound-match",         // Pattern match may not be typesafe.
  //"-Yno-adapted-args", // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ypartial-unification", // Enable partial unification in type constructor inference
  "-Ywarn-dead-code", // Warn when dead code is identified.
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any", // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit", // Warn when nullary methods return Unit.
  "-Ywarn-numeric-widen", // Warn when numerics are widened.
  "-Ywarn-value-discard" // Warn when non-Unit expression results are unused.
)

val flagsFor11 = Seq(
  "-Xlint:_",
  "-Yconst-opt",
  "-Ywarn-infer-any",
  "-Yclosure-elim",
  "-Ydead-code",
  "-Xsource:2.12" // required to build case class construction
)

val flagsFor12 = Seq(
  "-Xlint:_",
  "-Ywarn-infer-any",
  "-opt-inline-from:<sources>"
)

scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, n)) if n >= 12 =>
      flagsFor12
    case Some((2, n)) if n == 11 =>
      flagsFor11
  }
}

libraryDependencies ++= Seq(
  "com.typesafe.akka"          %% "akka-http"           % akkaHttpVersion,
  "com.typesafe.akka"          %% "akka-slf4j"          % akkaVersion,
  "com.typesafe.akka"          %% "akka-stream"         % akkaVersion,
  "org.mdedetrich"             %% "censored-raw-header" % "0.2.0",
  "org.mdedetrich"             %% "webmodels"           % "0.2.0",
  "com.beachape"               %% "enumeratum-circe"    % enumeratumCirceVersion,
  "io.circe"                   %% "circe-java8"         % circeVersion,
  "io.circe"                   %% "circe-parser"        % circeVersion,
  "org.mdedetrich"             %% "akka-stream-circe"   % akkaStreamsJsonVersion,
  "org.mdedetrich"             %% "akka-http-circe"     % akkaStreamsJsonVersion,
  "de.heikoseeberger"          %% "akka-http-circe"     % "1.21.0",
  "com.iheart"                 %% "ficus"               % "1.4.3",
  "com.typesafe.scala-logging" %% "scala-logging"       % "3.8.0",
  "ch.qos.logback"             % "logback-classic"      % "1.1.7",
  "org.specs2"                 %% "specs2-core"         % "3.8.9" % Test
)

scalacOptions in Test ++= Seq("-Yrangepos")

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
            url("https://github.com/gouthampradhan"))
)

licenses += ("MIT", url("https://opensource.org/licenses/MIT"))

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := (_ => false)

resolvers += Resolver.jcenterRepo
