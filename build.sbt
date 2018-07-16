name := """kanadi"""

val akkaHttpVersion        = "10.1.3"
val akkaStreamsJsonVersion = "0.1.0"
val currentScalaVersion    = "2.11.12"
val enumeratumCirceVersion = "1.5.12"
val circeVersion           = "0.9.3"
val akkaVersion            = "2.5.12"

scalafmtVersion in ThisBuild := "1.1.0"

version := "0.2.0-MILESTONE-M11"

scalaVersion in ThisBuild := currentScalaVersion

crossScalaVersions in ThisBuild := Seq(currentScalaVersion, "2.12.6")

organization := "org.zalando"

javaOptions in Test += "-DTOKEN=" + Option(System.getProperty("TOKEN"))
  .getOrElse("")

fork in Test := true

updateOptions := updateOptions.value.withGigahorse(false)

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint", // recommended additional warnings
  "-Xcheckinit", // runtime error when a val is not initialized due to trait hierarchies (instead of NPE somewhere else)
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  "-Ywarn-inaccessible",
  "-Ywarn-dead-code"
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
  "-opt:l:project"
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

publishMavenStyle := true

publishTo in ThisBuild := {
  val nexus = "https://maven.zalando.net/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots/")
  else
    Some("releases" at nexus + "content/repositories/releases/")
}

resolvers += Resolver.jcenterRepo
