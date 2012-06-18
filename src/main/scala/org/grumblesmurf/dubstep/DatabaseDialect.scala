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

import Utilities._
import java.sql.{Statement, Connection}

/** This class encapsulates behavior specific to a given flavor of database.
  * @see [[org.grumblesmurf.dubstep.NaiveDatabaseDialect]]
  */
abstract class DatabaseDialect {
  /** Delete all data from `tables`, taking care of foreign key references.
    * This method should also ensure that any autoincrementing columns are reset.
    * The supplied connection will have a running transaction.
    * @param tables tables to delete from
    * @param connection connection to operate in
    * @param meta a representation of the database structure
    */
  def truncateTables(tables: Seq[Table], connection: Connection, meta: DatabaseStructure)

  /** Update autoincrement of `tables` if necessary so that subsequent inserts will work.
    * @param tables tables to delete from
    * @param connection connection to operate in
    * @param meta a representation of the database structure
    */
  def updateAutoincrements(tables: Seq[Table], connection: Connection, meta: DatabaseStructure)

  /** Clear the database; that is, drop everything in preparation for reloading the schema.  This implementation
    * does `DROP TABLE t1, t2, t3... CASCADE` (works on at least H2 and PostgreSQL).
    * @param connection connection to operate in
    * @param meta a representation of the database structure
    */
  def clearDatabase(connection: Connection, meta: DatabaseStructure) {
    if (meta.tables.nonEmpty) {
      withStatement(connection) { st =>
        st.execute("DROP TABLE %s CASCADE" format (meta.tables.map(_.name).mkString(",")))
      }
    }
  }

  /** Name of the JDBC driver class, for use with [[org.grumblesmurf.dubstep.SimpleDatabase]]. */
  val driverClassname: String
}

/** A naïve implementation of [[org.grumblesmurf.dubstep.DatabaseDialect]], suitable as a starting point.
  */
abstract class NaiveDatabaseDialect extends DatabaseDialect {
  /** Sequentially runs `TRUNCATE TABLE t` for each table in `tables`.
    * @param tables tables to delete from
    * @param connection connection to operate in
    */
  def truncateTables(tables: Seq[Table], connection: Connection, meta: DatabaseStructure) {
    withStatement(connection) { st =>
      tables foreach { t =>
        st.executeUpdate("DELETE FROM " + t.name)
      }
    }
  }

  /** Does nothing.
    *
    * @param tables ignored
    * @param connection ignored
    */
  def updateAutoincrements(tables: Seq[Table], connection: Connection, meta: DatabaseStructure) {
  }
}

object H2 extends NaiveDatabaseDialect {
  val driverClassname = "org.h2.Driver"

  override def truncateTables(tables: Seq[Table], connection: Connection, meta: DatabaseStructure) {
    super.truncateTables(tables, connection, meta)

    updateAutoincrementColumns(connection, tables, meta) { (_, _, _) => 1L }
  }

  private def updateAutoincrementColumns(connection: Connection, tables: Seq[Table], meta: DatabaseStructure)(nextValue: (Statement, String, String) => Long) {
    withStatement(connection) { st =>
      tables foreach { t =>
        t.autoIncrementColumns foreach { c =>
          st.executeUpdate("ALTER TABLE %1$s ALTER COLUMN %2$s RESTART WITH %3$d" format(t.name, c, nextValue(st, t.name, c)))
        }
      }
    }
  }

  /** Update autoincrement of `tables` if necessary so that subsequent inserts will work.
    * @param tables tables to delete from
    * @param connection connection to operate in
    */
  override def updateAutoincrements(tables: Seq[Table], connection: Connection, meta: DatabaseStructure) {
    updateAutoincrementColumns(connection, tables, meta) { (st, table, column) =>
      st.executeQuery("SELECT MAX(%2$s) + 1 FROM %1$s" format (table, column)).map(_.getLong(1)).head
    }
  }
}

object PostgreSQL extends DatabaseDialect {
  val driverClassname = "org.postgresql.Driver"

  def truncateTables(tables: Seq[Table], connection: Connection, meta: DatabaseStructure) {
    withStatement(connection) { st =>
      st.executeUpdate("TRUNCATE TABLE %s RESTART IDENTITY" format (tables.map(_.name) mkString ","))
    }
  }

  def updateAutoincrements(tables: Seq[Table], connection: Connection, meta: DatabaseStructure) {
    withStatement(connection) { st =>
      tables foreach { t =>
        t.autoIncrementColumns foreach { c =>
          st.execute("SELECT pg_catalog.setval(pg_get_serial_sequence('%1$s', '%2$s'), (SELECT MAX(%2$s) FROM %1$s))" format(t.name, c))
        }
      }
    }
  }
}
