import sbt._

object Dependencies {

  object versions {
    val logback                  = "1.4.11"
    val scalaLogging             = "3.9.5"
    val ficus                    = "1.5.2"
    val pekko                    = "1.0.1"
    val pekkoHttp                = "1.0.0"
    val pekkoStreamJson          = "1.0.0"
    val enumeratumCirce          = "1.7.3"
    val circe                    = "0.14.5"
    val specs2                   = "4.20.2"
    val scalaParallelCollections = "0.2.0"
  }

  private val pekkoHttp        = "org.apache.pekko"           %% "pekko-http"         % versions.pekkoHttp
  private val pekkoSlf4j       = "org.apache.pekko"           %% "pekko-slf4j"        % versions.pekko
  private val pekkoStream      = "org.apache.pekko"           %% "pekko-stream"       % versions.pekko
  private val enumeratumCirce  = "com.beachape"               %% "enumeratum-circe"   % versions.enumeratumCirce
  private val circeParser      = "io.circe"                   %% "circe-parser"       % versions.circe
  private val pekkoStreamCirce = "org.mdedetrich"             %% "pekko-stream-circe" % versions.pekkoStreamJson
  private val pekkoHttpCirce   = "org.mdedetrich"             %% "pekko-http-circe"   % versions.pekkoStreamJson
  private val ficus            = "com.iheart"                 %% "ficus"              % versions.ficus
  private val scalaLogging     = "com.typesafe.scala-logging" %% "scala-logging"      % versions.scalaLogging
  private val logbackClassic   = "ch.qos.logback"              % "logback-classic"    % versions.logback
  private val specs2           = "org.specs2"                 %% "specs2-core"        % versions.specs2

  private val scalaParallelCollections =
    "org.scala-lang.modules" %% "scala-parallel-collections" % versions.scalaParallelCollections

  private val testSeq = Seq(specs2)

  val test213Seq: Seq[ModuleID] = Seq(scalaParallelCollections)

  val kanadi: Seq[ModuleID] = {
    val provided = Seq(
      pekkoHttp,
      pekkoSlf4j,
      pekkoStream
    ) map (_ % Provided)
    val compile = Seq(
      enumeratumCirce,
      circeParser,
      pekkoStreamCirce,
      pekkoHttpCirce,
      ficus,
      scalaLogging,
      logbackClassic
    ) map (_ % "compile")
    val test = testSeq map (_ % "test")
    compile ++ test
  }
}
