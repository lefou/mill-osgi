import $exec.plugins
import $exec.shared

import java.io.IOException
import java.util.jar.Manifest
import java.util.zip.ZipFile

import os.Path
import mill._
import mill.scalalib._

// plugin-specific imports, generated by integration test infra
import de.tobiasroeser.mill.osgi._

val projVersion = "1.0.0"

def verify() = T.command {
  import de.tobiasroeser.mill.osgi.testsupport.TestSupport._

  withManifest(proj1.jar().path) { manifest =>
    checkExact(manifest, "Bundle-SymbolicName", "proj1_2.12")
    checkExact(manifest, "Bundle-Version", projVersion)
    checkSlices(manifest, "Export-Package", Seq("proj1;", s"""version="${projVersion}""""))
  }

  withManifest(proj2.jar().path) { manifest =>
    checkExact(manifest, "Bundle-SymbolicName", "proj2_2.12")
    checkExact(manifest, "Bundle-Version", projVersion)
    checkSlices(manifest, "Export-Package", Seq("proj2;", s"""version="${projVersion}""""))

    val rangeFrom = projVersion.split("[.]").take(2).mkString(".")
    val rangeTo = projVersion.split("[.]").head.toInt + 1
    checkSlices(manifest, "Import-Package", Seq(s"""proj1;version="[${rangeFrom},${rangeTo})""""))
  }

}

object proj1 extends ScalaModule with OsgiBundleModule {
  override def scalaVersion: T[String] = "2.12.7"

  override def bundleVersion = projVersion

  override def osgiHeaders = super.osgiHeaders().copy(
    `Export-Package` = Seq("proj1")
  )
}

object proj2 extends ScalaModule with OsgiBundleModule {
  override def scalaVersion: T[String] = "2.12.7"

  override def bundleVersion = projVersion

  override def osgiHeaders = super.osgiHeaders().copy(
    `Export-Package` = Seq("proj2")
  )

  override def moduleDeps = Seq(proj1)
}
