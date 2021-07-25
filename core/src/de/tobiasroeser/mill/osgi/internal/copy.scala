package de.tobiasroeser.mill.osgi.internal

import java.nio.file
import java.nio.file.{CopyOption, Files, LinkOption, StandardCopyOption}

import os.Path

/** Copy of a newer version of os.copy, which already contains the mergeFolders option. */
object copy {
  def apply(
      from: Path,
      to: Path,
      followLinks: Boolean = true,
      replaceExisting: Boolean = false,
      copyAttributes: Boolean = false,
      createFolders: Boolean = false,
      mergeFolders: Boolean = false
  ): Unit = {
    if (createFolders) os.makeDir.all(to / os.up)
    val opts1 =
      if (followLinks) Array[CopyOption]()
      else Array[CopyOption](LinkOption.NOFOLLOW_LINKS)
    val opts2 =
      if (replaceExisting) Array[CopyOption](StandardCopyOption.REPLACE_EXISTING)
      else Array[CopyOption]()
    val opts3 =
      if (copyAttributes) Array[CopyOption](StandardCopyOption.COPY_ATTRIBUTES)
      else Array[CopyOption]()
    require(
      !to.startsWith(from),
      s"Can't copy a directory into itself: $to is inside $from"
    )

    def copyOne(p: Path): file.Path = {
      val target = to / p.relativeTo(from)
      if (mergeFolders && os.isDir(p, followLinks) && os.isDir(target, followLinks)) {
        // nothing to do
        target.wrapped
      } else {
        Files.copy(p.wrapped, target.wrapped, opts1 ++ opts2 ++ opts3: _*)
      }
    }

    copyOne(from)
    if (os.stat(from, followLinks = followLinks).isDir) os.walk(from).map(copyOne)
  }

}
