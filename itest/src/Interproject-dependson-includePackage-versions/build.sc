import $file.shared
import java.io.IOException
import java.util.jar.Manifest
import java.util.zip.ZipFile

import os.Path
import mill._
import mill.scalalib._
import de.tobiasroeser.mill.osgi._
import de.tobiasroeser.mill.osgi.testsupport.TestSupport._

val projVersion = "1.0.0"

def verifyTask(jar1: PathRef, jar2: PathRef) =  {
  println(s"jar1: ${jar1}")
  withManifest(jar1.path) { manifest =>
    checkExact(manifest, "Bundle-SymbolicName", "proj1_2.12")
    checkExact(manifest, "Bundle-Version", projVersion)
    checkSlices(manifest, "Export-Package", Seq("proj1;", s"""version="${projVersion}""""))
  }
  println(s"jar2: ${jar2}")
  withManifest(jar2.path) { manifest =>
    checkExact(manifest, "Bundle-SymbolicName", "proj2_2.12")
    checkExact(manifest, "Bundle-Version", projVersion)
    checkSlices(manifest, "Export-Package", Seq("proj2;", s"""version="${projVersion}""""))

    val rangeFrom = projVersion.split("[.]").take(2).mkString(".")
    val rangeTo = projVersion.split("[.]").head.toInt + 1
    checkSlices(manifest, "Import-Package", Seq(s"""proj1;version="[${rangeFrom},${rangeTo})""""))
  }
}

def verify() = T.command {
  verifyTask(proj1.jar(), proj2.jar())
  verifyTask(manifest.proj1.jar(), manifest.proj2.jar())
  ()
}

trait TemplateBnd extends ScalaModule with OsgiBundleModule {
  override def osgiBuildMode: OsgiBundleModule.BuildMode = OsgiBundleModule.BuildMode.ReplaceJarTarget
  override def scalaVersion: T[String] = "2.12.7"
  override def bundleVersion = projVersion
}

object proj1 extends TemplateBnd {
  override def osgiHeaders = super.osgiHeaders().copy(
    `Export-Package` = Seq("proj1")
  )
}

object proj2 extends TemplateBnd {
  override def osgiHeaders = super.osgiHeaders().copy(
    `Export-Package` = Seq("proj2")
  )
  override def moduleDeps = Seq(proj1)
}

trait TemplateManifest extends TemplateBnd {
  override def osgiBuildMode = OsgiBundleModule.BuildMode.CalculateManifest
}

object manifest extends Module {
  override def millSourcePath = super.millSourcePath / os.up
  object proj1 extends TemplateManifest {
    override def artifactName = "proj1"
    override def osgiHeaders = super.osgiHeaders().copy(
      `Export-Package` = Seq("proj1")
    )
  }

  object proj2 extends TemplateManifest {
    override def artifactName = "proj2"
    override def osgiHeaders = super.osgiHeaders().copy(
      `Export-Package` = Seq("proj2")
    )
    override def moduleDeps = Seq(proj1)
  }
}
