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

package test

import org.grumblesmurf.dubstep._

class H2Test extends DubstepTests {
  implicit val db = H2Test.db
}

object H2Test {
  implicit val dbDialect = H2
  val db = SimpleDatabase("sa", "", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", Some("/testdata_h2.sql"))
}
