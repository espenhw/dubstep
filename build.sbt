scalaVersion := "2.9.1"

name := "dubstep"

organization := "org.grumblesmurf"

crossScalaVersions := Seq("2.8.1", "2.8.2", "2.9.1", "2.9.2")

scalacOptions += "-deprecation"

seq(releaseSettings: _*)
