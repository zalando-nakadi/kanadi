import sbt.Keys.{credentials, resolvers}
import sbt.*
import sbt.Keys.*
import sbtrelease.ReleasePlugin.autoImport.*
import sbtrelease.ReleaseStateTransformations.*
import xerial.sbt.Sonatype.autoImport.*

object Settings {
  val scala212Version: String     = "2.12.18"
  val scala213Version: String     = "2.13.11"
  val currentScalaVersion: String = scala212Version

  val globalScalaVersion: String           = currentScalaVersion
  val supportedScalaVersions: List[String] = List(scala212Version, scala213Version)

  private val extraJavaOptions: List[String] = sys.props
    .get("TOKEN")
    .flatMap(Settings.emptyStringToNone)
    .map { token =>
      s"-DTOKEN=$token"
    }
    .toList

  private val sharedScalacOptions = Seq(
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
    // "-Yno-adapted-args", // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
    "-Ywarn-dead-code",     // Warn when dead code is identified.
    "-Ywarn-numeric-widen", // Warn when numerics are widened.
    "-Ywarn-value-discard"  // Warn when non-Unit expression results are unused.
  )

  private val scalacOptionsFor12 = Seq(
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

  private val scalacOptionsFor13 = Seq(
    "-Xlint:_",
    "-opt-inline-from:<sources>"
  )

  val sharedResolvers: Vector[MavenRepository] =
    Vector(
      Resolver.mavenLocal,
      Resolver.ApacheMavenSnapshotsRepo,
      Resolver.jcenterRepo
    ) ++ Resolver.sonatypeOssRepos("releases")

  val shared: Seq[Setting[?]] = Seq(
    scalacOptions ++= sharedScalacOptions ++ {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n == 13 =>
          scalacOptionsFor13
        case Some((2, n)) if n == 12 =>
          scalacOptionsFor12
      }
    },
    ThisBuild / crossScalaVersions := supportedScalaVersions,
    ThisBuild / scalaVersion       := globalScalaVersion,
    ThisBuild / turbo              := true,
    ThisBuild / versionScheme      := Some(VersionScheme.EarlySemVer),
    resolvers                      := Resolver.combineDefaultResolvers(sharedResolvers),
    compileOrder                   := CompileOrder.JavaThenScala,
    organization                   := "org.zalando",
    Test / fork                    := true,
    Test / parallelExecution       := true,
    Test / testForkedParallel      := true,
    Test / publishArtifact         := false,
    Test / scalacOptions ++= Seq("-Yrangepos"),
    homepage := Some(url("https://github.com/zalando-incubator/kanadi")),
    scmInfo := Some(
      ScmInfo(url("https://github.com/zalando-incubator/kanadi"), "git@github.com:zalando-incubator/kanadi.git")
    ),
    developers := List(
      Developer("mdedetrich", "Matthew de Detrich", "mdedetrich@gmail.com", url("https://github.com/mdedetrich")),
      Developer("xjrk58", "Jiri Syrovy", "jiri.syrovy@zalando.de", url("https://github.com/xjrk58")),
      Developer("itachi3", "Balaji Sonachalam", "balajispsg@gmail.com", url("https://github.com/itachi3")),
      Developer("Deeds67", "Pierre Marais", "pierrem@live.co.za", url("https://github.com/Deeds67")),
      Developer("gouthampradhan",
                "Goutham Vidya Pradhan",
                "goutham.vidya.pradhan@gmail.com",
                url("https://github.com/gouthampradhan")),
      Developer("javierarrieta",
                "Javier Arrieta",
                "javier.arrieta@zalando.ie",
                url("https://github.com/javierarrieta")),
      Developer("pascalh", "Pascal Hof", "pascal.hof@zalando.de", url("https://github.com/pascalh")),
      Developer("gchudnov", "Grigorii Chudnov", "g.chudnov@gmail.com", url("https://github.com/gchudnov"))
    ),
    licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
    pomIncludeRepository := (_ => false),
    javaOptions ++= extraJavaOptions,
    envVars ++= Map("TOKEN" -> sys.env.get("TOKEN").flatMap(Settings.emptyStringToNone)).collect { case (k, Some(v)) =>
      (k, v)
    }
  )

  val publish: Seq[Setting[?]] = Seq(
    publishMavenStyle := true,
    publishTo         := sonatypePublishTo.value
  )

  def emptyStringToNone(string: String): Option[String] =
    if (string.trim.isEmpty)
      None
    else
      Some(string)
}
