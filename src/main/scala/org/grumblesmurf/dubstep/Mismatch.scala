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

sealed abstract class Mismatch {
  import Mismatch._

  val table: Table
  val primaryKeyValues: Seq[Any]

  protected def toStringExtra: String

  override def toString: String = {
    """%s in table %s:
      |  row with primary key: %s
      |%s
    """.stripMargin.format(
      getClass.getSimpleName,
      table.name,
      table.primaryKey.map(_.zip(primaryKeyValues).toMap).getOrElse(Map.empty).map(colValue).mkString(", "),
      toStringExtra
    ).trim
  }

  protected def colValue(col: (String, Any)): String = {
    "%s=%s" format (col._1, formattedValue(col._2))
  }
}

object Mismatch {
  def formattedValue(value: Any): Any = {
    value match {
      case n: Numeric[_] => n
      case s: String => s formatted "'%s'"
      case x => x
    }
  }
}

case class RowDataMismatch(table: Table, primaryKeyValues: Seq[Any], columns: Iterable[ColumnMismatch]) extends Mismatch {
  protected def toStringExtra = columns.mkString("\n")
}

case class MissingRow(table: Table, primaryKeyValues: Seq[Any], moreColumns: Map[String,Any]) extends Mismatch {
  protected def toStringExtra = "  row data: \n    " + moreColumns.map(colValue).mkString("\n    ")
}

case class ExtraRow(table: Table, primaryKeyValues: Seq[Any], moreColumns: Map[String, Any]) extends Mismatch {
  protected def toStringExtra = "  row data: \n    " + moreColumns.map(colValue).mkString("\n    ")
}

case class ColumnMismatch(column: String, expected: Any, actual: Any) {
  protected def humanReadable: String = "  column %s: expected %s got %s" format (
    column, Mismatch.formattedValue(expected), Mismatch.formattedValue(actual))
}
