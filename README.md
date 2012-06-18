# Dubstep - Framework-agnostic test data loading for Scala

## A short example to whet your appetite

```scala
class DatabaseUsingJunitTest {
  implicit val dbDialect = H2
  implicit val db = SimpleDatabase("sa", "", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", Some("/testdb_schema.sql"))

  @Test
  def aReallyStupidTestName() {
    loadData(DbtDataset("/predata.dbt"))
    // ... do stuff with the data ...
    checkData(DbtDataset("/postdata.dbt"))  // NOT IMPLEMENTED YET
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

## So what does it do?

Dubstep is centered around datasets.  A dataset has a reference to a data file (loaded as a classpath resource) and a database.
A database, in turn, knows how to connect and knows its dialect (e.g. H2 or PostgreSQL); in addition, it can optionally
have a reference to a schema definition (again, a classpath resource).

You can tell Dubstep to `org.grumblesmurf.dubstep.loadData(dataset)`.  That will:

1.  Check the whether dataset's database has had its schema loaded (if it has one).  If not, it will drop all tables in
    the database and load the supplied schema definition.  Incidentally, this is remembered by the database object - thus,
    if you arrange to share the database object between your tests the schema will only be reloaded once.

1.  Empty any tables referenced in the dataset.

1.  Load the data.

1.  Update any autoincrement sequences to match the newly inserted data, if necessary.

## But....

* Does it support multiple databases?

  Yes!  That's why the database is is a property of the dataset.

* But I only have one database!

  Declare it as an `implicit val`; then you don't need to pass it to every dataset (this handy trick also applies to multiple
  databases of the same dialect).

* I already have a `DataSource`, can Dubstep use that?

  Yes!  Instantiate a `DataSourceDatabase` with it, and off you go.

* Can Dubstep check database content too?

  No, not yet.  But that is the next feature on the list...

* Will Dubstep touch any tables not mentioned in the dataset?

  No (well, apart from when reloading the schema). And when checking is implemented, it will only look at the tables you mention.

* Can Dubstep load/check multiple datasets?

  Of course!  Both `loadDataset` and `checkDataset` can take as many datasets as you care to throw at them (well, up to whatever
  limit Scala imposes on varargs).  And since each dataset knows what database it belongs to...

* What about support for other databases?

  You can easily implement your own `DatabaseDialect`; as a starting point, extend `NaiveDatabaseDialect` and define `driverClassname`.
  If you do this, feel free to send me your code for inclusion.

* This is cool, can I help?

  Sure!  See that fork button up there?  Hit it, improve the code, implement cool new features, whatever; and then let me know
  so I can merge it back in.  If you don't feel quite up to doing that, create an issue for your bug report or feature request
  or gushing exclamation of love.

## Building Dubstep

For the integration tests to pass you need a local PostgreSQL database named `testdb` where a user named `test` has access
with password `test`.  I know, very imaginative.  Then run:

```
./sbt test it:test +publish-local
```

to publish Dubstep to your local Ivy cache, built for stable Scala versions 2.8.1 and up.

You can now refer to `"org.grumblesmurf" %% "dubstep" % "0.1-SNAPSHOT"` in your SBT build definition.

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
