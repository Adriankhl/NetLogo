import sbt._

import java.nio.file.{ Files, Path => NioPath }
import scala.collection.JavaConverters._

import com.github.mustachejava.TemplateFunction
import org.nlogo.build.{ DocumentationConfig, Documenter, HoconParser }

class ExtensionDocs(extensionsDirectory: File, extensionDocConfigFile: File) {
  def generateHTMLPageForExtension(
    extensionName:  String,
    targetFile:     NioPath,
    buildVariables: Map[String, Object]): NioPath = {

    val documentationConf = (extensionsDirectory / extensionName / "documentation.conf").toPath
    val netLogoConfFile = extensionDocConfigFile.toPath
    val configDocument = HoconParser.parseConfigFile(documentationConf.toFile)
    val netLogoConfig = HoconParser.parseConfig(HoconParser.parseConfigFile(netLogoConfFile.toFile))
    val docConfig = HoconParser.parseConfig(configDocument)
    val primitives = HoconParser.parsePrimitives(configDocument).primitives
    val filesToIncludeInManual: Seq[String] = configDocument.getStringList("filesToIncludeInManual").asScala
    val prePrimFiles =
      getIncludes(filesToIncludeInManual.takeWhile(_ != "primitives"), documentationConf.getParent)
    val postPrimFiles =
      getIncludes(filesToIncludeInManual.dropWhile(_ != "primitives").tail, documentationConf.getParent)
    val emptyToC =
      docConfig.tableOfContents.isEmpty ||
        (docConfig.tableOfContents.size == 1 && docConfig.tableOfContents.isDefinedAt(""))
    val additionalConfig = Map(
      "extensionName"         -> extensionName.capitalize,
      "prePrimitiveSections"  -> prePrimFiles,
      "postPrimitiveSections" -> postPrimFiles,
      "emptyTableOfContents"  -> Boolean.box(emptyToC)
    )
    val finalConfig = DocumentationConfig(
      markdownTemplate = netLogoConfig.markdownTemplate,
      primTemplate     = netLogoConfig.primTemplate,
      tableOfContents  = docConfig.tableOfContents,
      additionalConfig = docConfig.additionalConfig ++ additionalConfig ++ buildVariables
    )

    val renderedPage =
      renderMarkdown(extensionName)(Documenter.documentAll(finalConfig, primitives, documentationConf.getParent))
    Files.write(targetFile, renderedPage.getBytes("UTF-8"))
  }

  def generateExtensionDocs(
    htmlFileRoot: File,
    documentedExtensions: Seq[(String, String)],
    buildVariables: Map[String, Object]): Seq[NioPath] = documentedExtensions map {
      case (extName, extTitle) =>
        val targetPath = (htmlFileRoot / (extName + ".html").toLowerCase).toPath
        generateHTMLPageForExtension(extName, targetPath, buildVariables)
    }

  private def renderMarkdown(extensionName: String)(str: String): String =
    Markdown(str, addTableOfContents = false, manualizeLinks = true, extName = Some(extensionName))

  private def getIncludes(filenames: Seq[String], basePath: NioPath): TemplateFunction =
    new TemplateFunction {
      override def apply(s: String): String = filenames
        .map(name => basePath.resolve(name))
        .map(path => Files.readAllLines(path).asScala.mkString("\n"))
        .mkString("\n\n")
    }
}

