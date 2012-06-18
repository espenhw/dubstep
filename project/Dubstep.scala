/*
 * Copyright 2012 Espen Wiborg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

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
