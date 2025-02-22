import com.typesafe.sbt.packager.docker.Cmd

ThisBuild / organization := "org.cueto"
ThisBuild / version := "0.0.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.2"

lazy val root = project
  .in(file("."))
  .aggregate(domain, emailbuddy, eventTracker)

lazy val emailbuddy = project
  .in(file("emailbuddy"))
  .dependsOn(domain)
  .enablePlugins(JavaAppPackaging)
  .settings(
    libraryDependencies := Dependencies.emailBuddyDependencies,
    mainClass in assembly := Some("org.cueto.pfi.Main"),
    mainClass in Compile := Some("org.cueto.pfi.Main"),
    packageName in Docker := "manuelcuet/email-buddy",
    commonSettings,
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.0"),
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      Cmd("RUN", "apk update && apk add bash")
    )
  )

lazy val eventTracker = project
  .in(file("event-tracker"))
  .dependsOn(domain)
  .enablePlugins(JavaAppPackaging)
  .settings(
    libraryDependencies := Dependencies.eventTrackerDependencies,
    mainClass in assembly := Some("org.cueto.pfi.Main"),
    mainClass in Compile := Some("org.cueto.pfi.Main"),
    packageName in Docker := "manuelcueto/event-tracker",
    dockerBaseImage := "openjdk:jre", // a smaller JVM base image
    commonSettings,
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.0")
  )

lazy val domain = project
  .in(file("domain"))
  .settings(libraryDependencies := Dependencies.domainDependencies, commonSettings)

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-language:higherKinds",
    "-language:postfixOps",
    "-feature",
    "-Xfatal-warnings"
  ),
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case PathList(x @ _*) if x.exists(y => y.contains("XmlPullParser") || y.contains("XmlPullParserException")) =>
      MergeStrategy.last
    case x => MergeStrategy.defaultMergeStrategy(x)
  }
)
enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
