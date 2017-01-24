package models

import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.error.TypeCoercionError
import fastparse.WhitespaceApi

case class CustomisedRole(
    roleId: RoleId,
    variables: Map[String, ParamValue]) {

  def variablesToString = variables.map { case (k, v) => s"$k: $v" }.mkString("{ ", ", ", " }")

  /** Render the variables for display in a form input box */
  def variablesToFormInputText = {
    if (variables.isEmpty) ""
    else variables.map { case (k, v) => s"$k: $v" }.mkString(", ")
  }

}

sealed trait ParamValue { def quoted: String }
case class SingleParamValue(param: String) extends ParamValue {
  override def toString: String = param
  val quoted = s"'$param'"
}
case class ListParamValue(params: List[SingleParamValue]) extends ParamValue {
  override def toString: String = s"[${params.mkString(", ")}]"
  val quoted = s"[${params.map(_.quoted).mkString(", ")}]"
}
object ListParamValue {
  def of(params: String*) = ListParamValue(params.map(SingleParamValue).toList)
}
object ParamValue {
  implicit val format = DynamoFormat.xmap[ParamValue, String](
    CustomisedRole.paramValue.parse(_).fold(
      (_, _, _) => Left(TypeCoercionError(new RuntimeException("Unable to read ParamValue"))),
      (pv, _) => Right(pv))
  )(_.toString)
}

object CustomisedRole {
  val White = WhitespaceApi.Wrapper {
    import fastparse.all._
    NoTrace(" ".rep)
  }
  import fastparse.noApi._
  import White._

  val key: Parser[String] = P(CharsWhile(_ != ':').!)
  val singleValue: Parser[SingleParamValue] = P(CharPred(c => c.isLetterOrDigit || c == '-' || c == '_' || c == '/').rep.!).map(SingleParamValue(_))
  val multiValues: Parser[ListParamValue] = P("[" ~ singleValue.rep(sep = ",") ~ "]").map(
    params => ListParamValue(params.toList))
  val paramValue: Parser[ParamValue] = multiValues | singleValue
  val pair: Parser[(String, ParamValue)] = P(key ~ ":" ~ paramValue)

  val parameters = P(Start ~ pair.rep(sep = ",") ~ End)

  def formInputTextToVariables(input: String): Map[String, ParamValue] = {
    parameters.parse(input) match {
      case Parsed.Success(value, _) => value.toMap
      case f: Parsed.Failure => Map.empty
    }
  }

}
