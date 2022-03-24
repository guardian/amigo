package models

import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.error.TypeCoercionError

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
    fastparse.parse(_, CustomisedRole.paramValue(_)).fold(
      (_, _, _) => Left(TypeCoercionError(new RuntimeException("Unable to read ParamValue"))),
      (pv, _) => Right(pv))
  )(_.quoted)
}

object CustomisedRole {
  //  val White = ScalaWhitespace.Wrapper {
  //    import fastparse.parse
  //    NoTrace(" ".rep)
  //  }
  //  import fastparse.all._
  //  import White._
  import fastparse._, ScalaWhitespace._

  def key[_: P]: P[String] = P(CharsWhile(_ != ':').!)
  def allowedUnquotedChars: Char => Boolean = c => c.isLetterOrDigit || c == '-' || c == '_' || c == '/' || c == '.'
  def unquotedSingleValue[_: P]: P[SingleParamValue] = P(CharPred(allowedUnquotedChars).rep(1).!).map(SingleParamValue)
  def quotedSingleValue[_: P]: P[SingleParamValue] = P("'" ~ CharsWhile(_ != '\'', 0).! ~ "'").map(SingleParamValue)
  def singleValue[_: P]: P[SingleParamValue] = P(unquotedSingleValue | quotedSingleValue)
  def multiValues[_: P]: P[ListParamValue] = P("[" ~/ singleValue.rep(sep = ",") ~ "]").map(
    params => ListParamValue(params.toList))
  def dictValues[_: P]: P[DictParamValue] = P("{" ~/ dictPair.rep(sep = ",") ~ "}").map {
    pairs => DictParamValue(pairs.toMap)
  }
  def dictPair[_: P]: P[(String, SingleParamValue)] = P(key ~ ":" ~/ singleValue)
  def paramValue[_: P]: P[ParamValue] = P(dictValues | multiValues | singleValue)
  def pair[_: P]: P[(String, ParamValue)] = P(key ~ ":" ~ paramValue)

  def parameters[_: P]: P[Seq[(String, ParamValue)]] = P(Start ~ pair.rep(sep = ",") ~ End)

  def formInputTextToVariables(input: String): Either[String, Map[String, ParamValue]] = {
    fastparse.parse(input, parameters(_)) match {
      case Parsed.Success(value, _) => Right(value.toMap)
      case f: Parsed.Failure => Left(f.toString)
    }
  }

  implicit val format = implicitly[DynamoFormat[CustomisedRole]]

}
