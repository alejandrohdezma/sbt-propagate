SBT plugin to help creating resource-generating plugins

---

- [Installation](#installation)
- [How to use this project?](#how-to-use-this-project)
- [Extras](#extras)
  - [Auto-generated headers](#auto-generated-headers)
  - [Resources documentation](#resources-documentation)
  - [Transform resource's contents/paths](#transform-resources-contentspaths)
  - [Using extra information](#using-extra-information)
  - [Excluding files](#excluding-files)
  - [Generating outside SBT](#generating-outside-sbt)

## Installation

Add the following line to your `plugins.sbt` file:

```sbt
addSbtPlugin("com.alejandrohdezma" % "sbt-propagate" % "0.2.1")
```

## How to use this project?

To use this plugin in your repo, you just need some files to propagate
and a couple minutes of your time. Here is a step-by-step tutorial on how to do it:

1 - **Enable the `ResourceGeneratorPlugin`** in the project from where you want to generate the files later.


```scala
lazy val `my-generator` = project
  .enablePlugins(ResourceGeneratorPlugin)
```

2 - **Add some files** that you want to propagate to other projects using `resourcesToPropagate`. 
This setting receives a pair with the original file on the left, and the destination where it
will be copied on other places on the right.


```scala
lazy val `my-generator` = project
  .enablePlugins(ResourceGeneratorPlugin)
  .settings(resourcesToPropagate += "docs/CODE_OF_CONDUCT.md" -> "docs/CODE_OF_CONDUCT.md")
  .settings(resourcesToPropagate += "docs/CONTRIBUTING.md" -> "docs/CONTRIBUTING.md")
  .settings(resourcesToPropagate += "docs/LICENSE.md" -> "docs/LICENSE.md")
  .settings(resourcesToPropagate += ".github/workflows/ci.yml" -> ".github/workflows/ci.yml")
  .settings(resourcesToPropagate += ".gitignore" -> ".gitignore")
```

3 - **Create an SBT plugin** that will generate your files. For that you need to do two things:

3.1 - Enable `SbtPlugin` in your project:


```scala
lazy val `my-generator` = project
  .enablePlugins(SbtPlugin, ResourceGeneratorPlugin)
```

3.2 - Create a new `object` extending `AutoPlugin` in your `src` folder.

```scala
import sbt._

object MyGeneratorPlugin extends AutoPlugin {

  override def trigger = allRequirements

}
```

4 - **Extend `ResourceGenerator`** from your `AutoPlugin`. This class should be already in scope since dependency is automatically added by `ResourceGeneratorPlugin`.

```scala
import sbt._

import com.alejandrohdezma.resource.generator.ResourceGenerator

object MyGeneratorPlugin extends AutoPlugin with ResourceGenerator[Unit] {

  override def trigger = allRequirements

}
```

> If you want to know what the type-param `Unit` means here, have a look at [Using extra information](#using-extra-information)

5 - **Add a task** to our newly created plugin to generate the previously stored files.
You can call this task whatever you like.

```scala
import sbt._

import com.alejandrohdezma.resource.generator.ResourceGenerator

object MyGeneratorPlugin extends AutoPlugin with ResourceGenerator[Unit] {

  val generateMyFiles = taskKey[Unit] {
    s"Generates the following files: ${resources.mkString(", ")}"
  }

  override def trigger = allRequirements

}
```

6 - **Implement the task** under `buildSettings`:

```scala
import sbt._
import sbt.Keys.streams

import com.alejandrohdezma.resource.generator.ResourceGenerator

object MyGeneratorPlugin extends AutoPlugin with ResourceGenerator[Unit] {

  val generateMyFiles = taskKey[Unit] {
    s"Generates the following files: ${resources.mkString(", ")}"
  }

  override def trigger = allRequirements

  override def buildSettings = Seq(
    generateMyFiles := generate((), logger = streams.value.log.info(_))
  )

}
```

And that's it! You can now publish this project and start adding it as a plugin in repositories
where you want these files propagated.

You will need to ensure your `generateMyFiles` task is called when this plugin
is updated as well as ensuring that changes are commited to the repository. 
You can ensure this manually, but if you use
[Scala Steward](https://github.com/scala-steward-org/scala-steward)
to keep your repositories up-to-date it is super easy. Just add the following
lines to the `.scala-steward.conf` in the root of your repositories:

```
postUpdateHooks = [
  {
    command = ["sbt", "generateMyFiles"],
    commitMessage = "Run `sbt generateMyFiles`",
    groupId = "my.org",
    artifactId = "my-generator"
  }
]
```

Once this is in place, whenever the `my-generator` artifact is updated, Scala
Steward will run `sbt generateMyFiles` and commit the changes in the same PR
with the update.

## Extras

This project provides some extras that you can use to improve your generator plugin.

### Auto-generated headers

By default, the plugin adds a header similar to this to every file:

```markdown
# Don't edit this file! It is automatically updated.
# If you want to suggest a change, please open a PR or issue in the original repository
```

This header can be enriched with a link to the original repository just by overriding
`repository` with the repository name:

```scala
import sbt._
import sbt.Keys.streams

import com.alejandrohdezma.resource.generator.ResourceGenerator

object MyGeneratorPlugin extends AutoPlugin with ResourceGenerator[Unit] {

  val generateMyFiles = taskKey[Unit] {
    s"Generates the following files: ${resources.mkString(", ")}"
  }

  override def trigger = allRequirements

  override def buildSettings = Seq(
    generateMyFiles := generate((), logger = streams.value.log.info(_))
  )

  override def repository: Option[String] = Some("my-org/my-repo")

}

```

By default it will add headers for MarkDown files (using `[comment]: <> ("my comment")`)
and for any other file (using `#`). This functionality can be extended by overriding
`resourceTransformers` method in your plugin.

```scala
import sbt._
import sbt.Keys.streams

import com.alejandrohdezma.resource.generator.ResourceGenerator

object MyGeneratorPlugin extends AutoPlugin with ResourceGenerator[Unit] {

  val generateMyFiles = taskKey[Unit] {
    s"Generates the following files: ${resources.mkString(", ")}"
  }

  override def trigger = allRequirements

  override def buildSettings = Seq(
    generateMyFiles := generate((), logger = streams.value.log.info(_))
  )


  override def resourceTransformers = super.resourceTransformers.or {
    case ((path, extras), content) if path.extension == "xml" =>
      val lines = content.split("\n").toList

      path -> extras -> s"""${lines.head}
                |<!-- Don't edit this file! It is automatically updated -->
                |<!-- If you want to suggest a change, please open a PR or issue in the original repository -->
                |${lines.tail.mkString("\n")}""".stripMargin
  }

}
```

Or if you want to override any existant transformer:

```scala
import sbt._
import sbt.Keys.streams

import com.alejandrohdezma.resource.generator.ResourceGenerator

object MyGeneratorPlugin extends AutoPlugin with ResourceGenerator[Unit] {

  val generateMyFiles = taskKey[Unit] {
    s"Generates the following files: ${resources.mkString(", ")}"
  }

  override def trigger = allRequirements

  override def buildSettings = Seq(
    generateMyFiles := generate((), logger = streams.value.log.info(_))
  )

  override def resourceTransformers = ResourceTransformers {
    case ((path, extras), content) if path.extension == "xml" =>
      val lines = content.split("\n").toList

      path -> extras -> s"""${lines.head}
                |<!-- Don't edit this file! It is automatically updated -->
                |<!-- If you want to suggest a change, please open a PR or issue in the original repository -->
                |${lines.tail.mkString("\n")}""".stripMargin
  }.or(super.resourceTransformers)

}
```

Lastly, for the default transfomers if you don't want them to add headers to
specific files, you can override the `noHeaderFiles` list. By default this list
just contains `LICENSE.md`:

```scala
import sbt._
import sbt.Keys.streams

import com.alejandrohdezma.resource.generator.ResourceGenerator

object MyGeneratorPlugin extends AutoPlugin with ResourceGenerator[Unit] {

  val generateMyFiles = taskKey[Unit] {
    s"Generates the following files: ${resources.mkString(", ")}"
  }

  override def trigger = allRequirements

  override def buildSettings = Seq(
    generateMyFiles := generate((), logger = streams.value.log.info(_))
  )

  override def noHeaderFiles: List[String] = super.noHeaderFiles :+ "some_file.md"

}
```

### Resources documentation

You can add descriptions to your resources using comments. By default this is done:

- For markdown files, adding lines like `[comment]: <> (a-description-line)`.
- For any other file, adding lines started with `# `.

To mark the end of the description just leave an empty line between the description
and the actual resource's contents.


You can enhance this functionality by adding new values to the `resourcesToPropagateDescriptionScraper` setting:

```scala
lazy val `my-generator` = project
  .enablePlugins(SbtPlugin, ResourceGeneratorPlugin)
  .settings(resourcesToPropagateDescriptionScraper += "xml" -> { lines: List[String] =>
    lines.takeWhile(_.startsWith("<!--")).map(_.stripPrefix("<!-- ").stripSuffix(" --"))
  })
```

You can use the special "*" as a fallback.

By default, the plugin will recover resource descriptions and expose them using the
`resourcesToPropagateDocs`. You can then use this setting to create a useful documentation
for your plugin like (using [mdoc](https://scalameta.org/mdoc/)):


```scala
lazy val documentation = project
  .enablePlugins(MdocPlugin)
  .settings(mdocVariables += "PROPAGATED_RESOURCES" -> propagatedResouces.value)

lazy val propagatedResouces = Def.setting {
  (`my-generator` / resourcesToPropagateDocs).value.map { case (resource, destination, description) =>
    s"### :octocat: [$resource](https://github.com/my-org/my-generator/blob/main/$resource) (copied as $destination)\n\n$description"
  }.mkString("", "\n\n", "\n\n")
}
```

### Transform resource's contents/paths

If you want to tweak the resource's contents or paths before generating them,
you can use the same `resourceTransformers` we use for adding headers:

```scala
import sbt._
import sbt.Keys.streams

import com.alejandrohdezma.resource.generator.ResourceGenerator

object MyGeneratorPlugin extends AutoPlugin with ResourceGenerator[Unit] {

  val generateMyFiles = taskKey[Unit] {
    s"Generates the following files: ${resources.mkString(", ")}"
  }

  override def trigger = allRequirements

  override def buildSettings = Seq(
    generateMyFiles := generate((), logger = streams.value.log.info(_))
  )

  override def resourceTransformers = super.resourceTransformers.and {
    case ((path, extras), content) if path.extension == "md" =>
      "docs" / path.getFileName() -> extras -> content.replace("hate", "flowers")
  }

}
```

### Using extra information

You can use any type-param when extending `ResourceGenerator`. An instance of the type
you add here must be provided when calling `generate` (see step 6 under
[How to use this project?](#how-to-use-this-project)).

That value will then be provided to the `ResourceTransformers` described in
[Transform resource's contents/paths](#transform-resources-contentspaths) and can
be used like:

```scala
import sbt._
import sbt.Keys._

import com.alejandrohdezma.resource.generator.ResourceGenerator

object MyGeneratorPlugin extends AutoPlugin with ResourceGenerator[String] {

  val generateMyFiles = taskKey[Unit] {
    s"Generates the following files: ${resources.mkString(", ")}"
  }

  override def trigger = allRequirements

  override def buildSettings = Seq(
    generateMyFiles := generate(
      extras = name.value, logger = streams.value.log.info(_))
  )

  override def resourceTransformers = super.resourceTransformers.and {
    case ((path, name), content) if path.extension == "md" =>
      path -> name -> content.replace("{{name}}", name)
  }

}
```

### Excluding files

You can pass a function to `generate` to decide when a file should be excluded,
and thus, not generated. This function receives both the final path and content
for the file (after all `ResourceTransformers` are applied) and returns a
`Boolean`. Return `true` when you want a file excluded.

For example if we want to exclude files based on glob patterns, we could do it
like:

```scala
import java.nio.file._

import sbt._
import sbt.Keys._

import com.alejandrohdezma.resource.generator.ResourceGenerator

object MyGeneratorPlugin extends AutoPlugin with ResourceGenerator[String] {

  object autoImport {

    val excludedFiles = settingKey[List[String]] {
      "List of glob patterns. Files matching any of the patterns in this list" +
        " will be excluded from generation"
    }

  }

  val generateMyFiles = taskKey[Unit] {
    s"Generates the following files: ${resources.mkString(", ")}"
  }

  override def trigger = allRequirements

  import autoImport._

  override def buildSettings = Seq(
    excludedFiles := Nil,
    generateMyFiles := generate(
      extras = name.value,
      excludeFile = globPatterns.value,
      logger = streams.value.log.info(_)
    )
  )

  private val globPatterns = Def.setting {
    val fileSystem = FileSystems.getDefault()

    val matchers = excludedFiles.value
      .map("glob:" + _)
      .map(fileSystem.getPathMatcher(_))

    (path: Path, _: String) => matchers.find(_.matches(path)).nonEmpty
  }

}
```

### Generating outside SBT

There could be a case where you want to use your generator outside an SBT project.
It is a very simple task.

1 - Add a new `Main` file along with your `MyGenerator`


```scala
object Main extends App {

  MyGeneratorPlugin.generate(())

}
```

2 - Once you have published your artifact, you can run it with coursier:

```bash
cs launch --sbt-plugin my.org:my-generator:1.0.0
```