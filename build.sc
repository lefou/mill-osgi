// mill plugins
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.1`
// Run integration tests with mill
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.7.3`
// Generate converage reports
import $ivy.`com.lihaoyi::mill-contrib-scoverage:`

import mill.define.{Command, Task, TaskModule}

import java.nio.file.attribute.PosixFilePermission

import de.tobiasroeser.mill.integrationtest._
import de.tobiasroeser.mill.vcs.version._

import mill._
import mill.contrib.scoverage.ScoverageModule
import mill.scalalib._
import mill.scalalib.api.JvmWorkerUtil
import mill.scalalib.publish._

import os.Path

private def baseDir = build.moduleDir

trait Deps {
  // The mill API version used in the project/sources/dependencies, also default for integration tests
  def millVersion: String
  def millPlatform: String
  def scalaVersion: String = "2.13.16"
  def millTestVersions: Seq[String]
  val scoverageVersion = "2.4.1"

  val bndlib = mvn"biz.aQute.bnd:biz.aQute.bndlib:6.4.1"
  val logbackClassic = mvn"ch.qos.logback:logback-classic:1.1.3"
  def millMain = mvn"com.lihaoyi::mill-main:${millVersion}"
  def millScalalib = mvn"com.lihaoyi::mill-scalalib:${millVersion}"
  val scalaTest = mvn"org.scalatest::scalatest:3.2.19"
  def scalaLibrary = mvn"org.scala-lang:scala-library:${scalaVersion}"
  val scoveragePlugin = mvn"org.scoverage:::scalac-scoverage-plugin:${scoverageVersion}"
  val scoverageRuntime = mvn"org.scoverage::scalac-scoverage-runtime:${scoverageVersion}"
  val slf4j = mvn"org.slf4j:slf4j-api:1.7.36"
}

object Deps_1 extends Deps {
  override val millVersion = "1.0.0" // scala-steward:off
  override def millPlatform = "1"
  // keep in sync with .github/workflows/build.yml
  override val millTestVersions = Seq("1.1.0-RC1", millVersion)
  override def millScalalib: Dep = mvn"com.lihaoyi::mill-libs:${millVersion}"
  override def scalaVersion = "3.7.4"
}
object Deps_0_11 extends Deps {
  override val millVersion = "0.11.0" // scala-steward:off
  override def millPlatform = "0.11"
  // keep in sync with .github/workflows/build.yml
  override val millTestVersions = Seq("0.12.16", "0.12.0", "0.11.12", millVersion)
}
object Deps_0_10 extends Deps {
  override val millVersion = "0.10.0" // scala-steward:off
  override def millPlatform = "0.10"
  // keep in sync with .github/workflows/build.yml
  override val millTestVersions = Seq("0.10.12", millVersion)
}

/** Cross build versions */
val millPlatforms = Seq(Deps_1, Deps_0_11, Deps_0_10).map(x => x.millPlatform -> x)

trait MyScoverageModule extends ScoverageModule {
  override lazy val scoverage: ScoverageData = new MyScoverageData {}
  trait MyScoverageData extends ScoverageData {
    override def skipIdea: Boolean = true
  }
}

trait MillOsgiModule extends ScalaModule with PublishModule with Cross.Module[String] {
  def millPlatform: String = crossValue
  def deps: Deps = millPlatforms.toMap.apply(millPlatform)
  override def scalaVersion = Task { deps.scalaVersion }
//  override def ivyDeps = Agg(deps.scalaLibrary)
//  override def artifactSuffix = s"_mill${deps.millPlatform}_${artifactScalaVersion()}"
  override def platformSuffix = s"_mill${millPlatform}"
  def publishVersion = VcsVersion.vcsState().format()
  override def javacOptions = Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF-8")
  override def scalacOptions = Task {
    val jvmSpecific =
      if (scala.util.Properties.isJavaAtLeast(11))
        Seq("-release", "8")
      else
        Seq("-target:jvm-1.8")

    val svSpecific =
      if (scalaVersion().startsWith("3")) Seq("-Ydebug-unpickling")
      else Seq("-Xsource:3")

    jvmSpecific ++ svSpecific ++ Seq("-encoding", "UTF-8", "-deprecation")
  }
  override def pomSettings = Task {
    PomSettings(
      description = "Mill module adding OSGi bundle support",
      organization = "de.tototec",
      url = "https://github.com/lefou/mill-osgi",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("lefou", "mill-osgi"),
      developers = Seq(Developer("lefou", "Tobias Roeser", "https.//github.com/lefou"))
    )
  }
  def sources0 = Task.Sources {
    (
      JvmWorkerUtil.matchingVersions(deps.millPlatform) ++
        JvmWorkerUtil.versionRanges(deps.millPlatform, millPlatforms.map(_._1))
    )
      .map(p => PathRef(moduleDir / s"src-${p}"))
  }
  override def sources = Task {
    super.sources() ++ sources0()

  }
}

object core extends Cross[Core](millPlatforms.map(_._1))
trait Core extends MillOsgiModule with MyScoverageModule {
  override def artifactName = "de.tobiasroeser.mill.osgi"
  override def ivyDeps = super.ivyDeps() ++ Agg(
    deps.bndlib,
    deps.slf4j
  )
  override def compileIvyDeps =
    if (deps.millPlatform == "1") Agg(
      deps.millScalalib
    )
    else Agg(
      deps.millMain,
      deps.millScalalib
    )

  override def generatedSources: T[Seq[PathRef]] = T {
    val dest = Task.dest
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

  override def skipIdea: Boolean = millPlatforms.head._1 != millPlatform

  object test extends ScoverageTests with TestModule.ScalaTest {
    override def ivyDeps = Agg(
      deps.scalaTest
    )
  }

}

object testsupport extends Cross[TestSupport](millPlatforms.map(_._1))
trait TestSupport extends MillOsgiModule {
  override def compileIvyDeps = if (deps.millPlatform == "1") Agg(
    deps.millScalalib
  )
  else Agg(
    deps.millMain,
    deps.millScalalib
  )
  override def artifactName = "mill-osgi-testsupport"
  override def moduleDeps = Seq(core(millPlatform))
}

val testVersions: Seq[(String, Deps)] = millPlatforms.flatMap { case (_, d) => d.millTestVersions.map(_ -> d) }

object itest extends Cross[ItestCross](testVersions.map(_._1))
trait ItestCross extends MillIntegrationTestModule with Cross.Module[String] {
  def millVersion = crossValue
  def deps = testVersions.toMap.apply(millVersion)
  override def millTestVersion = T { millVersion }
  override def pluginsUnderTest = Seq(core(deps.millPlatform), testsupport(deps.millPlatform))
  override def prefetchIvyDeps = Agg(
    mvn"com.typesafe.akka:akka-http-core_2.12:10.1.11"
  )
  override def sources = Task.Sources {
    super.sources() ++
      JvmWorkerUtil.versionRanges(deps.millPlatform, millPlatforms.map(_._1))
        .map(p => PathRef(moduleDir / s"src-${p}"))
  }

  override def pluginUnderTestDetails: Task[Seq[(PathRef, (PathRef, (PathRef, (PathRef, (PathRef, Artifact)))))]] =
    Task.traverse(pluginsUnderTest) { p =>
      val jar = p match {
        case p: ScoverageModule => p.scoverage.jar
        case p => p.jar
      }
      jar zip (p.sourceJar zip (p.docJar zip (p.pom zip (p.ivy zip p.artifactMetadata))))
    }
  override def perTestResources = Task { Seq(generatedSharedSrc()) }
  def generatedSharedSrc = Task {
    val scov = deps.scoverageRuntime
    os.write(
      Task.dest / "shared.sc",
      s"""// Load the plugin under test
         |import $$file.plugins
         |// Load scoverage runtime to get coverage results
         |import $$ivy.`${scov.organization}::${scov.name}:${scov.version}`
         |""".stripMargin
    )
    PathRef(Task.dest)
  }
}
