import ammonite.ops.home
import ammonite.ops.ls
import mill._
import mill.define.Module
import mill.scalalib._
import mill.scalalib.publish._

/** Build JARs. */
def _build() = T.command {
  core.jar()
}

/** Run test. */
def _test() = T.command {
  core.test.test()()
}

def _install() = T.command {
  _build()()
  _test()()
  core.publishLocal()()
  core.publishM2Local()()
  val a = core.artifactMetadata()
  T.ctx().log.info(s"Installed ${a} into Ivy and Maven repository")
}

//def idea() = T.command {
//  mill.scalalib.GenIdea.idea()
//}

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
    Deps.slf4j,
    Deps.millMain,
    Deps.millScalalib
  )

  object test extends Tests {

    override def ivyDeps = Agg(
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

object integrationTest extends Module {

  def testCases = T.input {
    val src = millSourcePath / 'src
    ls(src).filter(_.isDir).map(PathRef(_))
  }

  def test() = T.command {
    val tests = testCases()
    mkdir(T.ctx().dest)

    def resolve(name: String): Path = {
      core.runClasspath().find(pr =>
        pr.path.last.startsWith(name)).get.path
    }

    //    pr.path.last.startsWith("bnd-")

    val libs = Seq(
      core.jar().path -> "mill-osgi.jar",
      resolve("slf4j-api-") -> "slf4j-api.jar",
      resolve("biz.aQute.bndlib-") -> "bndlib.jar"
    )

    val libPath = T.ctx().dest / 'lib
    mkdir(libPath)
    libs.foreach { lib =>
      // copy plugin here
      cp(lib._1, libPath / lib._2)
    }

    case class TestCase(name: String, exitCode: Int, out: Seq[String], err: Seq[String]) {
      override def toString(): String =
        s"Test case: ${
          name
        }\nExit code: ${
          exitCode
        }\n\n[out]\n\n${
          out.mkString("\n")
        }\n\n[err]\n\n${
          err.mkString("\n")
        }"

    }

    val results = tests.map { t =>
      val testPath = T.ctx().dest / t.path.last
      T.ctx().log.info("Running integration test: " + t.path.last)

      // start clean
      rm(testPath)

      // copy test project here
      cp(t.path, testPath)

      // create plugin classpath file
      write(testPath / "plugin.sc", libs.map(lib =>
        "import $cp.^.lib.`" + lib._2 + "`\n"))

      // run mill with _verify target in test path
      val result = try {
        %%("mill", "_verify")(testPath)
      } catch {
        case e: ShelloutException => e.result
      }

      TestCase(t.path.last, result.exitCode, result.out.lines, result.err.lines)
    }

    val (succeeded, failed) = results.partition(_.exitCode == 0)

    println(s"\nSucceeded tests: ${succeeded.size}\n${succeeded.mkString("\n", "\n", "")}")
    println(s"\nFailed tests: ${failed.size}\n${failed.mkString("\n", "\n", "")}")

    if(!failed.isEmpty) throw new AssertionError(s"${failed.size} integration test(s) failed")

  }

}

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
