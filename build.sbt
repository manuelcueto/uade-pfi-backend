

ThisBuild / organization := "org.cueto"
ThisBuild / version := "0.0.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.2"


lazy val root = project.in(file("."))
  .aggregate(domain, emailbuddy, eventTracker)

lazy val emailbuddy = project.in(file("emailbuddy"))
  .dependsOn(domain)
  .settings(libraryDependencies := Dependencies.emailBuddyDependencies,
    commonSettings,
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.0")
  )
lazy val eventTracker = project.in(file("event-tracker"))
  .settings(libraryDependencies := Dependencies.eventTrackerDependencies,
    commonSettings,
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.0")
  )
  .dependsOn(domain)

lazy val domain = project.in(file("domain"))
  .settings(libraryDependencies := Dependencies.domainDependencies)

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-language:higherKinds",
    "-language:postfixOps",
    "-feature",
    "-Xfatal-warnings"
  )

)