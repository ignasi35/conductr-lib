/*
 * Copyright © 2014-2015 Typesafe, Inc. All rights reserved. No information contained herein may be reproduced or
 * transmitted in any form or by any means without the express written permission of Typesafe, Inc.
 */

import Tests._

name := "play-conductr-bundle-lib"

libraryDependencies ++= List(
  Library.playWs,
  Library.akkaTestkit     % "test",
  Library.playTest        % "test",
  Library.scalaTest       % "test"
)

resolvers += Resolvers.typesafeReleases

fork in Test := true

def groupByFirst(tests: Seq[TestDefinition]) =
  tests
    .groupBy(t => t.name.drop(t.name.indexOf("WithEnv")))
    .map {
      case ("WithEnv", t) =>
        new Group("WithEnv", t, SubProcess(ForkOptions(envVars = Map(
          "BUNDLE_ID" -> "0BADF00DDEADBEEF",
          "CONDUCTR_STATUS" -> "http://127.0.0.1:60007",
          "SERVICE_LOCATOR" -> "http://127.0.0.1:60008/services"))))
      case (x, t) =>
        new Group("WithoutEnv", t, SubProcess(ForkOptions()))
    }.toSeq

testGrouping in Test <<= (definedTests in Test).map(groupByFirst)
