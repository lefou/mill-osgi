// mill plugins
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version:0.0.1`
// Run integration tests with mill
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest:0.3.3`
// Generate converage reports
import $ivy.`com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION`

import java.nio.file.attribute.PosixFilePermission

import de.tobiasroeser.mill.integrationtest._
import de.tobiasroeser.mill.vcs.version._

import mill.contrib.scoverage.ScoverageModule
import mill.define.{Cross, Module, Target}
import mill.eval.PathRef
import mill.modules.Util
import mill.scalalib._
import mill.scalalib.publish._

import os.Path

val baseDir = build.millSourcePath

trait Deps {
  // The mill API version used in the project/sources/dependencies, also default for integration tests
  def millVersion: String
  def millTestVersions: Seq[String]
  def scalaVersion: String
  val scoverageVersion = "1.4.1"

  val bndlib = ivy"biz.aQute.bnd:biz.aQute.bndlib:5.1.2"
  val logbackClassic = ivy"ch.qos.logback:logback-classic:1.1.3"
  def millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
  def millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  val scalaTest = ivy"org.scalatest::scalatest:3.2.2"
  def scalaLibrary = ivy"org.scala-lang:scala-library:${scalaVersion}"
  val slf4j = ivy"org.slf4j:slf4j-api:1.7.30"
}
object Deps_0_6 extends Deps {
  override val millVersion = "0.6.0"
  override val millTestVersions = Seq(
    "0.6.0",
    "0.6.1",
    "0.6.2",
    "0.6.3"
  )
  override val scalaVersion = "2.12.11"
}

object Deps_0_7 extends Deps {
  override val millVersion = "0.7.0"
  override val millTestVersions = Seq(
    "0.7.0",
    "0.7.1",
    "0.7.2",
    "0.7.3"
  )
  override val scalaVersion = "2.13.2"
}

trait MillOsgiModule extends ScalaModule with PublishModule {
  def deps: Deps
  def scalaVersion = T { deps.scalaVersion }
  def ivyDeps = T { Agg(deps.scalaLibrary) }
  def publishVersion = VcsVersion.vcsState().format()
  override def javacOptions = Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF-8")
  override def scalacOptions = Seq("-target:jvm-1.8", "-encoding", "UTF-8")
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

val millVersions: Map[String, Deps] = Map(
  "0.6" -> Deps_0_6,
  "0.7" -> Deps_0_7
)

object core extends Cross[Core](millVersions.keySet.toSeq: _*)
class Core(millApiVersion: String) extends MillOsgiModule with ScoverageModule {
  override def millSourcePath: Path = super.millSourcePath / os.up
  override def deps = millVersions(millApiVersion)

  def scoverageVersion = T { deps.scoverageVersion }

  override def artifactName = T { "de.tobiasroeser.mill.osgi" }

  def ivyDeps = T {
    super.ivyDeps() ++ Agg(
      deps.bndlib,
      deps.slf4j
    )
  }

  def compileIvyDeps = Agg(
    deps.millMain,
    deps.millScalalib
  )

  override def generatedSources: Target[Seq[PathRef]] = T{
    val dest = T.dest
    val infoClass =
      s"""// Generated with mill from build.sc
         |package de.tobiasroeser.mill.osgi.internal
         |
         |object BuildInfo {
         |  def millOsgiVerison = "${publishVersion()}"
         |  def millVersion = "${deps.millVersion}"
         |}
         |""".stripMargin
    os.write(dest / "BuildInfo.scala", infoClass)
      super.generatedSources() ++ Seq(PathRef(dest))
  }

  object test extends ScoverageTests {
    override def ivyDeps = T { Agg(
      deps.scalaTest
    ) }
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }

}

object testsupport extends Cross[TestSupport](millVersions.keySet.toSeq: _*)
class TestSupport(millApiVersion: String) extends MillOsgiModule {
  override def millSourcePath: Path = super.millSourcePath / os.up
  override def deps = millVersions(millApiVersion)
  def compileIvyDeps = Agg(
    deps.millMain,
    deps.millScalalib
  )
  override def artifactName = "mill-osgi-testsupport"
  override def moduleDeps = Seq(core(millApiVersion))
}

val testVersions = millVersions.toSeq.flatMap { case (l,d) => d.millTestVersions.map(l -> _) }

object itest extends Cross[Itest](testVersions.toSeq: _*)
class Itest(millApiVersion: String, millVersion: String) extends MillIntegrationTestModule {
  override def millSourcePath: Path = super.millSourcePath / os.up / os.up
  def Deps = millVersions(millApiVersion)
  override def millTestVersion = T { millVersion }
  override def pluginsUnderTest = Seq(core(millApiVersion), testsupport(millApiVersion))
}

/** Convenience targets. */
object P extends Module {

  /** Build JARs. */
  def build() = T.command {
    Target.traverse(millVersions.keySet.toSeq)(core(_).jar)()
    ()
  }

  /** Run tests. */
  def test() = T.command {
    Target.traverse(millVersions.keySet.toSeq)(core(_).test.test())()
    ()
  }

  def install() = T.command {
    T.log.info("Installing")
    test()()
    Target.traverse(millVersions.keySet.toSeq)(core(_).publishLocal())()
    ()
  }

  def checkRelease: T[Boolean] = T.input {
    if (VcsVersion.vcsState().dirtyHash.isDefined) {
      T.log.error("Project (git) state is dirty. Release not recommended!")
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
      Target.traverse(millVersions.keySet.toSeq)(core(_).publish(sonatypeCreds = sonatypeCreds, release = release))()
    }
    ()
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
