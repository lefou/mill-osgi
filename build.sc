import mill.define.{Module, TaskModule}
import mill.eval.PathRef
import mill.scalalib._
import mill.scalalib.publish._

// Generate BuildInfo.scala
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:0.5.0`, mill.contrib.BuildInfo

// Run integration tests with mill 
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest:0.1.0`, de.tobiasroeser.mill.integrationtest._

// Generate converage reports
import $ivy.`com.lihaoyi::mill-contrib-scoverage:0.5.0`, mill.contrib.scoverage.ScoverageModule

// The mill version used in the project/sources/dependencies, also default for integration tests
def millVersion = "0.3.6"

/** Build JARs. */
def _build() = T.command {
  core.jar()
}

/** Run tests. */
def test() = T.command {
  core.test.test()()
  integrationTest.test()()
}

def install() = T.command {
  T.ctx().log.info("Installing")
  test()()
  core.publishLocal()()
}

def checkRelease: T[Boolean] = T.input {
  if (GitSupport.publishVersion()._2.contains("DIRTY")) {
    T.ctx().log.error("Project (git) state is dirty. Release not recommended!")
    false
  } else { true }
}

/** Test and release to Maven Central. */
def release(
  sonatypeCreds: String,
  release: Boolean = true
) = T.command {
  if (checkRelease()) {
    test()()
    core.publish(sonatypeCreds = sonatypeCreds, release = release)()
  }
}

trait MillOsgiModule extends ScalaModule with PublishModule {

  def scalaVersion = T { "2.12.8" }
  
  def ivyDeps = T {
    Agg(ivy"org.scala-lang:scala-library:${scalaVersion()}")
  }

  def publishVersion = GitSupport.publishVersion()._2

  object Deps {
    val ammonite = ivy"com.lihaoyi:::ammonite:1.3.2"
    val bndlib = ivy"biz.aQute.bnd:biz.aQute.bndlib:4.2.0"
    val logbackClassic = ivy"ch.qos.logback:logback-classic:1.1.3"
    val millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
    val millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
    val scalaTest = ivy"org.scalatest::scalatest:3.0.1"
    val slf4j = ivy"org.slf4j:slf4j-api:1.7.25"
  }

  def javacOptions = Seq("-source", "1.8", "-target", "1.8")

  def pomSettings = T {
    PomSettings(
      description = "Mill module adding OSGi bundle support",
      organization = "de.tototec",
      url = "https://github.com/lefou/mill-osgi",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("lefou", "mill-osgi"),
      developers = Seq(Developer("lefou", "Tobias Roeser", "https.//github.com/lefou"))
    )
  }

}

object core extends MillOsgiModule with BuildInfo with ScoverageModule {

  def scoverageVersion = T { "1.3.1" }

  override def artifactName = T { "de.tobiasroeser.mill.osgi" }

  def ivyDeps = T {
    super.ivyDeps() ++ Agg(
      Deps.bndlib,
      Deps.slf4j
    )
  }

  def compileIvyDeps = Agg(
    Deps.millMain,
    Deps.millScalalib
  )

  def buildInfoPackageName = Some("de.tobiasroeser.mill.osgi.internal")
  def buildInfoMembers = T {
    Map(
      "millOsgiVersion" -> publishVersion(),
      "millVersion" -> millVersion
    )
  }

  object test extends ScoverageTests {

    override def ivyDeps = Agg(
      Deps.scalaTest
    )

    def testFrameworks = Seq("org.scalatest.tools.Framework")

  }

}

object testsupport extends MillOsgiModule {

  def compileIvyDeps = Agg(
    Deps.millMain,
    Deps.millScalalib
  )

  override def artifactName = "mill-osgi-testsupport"

  override def moduleDeps = Seq(core)
}

import mill.define.Sources


object GitSupport extends Module {

  /**
   * The current git revision.
   */
  def gitHead: T[String] = T.input {
    sys.env.get("TRAVIS_COMMIT").getOrElse(
      os.proc('git, "rev-parse", "HEAD").call().out.trim
    ).toString()
  }

  /**
   * Calc a publishable version based on git tags and dirty state.
   *
   * @return A tuple of (the latest tag, the calculated version string)
   */
  def publishVersion: T[(String, String)] = T.input {
    val tag =
      try Option(
        os.proc('git, 'describe, "--exact-match", "--tags", "--always", gitHead()).call().out.trim
      )
      catch {
        case e => None
      }

    val dirtySuffix = os.proc('git, 'diff).call().out.string.trim() match {
      case "" => ""
      case s => "-DIRTY" + Integer.toHexString(s.hashCode)
    }

    tag match {
      case Some(t) => (t, t)
      case None =>
        val latestTaggedVersion = os.proc('git, 'describe, "--abbrev=0", "--always", "--tags").call().out.trim

        val commitsSinceLastTag =
          os.proc('git, "rev-list", gitHead(), "--not", latestTaggedVersion, "--count").call().out.trim.toInt

        (latestTaggedVersion, s"$latestTaggedVersion-$commitsSinceLastTag-${gitHead().take(6)}$dirtySuffix")
    }
  }

}

object integrationTest extends MillIntegrationTestModule {

  def millTestVersion = T {
    val ctx = T.ctx()
    ctx.env.get("TEST_MILL_VERSION").filterNot(_.isEmpty).getOrElse(millVersion)
  }

  def pluginsUnderTest = Seq(core,testsupport)

}
