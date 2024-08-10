/*
 * Copyright 2022-2024 Alejandro Hernández <https://github.com/alejandrohdezma>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alejandrohdezma.resource.generator

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

import scala.io.Source

/** This trait allows you to generate resources stored with the `ResourceGeneratorPlugin` in the directory where
  * `generate` is executed. Because of this, you will probably want to use this `trait` in a project with such a plugin
  * enabled.
  *
  * Then you can create either a `main` or an SBT's `AutoPlugin` to generate the resources:
  *
  * {{{
  * package my.org.sbt
  *
  * import sbt.Keys._
  *
  * import com.alejandrohdezma.resource.generator.ResourceGenerator
  *
  * object MyGeneratorPlugin extends sbt.AutoPlugin with ResourceGenerator[Unit] {
  *
  *   val generateMyFiles = sbt.taskKey[Unit](s"Generates the following files: $${resources.mkString(", ")}")
  *
  *   override def trigger = allRequirements
  *
  *   override def buildSettings = Seq(
  *     generateMyFiles := generate((name.value, organization.value), streams.value.log.info(_))
  *   )
  *
  * }
  * }}}
  *
  * {{{
  * import com.alejandrohdezma.resource.generator.ResourceGenerator
  *
  * object Main extends App with ResourceGenerator[Unit] {
  *
  *   override def repository: Option[String] = Some("my-org/my-repo")
  *
  *   generate(())
  *
  * }
  * }}}
  */
@SuppressWarnings(Array("scalafix:DisableSyntax.valInAbstract", "scalafix:Disable.toString"))
trait ResourceGenerator[A] {

  /** The repository generating the files. It is used on the default headers. Will not be included if `None`. */
  def repository: Option[String] = None

  /** List of files that should not contain an informative header.
    *
    * @see
    *   [[ResourceGenerator#resourceTransformers]]
    */
  def noHeaderFiles: List[String] = List("LICENSE.md")

  /** A partial function containing resource transformers. The transformers implemented here are executed before writing
    * a file.
    *
    * You can use them to modify both the `path` where a file is generated as well as its contents.
    *
    * The default transformers just add headers for MarkDown files (using `[comment]: <> ("my comment")`) and for any
    * other file (using `#`).
    */
  @SuppressWarnings(Array("scalafix:Disable.Option.get", "scalafix:DisableSyntax.=="))
  def resourceTransformers: ResourceTransformers = {
    case ((path, extras), content) if path.extension == "md" && repository.isEmpty && !isNoHeaderFile(path) =>
      path -> extras -> s"""[comment]: <> (Don't edit this file! It is automatically updated)
                           |[comment]: <> (If you want to suggest a change, please open a PR or issue in the original repository)
                           |
                           |$content""".stripMargin
    case ((path, extras), content) if path.extension == "md" && repository.nonEmpty && !isNoHeaderFile(path) =>
      path -> extras -> s"""[comment]: <> (Don't edit this file!)
                           |[comment]: <> (It is automatically updated after every release of https://github.com/${repository.get})
                           |[comment]: <> (If you want to suggest a change, please open a PR or issue in that repository)
                           |
                           |$content""".stripMargin
    case ((path, extras), content) if repository.isEmpty && !isNoHeaderFile(path) =>
      path -> extras -> s"""# Don't edit this file! It is automatically updated.
                           |# If you want to suggest a change, please open a PR or issue in the original repository
                           |
                           |$content""".stripMargin
    case ((path, extras), content) if repository.nonEmpty && !isNoHeaderFile(path) =>
      path -> extras -> s"""# Don't edit this file!
                           |# It is automatically updated after every release of https://github.com/${repository.get}
                           |# If you want to suggest a change, please open a PR or issue in that repository
                           |
                           |$content""".stripMargin
  }

  /** Generates the files detailed on the comma-separated list `resources` property from a resource named
    * `resource-generator-metadata.properties`.
    *
    * @param extras
    *   Extra attributes to be passed on to `resourceTransformers`.
    * @param excludeFile
    *   Function used to calculate when a file should not be generated.
    * @param logger
    *   Logger used to report each file generated. Defaults to `println`. If using from SBT one can used
    *   `sbt.Keys.streams.value.log.info`.
    */
  @SuppressWarnings(Array("scalafix:DisableSyntax.defaultArgs", "scalafix:Disable.blocking.io"))
  def generate(
      extras: A,
      excludeFile: (Path, String) => Boolean = (_, _) => false,
      logger: String => Unit = System.out.println
  ): Unit =
    resources.foreach { resource =>
      val rawContent = Source.fromResource(resource, super.getClass().getClassLoader()).mkString

      val data = ((Path.of(resource), extras), rawContent)

      val ((destination, _), content) = resourceTransformers.lift(data).getOrElse(data)

      if (!excludeFile(destination, content)) {
        // Ensure parent directories exist before trying to write the file
        Option(destination.getParent()).foreach(Files.createDirectories(_))

        Files.writeString(destination, content)

        logger(s"Generated file $destination" + repository.map(" from " + _).getOrElse(""))
      }
    }

  /** List of resources being propagated */
  lazy val resources: List[String] =
    `resource-generator-metadata.properties`.get("resources").toString().split(",").toList

  private def isNoHeaderFile(path: Path) =
    noHeaderFiles.map(_.toLowerCase()).contains(path.getFileName().toString().toLowerCase())

  @SuppressWarnings(Array("scalafix:Disable.blocking.io"))
  private lazy val `resource-generator-metadata.properties` = {
    val properties = new Properties()

    val content = Source.fromResource("resource-generator-metadata.properties", super.getClass().getClassLoader())

    properties.load(content.reader())

    properties
  }

  implicit class StringPathOps(string: String) {

    /** @see Path.resolve */
    def /(child: Path): Path = Path.of(string).resolve(child)

    /** @see Path.resolve */
    def /(child: String): Path = Path.of(string, child)

  }

  implicit class PathOps(path: Path) {

    /** @see Path.resolve */
    def /(child: Path): Path = path.resolve(child)

    /** @see Path.resolve */
    def /(child: String): Path = path.resolve(child)

    /** Returns this file's extension or empty string if no extension is found. */
    def extension: String = {
      val name = path.getFileName().toString
      val dot  = name.lastIndexOf('.')
      if (dot < 0) "" else name.substring(dot + 1)
    }

  }

  type ResourceTransformers = PartialFunction[((Path, A), String), ((Path, A), String)]

  implicit class ResourceTransformerOps(transformers: ResourceTransformers) {

    /** If this transformers don't procude any output use the provided ones. */
    def or(pf: ResourceTransformers): ResourceTransformers = transformers.orElse(pf)

    /** Execute this transformers first and then the provided ones. */
    def and(pf: ResourceTransformers): ResourceTransformers = transformers.andThen {
      case values if pf.isDefinedAt(values) => pf(values)
      case values                           => values
    }

  }

  object ResourceTransformers {

    def apply(pf: ResourceTransformers): ResourceTransformers = pf

  }

}
