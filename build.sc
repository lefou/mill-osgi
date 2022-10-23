// mill plugins
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.3.0`
// Run integration tests with mill
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.6.1`
// Generate converage reports
import $ivy.`com.lihaoyi::mill-contrib-scoverage:`

import mill.define.{Command, Task, TaskModule}

import java.nio.file.attribute.PosixFilePermission

import de.tobiasroeser.mill.integrationtest._
import de.tobiasroeser.mill.vcs.version._

import mill.{Agg, PathRef, T}
import mill.contrib.scoverage.ScoverageModule
import mill.define.{Cross, Module, Target}
import mill.modules.Util
import mill.scalalib._
import mill.scalalib.publish._

import os.Path

val baseDir = build.millSourcePath

trait Deps {
  // The mill API version used in the project/sources/dependencies, also default for integration tests
  def millVersion: String
  def millPlatform: String
  def scalaVersion: String
  def millTestVersions: Seq[String]
  val scoverageVersion = "2.0.7"

  val bndlib = ivy"biz.aQute.bnd:biz.aQute.bndlib:6.3.1"
  val logbackClassic = ivy"ch.qos.logback:logback-classic:1.1.3"
  def millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
  def millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  val scalaTest = ivy"org.scalatest::scalatest:3.2.14"
  def scalaLibrary = ivy"org.scala-lang:scala-library:${scalaVersion}"
  val scoveragePlugin = ivy"org.scoverage:::scalac-scoverage-plugin:${scoverageVersion}"
  val scoverageRuntime = ivy"org.scoverage::scalac-scoverage-runtime:${scoverageVersion}"
  val slf4j = ivy"org.slf4j:slf4j-api:1.7.36"
}

object Deps_0_10 extends Deps {
  override val millVersion = "0.10.0" // scala-steward:off
  override def millPlatform = "0.10"
  override val scalaVersion = "2.13.10"
  // keep in sync with .github/workflows/build.yml
  override val millTestVersions = Seq("0.10.7", millVersion)
}
object Deps_0_9 extends Deps {
  override val millVersion = "0.9.3" // scala-steward:off
  override def millPlatform = "0.9"
  override val scalaVersion = "2.13.10"
  // keep in sync with .github/workflows/build.yml
  override val millTestVersions = Seq("0.9.12", millVersion)
}
object Deps_0_7 extends Deps {
  override val millVersion = "0.7.0" // scala-steward:off
  override def millPlatform = "0.7"
  override val scalaVersion = "2.13.10"
  // keep in sync with .github/workflows/build.yml
  override val millTestVersions = Seq("0.8.0", "0.7.4", millVersion)
}
object Deps_0_6 extends Deps {
  override val millVersion = "0.6.0" // scala-steward:off
  override def millPlatform = "0.6"
  override val scalaVersion = "2.12.17"
  // keep in sync with .github/workflows/build.yml
  override val millTestVersions = Seq("0.6.3", millVersion)
}

/** Cross build versions */
val millPlatforms = Seq(Deps_0_10, Deps_0_9, Deps_0_7, Deps_0_6).map(x => x.millPlatform -> x)

trait MillOsgiModule extends ScalaModule with PublishModule {
  def millPlatform: String
  def deps: Deps = millPlatforms.toMap.apply(millPlatform)
  override def scalaVersion = T { deps.scalaVersion }
  override def ivyDeps = Agg(deps.scalaLibrary)
  override def artifactSuffix = s"_mill${deps.millPlatform}_${artifactScalaVersion()}"
  def publishVersion = VcsVersion.vcsState().format()
  override def javacOptions = Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF-8")
  override def scalacOptions = Seq("-target:jvm-1.8", "-encoding", "UTF-8")
  override def pomSettings = T {
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

object core extends Cross[Core](millPlatforms.map(_._1): _*)
class Core(override val millPlatform: String) extends MillOsgiModule with ScoverageModule {
  override def millSourcePath: Path = super.millSourcePath / os.up
  override def artifactName = "de.tobiasroeser.mill.osgi"
  override def ivyDeps = super.ivyDeps() ++ Agg(
    deps.bndlib,
    deps.slf4j
  )
  override def compileIvyDeps = Agg(
    deps.millMain,
    deps.millScalalib
  )

  override def generatedSources: Target[Seq[PathRef]] = T {
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

  override def scoverageVersion = deps.scoverageVersion
  // we need to adapt to changed publishing policy - patch-level
  override def scoveragePluginDep = T {
    deps.scoveragePlugin
  }

  object test extends ScoverageTests with TestModule.ScalaTest {
    override def ivyDeps = Agg(
      deps.scalaTest
    )
  }

}

object testsupport extends Cross[TestSupport](millPlatforms.map(_._1): _*)
class TestSupport(override val millPlatform: String) extends MillOsgiModule {
  override def millSourcePath: Path = super.millSourcePath / os.up
  override def compileIvyDeps = Agg(
    deps.millMain,
    deps.millScalalib
  )
  override def artifactName = "mill-osgi-testsupport"
  override def moduleDeps = Seq(core(millPlatform))
}

val testVersions: Seq[(String, Deps)] = millPlatforms.flatMap { case (_, d) => d.millTestVersions.map(_ -> d) }

object itest extends Cross[ItestCross](testVersions.map(_._1): _*) with TaskModule {
  override def defaultCommandName(): String = "test"
  def testCached: T[Seq[TestCase]] = itest(testVersions.map(_._1).head).testCached
  def test(args: String*): Command[Seq[TestCase]] = itest(testVersions.map(_._1).head).test(args: _*)
}

class ItestCross(millVersion: String) extends MillIntegrationTestModule {
  override def millSourcePath: Path = super.millSourcePath / os.up
  def deps = testVersions.toMap.apply(millVersion)
  override def millTestVersion = T { millVersion }
  override def pluginsUnderTest = Seq(core(deps.millPlatform), testsupport(deps.millPlatform))
  override def prefetchIvyDeps = Agg(
    ivy"com.typesafe.akka:akka-http-core_2.12:10.1.11"
  )

  override def pluginUnderTestDetails: Task.Sequence[(PathRef, (PathRef, (PathRef, (PathRef, (PathRef, Artifact)))))] =
    T.traverse(pluginsUnderTest) { p =>
      val jar = p match {
        case p: ScoverageModule => p.scoverage.jar
        case p => p.jar
      }
      jar zip (p.sourceJar zip (p.docJar zip (p.pom zip (p.ivy zip p.artifactMetadata))))
    }
  override def perTestResources = T.sources { Seq(generatedSharedSrc()) }
  def generatedSharedSrc = T {
    val scov = deps.scoverageRuntime.dep
    os.write(
      T.dest / "shared.sc",
      s"""import $$ivy.`${scov.module.organization.value}::${scov.module.name.value}:${scov.version}`
         |""".stripMargin
    )
    PathRef(T.dest)
  }
}

/** Convenience targets. */
object P extends Module {

  /**
   * Update the millw script.
   */
  def millw() = T.command {
    // https://raw.githubusercontent.com/lefou/millw/master/millw
    for {
      file <- Seq("millw", "millw.bat")
    } yield {
      val target = Util.download(s"https://raw.githubusercontent.com/lefou/millw/master/${file}")
      val millw = baseDir / file
      os.copy.over(target.path, millw)
      os.perms.set(millw, os.perms(millw) + PosixFilePermission.OWNER_EXECUTE)
      target
    }
  }

}
