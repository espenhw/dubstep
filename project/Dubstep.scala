import sbt._
import Keys._

object Dubstep extends Build {
  lazy val root = Project("dubstep", file("."))
    .configs(IntegrationTest)
    .settings(Defaults.itSettings : _*)
    .settings(Seq(
      libraryDependencies ++= Seq(
        "junit" % "junit" % "4.10",
        "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
        "com.novocode" % "junit-interface" % "0.7",
        "com.h2database" % "h2" % "1.3.167"
      ).map(_.copy(configurations=Some("test,it"))),
      (testFrameworks in IntegrationTest) += TestFrameworks.JUnit,
      (parallelExecution in IntegrationTest) := false
    ) : _*)


}
