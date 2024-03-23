libraryDependencies ++= {
  Dependencies.kanadi ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, n)) if n == 13 =>
      Dependencies.test213Seq.map(_ % Test)
    case _ =>
      Seq.empty
  })
}

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Use(
    UseRef.Public("docker", "login-action", "v2"),
    name = Some("Login to GitHub Container Registry"),
    params = Map(
      "registry" -> "ghcr.io",
      "username" -> "${{ github.actor }}",
      "password" -> "${{ secrets.GITHUB_TOKEN }}"
    )
  ),
  WorkflowStep.Run(List("docker-compose up -d"), name = Some("Launch Nakadi")),
  WorkflowStep.Sbt(List("clean", "coverage", "test"), name = Some("Build project"))
)

ThisBuild / githubWorkflowJavaVersions := Seq(
  // See https://github.com/sbt/sbt-github-actions#jdk-settings
  JavaSpec.temurin("11")
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

import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations.*

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

lazy val root = (project in file("."))
  .settings(Settings.shared)
  .settings(
    name := "kanadi"
  )

// COMMANDS
addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt it:scalafmt")
addCommandAlias("chk", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck it:scalafmtCheck")
addCommandAlias("plg", "; reload plugins ; libraryDependencies ; reload return")
// NOTE: to use version check for plugins, add to the meta-project (/project/project) sbt-updates.sbt with "sbt-updates" plugin as well.
addCommandAlias("upd", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")
