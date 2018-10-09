package de.tobiasroeser.mill.osgi

import java.util.Properties

import scala.collection.JavaConverters._

import aQute.bnd.osgi.Builder
import aQute.bnd.osgi.Constants
import ammonite.ops.LsSeq
import ammonite.ops.mkdir
import ammonite.ops.rm
import mill._
import mill.define.Target
import mill.define.Target.apply
import mill.scalalib.JavaModule
import mill.scalalib.PublishModule

trait OsgiBundleModule extends JavaModule {

  import OsgiBundleModule._

  def bundleSymbolicName: T[String] = this match {
    case _: PublishModule => T {
      val artifact = this.asInstanceOf[PublishModule].artifactMetadata()
      calcBundleSymbolicName(artifact.group, artifact.id)
    }
    case _ =>
      millModuleSegments.parts.mkString(".")
  }

  def bundleVersion: T[String] = this match {
    case _: PublishModule => T { this.asInstanceOf[PublishModule].publishVersion() }
    case _ => "0.0.0"
  }

  /**
   * Create a reproducible bundle files.
   */
  def reproducibleBundle: T[Boolean] = T {
    true
  }

  def embeddedJars: T[Seq[PathRef]] = T {
    Seq[PathRef]()
  }

  def explodedJars: T[Seq[PathRef]] = T {
    Seq[PathRef]()
  }

  def osgiHeaders: T[OsgiHeaders] = T {
    OsgiHeaders(
      `Bundle-SymbolicName` = bundleSymbolicName(),
      `Bundle-Version` = Option(bundleVersion()),
      `Import-Package` = Seq("*")
    )
  }

  /**
   * Include sources in the final bundle (under `OSGI-OPT/src`)
   */
  def includeSources: T[Boolean] = T {
    false
  }

  // TODO: do we want support default Mill Jar headers?

  def additionalHeaders: T[Map[String, String]] = T {
    Map[String, String]()
  }

  def osgiBundle: T[PathRef] = T {
    val log = T.ctx().log
    log.info("bnd info: Packaging bundle")

    val builder = new Builder()
    if (reproducibleBundle()) {
      log.info("bnd info: Enabling reproducible mode")
      builder.setProperty(Constants.REPRODUCIBLE, "true")
    }

    // TODO: check if all dependencies have proper Manifests (are bundled as jars instead of class folders)
    val bndClasspath = (compileClasspath() ++ localClasspath()).toList.map(p => p.path.toIO).asJava
    builder.setClasspath(bndClasspath)

    // TODO: scan classes directory and auto-add all dirs as private package
    val classesPath = compile().classes.path
    val ps: LsSeq = ammonite.ops.ls.rec.!(classesPath)
    val packages = ps.filter(_.isFile).flatMap { pFull =>
      val p = pFull.relativeTo(classesPath)
      if (p.segments.size > 1) {
        println("level " + p.segments.size + " file " + p)
        Seq((p / ammonite.ops.up).segments.mkString("."))
      } else {
        // Find way to include top-level package
        Seq(".")
      }
    }.distinct

    if (!packages.isEmpty) {
      builder.setProperty(Constants.PRIVATE_PACKAGE, packages.mkString(","))
    }
    //    // Special case, files in top level package
    //    // Those can't be exported, but we include them
    //    val rootPackageFiles: LsSeq = ammonite.ops.ls ! (classesPath)
    //    if (!rootPackageFiles.filter(_.isFile).isEmpty) {
    //      println("Found files in top level package")
    //      // mergeSeqProps(builder, Constants.INCLUDERESOURCE, Seq(classesPath.toIO.getAbsolutePath() + ";recursive:=false"))
    //      mergeSeqProps(builder, Constants.PRIVATE_PACKAGE, Seq(".;-split-package:=last"))
    //    }

    allSources().foreach { dir =>
      builder.setProperty(Constants.SOURCEPATH, dir.path.toIO.getAbsolutePath())
    }

    //    builder.setProperty(Constants.SOURCES, includeSources().toString())
    if (includeSources()) {
      mergeSeqProps(builder, Constants.INCLUDERESOURCE,
        allSources().map(s => "OSGI-OPT/src=" + s.path.toIO.getAbsolutePath()).toList)
    }

    // TODO: Some validation that should at least war
    // * Fragment and activator at the same time
    // * Activator in exported package
    // * Packages not part of export or private

    // TODO: handle special props with defaults

    // Add contents of resources to final bundle
    resources().filter(_.path.toIO.exists()).foreach { dir =>
      mergeSeqProps(builder, Constants.INCLUDERESOURCE, Seq(dir.path.toIO.getAbsolutePath()))
    }

    // handle embedded Jars
    embeddedJars().foreach { jar =>
      mergeSeqProps(builder, Constants.INCLUDERESOURCE, Seq(jar.path.toIO.getAbsolutePath()))
    }

    // handle exploded Jars
    explodedJars().foreach { jar =>
      mergeSeqProps(builder, Constants.INCLUDERESOURCE, Seq("@" + jar.path.toIO.getAbsolutePath()))
    }

    builder.addProperties(osgiHeaders().toProperties)

    builder.addProperties(additionalHeaders().asJava)

    log.info("bnd info: About to build bundle")
    println("Props:" + builder.getProperties().asScala.toList.map {
      case (k, v) =>
        if (v.indexOf(",") > 0) {
          s"${k}:\n    ${v.split("[,]").mkString(",\n    ")}"
        } else {
          s"${k}: ${v}"
        }
    }.mkString("\n  "))
    val jar = builder.build()

    builder.getErrors().asScala.foreach(msg => log.error("bnd error: " + msg))
    builder.getWarnings().asScala.foreach(msg => log.error("bnd warning: " + msg))

    val outputPath = T.ctx().dest / "out.jar"
    mkdir(outputPath / ammonite.ops.up)
    rm(outputPath)

    jar.write(outputPath.toIO)

    PathRef(outputPath)
  }

}

object OsgiBundleModule {

  def calcBundleSymbolicName(group: String, artifact: String): String = {
    val groupParts = group.split("[.]")
    val nameParts = artifact.split("[.]").flatMap(_.split("[-]"))

    val parts = (groupParts.lastOption, nameParts.headOption) match {
      case (Some(last), Some(head)) if last == head => groupParts ++ nameParts.tail
      case (Some(last), Some(head)) if head.startsWith(last) => groupParts.take(groupParts.size - 1) ++ nameParts
      case _ => groupParts ++ nameParts
    }

    parts.mkString(".")
  }

  def mergeSeqProps(builder: Builder, key: String, value: Seq[String]): Unit = {
    val existing = builder.getProperty(key) match {
      case null => Seq()
      case p => Seq(p)
    }
    builder.setProperty(key, (existing ++ value).mkString(","))
  }

}
