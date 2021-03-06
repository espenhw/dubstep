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

package org.grumblesmurf

package object dubstep {
  def loadData(dataset: Dataset, moreDatasets: Dataset*) {
    (Seq(dataset) ++ moreDatasets).foreach(ds => ds.database.load(ds))
  }

  def checkData(dataset: Dataset, moreDatasets: Dataset*): Option[Iterable[Mismatch]] = {
    (Seq(dataset) ++ moreDatasets).flatMap(ds => ds.database.check(ds)) match {
      case ms if ms.nonEmpty => Some(ms)
      case _ => None
    }
  }
}
