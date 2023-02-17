import $exec.plugins
import $exec.shared

import mill._
import mill.scalalib._
import de.tobiasroeser.mill.osgi._
import mill.api.Loose
import mill.define.Target
import de.tobiasroeser.mill.osgi.testsupport.TestSupport._

def verify() = T.command {

  for {
    (originalJar, jar) <- Seq(
      (akkaHttpCore.originalJar(), akkaHttpCore.jar())
      // (akkaHttpCoreCalcManifest.originalJar(), akkaHttpCoreCalcManifest.jar())
    )
  } {

    withManifest(jar.path) { manifest =>
      checkExact(manifest, "Manifest-Version", "1.0")
      checkExact(manifest, "Bundle-SymbolicName", "akkaHttpCore_2.12")
      checkExact(manifest, "Bundle-Version", "0.0.0")
    }

    val origEntries = jarFileEntries(originalJar.path)
    val osgiEntries = jarFileEntries(jar.path)

    val missingEntries = origEntries.filterNot(e => osgiEntries.contains(e))
    assert(
      "Wrapped jar has no missing entries",
      missingEntries.isEmpty,
      s"\nMissing entries:\n  ${missingEntries.mkString(",\n  ")}"
    )

    val addedEntries = osgiEntries.filterNot(e => origEntries.contains(e))
    assert(
      "Wrapped jar has no additional entries",
      addedEntries.isEmpty,
      s"\nAdded entries:\n  ${addedEntries.mkString(",\n  ")}"
    )

    assert(
      "Wrapped jar contains the same entries",
      origEntries.sorted == osgiEntries.sorted,
      s"Different entries: ${origEntries} vs. ${osgiEntries}"
    )
  }
}

trait TemplateAkkaHttpCore extends ScalaModule with OsgiBundleModule {
  def orig = ivy"com.typesafe.akka::akka-http-core:10.1.11"
  override def scalaVersion: T[String] = "2.12.10"
  override def artifactName = "akkaHttpCore"
  override def compileIvyDeps: Target[Loose.Agg[Dep]] = Agg(orig)
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
  override def exportContents = Seq(
    "akka.http.ccompat"
  )
  override def osgiHeaders: T[OsgiHeaders] = T {
    super.osgiHeaders().copy(
      `Export-Package` = Seq(
        "akka.http",
        // "akka.http.ccompat",
        "akka.http.ccompat.imm",
        "akka.http.impl.*",
        "akka.http.javadsl.*",
        "akka.http.scaladsl.*"
      )
    )
  }

}

object akkaHttpCore extends TemplateAkkaHttpCore

//object akkaHttpCoreCalcManifest extends TemplateAkkaHttpCore {
//  override def osgiBuildMode = OsgiBundleModule.BuildMode.CalculateManifest
//}
