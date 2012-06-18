package test

import org.junit.Test
import org.grumblesmurf.dubstep.{DbtDataset, PostgreSQL, SimpleDatabase, loadData}

class PostgresTest {
  implicit val dbDialect = PostgreSQL
  implicit val db = SimpleDatabase("test", "test", "jdbc:postgresql:testdb", Some("/testdata_psql.sql"))

  @Test
  def loadTestData() {
    loadData(DbtDataset("/testdata.dbt"))
  }
}
