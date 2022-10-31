package my.org.sbt

import sbt.Keys._

import com.alejandrohdezma.resource.generator.ResourceGenerator

object MyGeneratorPlugin extends sbt.AutoPlugin with ResourceGenerator[(String, String)] {

  val generateMyFiles = sbt.taskKey[Unit](s"Generates the following files: ${resources.mkString(", ")}")

  override def repository: Option[String] = Some("my-org/my-repo")

  override def trigger = allRequirements

  override def buildSettings = Seq(
    generateMyFiles := generate((name.value, organization.value), streams.value.log.info(_))
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

}
