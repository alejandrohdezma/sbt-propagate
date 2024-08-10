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

package com.alejandrohdezma.sbt.propagate

import java.util.Properties

import sbt.Keys._
import sbt._

/** This plugin copies the resources listed in `resourcesToPropagate` to `target/resources-to-propagate` and adds that
  * folder as a resources directory. It also creates a `resource-generator-metadata.properties` containing the name of
  * the propagated resources that is then picked-up by `ResourceGenerator` to generate the results.
  *
  * It also provides a `resourcesToPropagateDocs` setting that contains the same elements as `resourcesToPropagate` but
  * including each file's description.
  *
  * @see
  *   ResourceGenerator
  */
object ResourceGeneratorPlugin extends AutoPlugin {

  object autoImport {

    val resourcesToPropagate = settingKey[List[(String, String)]] {
      "List of resources to propagate and their destination folders"
    }

    val resourcesToPropagateDocs = settingKey[List[(String, String, String)]] {
      "Contains the same elements as `resourcesToPropagate` but including each file's description (extracted using `resourcesToPropagateDescriptionScraper`)."
    }

    val resourcesToPropagateDescriptionScraper = settingKey[Map[String, List[String] => List[String]]] {
      """Contains a function that receives a file extension and returns a function to scrape the description lines from a document with said extension.
        |The list received as file's contents correspond to file's lines.""".stripMargin
    }

  }

  import autoImport._

  override def projectSettings: Seq[Setting[_]] = List(
    libraryDependencies += "com.alejandrohdezma" %% "resource-generator" % BuildInfo.version,
    resourcesToPropagateDescriptionScraper := Map(
      "md" -> (_.takeWhile(_.startsWith("[comment]: <>")).map(_.stripPrefix("[comment]: <> (").stripSuffix(")"))),
      "*"  -> (_.takeWhile(_.startsWith("#")).map(_.stripPrefix("# ").stripPrefix("#")))
    ),
    resourcesToPropagateDocs := resourcesToPropagate.value.map { case (resource, destination) =>
      val allScrapers = resourcesToPropagateDescriptionScraper.value

      val resourceFile = file(resource)

      val scraper = allScrapers.get(resourceFile.ext).orElse(allScrapers.get("*"))

      val description = scraper.map(_(IO.readLines(resourceFile))).getOrElse(Nil)

      (resource, destination, description.mkString("\n"))
    },
    resourcesToPropagate                   := Nil,
    Compile / unmanagedResourceDirectories += target.value / "resources-to-propagate",
    Compile / resourceGenerators += Def.task {
      val file = target.value / "resources-to-propagate" / "resource-generator-metadata.properties"

      val properties = new Properties()

      properties.setProperty("resources", resourcesToPropagate.value.map(_._2).mkString(","))

      IO.write(properties, "Metadata for sbt-propagate SBT plugin", file)

      List(file)
    }.taskValue,
    Compile / resourceGenerators += Def.task {
      resourcesToPropagate.value.map { case (resource, destination) =>
        val resourceFile = target.value / "resources-to-propagate" / destination

        streams.value.log.info(s"Copying $resource to $resourceFile")

        IO.copyFile(file(resource), resourceFile)

        resourceFile
      }
    }.taskValue
  )

}
