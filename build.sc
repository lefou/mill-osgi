import java.nio.file.attribute.PosixFilePermission

import mill.define.{Module, Target}
import mill.eval.PathRef
import mill.modules.Util
import mill.scalalib._
import mill.scalalib.publish._

// Run integration tests with mill
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest:0.2.1`, de.tobiasroeser.mill.integrationtest._

// Generate converage reports
import $ivy.`com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION`, mill.contrib.scoverage.ScoverageModule

// The mill version used in the project/sources/dependencies, also default for integration tests
def millVersion = "0.6.2-20-08228b"

val baseDir = build.millSourcePath

object Deps {
  val scalaVersion = "2.13.2"
  val scoverageVersion = "1.3.1"

  val ammonite = ivy"com.lihaoyi:::ammonite:1.3.2"
  val bndlib = ivy"biz.aQute.bnd:biz.aQute.bndlib:4.3.1"
  val logbackClassic = ivy"ch.qos.logback:logback-classic:1.1.3"
  val millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
  val millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  val scalaTest = ivy"org.scalatest::scalatest:3.0.8"
  val scalaLibrary = ivy"org.scala-lang:scala-library:${scalaVersion}"
  val slf4j = ivy"org.slf4j:slf4j-api:1.7.30"
}

trait MillOsgiModule extends ScalaModule with PublishModule {
  def scalaVersion = T { Deps.scalaVersion }
  def ivyDeps = T { Agg(Deps.scalaLibrary) }
  def publishVersion = GitSupport.publishVersion()._2
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

object core extends MillOsgiModule with ScoverageModule {

  def scoverageVersion = T { Deps.scoverageVersion }

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

  override def generatedSources: Target[Seq[PathRef]] = T{
    val dest = T.ctx().dest
    val infoClass =
      s"""// Generated with mill from build.sc
         |package de.tobiasroeser.mill.osgi.internal
         |
         |object BuildInfo {
         |  def millOsgiVerison = "${publishVersion()}"
         |  def millVersion = "${millVersion}"
         |}
         |""".stripMargin
    os.write(dest / "BuildInfo.scala", infoClass)
      super.generatedSources() ++ Seq(PathRef(dest))
  }

  object test extends ScoverageTests {
    override def ivyDeps = T { Agg(
      Deps.scalaTest
    ) }
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

object itest extends MillIntegrationTestModule {
  def millTestVersion = T {
    val ctx = T.ctx()
    ctx.env.get("TEST_MILL_VERSION").filterNot(_.isEmpty).getOrElse(millVersion)
  }
  def pluginsUnderTest = Seq(core, testsupport)
}

/** Convenience targets. */
object P extends Module {

  /** Build JARs. */
  def build() = T.command {
    core.jar()
  }

  /** Run tests. */
  def test() = T.command {
    core.test.test()()
    itest.test()()
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

  /**
   * Update the millw script.
   */
  def millw() = T.command {
    // https://raw.githubusercontent.com/lefou/millw/master/millw
    val target = Util.download("https://raw.githubusercontent.com/lefou/millw/master/millw")
    val millw = baseDir / "millw"
    os.copy.over(target.path, millw)
    os.perms.set(millw, os.perms(millw) + PosixFilePermission.OWNER_EXECUTE)
    target
  }

}
