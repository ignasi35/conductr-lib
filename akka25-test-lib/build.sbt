name := "akka25-test-lib"

libraryDependencies ++= List(
  Library.akka25Testkit,
  Library.akkaHttp10Testkit,
  Library.junit,
  Library.scalaTest
)
