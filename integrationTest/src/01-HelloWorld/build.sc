import mill._
import mill.scalalib._
import $exec.plugin
import de.tobiasroeser.mill.osgi._

def _verify() = T.command {
  hello.osgiBundle()
}

object hello extends ScalaModule with OsgiBundleModule {
  override def scalaVersion: T[String] = "2.12.7"
}
