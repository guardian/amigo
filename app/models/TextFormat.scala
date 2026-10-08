package models

sealed abstract class TextContent
case class Yaml(content: String) extends TextContent
case class Markdown(content: String) extends TextContent
