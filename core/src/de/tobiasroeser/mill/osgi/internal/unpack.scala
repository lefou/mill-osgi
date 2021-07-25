package de.tobiasroeser.mill.osgi.internal

import mill.api.IO
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
                  IO.stream(zipStream, fileOut)
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
}
