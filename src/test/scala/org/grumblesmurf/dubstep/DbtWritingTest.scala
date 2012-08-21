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

package org.grumblesmurf.dubstep

import org.junit.Test
import org.junit.Assert.assertThat
import org.hamcrest.CoreMatchers._

class DbtWritingTest {
  @Test
  def canWriteEmptyRowSet() {
    assertThat(RowSet("foo", None, Seq.empty).asDbt, is(
      """foo:
        |""".stripMargin
    ))
  }

  @Test
  def canWriteRowSetWithOneRow() {
    assertThat(RowSet("foo", None, Seq(Row(Map("foo" -> 1, "bar" -> "zot")))).asDbt, is(
      """foo:
        |- foo: 1, bar: "zot"""".stripMargin
    ))
  }

  @Test
  def canWriteRowSetWithOneRowAndDefaults() {
    assertThat(RowSet("foo", Some(Row(Map("zot" -> "foo"))), Seq(Row(Map("foo" -> 1, "bar" -> "zot")))).asDbt, is(
      """foo:
        |? zot: "foo"
        |- foo: 1, bar: "zot"""".stripMargin
    ))
  }

  @Test
  def escapesQuotesInRowData() {
    assertThat(Row(Map("foo" -> """bar"bar""")).asDbt("-"), is("""- foo: "bar\"bar""""))
  }

  @Test
  def escapesBackslashesInRowData() {
    assertThat(Row(Map("foo" -> """bar\bar""")).asDbt("-"), is("""- foo: "bar\\bar""""))
  }
}
