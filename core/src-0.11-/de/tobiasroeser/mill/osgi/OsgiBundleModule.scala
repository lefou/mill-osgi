package de.tobiasroeser.mill.osgi

import java.io.FileOutputStream

import scala.collection.JavaConverters._
import scala.util.Try

import aQute.bnd.osgi.{Builder, Constants, Jar}
import de.tobiasroeser.mill.osgi.internal.{BuildInfo, copy => icopy, unpack => iunpack}
import de.tobiasroeser.mill.osgi.{OsgiBundleModulePlatform, OsgiHeaders}
import mill._
import mill.api.PathRef
import mill.define.{Sources, Task}
import mill.modules.Jvm
import mill.scalalib.{JavaModule, PublishModule}
import os.Path

trait OsgiBundleModule extends OsgiBundleModulePlatform {

  import OsgiBundleModule._

  /**
   * The build mode.
   * Defaults to [[BuildMode.ReplaceJarTarget]], in which the [[jar]] target delegates to [[osgiBundle]].
   * You can also select [[BuildMode.CalculateManifest]], in which only the manifest entries will be generated with
   * bnd tool, but the JAR is creates by the regular (derived) [[jar]] target.
   *
   * @return
   */
  def osgiBuildMode: BuildMode = BuildMode.ReplaceJarTarget

  /**
   * The transitive version of `localClasspath`.
   * This overrides [[JavaModule.transitiveLocalClasspath]], but uses the final
   * JAR files instead of just the classes directories where possible.
   * This is needed, as only the final JARs contain proper OSGi manifest entries.
   */
  override def transitiveLocalClasspath: T[Agg[PathRef]] = osgiBuildMode match {
    case BuildMode.ReplaceJarTarget => T {
        T.traverse(recursiveModuleDeps) { m =>
          T.task {
            Agg(m.jar())
          }
        }().flatten
      }
    case BuildMode.CalculateManifest => super.transitiveLocalClasspath
  }

  override def localClasspath: T[Seq[PathRef]] = osgiBuildMode match {
    case BuildMode.ReplaceJarTarget => super.localClasspath
    case BuildMode.CalculateManifest => T {
        Seq(osgiManifest()) ++ super.localClasspath()
      }
  }

  /**
   * Build the final bundle.
   * Overrides the [[JavaModule#jar]].
   * If [[osgiBuildMode]] is [[BuildMode.ReplaceJarTarget]] then this links to [[osgiBundle]] instead.
   */
  override def jar: T[PathRef] = osgiBuildMode match {
    case BuildMode.ReplaceJarTarget => T { osgiBundle() }
    case BuildMode.CalculateManifest => super.jar
  }

  /**
   * The bundle symbolic name used to initialize [[osgiHeaders]].
   * If the module is a [[PublishModule]], it calculated the bundle symbolic name
   * from [[PublishModule.artifactMetadata]]
   */
  def bundleSymbolicName: T[String] = this match {
    case pm: PublishModule => T {
        calcBundleSymbolicName(pm.pomSettings().organization, artifactId())
      }
    case _ =>
      artifactId
  }

  /**
   * The bundle version used to initialize [[osgiHeaders]].
   * If the module is a [[PublishModule]], it uses the [[PublishModule.publishVersion]]
   */
  def bundleVersion: T[String] = this match {
    case pm: PublishModule => T {
        pm.publishVersion()
      }
    case _ => "0.0.0"
  }

  /**
   * Instruct bnd to create a reproducible bundle file.
   */
  def reproducibleBundle: T[Boolean] = T {
    true
  }

  /**
   * Embed these JAR files and also add them to the bundle classpath.
   */
  def embeddedJars: T[Seq[PathRef]] = T {
    Seq[PathRef]()
  }

  /**
   * Embed the content of the given JAR files into the bundle.
   */
  def explodedJars: T[Seq[PathRef]] = T {
    Seq[PathRef]()
  }

  def osgiHeaders: T[OsgiHeaders] = {
    def withDefaults: OsgiHeaders => OsgiHeaders = h =>
      h.copy(
        `Import-Package` = Seq("*")
      )

    this match {
      case pm: PublishModule => T {
          val pom = pm.pomSettings()
          withDefaults(OsgiHeaders(
            `Bundle-SymbolicName` = bundleSymbolicName(),
            `Bundle-Version` = Option(bundleVersion()),
            `Bundle-License` = pom.licenses.map(l => l.url.toString),
            `Bundle-Vendor` = Option(pom.organization),
            `Bundle-Description` = Option(pom.description)
          ))
        }
      case _ => T {
          withDefaults(OsgiHeaders(
            `Bundle-SymbolicName` = bundleSymbolicName(),
            `Bundle-Version` = Option(bundleVersion())
          ))
        }
    }
  }

  /**
   * Iff `true` include sources in the final bundle under `OSGI-OPT/src`.
   */
  def includeSources: T[Boolean] = T {
    false
  }

  /**
   * Resources to include into the final bundle.
   * Defaults to include [[JavaModule.resources()]].
   */
  def includeResource: T[Seq[String]] = T {
    // default: add contents of resources to final bundle
    resources()
      // only take non-empty directories to avoid bnd warning/error
      .filter(p => p.path.toIO.exists()) //  && Option(p.path.toIO.list()).map(!_.isEmpty).getOrElse(false))
      // add to the root of the JAR
      .map(dir => dir.path.toIO.getAbsolutePath())
  }

  /**
   * Exports the given packages but does not try to include them from the class path.
   * The packages should be loaded with alternative means.
   */
  def exportContents: T[Seq[String]] = T {
    Seq[String]()
  }

  // TODO: do we want support default Mill Jar headers?

  /**
   * Additional headers to add to the bundle manifest.
   * Warning: All headers added here will override their previous value,
   * hence, be careful to not add standard OSGi headers here, but via [[osgiHeaders]].
   */
  def additionalHeaders: T[Map[String, String]] = T {
    Map[String, String]()
  }

  /**
   * Build the OSGi Bundle by using the bnd tool.
   */
  def osgiBundle: T[PathRef] = T {
    val jar = osgiBundleTask()
    val outputPath = T.ctx().dest / s"${bundleSymbolicName()}-${bundleVersion()}.jar"
    jar.write(outputPath.toIO)
    PathRef(outputPath)
  }

  /**
   * Generated the OSGi Bundle manifest by using the bnd tool.
   * @return The path containing `META-INF/MANIFEST.MF`, can be used as classpath too.
   */
  def osgiManifest: T[PathRef] = T {
    val jar = osgiBundleTask()
    val manifest = jar.getManifest()

    val outputFile = T.dest / "META-INF" / "MANIFEST.MF"
    os.makeDir.all(outputFile / os.up)
    val stream = new FileOutputStream(outputFile.toIO)
    try {
      manifest.write(stream)
    } finally {
      stream.close()
    }
    PathRef(T.dest)
  }

  /**
   * Creates a manifest representation which can be modified or replaced.
   * The default implementation generates OSGi manifest entries from compiled classes with bnd tool and
   * additionally adds a `Main-Class`, if defined in [[mainClass]].
   */
  override def manifest: T[Jvm.JarManifest] = T {
    // Mill defined
    val pre = super.manifest()
    // bnd calculated
    val manifest = osgiBundleTask().getManifest()
    def entryAsStringPair(entry: java.util.Map.Entry[Object, Object]): (String, String) = {
      entry.getKey().toString() -> Option(entry.getValue()).map(_.toString()).getOrElse("")
    }
    Jvm.JarManifest(
      main = pre.main ++ manifest.getMainAttributes.entrySet().asScala.map(entryAsStringPair).toMap,
      groups = pre.groups ++ manifest.getEntries().asScala.map(e =>
        e._1 -> e._2.entrySet().asScala.map(entryAsStringPair).toMap
      )
    )
  }

  override def resources: Sources = osgiBuildMode match {
    case BuildMode.ReplaceJarTarget => super.resources
    case BuildMode.CalculateManifest => T.sources {
        super.resources() ++ {
          val dest = T.dest
          if (includeSources()) {
            sources().map(_.path).filter(os.exists).map { path =>
              icopy(path, dest / "OSGI-OPT" / "src", createFolders = true, mergeFolders = true)
            }
          }
          embeddedJars().foreach { jar =>
            icopy(jar.path, dest / jar.path.last)
          }
          explodedJars().foreach { jar =>
            iunpack.zip(jar.path, dest)
          }
          Seq(PathRef(dest))
        }
      }
  }

  /**
   * Build the OSGi Bundle.
   */
  def osgiBundleTask: Task[Jar] = osgiBuildMode match {
    case BuildMode.ReplaceJarTarget => osgiBundleTask(localClasspath, calcPrivatePackage = true)
    case BuildMode.CalculateManifest => osgiBundleTask(super.localClasspath, calcPrivatePackage = false)
  }

  def osgiBundleTask(localClasspath: Task[Seq[PathRef]], calcPrivatePackage: Boolean): Task[Jar] = T.task {
    val log = T.ctx().log

    val currentRunningMillVersion = T.ctx().env.get("MILL_VERSION")
    if (!checkMillVersion(currentRunningMillVersion)) {
      log.error(
        s"Your used mill version is most probably too old. In case of errors use (at least) mill version ${BuildInfo.millVersion}."
      )
    }

    val builder = new Builder()
    if (reproducibleBundle()) {
      builder.setProperty(Constants.REPRODUCIBLE, "true")
    }

    // TODO: check if all dependencies have proper Manifests (are bundled as jars instead of class folders)
    val bndClasspath = (compileClasspath() ++ localClasspath()).toList.map(p => p.path.toIO).filter(_.exists()).asJava
    builder.setClasspath(bndClasspath)

    if (calcPrivatePackage) {
      // We need to make sure we package all classfiles, event if they are not exported
      // Unfortunately, this doesn't work very well for to top-level (no-name) package
      // and also is known to include to much resource files (from dependencies) into the top-level package.
      // That's why the BuildMode.CalcuateManifest is expected to produce better jars

      // TODO: scan classes directory and auto-add all dirs as private package
      val classesPath = compile().classes.path
      val ps: Seq[Path] = if (!os.exists(classesPath)) Seq() else os.walk(classesPath)
      val packages = ps
        .filter(_.toIO.isFile())
        .flatMap { pFull =>
          val p = pFull.relativeTo(classesPath)
          if (p.segments.size > 1) {
            Seq((p / os.up).segments.mkString("."))
          } else {
            // Find way to include top-level package
            Seq(".")
          }
        }
        .distinct

      if (!packages.isEmpty) {
        builder.setProperty(Constants.PRIVATE_PACKAGE, packages.mkString(","))
      }
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

    if (includeSources()) {
      mergeSeqProps(
        builder,
        Constants.INCLUDERESOURCE,
        allSources().filter(_.path.toIO.exists()).map(s => "OSGI-OPT/src=" + s.path.toIO.getAbsolutePath()).toList
      )
    }

    // TODO: Some validation that should at least war
    // * Fragment and activator at the same time
    // * Activator in exported package
    // * Packages not part of export or private

    // TODO: handle special props with defaults

    // handle included resources
    mergeSeqProps(builder, Constants.INCLUDERESOURCE, includeResource())

    // handle embedded Jars
    embeddedJars().foreach { jar =>
      mergeSeqProps(builder, Constants.INCLUDERESOURCE, Seq(jar.path.toIO.getAbsolutePath()))
    }

    // handle exploded Jars
    explodedJars().foreach { jar =>
      mergeSeqProps(builder, Constants.INCLUDERESOURCE, Seq("@" + jar.path.toIO.getAbsolutePath()))
    }

    mergeSeqProps(builder, Constants.EXPORT_CONTENTS, exportContents())

    builder.addProperties(osgiHeaders().toProperties)

    builder.addProperties(additionalHeaders().asJava)

    //    println("Props:" + builder.getProperties().asScala.toList.map {
    //      case (k, v) =>
    //        if (v.indexOf(",") > 0) {
    //          s"${k}:\n    ${v.split("[,]").mkString(",\n    ")}"
    //        } else {
    //          s"${k}: ${v}"
    //        }
    //    }.mkString("\n  "))

    val jar = builder.build()

    builder.getErrors().asScala.foreach(msg => log.error("bnd error: " + msg))
    builder.getWarnings().asScala.foreach(msg => log.error("bnd warning: " + msg))

    jar
  }

}

object OsgiBundleModule extends OsgiBundleModuleSupport {

  sealed trait BuildMode
  object BuildMode {
    object ReplaceJarTarget extends BuildMode
    object CalculateManifest extends BuildMode
  }

}
