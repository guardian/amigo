package models

sealed abstract class TextContent(content: String)
case class Yaml(content: String) extends TextContent(content)
case class Markdown(content: String) extends TextContent(content)

