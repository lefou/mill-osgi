import $exec.plugins
import $exec.shared
import mill._
import mill.scalalib._
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
      checkExact(manifest, "Bundle-SymbolicName", "hello_2.13")
      checkExact(manifest, "Bundle-Version", "0.0.0")
      mode match {
        case OsgiBundleModule.BuildMode.ReplaceJarTarget =>
          checkSlices(manifest, "Private-Package", Seq("example"))
        case OsgiBundleModule.BuildMode.CalculateManifest =>
          checkUndefined(manifest, "Private-Package")
      }
    }
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
