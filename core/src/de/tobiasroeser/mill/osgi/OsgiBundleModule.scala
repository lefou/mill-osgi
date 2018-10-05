package de.tobiasroeser.mill.osgi

import java.util.Properties

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

import aQute.bnd.osgi.Builder
import aQute.bnd.osgi.Constants
import ammonite.ops.mkdir
import ammonite.ops.rm
import mill._
import mill.scalalib.JavaModule

trait OsgiBundleModule extends JavaModule {

  def bundleVersion = T {
    // TODO: inspect build for a version, e.g. in PublishModule
    "0.0.0"
  }

  def bundleSymbolicName = T {
    millModuleSegments.parts.mkString(".")
  }

  /**
   * Should the built bundle be reproducible?
   */
  def reproducibleBundle = T {
    true
  }

  def embeddedJars = T {
    Seq[PathRef]()
  }

  def explodedJars = T {
    Seq[PathRef]()
  }

  def osgiHeaders = T {
    OsgiHeaders(
      `Bundle-SymbolicName` = bundleSymbolicName(),
      `Bundle-Version` = Option(bundleVersion()),
      `Import-Package` = Seq("*")
    )
  }

  // TODO: do we want support default Mill Jar headers?

  def additionalHeaders = T {
    Map[String, String]()
  }

  def osgiBundle = T {
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

    // TODO: Some validation that should at least war
    // * Fragment and activator at the same time
    // * Activator in exported package
    // * Packages not part of export or private

    // TODO: handle special props with defaults

    // handle resources
    resources().foreach { dir =>
      builder.setProperty(Constants.INCLUDERESOURCE, dir.path.toIO.getAbsolutePath())
    }

    // handle embedded Jars
    embeddedJars().foreach { jar =>
      builder.setProperty(Constants.INCLUDERESOURCE, jar.path.toIO.getAbsolutePath())
    }

    // handle exploded Jars
    explodedJars().foreach { jar =>
      builder.setProperty(Constants.INCLUDERESOURCE, "@" + jar.path.toIO.getAbsolutePath())
    }

    builder.addProperties(osgiHeaders().toProperties)

    builder.addProperties(additionalHeaders().asJava)

    log.info("bnd info: About to build bundle")
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




