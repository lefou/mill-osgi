import $exec.plugins
import $exec.shared
import mill._
import mill.scalalib._
import de.tobiasroeser.mill.osgi._
import os.Path

def verify() = T.command {
  import de.tobiasroeser.mill.osgi.testsupport.TestSupport._

  for {
    jar <- Seq(hello.jar().path)
    jarEntries = jarFileEntries(jar)
  } {
    Seq(
      "example/HelloWorld.class",
      "OSGI-OPT/src/example/HelloWorld.scala"
    ).foreach(p => assert(jarEntries.contains(p), s"Jar file ${jar} does not contain: ${p}. Content: ${jarEntries}"))

    withManifest(jar) { manifest =>
      checkExact(manifest, "Manifest-Version", "1.0")
      checkExact(manifest, "Bundle-SymbolicName", "hello_2.13")
      checkExact(manifest, "Bundle-Version", "0.0.0")
      checkSlices(manifest, "Private-Package", Seq("example"))
    }
  }
}

trait Template extends ScalaModule with OsgiBundleModule {
  override def millSourcePath: Path = super.millSourcePath / os.up / "hello"
  override def scalaVersion: T[String] = "2.13.6"
  override def includeSources = true
}

object hello extends Template {
  override def osgiBuildMode = OsgiBundleModule.BuildMode.ReplaceJarTarget
}

object helloCalcManifest extends Template {
  override def osgiBuildMode = OsgiBundleModule.BuildMode.CalculateManifest
}