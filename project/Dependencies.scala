import sbt._

object Dependencies {


  val Http4sVersion = "0.21.4"
  val CirceVersion = "0.13.0"
  val CatsVersion = "2.1.1"
  val Specs2Version = "4.7.0"
  val LogbackVersion = "1.2.3"
  val DoobieVersion = "0.8.8"


  lazy val circe = Seq(
    "io.circe" %% "circe-core" % CirceVersion,
    "io.circe" %% "circe-parser" % CirceVersion,
    "io.circe" %% "circe-generic" % CirceVersion
  )

  lazy val cats = Seq(
    "org.typelevel" %% "cats-core" % CatsVersion,
    "com.github.haifengl" %% "smile-scala" % "2.4.0"
  )

  lazy val doobie = Seq(
    "org.tpolecat" %% "doobie-core" % DoobieVersion,
    "org.tpolecat" %% "doobie-hikari" % DoobieVersion,
    "mysql" % "mysql-connector-java" % "8.0.20"
  )

  lazy val pureConfig = Seq(
    "com.github.pureconfig" %% "pureconfig" % "0.12.3"
  )

  lazy val http4s = Seq(
    "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
    "org.http4s" %% "http4s-circe" % Http4sVersion,
    "org.http4s" %% "http4s-dsl" % Http4sVersion
  )

  lazy val logging = Seq(
    "io.chrisdavenport" %% "log4cats-slf4j" % "1.1.1",
    "ch.qos.logback" % "logback-classic" % LogbackVersion
  )

  lazy val fs2 = Seq(
    "com.github.fd4s" %% "fs2-kafka" % "1.0.0"
  )

  lazy val test = Seq(
    "com.ironcorelabs" %% "cats-scalatest" % "3.0.5" % "test",
    "org.scalatest" %% "scalatest" % "3.1.1" % "test"
  )

  lazy val email = Seq(
    "com.github.daddykotex" %% "courier" % "2.0.0"
  )

  lazy val common = cats ++ test ++ pureConfig
  lazy val domainDependencies = common ++ circe ++ doobie
  lazy val emailBuddyDependencies = common ++ circe ++ doobie ++ logging ++ fs2 ++ http4s ++ email
  lazy val eventTrackerDependencies = common ++ circe ++ logging ++ fs2 ++ http4s
}
