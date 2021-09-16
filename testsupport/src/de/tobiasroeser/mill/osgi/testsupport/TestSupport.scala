package de.tobiasroeser.mill.osgi.testsupport

import java.io.IOException
import java.util.jar.{JarEntry, JarInputStream, Manifest}
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
    assert(value != null, s"""Expected header '${header}' was not found""")
    assert(
      expectedSlices.forall(s => value.containsSlice(s)),
      s"""Expected header '${header}' with slices ${expectedSlices.mkString("'", "' and '", "'")}! But was '${value}'"""
    )
  }

  def checkExact(manifest: Manifest, header: String, expectedValue: String) = {
    val value = manifest.getMainAttributes().getValue(header)
    assert(
      expectedValue == value,
      s"""Expected header '${header}' with value '${expectedValue}'! But was '${value}'"""
    )
  }

  def checkUndefined(manifest: Manifest, header: String) = {
    assert(manifest.getMainAttributes().containsKey(header) == false,
      s"""Expected header '${header}' to be undefined, but found value '${manifest.getMainAttributes().getValue(header)}'""")
  }

  def jarFileEntries(file: os.Path): Seq[String] = {
    val ois = new JarInputStream(file.getInputStream)
    try {
      var entry: JarEntry = ois.getNextJarEntry()
      var entries: List[String] = List()
      while (entry != null) {
        if (!entry.isDirectory()) entries ::= entry.getName()
        entry = ois.getNextJarEntry()
      }
      entries.reverse
    } finally {
      ois.close()
    }
  }

  @inline def assert(condition: Boolean, hint: => String): Unit = {
    if (!condition) {
      throw new AssertionError(hint)
    }
  }

  @inline def assert(check: String, condition: Boolean, hint: => String): Unit = {
    if(condition) println(s"Checked: ${check}")
    else println(s"FAILED: ${check}")
    assert(condition, hint)
  }

}

object TestSupport extends TestSupport