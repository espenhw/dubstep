package org.grumblesmurf

package object dubstep {
  def loadData(dataset: Dataset, moreDatasets: Dataset*) {
    (Seq(dataset) ++ moreDatasets).foreach(ds => ds.database.load(ds))
  }

  def checkData(dataset: Dataset, moreDatasets: Dataset*) {
    (Seq(dataset) ++ moreDatasets).foreach(ds => ds.database.check(ds))
  }
}
