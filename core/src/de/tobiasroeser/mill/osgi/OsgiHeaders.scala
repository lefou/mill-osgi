package de.tobiasroeser.mill.osgi

import java.util.Properties

import scala.collection.immutable.Seq

case class OsgiHeaders(
  `Bundle-ActivationPolicy`: Option[String] = None,
  `Bundle-Activator`: Option[String] = None,
  `Bundle-Classpath`: Seq[String] = Seq.empty,
  `Bundle-Description`: Option[String] = None,
  `Bundle-ContactAddress`: Option[String] = None,
  `Bundle-Copyright`: Option[String] = None,
  `Bundle-DocURL`: Option[String] = None,
  `Bundle-Icon`: Option[String] = None,
  `Bundle-License`: Seq[String] = Seq.empty,
  `Bundle-Localization`: Option[String] = None,
  `Bundle-ManifestVersion`: Option[String] = None,
  `Bundle-Name`: Option[String] = None,
  `Bundle-NativeCode`: Option[String] = None,
  `Bundle-RequiredExecutionEnvironment`: Seq[String] = Seq.empty,
  `Bundle-SymbolicName`: String,
  //  `Bundle-UpdateLocation`: Option[String] = None,
  `Bundle-Vendor`: Option[String] = None,
  `Bundle-Version`: Option[String] = None,
  `Bundle-Developers`: Option[String] = None,
  `Bundle-Contributors`: Option[String] = None,
  `Bundle-SCM`: Option[String] = None,
  `DynamicImport-Package`: Seq[String] = Seq.empty,
  `Export-Package`: Seq[String] = Seq.empty,
  `Export-Service`: Seq[String] = Seq.empty,
  `Fragment-Host`: Option[String] = None,
  `Import-Package`: Seq[String] = Seq.empty,
  `Import-Service`: Seq[String] = Seq.empty,
  `Provide-Capability`: Option[String] = None,
  `Require-Bundle`: Seq[String] = Seq.empty,
  `Require-Capability`: Option[String] = None,
  `Service-Component`: Option[String] = None,
  `Private-Package`: Seq[String] = Seq.empty,
  `Ignore-Package`: Seq[String] = Seq.empty,
  `Include-Resource`: Seq[String] = Seq.empty,
  `Conditional-Package`: Seq[String] = Seq.empty
) {

  def toProperties: Properties = {
    val props = new Properties()

    def addProp(header: String, value: Any): Unit = value match {
      case null | None | Seq() => // we don't add any property
      case e: String => props.put(header, e)
      case Some(e: String) => props.put(header, e)
      case es: Seq[_] => props.put(header, es.map(_.toString()).mkString(","))
      case e => throw new UnsupportedOperationException(s"Unsupported entry type [${e.getClass()}] for manifest header [${header}]")
    }

    addProp("Bundle-ActivationPolicy", `Bundle-ActivationPolicy`)
    addProp("Bundle-Activator", `Bundle-Activator`)
    addProp("Bundle-Classpath", `Bundle-Classpath`)
    addProp("Bundle-ContactAddress", `Bundle-ContactAddress`)
    addProp("Bundle-Copyright", `Bundle-Copyright`)
    addProp("Bundle-Description", `Bundle-Description`)
    addProp("Bundle-DocURL", `Bundle-DocURL`)
    addProp("Bundle-Icon", `Bundle-Icon`)
    addProp("Bundle-License", `Bundle-License`)
    addProp("Bundle-Localization", `Bundle-Localization`)
    addProp("Bundle-ManifestVersion", `Bundle-ManifestVersion`)
    addProp("Bundle-Name", `Bundle-Name`)
    addProp("Bundle-NativeCode", `Bundle-NativeCode`)
    addProp("Bundle-RequiredExecutionEnvironment", `Bundle-RequiredExecutionEnvironment`)
    addProp("Bundle-SymbolicName", `Bundle-SymbolicName`)
    //    addProp("Bundle-UpdateLocation", `Bundle-UpdateLocation`)
    addProp("Bundle-Vendor", `Bundle-Vendor`)
    addProp("Bundle-Version", `Bundle-Version`)
    addProp("Bundle-Developers", `Bundle-Developers`)
    addProp("Bundle-Contributors", `Bundle-Contributors`)
    addProp("Bundle-SCM", `Bundle-SCM`)
    addProp("DynamicImport-Package", `DynamicImport-Package`)
    addProp("Export-Package", `Export-Package`)
    addProp("Export-Service", `Export-Service`)
    addProp("Fragment-Host", `Fragment-Host`)
    addProp("Import-Package", `Import-Package`)
    addProp("Import-Service", `Import-Service`)
    addProp("Provide-Capability", `Provide-Capability`)
    addProp("Require-Bundle", `Require-Bundle`)
    addProp("Require-Capability", `Require-Capability`)
    addProp("Service-Component", `Service-Component`)
    addProp("Private-Package", `Private-Package`)
    addProp("Ignore-Package", `Ignore-Package`)
    addProp("Include-Resource", `Include-Resource`)
    addProp("Conditional-Package", `Conditional-Package`)

    props
  }
}

object OsgiHeaders {
  implicit def rw: upickle.default.ReadWriter[OsgiHeaders] = upickle.default.macroRW
}