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

import java.sql.{Timestamp, ResultSet, Statement, Connection}
import scala.collection.mutable

object Utilities {
  /** Calls `fn` with `connection`. `connection` will be closed after `fn` completes.
    *
    * @param connection the Connection to use
    * @param fn the function which receives `connection`
    * @tparam T the return type
    * @return the return value of `fn`
    */
  def withConnection[T](connection: => Connection)(fn: Connection => T): T = {
    try {
      fn(connection)
    } finally {
      connection.close()
    }
  }

  /** Calls `fn` with a newly-created Statement from `connection`. The Statement will be closed after `fn` completes.
    *
    * @param connection the Connection to use
    * @param fn the function which receives a fresh Statement
    * @tparam T the return type
    * @return the return value of `fn`
    */
  def withStatement[T](connection: Connection)(fn: Statement => T): T = {
    val s = connection.createStatement()
    try {
      fn(s)
    } finally {
      s.close()
    }
  }

  def inTransaction[T](connection: Connection)(fn: => T): T = {
    val autoCommitState = connection.getAutoCommit
    connection.setAutoCommit(false)
    try {
      val result = fn
      connection.commit()
      result
    } catch {
      case e: Exception => {
        connection.rollback()
        throw e
      }
    } finally {
      connection.setAutoCommit(autoCommitState)
    }
  }

  implicit def wrapResultSet(rs: ResultSet): RichResultSet = new RichResultSet(rs)
}

class RichResultSet(rs: ResultSet) extends mutable.Traversable[ResultSet] {
  def foreach[U](f: (ResultSet) => U) {
    try {
      while (rs.next()) {
        f(rs)
      }
    } finally {
      rs.close()
    }
  }
}
