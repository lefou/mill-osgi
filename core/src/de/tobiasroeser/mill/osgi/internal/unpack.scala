package de.tobiasroeser.mill.osgi.internal

import java.io.{InputStream, OutputStream}

import os.Path

object unpack {
  def zip(src: Path, target: Path) = {
    val byteStream = os.read.inputStream(src)
    try {
      val zipStream = new java.util.zip.ZipInputStream(byteStream)
      try {
        while ({
          zipStream.getNextEntry match {
            case null => false
            case entry =>
              if (!entry.isDirectory) {
                val entryDest = target / os.RelPath(entry.getName)
                os.makeDir.all(entryDest / os.up)
                val fileOut = new java.io.FileOutputStream(entryDest.toString)
                try {
                  stream(zipStream, fileOut)
                } finally {
                  fileOut.close()
                  zipStream.closeEntry()
                }
              }
              true
          }
        }) ()
      } finally {
        zipStream.close()
      }
    } finally {
      byteStream.close()
    }
  }


  /**
   * Pump the data from the `src` stream into the `dest` stream.
   */
  private def stream(src: InputStream, dest: OutputStream): Unit = {
    val buffer = new Array[Byte](4096)
    while ({
      src.read(buffer) match {
        case -1 => false
        case n =>
          dest.write(buffer, 0, n)
          true
      }
    }) ()
  }
}
