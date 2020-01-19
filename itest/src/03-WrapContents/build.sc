import mill._
import mill.scalalib._
import $exec.plugins
import de.tobiasroeser.mill.osgi._
import mill.api.Loose
import mill.define.Target

def verify() = T.command {
  import de.tobiasroeser.mill.osgi.testsupport.TestSupport._

    withManifest(akkaHttpCore.osgiBundle().path) { manifest =>
      checkExact(manifest, "Manifest-Version", "1.0")
      checkExact(manifest, "Bundle-SymbolicName", "akkaHttpCore_2.12")
      checkExact(manifest, "Bundle-Version", "0.0.0")
    }

  val origEntries = jarFileEntries(akkaHttpCore.originalJar().path)
  val osgiEntries = jarFileEntries(akkaHttpCore.osgiBundle().path)

  val missingEntries = origEntries.filterNot(e => osgiEntries.contains(e))
  assert("Wrapped jar has no missing entries", missingEntries.isEmpty, s"\nMissing entries:\n  ${missingEntries.mkString(",\n  ")}")

  val addedEntries = osgiEntries.filterNot(e => origEntries.contains(e))
  assert("Wrapped jar has no additional entries", addedEntries.isEmpty, s"\nAdded entries:\n  ${addedEntries.mkString(",\n  ")}")

  assert(
    "Wrapped jar contains the same entries",
    origEntries.sorted == osgiEntries.sorted,
    s"Different entries: ${origEntries} vs. ${osgiEntries}"
  )

}

object akkaHttpCore extends ScalaModule with OsgiBundleModule {
  def orig = ivy"com.typesafe.akka::akka-http-core:10.1.11"

  override def scalaVersion: T[String] = "2.12.10"

  override def compileIvyDeps: Target[Loose.Agg[Dep]] = T {
    Agg(orig)
  }

  def originalJar: T[PathRef] = T {
    resolveDeps(T.task {
      Agg(orig.exclude("*" -> "*"))
    })().toSeq.head
  }

  override def includeResource: T[Seq[String]] = T {
    val includeFromJar = Seq(
      "reference.conf",
      "akka-http-version.conf",
      "akka/http/ccompat/CompatImpl.class",
      "akka/http/ccompat/package$.class",
      "akka/http/ccompat/MapHelpers$.class",
      "akka/http/ccompat/CompatImpl$.class",
      "akka/http/ccompat/Builder.class",
      "akka/http/ccompat/package.class",
      "akka/http/ccompat/QuerySeqOptimized.class",
      "akka/http/ccompat/CompatImpl$$anon$1.class",
      "akka/http/ccompat/MapHelpers.class"
    )
    super.includeResource() ++ includeFromJar.map { f =>
      s"@${originalJar().path.toIO.getAbsolutePath()}!/$f"
    }
  }

  override def exportContents = T {
    Seq(
      "akka.http.ccompat"
    )
  }

  override def osgiHeaders: T[OsgiHeaders] = T {
    super.osgiHeaders().copy(
      `Export-Package` = Seq(
        "akka.http",
        // "akka.http.ccompat",
        "akka.http.ccompat.imm",
        "akka.http.impl.*",
        "akka.http.javadsl.*",
        "akka.http.scaladsl.*",
      )
    )
  }

}
