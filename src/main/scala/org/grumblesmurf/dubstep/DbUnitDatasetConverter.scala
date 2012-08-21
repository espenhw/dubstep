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

import java.io.{PrintStream, File}
import xml.{Elem, Node, XML}
import collection.immutable.ListMap

object DbUnitDatasetConverter {
  def main(args: Array[String]) {
    args.foreach { path =>
      val f = new File(path)
      convert(f)
    }
  }

  def convert(f: File) {
    if (f.isDirectory) {
      convertDirectory(f)
    } else {
      convertFile(f)
    }
  }

  def convertDirectory(file: File) {
    file.listFiles().foreach(convert _)
  }

  def row(columns: IndexedSeq[String], rowNode: Node): Map[String,Any] = {
    ListMap.empty ++ rowNode.child.collect {
      case e: Elem => e
    }.zipWithIndex.map {
      case (<null/>, i) => columns(i) -> null
      case (<value>{v}</value>, i) =>
        columns(i) -> (v.text match {
          case s if s.matches("^-?[0-9]+$") => s.toInt
          case s if s.matches("^-?[0-9]*.[0-9]+$") => s.toDouble
          case s => s
        })
    }
  }

  def convertTable(node: Node): RowSet = {
    val columns = (node \ "column" map (_.text)).toIndexedSeq
    val rowData = (node \ "row" map(r => row(columns, r)))
    val commonValues: Map[String,Any] = if (rowData.size == 1) {
      Map.empty
    } else {
      ListMap.empty ++ columns.flatMap { c =>
        val distinctValues = rowData.map(_(c)).toSet
        if (distinctValues.size == 1) {
          Some(c -> distinctValues.head)
        } else {
          None
        }
      }
    }

    RowSet(node \ "@name" text, if (commonValues.isEmpty) None else Some(Row(commonValues.filterNot(_._2 == null))), rowData.map(d => Row(d -- commonValues.keys)))
  }

  def convertFile(file: File) {
    if (file.getName.endsWith(".xml")) {
      val outFile = new File(file.getParentFile, file.getName.replaceFirst("\\.xml$", ".dbt"))
      println("%s => %s" format (file, outFile.getName))

      val rowSets = XML.loadFile(file) \\ "table" map convertTable _
      val out = new PrintStream(outFile)
      out.println(rowSets.map(_.asDbt).mkString("\n\n"))
      out.close()
    }
  }
}
