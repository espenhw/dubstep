package test

import org.grumblesmurf.dubstep._
import org.junit.Test
import scala.Some

class H2Test {
  implicit val dbDialect = H2
  implicit val db = SimpleDatabase("testdb", "sa", "", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", Some("/testdata_h2.sql"))

  @Test
  def loadTestData() {
    loadData(DbtDataset("/testdata.dbt"))
  }
}
