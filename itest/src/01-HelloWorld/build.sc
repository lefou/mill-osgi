import mill._
import mill.scalalib._
import $exec.plugins
import de.tobiasroeser.mill.osgi._

def verify() = T.command {
  import de.tobiasroeser.mill.osgi.testsupport.TestSupport._

  withManifest(hello.jar().path) { manifest =>
    checkExact(manifest, "Manifest-Version", "1.0")
    checkExact(manifest, "Bundle-SymbolicName", "hello_2.12")
    checkExact(manifest, "Bundle-Version", "0.0.0")
    checkSlices(manifest, "Private-Package", Seq("example"))
  }
}

object hello extends ScalaModule with OsgiBundleModule {
  override def scalaVersion: T[String] = "2.12.7"
}
