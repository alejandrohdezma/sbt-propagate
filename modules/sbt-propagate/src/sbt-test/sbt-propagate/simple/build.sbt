ThisBuild / publish / skip := true
enablePlugins(MdocPlugin)
mdocVariables += "PROPAGATED_RESOURCES" -> propagatedResouces.value

lazy val example = project
  .settings(publish / skip := false)
  .enablePlugins(SbtPlugin)
  .settings(scriptedBatchExecution := false)
  .settings(scriptedBufferLog := false)
  .settings(scriptedLaunchOpts += s"-Dplugin.version=${version.value}")
  .enablePlugins(ResourceGeneratorPlugin)
  .settings(resourcesToPropagateDescriptionScraper += "xml" -> { lines: List[String] =>
    lines.drop(1).takeWhile(_.startsWith("<!--")).map(_.stripPrefix("<!-- ").stripSuffix(" -->"))
  })
  .settings(resourcesToPropagate += "docs-to-propagate/file1.md" -> "docs/propagated/file_1.md")
  .settings(resourcesToPropagate += "docs-to-propagate/file2.md" -> "docs/propagated/file_2.md")
  .settings(resourcesToPropagate += ".gitignore_global" -> ".gitignore")
  .settings(resourcesToPropagate += "configuration.xml" -> "configuration.xml")

lazy val propagatedResouces = Def.setting {
  (example / resourcesToPropagateDocs).value.map { case (resource, destination, description) =>
    s"### :octocat: [$resource](https://github.com/my-org/my-repo/blob/main/$resource) (copied as $destination)\n\n$description"
  }.mkString("\n\n")
}
