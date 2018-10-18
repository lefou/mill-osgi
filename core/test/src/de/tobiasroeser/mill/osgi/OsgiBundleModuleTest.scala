package de.tobiasroeser.mill.osgi

import org.scalatest.FreeSpec

class OsgiBundleModuleTest extends FreeSpec {

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
        case (pair, expected) => s"${pair} should result in ${expected}" in {
          assert((OsgiBundleModule.calcBundleSymbolicName _).tupled(pair) === expected)
        }
      }

  }

}