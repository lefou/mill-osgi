package de.tobiasroeser.mill.osgi

import org.scalatest.freespec.AnyFreeSpec

class OsgiBundleModuleTest extends AnyFreeSpec {

  object sut extends OsgiBundleModuleSupport

  "Calc BSN from group and artifact" - {
    Seq(
      "group" -> "artifact" -> "group.artifact",
      "a.b.c" -> "d.e" -> "a.b.c.d.e",
      "a.b.c" -> "c.d" -> "a.b.c.d",
      "a.b.c" -> "c-d-e" -> "a.b.c.d.e",
      "a.b.c" -> "c-d.e" -> "a.b.c.d.e",
      "a-b.c" -> "d.e" -> "a-b.c.d.e",
      "a.b" -> "a.b.c" -> "a.b.c"
    ).foreach {
      case (pair, expected) =>
        s"${pair} should result in ${expected}" in {
          assert(sut.calcBundleSymbolicName.tupled(pair) === expected)
        }
    }

  }

  "Check correct mill version" - {
    Seq(
      ("0.1.2", None, true, "Undefined runtime version"),
      ("unparsable", Some("0.3.6"), true, "Unparsable build version"),
      ("0.3.6", Some("0.3.6"), true, "Same version"),
      ("0.3.6", Some("0.3.7"), true, "Higher runtime micro version"),
      ("0.3.6", Some("0.3.5"), false, "Lower runtime micro version"),
      ("0.3.6", Some("0.4.0"), true, "Higher runtime minor version"),
      ("0.3.6", Some("0.2.7"), false, "Lower runtime minor version"),
      ("0.3.6", Some("1.0.0"), true, "Higher runtime major version"),
      ("1.3.6", Some("0.9.9"), false, "Lower runtime major version")
    ).foreach {
      case (buildVersion, runVersion, expected, testName) =>
        s"${testName} is ${if (expected) "" else "not "}ok [buildVersion: ${buildVersion}, runVersion: ${runVersion}]" in {
          assert(sut.checkMillVersion(buildVersion, runVersion) === expected)
        }
    }

  }
}
