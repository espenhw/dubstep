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

sealed abstract class Database {
  val dialect: DatabaseDialect
  val schemaDefinition: Option[String]

  private[this] var schemaLoaded = false
  private[this] var meta: DatabaseStructure = _

  private[dubstep] def check(dataset: Dataset) {
    // TODO
  }

  private[dubstep] def load(dataset: Dataset) {
    withConnection(connect()) { connection =>
      if (!schemaLoaded) {
        loadSchema(connection)
        schemaLoaded = true
      }

      val affectedTables = dataset.rowSets.map(_.table)

      val inserts: Map[String, PreparedStatement] = Map.empty ++ (affectedTables map { table =>
        (table -> connection.prepareStatement(meta.insertStatementFor(table)))
      })

      inTransaction(connection) {
        dialect.truncateTables(affectedTables, connection, meta)
      }

      inTransaction(connection) {
        dataset.rowSets.foreach { rowSet =>
          val ps = inserts(rowSet.table)
          rowSet.rows.foreach { row =>
            val data = rowSet.defaults.map(_.data).getOrElse(Map.empty) ++ row.data

            meta.tables(rowSet.table).zipWithIndex.map { case ((column, (sqlType, _)), idx) =>
              ps.setObject(idx + 1, data.get(column).orNull, sqlType)
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
      rs.getString("TABLE_NAME").toLowerCase
    }.toSet

    val columns = dmd.getColumns(null, null, "%", "%").map { rs =>
      val table = rs.getString("TABLE_NAME").toLowerCase
      if (realTables(table)) {
        val column = rs.getString("COLUMN_NAME").toLowerCase
        val sqlType = rs.getInt("DATA_TYPE")
        val isAutoIncrement = rs.getString("IS_AUTOINCREMENT") == "YES"
        Some(table -> (column -> (sqlType, isAutoIncrement)))
      } else {
        None
      }
    }.collect {
      case Some(tuple) => tuple
    }

    val tables = columns.groupBy(_._1) map { x =>
      (x._1 -> (Map.empty[String, (Int, Boolean)] ++ (x._2 map { _._2 })))
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

    meta = interpretMetadata(connection.getMetaData)
  }

  private def loadAsString(resource: String): String = {
    val is = getClass.getResourceAsStream(resource)
    try {
      IOUtils.toString(is, "UTF-8")
    } finally {
      is.close()
    }
  }

  protected def connect(): Connection
}

case class DatabaseStructure(tables: Map[String, Map[String, (Int, Boolean)]]) {
  def insertStatementFor(table: String): String = {
    """insert into %s (%s) values (%s)""" format (
      table,
      tables(table).keys mkString ",",
      tables(table) map Function.const("?") mkString ","
      )
  }

  def autoIncrementColumnsOf(table: String): Set[String] = {
    tables(table).collect { case (column, (_, true)) => column }.toSet
  }
}

case class SimpleDatabase(username: String, password: String, uri: String, schemaDefinition: Option[String] = None)
                         (implicit val dialect: DatabaseDialect) extends Database {
  Class.forName(dialect.driverClassname)

  protected def connect(): Connection = {
    DriverManager.getConnection(uri, username, password)
  }
}

case class DataSourceDatabase(dataSource: DataSource, schemaDefinition: Option[String] = None)
                             (implicit val dialect: DatabaseDialect) extends Database {
  protected def connect(): Connection = {
    dataSource.getConnection
  }
}
