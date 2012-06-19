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

import java.sql.{Array => _, _}
import javax.sql.DataSource
import Utilities._
import org.apache.commons.io.IOUtils
import scala.collection.mutable

sealed abstract class Database {
  val dialect: DatabaseDialect

  val schemaDefinition: Option[String]

  private[this] var schemaLoaded = false

  private[this] var meta: DatabaseStructure = _

  private[dubstep] def check(dataset: Dataset): Iterable[Mismatch] = {
    withConnection(connect()) { connection =>
      loadMetadata(connection)

      val expectedRows = dataset.rowSets.groupBy(_.tableName).map {
        case (table, rowSets) =>
          (table -> rowSets.flatMap { rs =>
            rs.rows.map(r => r.withDefaultsFrom(rs))
          })
      }

      val actualRows = loadRows(connection, expectedRows.keys)

      expectedRows.keys.toSeq.sorted.flatMap { t => checkTable(meta.table(t), expectedRows(t), actualRows.getOrElse(t, Seq.empty)) }
    }
  }

  private def checkTable(table: Table, expectedRows: Seq[Row], actualRows: Seq[Row]): Iterable[Mismatch] = {
    val pk = table.primaryKey.getOrElse(throw new IllegalArgumentException("Dubstep cannot check %s, it has no primary key" format (table.name)))
    def pkValues(row: Row) = {
      pk.map(row(_))
    }

    val mismatches = mutable.Buffer.empty[Mismatch]

    val extraActualRows = actualRows.toBuffer

    val unmatchedRows = expectedRows.filterNot { expectedRow =>
      val expectedRowPk = pkValues(expectedRow)
      actualRows.find(pkValues(_) == expectedRowPk).map { actualRow =>
        mismatches ++= expectedRow.data.flatMap { case (column, expectedValue) =>
          val actualValue = actualRow(column)
          if (expectedValue == actualValue) {
            None
          } else {
            Some(expectedRowPk, column, expectedValue, actualValue)
          }
        }.groupBy(_._1).map { case (pkValue, tuples) =>
          RowDataMismatch(table, pkValue, tuples.map { case (_, column, expected, actual) =>
            ColumnMismatch(column, expected, actual)
          })
        }
        extraActualRows -= actualRow
        true
      }.getOrElse(false)
    }

    def nonPrimaryKeyData(row: Row): Map[String, Any] = {
      row.data -- table.primaryKey.getOrElse(Seq.empty)
    }

    mismatches ++= unmatchedRows.map { row =>
      MissingRow(table, pkValues(row), nonPrimaryKeyData(row))
    }

    mismatches ++= extraActualRows.map { row =>
      ExtraRow(table, pkValues(row), nonPrimaryKeyData(row))
    }

    mismatches.toSeq
  }

  private def loadRows(connection: Connection, tables: Iterable[String]): Map[String, Seq[Row]] = {
    withStatement(connection) { st =>
      Map.empty ++ tables.map { t =>
        (t -> st.executeQuery("SELECT * FROM " + t).map { rs =>
          val rsMeta = rs.getMetaData
          val cols = rsMeta.getColumnCount

          Row(Map.empty ++ (1 until (cols + 1)).map { n =>
            (rsMeta.getColumnName(n).toLowerCase -> rs.getObject(n))
          })
        }.toSeq)
      }
    }
  }

  private[dubstep] def load(dataset: Dataset) {
    withConnection(connect()) { connection =>
      if (!schemaLoaded) {
        loadSchema(connection)
        schemaLoaded = true
      }

      val affectedTables = dataset.rowSets.map(rs => meta.table(rs.tableName))

      val inserts: Map[String, PreparedStatement] = Map.empty ++ (affectedTables map { table =>
        (table.name -> connection.prepareStatement(table.insertStatement))
      })

      inTransaction(connection) {
        dialect.truncateTables(affectedTables, connection, meta)
      }

      inTransaction(connection) {
        dataset.rowSets.foreach { rowSet =>
          val ps = inserts(rowSet.tableName)
          rowSet.rows.foreach { row =>
            val augmentedRow = row.withDefaultsFrom(rowSet)

            meta.table(rowSet.tableName).columns.zipWithIndex.foreach { case ((column, (sqlType, _)), idx) =>
                ps.setObject(idx + 1, augmentedRow.get(column).orNull, sqlType)
              }
            ps.executeUpdate()
          }
        }
        dialect.updateAutoincrements(affectedTables, connection, meta)
      }
    }
  }

  private def interpretMetadata(dmd: DatabaseMetaData): DatabaseStructure = {
    val realTables = dmd.getTables(null, null, "%", Array("TABLE")).map { rs: ResultSet =>
      rs.getString("TABLE_NAME")
    }.toSet

    val primaryKeys: Map[String, Seq[String]] = realTables.map { table =>
      val pkCols = dmd.getPrimaryKeys(null, null, table).map {
        rs =>
          val seq = rs.getInt("KEY_SEQ")
          val col = rs.getString("COLUMN_NAME").toLowerCase
          (seq -> col)
      }
      table -> (pkCols.toSeq.sortBy(_._1).map(_._2))
    }.toMap

    val tableColumns = dmd.getColumns(null, null, "%", "%").flatMap { rs =>
      val table = rs.getString("TABLE_NAME")
      if (realTables(table)) {
        val column = rs.getString("COLUMN_NAME").toLowerCase
        val sqlType = rs.getInt("DATA_TYPE")
        val isAutoIncrement = rs.getString("IS_AUTOINCREMENT") == "YES"
        Some(table -> (column -> (sqlType, isAutoIncrement)))
      } else {
        None
      }
    }.groupBy(_._1) map { case (table, cols) =>
      (table -> (Map.empty ++ (cols map { _._2 })))
    }

    val tables = realTables.map { table =>
      Table(table.toLowerCase, primaryKeys.get(table), tableColumns(table))
    }

    DatabaseStructure(tables)
  }

  private def loadSchema(connection: Connection) {
    dialect.clearDatabase(connection, interpretMetadata(connection.getMetaData))

    schemaDefinition foreach { resource =>
      val sql = loadAsString(resource)
      withStatement(connection) { s =>
        s.execute(sql)
      }
    }

    loadMetadata(connection)
  }

  private def loadMetadata(connection: Connection) {
    if (meta == null)
      meta = interpretMetadata(connection.getMetaData)
  }

  def table(name: String): Option[Table] = {
    Option(meta).map(_.table(name))
  }

  private def loadAsString(resource: String): String = {
    val is = getClass.getResourceAsStream(resource)
    try {
      IOUtils.toString(is, "UTF-8")
    } finally {
      is.close()
    }
  }

  def connect(): Connection
}

case class Table(name: String, primaryKey: Option[Seq[String]], columns: Map[String, (Int, Boolean)]) {
  lazy val autoIncrementColumns = columns.collect { case (column, (_, true)) => column }.toSet

  lazy val insertStatement = "insert into %s (%s) values (%s)" format (
    name,
    columns.keys mkString ",",
    columns map Function.const("?") mkString ","
    )
}

case class DatabaseStructure(tables: Set[Table]) {
  private val tableMap = Map.empty ++ tables.map { t => t.name -> t}

  def table(name: String): Table = {
    tableMap.get(name).getOrElse(throw new IllegalArgumentException("Table " + name + " does not exist"))
  }
}

case class SimpleDatabase(username: String, password: String, uri: String, schemaDefinition: Option[String] = None)
                         (implicit val dialect: DatabaseDialect) extends Database {
  Class.forName(dialect.driverClassname)

  def connect(): Connection = {
    DriverManager.getConnection(uri, username, password)
  }
}

case class DataSourceDatabase(dataSource: DataSource, schemaDefinition: Option[String] = None)
                             (implicit val dialect: DatabaseDialect) extends Database {
  def connect(): Connection = {
    dataSource.getConnection
  }
}
