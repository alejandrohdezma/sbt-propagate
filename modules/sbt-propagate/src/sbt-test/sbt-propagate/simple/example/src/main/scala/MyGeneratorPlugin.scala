package my.org.sbt

import java.nio.file._

import sbt.Keys._
import sbt.Def

import com.alejandrohdezma.resource.generator.ResourceGenerator

object MyGeneratorPlugin extends sbt.AutoPlugin with ResourceGenerator[(String, String)] {

  object autoImport {

    val excludedFiles = sbt.settingKey[List[String]] {
      "List of glob patterns. Files matching any of the patterns in this list" +
        " will be excluded from generation"
    }

  }

  val generateMyFiles = sbt.taskKey[Unit](s"Generates the following files: ${resources.mkString(", ")}")

  override def repository: Option[String] = Some("my-org/my-repo")

  override def trigger = allRequirements

  import autoImport._

  override def buildSettings = Seq(
    excludedFiles := Nil,
    generateMyFiles := generate(
      extras = (name.value, organization.value),
      excludeFile = globPatterns.value,
      logger = streams.value.log.info(_)
    )
  )

  override def resourceTransformers = ResourceTransformers {
    case ((path, extras), content) if path.extension == "xml" =>
      val lines = content.split("\n").toList

      path -> extras -> (s"""${lines.head}
                            |<!-- Don't edit this file! It is automatically updated -->
                            |<!-- If you want to suggest a change, please open a PR or issue in the original repository -->
                            |""".stripMargin + lines.tail.mkString("\n"))
  }.or(super.resourceTransformers)
    .and {
      case ((path, extras), content) if path.extension == "xml" =>
        "src" / "main" / "resources" / path.getFileName() -> extras -> content
    }
    .and {
      case ((path, (name, organization)), content) if path.extension == "md" =>
        path -> ((name, organization)) -> content.replace("{{name}}", name).replace("{{organization}}", organization)
    }

  private val globPatterns = Def.setting {
    val fileSystem = FileSystems.getDefault()

    val matchers = excludedFiles.value
      .map("glob:" + _)
      .map(fileSystem.getPathMatcher(_))

    (path: Path, _: String) => matchers.find(_.matches(path)).nonEmpty
  }

}
