package models

sealed abstract class TextContent(content: String) {
  locally {
    val _ = content
  }
}
case class Yaml(content: String) extends TextContent(content)
case class Markdown(content: String) extends TextContent(content)
