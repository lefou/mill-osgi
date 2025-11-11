package de.tobiasroeser.mill.osgi

import scala.util.Try

import aQute.bnd.osgi.Builder
import de.tobiasroeser.mill.osgi.internal.BuildInfo

trait OsgiBundleModuleSupport {
  def calcBundleSymbolicName(group: String, artifact: String): String = {
    val groupParts = group.split("[.]")
    val nameParts = artifact.split("[.]").flatMap(_.split("[-]"))

    val parts =
      if (nameParts.startsWith(groupParts)) nameParts
      else (groupParts.lastOption, nameParts.headOption) match {
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


  protected[osgi] def checkMillVersion(millVersion: Option[String]): Boolean =
    checkMillVersion(BuildInfo.millVersion, millVersion)

  protected[osgi] def checkMillVersion(buildVersion: String, millVersion: Option[String]): Boolean = millVersion match {
    case Some(v) =>

      /** Extract the major, minor and micro version parts of the given version string. */
      def parseVersion(version: String): Try[Array[Int]] = Try {
        version
          .split("[-]", 2)(0)
          .split("[.]", 4)
          .take(3)
          .map(_.toInt)
      }

      val buildMillVersion = parseVersion(buildVersion).getOrElse(Array(0, 0, 0))
      val runMillVersion = parseVersion(v).getOrElse(Array(999, 999, 999))

      (runMillVersion(0) > buildMillVersion(0)) ||
        (runMillVersion(0) == buildMillVersion(0) && runMillVersion(1) > buildMillVersion(1)) ||
        (runMillVersion(0) == buildMillVersion(0) && runMillVersion(1) == buildMillVersion(1) &&
          runMillVersion(2) >= buildMillVersion(2))

    case _ =>
      // ignore
      true
  }

}