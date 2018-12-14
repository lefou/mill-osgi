package de.tobiasroeser.mill.osgi.testsupport

import java.io.IOException
import java.util.jar.Manifest
import java.util.zip.ZipFile

import os.Path

trait TestSupport {

  def withManifest(file: Path)(f: Manifest => Unit) = {
    val zipFile = new ZipFile(file.toIO)
    val manifestIn = zipFile.getInputStream(zipFile.getEntry("META-INF/MANIFEST.MF"))
    try {
      val manifest = new Manifest(manifestIn)
      f(manifest)
    } catch {
      case e: IOException => throw new AssertionError(s"Could not read manifest of file ${file}", e)
    } finally {
      manifestIn.close()
    }
  }

  def checkSlices(manifest: Manifest, header: String, expectedSlices: Seq[String]) = {
    val value = manifest.getMainAttributes().getValue(header)
    if (!expectedSlices.forall(s => value.containsSlice(s))) {
      sys.error(s"""Expected '${header}' header with slices ${expectedSlices.mkString("'", "' and '", "'")}! But was '${value}'""")
    }
  }

  def checkExact(manifest: Manifest, header: String, expectedValue: String) = {
    val value = manifest.getMainAttributes().getValue(header)
    if (expectedValue != value) {
      sys.error(s"""Expected '${header}' header with value '${expectedValue}'! But was '${value}'""")
    }
  }

}

object TestSupport extends TestSupport