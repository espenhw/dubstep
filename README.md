# Dubstep - Framework-agnostic database testing for Scala

## A short example to whet your appetite

```scala
import org.grumblesmurf.dubstep._
import Utilities.cp // implicit conversion String -> Source from classpath resource

class DatabaseUsingJunitTest {
  implicit val dbDialect = H2
  implicit val db = SimpleDatabase("sa", "", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", Some("/testdb_schema.sql"))

  @Test
  def aReallyStupidTestName() {
    loadData(DbtDataset("/predata.dbt"))
    // ... do stuff with the data ...
    checkData(DbtDataset("/postdata.dbt")).map(x => fail(x.mkString("\n")))
  }
}
```

## What do you mean, framework-agnostic?

Dubstep has no ties to any test framework.  So, whether you like [JUnit](http://www.junit.org/),
[ScalaTest](http://www.scalatest.org/), [Specs2](http://etorreborre.github.com/specs2/) or
[TestingFrameworkDuJour](http://your.url.here/) you can use Dubstep.

## Why use Dubstep?

Because [DbUnit](http://www.dbunit.org), the nearest competition, is heavyweight and XML-centric.  Something like
[Unitils](http://www.unitils.org/) can take away some of the pain, but at the cost of introducing magical annotations and
binding you to its supported frameworks.

Dubstep, on the other hand is small, lightweight, agnostic and explicit, and its native dataset syntax (shamelessly <del>stolen</del>
borrowed from [scaladbtest](https://github.com/egervari/scaladbtest/)) is designed to minimize typing and maximize readability.
And supporting more dataset formats is easy.

## So what does it do?

Dubstep is centered around datasets.  A dataset has a reference to a data Source and a database.
A database, in turn, knows how to connect and knows its dialect (e.g. H2 or PostgreSQL); in addition, it can optionally
have a reference to a schema definition (again, a Source).

## Using Dubstep

### Reference the beast

In your SBT build descriptor:

```
resolvers += Resolver.url("grumblesmurf", url("http://maven.grumblesmurf.org/"))(Resolver.ivyStylePatterns)

libraryDependencies += "org.grumblesmurf" %% "dubstep" % "0.1-SNAPSHOT" % "it" // Or "test"
```

You can then `import org.grumblesmurf.dubstep._`.

### Loading data

You can tell Dubstep to `loadData(dataset)`.  That will:

1.  Check the whether dataset's database has had its schema loaded (if it has one).  If not, it will drop *all* tables in
    the database and load the supplied schema definition.  Incidentally, this is remembered by the database instance - thus,
    if you arrange to share the database instance between your tests the schema will only be reloaded once.

1.  Empty any tables referenced in the dataset.

1.  Load the data, in the order defined in the dataset.

1.  Update any autoincrement sequences to match the newly inserted data, if necessary.

### Checking data

After your code has run you can ask Dubstep to `checkData(dataset)`.  That will:

1.  Try to match the rows in the dataset with the ones in the database (using primary keys) and collect any mismatches.

1.  Collect any rows in the dataset that don't have a matching row in the database.

1.  Collect any rows in the database that don't have a matching row in the dataset.

The collected mismatches are returned as an `Option[Iterable[Mismatch]]`.  You are then free to do what you wish with them.  One
possible approach (using JUnit):

```scala
checkData(DbtDataset("/postdata.dbt")).map(mismatches => fail(mismatches.mkString("\n")))
```

More creatively (contrivedly?), using ScalaTest matches (and `OptionValues`):

```scala
// Verify that the code touched the customer name
checkData(dataset).value must contain (RowDataMismatch(db.table("customers").get, Seq(42), Seq(ColumnMismatch("name", "Oldname", "Newname"))))
```

## But....

* Does it support multiple databases?

  Yes!  That's why the database is is a property of the dataset.

* But I only have one database!

  Declare it as an `implicit val`; then you don't need to pass it to every dataset (this handy trick also applies to multiple
  databases of the same dialect).

* I already have a `DataSource`, can Dubstep use that?

  Yes!  Instantiate a `DataSourceDatabase` with it, and off you go.

* Will Dubstep touch any tables not mentioned in the dataset?

  No, apart from when reloading the schema - and it will only do that if you tell it to (by giving it a schema definition).

* How do I ignore columns when checking a dataset?

  By not mentioning them.  Dubstep will only look at the columns you give it.  Compare `testdata.dbt` with
  `testdata_result.dbt` in `src/it/resources` for an example.

* Can Dubstep load/check multiple datasets?

  Of course!  Both `loadDataset` and `checkDataset` can take as many datasets as you care to throw at them (well, up to whatever
  limit Scala imposes on varargs).  And since each dataset knows what database it belongs to...

* Dubstep falls over when loading my dataset!  The error message says something about foreign keys or referential integrity.

  By design, Dubstep does not disable referential integrity checks while loading data; since you have complete control
  over the insertion order it is not necessary to do so.  To fix your problem, reorder the rows in your dataset (see
  `src/it/resources/testdata.dbt` for an example)

* Dubstep barfs while checking my dataset, complaining about a missing primary key!

  Dubstep will cowardly refuse to check tables that do not have a primary key; there is no good way to match "corresponding"
  rows between datasets in that case.  If this bothers you, feel free to implement...

* What about support for other databases?

  You can easily implement your own `DatabaseDialect`; as a starting point, extend `NaiveDatabaseDialect` and define `driverClassname`.
  If you do this, please send me your code for inclusion.

* Help!  I don't understand the dataset format!

  See [the summary documentation](#dbtdoc).

* This is cool, can I help?

  Sure!  See that fork button up there?  Hit it, improve the code, implement cool new features, whatever; and then let me know
  so I can merge it back in.  If you don't feel quite up to doing that, create an issue for your bug report or feature request
  or gushing exclamation of love.

## Building Dubstep

For the integration tests to pass you need a local PostgreSQL database named `testdb` where a user named `test` has access
with password `test`; it requires CREATE privileges in order to create the schema.  I know, very imaginative.  Then run:

```
./sbt test it:test +publish-local
```

to publish Dubstep to your local Ivy cache, built for stable Scala versions 2.8.1 and up.

You can now refer to `"org.grumblesmurf" %% "dubstep" % "0.1-SNAPSHOT"` in your SBT build definition.

## <a id="dbtdoc"></a>The DBT format

Dubstep implements most of the DBT format as documented [in scaladbtest](https://github.com/egervari/scaladbtest#dbt-usage), with two differences:

### 1. Labels are not supported (if you need/want them, let me know)

### 2.  A new syntax for "global/remembered defaults" is provided:

```
master:
?? static_column: "value"
- id: 1, name: "foo"

child:
- id: 1, master_id: 1

master:
- id: 2, name: "bar"
```

When inserting/checking this dataset, Dubstep will remember that `static_column` should have the value `"value"` even
for the second set of rows.  This is especially useful during data load if you have non-nullable timestamp columns in your schema:

```
table:
?? inserted: $now, updated: $now
```

## Boring legal stuff

Dubstep is copyright 2012 Espen Wiborg.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. See the License for the specific language
governing permissions and limitations under the License.
