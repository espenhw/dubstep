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

import scala.util.parsing.combinator.JavaTokenParsers
import java.sql.Timestamp
import scala.io.Source
import scala.collection.mutable

abstract class Dataset {
  def rowSets: Seq[RowSet]

  val database: Database
}

case class RowSet(tableName: String, defaults: Option[Row], rows: Seq[Row])

case class Row(data: Map[String, Any]) {
  def withDefaultsFrom(rowSet: RowSet): Row = {
    val defaultData: Map[String, Any] = rowSet.defaults.map(_.data).getOrElse(Map.empty)
    withDefaultsFromMap(defaultData)
  }

  private[dubstep] def withDefaultsFromMap(defaultData: Map[String, Any]): Row = {
    copy(data = defaultData ++ data)
  }

  def apply(column: String): Any = {
    data(column)
  }

  def get(column: String): Option[Any] = {
    data.get(column)
  }
}

case class DbtDataset(dataSource: Source)(implicit val database: Database) extends Dataset {
  lazy val rowSets: Seq[RowSet] = {
    try {
      DbtParser.parse(dataSource)
    } finally {
      dataSource.close()
    }
  }
}

object DbtParser extends JavaTokenParsers {
  private def tables: Parser[Seq[RowSet]] = rep(table)

  private val globals = mutable.Map.empty[String,Map[String,Any]]

  private def table: Parser[RowSet] = ident ~ ":" ~ opt(globalDefaults) ~ opt(defaults) ~ rep(record) ^^ {
    case name ~ ":" ~ globalDefs ~ defs ~ rows =>
      globalDefs.map(r => globals.update(name, r.data))

      val defaults = defs.map(_.withDefaultsFromMap(globals.getOrElse(name, Map.empty)))
        .orElse(globals.get(name).map(Row(_)))
      RowSet(name.toLowerCase, defaults, rows)
  }

  private def globalDefaults: Parser[Row] = row("??")

  private def defaults: Parser[Row] = row("?")

  private def record: Parser[Row] = row("-")

  private def row(prefix: String): Parser[Row] = prefix ~ rep1sep(column, ",") ^^ {
    case _ ~ columns =>
      Row(Map.empty ++ columns)
  }

  private def column: Parser[(String, Any)] = ident ~ ":" ~ value ^^ {
    case column ~ ":" ~ value => (column.toLowerCase -> value)
  }

  private def value: Parser[Any] =
    stringLiteral ^^ (removeQuotes(_)) |
      wholeNumber ^^ (_.toLong) |
      floatingPointNumber ^^ (_.toDouble) |
      "true" ^^ (s => true) |
      "$true" ^^ (s => true) |
      "false" ^^ (s => false) |
      "$false" ^^ (s => false) |
      "$now" ^^ (s => new Timestamp(System.currentTimeMillis())) |
      "$null" ^^ (s => null) |
      "null" ^^ (s => null)

  private def removeQuotes(s: String): String = {
    s.substring(1, s.length() - 1)
  }

  def parse(source: Source): Seq[RowSet] = {
    val content = source.getLines().filterNot(_.startsWith("#")).mkString("\n")
    val result = parseAll(tables, content)
    if (!result.successful) {
      throw new RuntimeException(result.toString)
    }
    result.get
  }
}
