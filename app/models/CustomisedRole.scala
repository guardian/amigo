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
    else variables.map { case (k, v) => s"$k: ${v.quoted}" }.mkString(", ")
  }

}

sealed trait ParamValue { def quoted: String }
case class SingleParamValue(param: String) extends ParamValue {
  override def toString: String = param
  val quoted = if (param.forall(CustomisedRole.allowedUnquotedChars)) param else s"'$param'"
}
case class ListParamValue(params: List[SingleParamValue]) extends ParamValue {
  override def toString: String = s"[${params.mkString(", ")}]"
  val quoted = s"[${params.map(_.quoted).mkString(", ")}]"
}
case class DictParamValue(params: Map[String, SingleParamValue]) extends ParamValue {
  override def toString: String = params.map { case (k, v) => s"$k: $v" }.mkString("{", ", ", "}")
  val quoted: String = params.map { case (k, v) => s"$k: ${v.quoted}" }.mkString("{", ", ", "}")
}
object ListParamValue {
  def of(params: String*) = ListParamValue(params.map(SingleParamValue).toList)
}
object ParamValue {
  implicit val format = DynamoFormat.xmap[ParamValue, String](
    CustomisedRole.paramValue.parse(_).fold(
      (_, _, _) => Left(TypeCoercionError(new RuntimeException("Unable to read ParamValue"))),
      (pv, _) => Right(pv))
  )(_.quoted)
}

object CustomisedRole {
  val White = WhitespaceApi.Wrapper {
    import fastparse.all._
    NoTrace(" ".rep)
  }
  import fastparse.noApi._
  import White._

  val key: Parser[String] = P(CharsWhile(_ != ':').!)
  val allowedUnquotedChars: Char => Boolean = c => c.isLetterOrDigit || c == '-' || c == '_' || c == '/' || c == '.'
  val unquotedSingleValue: Parser[SingleParamValue] = P(CharPred(allowedUnquotedChars).rep(min = 1).!).map(SingleParamValue)
  val quotedSingleValue: Parser[SingleParamValue] = P("'" ~ CharsWhile(_ != '\'', 0).! ~ "'").map(SingleParamValue)
  val singleValue: Parser[SingleParamValue] = P(unquotedSingleValue | quotedSingleValue)
  val multiValues: Parser[ListParamValue] = P("[" ~/ singleValue.rep(sep = ",") ~ "]").map(
    params => ListParamValue(params.toList))
  val dictValues: Parser[DictParamValue] = P("{" ~/ dictPair.rep(sep = ",") ~ "}").map {
    pairs => DictParamValue(pairs.toMap)
  }
  val dictPair: Parser[(String, SingleParamValue)] = P(key ~ ":" ~/ singleValue)
  val paramValue: Parser[ParamValue] = P(dictValues | multiValues | singleValue)
  val pair: Parser[(String, ParamValue)] = P(key ~ ":" ~ paramValue)

  val parameters = P(Start ~ pair.rep(sep = ",") ~ End)

  def formInputTextToVariables(input: String): Either[String, Map[String, ParamValue]] = {
    parameters.parse(input) match {
      case Parsed.Success(value, _) => Right(value.toMap)
      case f: Parsed.Failure => Left(f.toString)
    }
  }

  implicit val format = implicitly[DynamoFormat[CustomisedRole]]

}
