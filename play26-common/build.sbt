name := "play26-conductr-lib-common"

libraryDependencies ++= List(
  Library.play26Ws
)

crossScalaVersions := crossScalaVersions.value.filterNot(_.startsWith("2.12"))
