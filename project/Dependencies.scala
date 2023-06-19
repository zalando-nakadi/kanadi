import sbt._

object Dependencies {

  object versions {
    val logback                     = "1.4.5"
    val scalaLogging                = "3.9.5"
    val ficus                       = "1.5.2"
    val akka                        = "2.6.20"  // NOTE: the last version with apache2 license
    val akkaHttp                    = "10.2.10" // NOTE: the last version under apache2 license
    val akkaStreamsJson             = "0.8.3"
    val enumeratumCirce             = "1.7.2"
    val circe                       = "0.14.4"
    val specs2                      = "4.19.2"
    val heikoseebergerAkkaHttpCirce = "1.39.2"
    val censoredRawHeader           = "0.7.0"
    val scalaParallelCollections    = "0.2.0"
  }

  private val akkaHttp          = "com.typesafe.akka" %% "akka-http"           % versions.akkaHttp
  private val akkaSlf4j         = "com.typesafe.akka" %% "akka-slf4j"          % versions.akka
  private val akkaStream        = "com.typesafe.akka" %% "akka-stream"         % versions.akka
  private val censoredRawHeader = "org.mdedetrich"    %% "censored-raw-header" % versions.censoredRawHeader
  private val enumeratumCirce   = "com.beachape"      %% "enumeratum-circe"    % versions.enumeratumCirce
  private val circeParser       = "io.circe"          %% "circe-parser"        % versions.circe
  private val akkaStreamsCirce  = "org.mdedetrich"    %% "akka-stream-circe"   % versions.akkaStreamsJson
  private val akkaHttpCirceM    = "org.mdedetrich"    %% "akka-http-circe"     % versions.akkaStreamsJson
  private val akkaHttpCirce     = "de.heikoseeberger" %% "akka-http-circe"     % versions.heikoseebergerAkkaHttpCirce
  private val ficus             = "com.iheart"        %% "ficus"               % versions.ficus
  private val scalaLogging   = "com.typesafe.scala-logging" %% "scala-logging"   % versions.scalaLogging
  private val logbackClassic = "ch.qos.logback"              % "logback-classic" % versions.logback
  private val specs2         = "org.specs2"                 %% "specs2-core"     % versions.specs2

  private val scalaParallelCollections =
    "org.scala-lang.modules" %% "scala-parallel-collections" % versions.scalaParallelCollections

  private val testSeq = Seq(specs2)

  val test213Seq: Seq[ModuleID] = Seq(scalaParallelCollections)

  val akkaDeps: Seq[ModuleID] = {
    val provided = Seq(
      akkaHttp,
      akkaSlf4j,
      akkaStream
    ) map (_ % Provided)
    val compile = Seq(
      censoredRawHeader,
      enumeratumCirce,
      circeParser,
      akkaStreamsCirce,
      akkaHttpCirceM,
      akkaHttpCirce,
      ficus,
      scalaLogging,
      logbackClassic
    ) map (_ % "compile")
    val test = testSeq map (_ % "test")
    compile ++ test
  }
}
