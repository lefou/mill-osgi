import ammonite.ops.home
import mill._
import mill.scalalib._
import mill.scalalib.publish._

/** Build JARs. */
def build() = T.command {
  core.jar()
}

/** Run test. */
def test() = T.command {
  core.test.test()()
}

def install() = T.command {
  build()()
  test()()
  core.publishLocal()()
  core.publishM2Local()()
  val a = core.artifactMetadata()
  T.ctx().log.info(s"Installed ${a} into Ivy and Maven repository")
}

object core
  extends ScalaModule
  with PublishModule {

  def scalaVersion = "2.12.7"

  def publishVersion = "0.0.1-SNAPSHOT"

  object Deps {
    val bndlib = ivy"biz.aQute.bnd:biz.aQute.bndlib:4.0.0"
    val logbackClassic = ivy"ch.qos.logback:logback-classic:1.1.3"
    val millMain = ivy"com.lihaoyi::mill-main:0.2.8"
    val millScalalib = ivy"com.lihaoyi::mill-scalalib:0.2.8"
    val scalaTest = ivy"org.scalatest::scalatest:3.0.1"
    val slf4j = ivy"org.slf4j:slf4j-api:1.7.25"
  }

  def javacOptions = Seq("-source", "1.8", "-target", "1.8")

  def ivyDeps = Agg(
    Deps.bndlib,
    Deps.millMain,
    Deps.millScalalib
  )

  object test extends Tests {

    def ivyDeps = Agg(
      Deps.scalaTest
    )

    def testFrameworks = Seq("org.scalatest.tools.Framework")

  }

  override def artifactName: T[String] = T {
    "mill-osgi"
  }

  def pomSettings = T {
    PomSettings(
      description = "Mill module adding OSGi bundle support",
      organization = "de.tobiasroeser",
      url = "https://github.com/lefou/mill-osgi",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("lefou", "mill-osgi"),
      developers = Seq(Developer("lefou", "Tobias Roeser", "https.//github.com/lefou"))
    )
  }

  /** Publish to the local Maven repository */
  def publishM2Local() = T.command {
    new LocalM2Publisher(home / ".m2" / "repository")
      .publish(
        jar = jar().path,
        sourcesJar = sourceJar().path,
        docJar = docJar().path,
        pom = pom().path,
        artifact = artifactMetadata()
      )
  }

}

import ammonite.ops._

class LocalM2Publisher(m2Repo: Path) {

  def publish(
    jar: Path,
    sourcesJar: Path,
    docJar: Path,
    pom: Path,
    artifact: Artifact
  ): Unit = {
    val releaseDir = m2Repo / artifact.group.split("[.]") / artifact.id / artifact.version
    writeFiles(
      jar -> releaseDir / s"${artifact.id}-${artifact.version}.jar",
      sourcesJar -> releaseDir / s"${artifact.id}-${artifact.version}-sources.jar",
      docJar -> releaseDir / s"${artifact.id}-${artifact.version}-javadoc.jar",
      pom -> releaseDir / s"${artifact.id}-${artifact.version}.pom"
    )
  }

  private def writeFiles(fromTo: (Path, Path)*): Unit = {
    fromTo.foreach {
      case (from, to) =>
        mkdir(to / up)
        cp.over(from, to)
    }
  }

}