import mill._
import mill.scalalib._
import $exec.plugin
import de.tobiasroeser.mill.osgi._
import de.tobiasroeser.mill.osgi.testsupport._

def _verify() = T.command {
  TestSupport.withManifest(hello.jar().path) { manifest =>
    TestSupport.check(manifest, "Manifest-Version", Seq("1.0"))
    TestSupport.check(manifest, "Private-Package", Seq("example"))
  }
}

object hello extends ScalaModule with OsgiBundleModule {
  override def scalaVersion: T[String] = "2.12.7"
}
