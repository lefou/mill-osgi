import $exec.plugins
import $exec.shared
import mill._
import mill.scalalib._
import mill.scalalib.publish._
import de.tobiasroeser.mill.osgi._
import os.Path

def verify() = T.command {
  import de.tobiasroeser.mill.osgi.testsupport.TestSupport._

  for {
    (mode, jar) <- Seq(
      (hello.osgiBuildMode, hello.jar()),
      (helloManifest.osgiBuildMode, helloManifest.jar())
    )
  } {
    withManifest(jar.path) { manifest =>
      checkExact(manifest, "Manifest-Version", "1.0")
      checkExact(manifest, "Bundle-SymbolicName", "hello_2.12")
      checkExact(manifest, "Bundle-Version", "0.0.0")
      mode match {
        case OsgiBundleModule.BuildMode.ReplaceJarTarget =>
          checkSlices(manifest, "Private-Package", Seq("example"))
        case OsgiBundleModule.BuildMode.CalculateManifest =>
          checkUndefined(manifest, "Private-Package")
      }
    }
  }

  withManifest(helloPublish.jar().path) { manifest =>
    checkExact(manifest, "Manifest-Version", "1.0")
    checkExact(manifest, "Bundle-SymbolicName", "de.tototec.hello_2.12")
    checkExact(manifest, "Bundle-Version", "1.2.3")
    checkSlices(manifest, "Private-Package", Seq("example"))
    checkExact(manifest, "Bundle-License", "https://spdx.org/licenses/Apache-2.0.html")
    checkExact(manifest, "Bundle-Vendor", "de.tototec")
    checkExact(manifest, "Bundle-Description", "Test")
  }

}

trait Template extends ScalaModule with OsgiBundleModule {
  override def millSourcePath: Path = super.millSourcePath / os.up / "hello"
  override def scalaVersion: T[String] = "2.12.7"
  override def artifactName = "hello"
}

object hello extends Template {
  override def osgiBuildMode = OsgiBundleModule.BuildMode.ReplaceJarTarget
}

object helloManifest extends Template {
  override def osgiBuildMode = OsgiBundleModule.BuildMode.CalculateManifest
}

object helloPublish extends Template with PublishModule {
  override def osgiBuildMode = OsgiBundleModule.BuildMode.ReplaceJarTarget
  override def pomSettings = PomSettings(
    description = "Test",
    organization = "de.tototec",
    url = "https://example.com/",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("lefou", "mill-osgi"),
    developers = Seq(Developer("lefou", "Tobias Roeser", "https://github.com/lefou"))
  )

  override def publishVersion = "1.2.3"
}
