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

import org.grumblesmurf.dubstep.Utilities._
import org.grumblesmurf.dubstep._
import org.hamcrest.CoreMatchers._
import org.junit.Assert._
import org.junit.Test

abstract class DubstepTests {
  implicit val db: Database

  @Test
  def loadTestData() {
    val dataset = DbtDataset("/testdata.dbt")
    loadData(dataset)

    withConnection(db.connect()) { connection =>
      withStatement(connection) { st =>
        assertThat(
          st.executeQuery("select count(1) from order_lines").map(_.getInt(1)).head,
          is(dataset.rowSets.groupBy(_.tableName)("order_lines").map(_.rows.size).sum)
        )
      }
    }
  }

  @Test
  def checkTestData() {
    val dataset = DbtDataset("/testdata.dbt")
    loadData(dataset)

    checkData(DbtDataset("/testdata_result.dbt")).map(mismatches => fail(mismatches.mkString("\n")))
  }
}
