import sbt._

object Dependencies {

  object versions {
    val logback                  = "1.4.11"
    val scalaLogging             = "3.9.5"
    val pureConfig               = "0.17.4"
    val pekko                    = "1.0.1"
    val pekkoHttp                = "1.0.0"
    val pekkoStreamJson          = "1.0.0"
    val enumeratumCirce          = "1.7.3"
    val circe                    = "0.14.5"
    val scalaTest                = "3.2.16"
    val scalaParallelCollections = "0.2.0"
  }

  private val pekkoHttp        = "org.apache.pekko"           %% "pekko-http"         % versions.pekkoHttp
  private val pekkoSlf4j       = "org.apache.pekko"           %% "pekko-slf4j"        % versions.pekko
  private val pekkoStream      = "org.apache.pekko"           %% "pekko-stream"       % versions.pekko
  private val enumeratumCirce  = "com.beachape"               %% "enumeratum-circe"   % versions.enumeratumCirce
  private val circeParser      = "io.circe"                   %% "circe-parser"       % versions.circe
  private val pekkoStreamCirce = "org.mdedetrich"             %% "pekko-stream-circe" % versions.pekkoStreamJson
  private val pekkoHttpCirce   = "org.mdedetrich"             %% "pekko-http-circe"   % versions.pekkoStreamJson
  private val pureConfig       = "com.github.pureconfig"      %% "pureconfig"         % versions.pureConfig
  private val scalaLogging     = "com.typesafe.scala-logging" %% "scala-logging"      % versions.scalaLogging
  private val logbackClassic   = "ch.qos.logback"              % "logback-classic"    % versions.logback
  private val scalaTest        = "org.scalatest"              %% "scalatest"          % versions.scalaTest
  private val pekkoTestKit     = "org.apache.pekko"           %% "pekko-testkit"      % versions.pekko

  private val scalaParallelCollections =
    "org.scala-lang.modules" %% "scala-parallel-collections" % versions.scalaParallelCollections

  private val testSeq = Seq(scalaTest, pekkoTestKit)

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
      pureConfig,
      scalaLogging,
      logbackClassic
    ) map (_ % "compile")
    val test = testSeq map (_ % "test")
    compile ++ test
  }
}
