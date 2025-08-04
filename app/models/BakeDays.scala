package models

object BakeDays {
  // Doesn't allow scheduling bakes for weekends!
  val days: Seq[String] =
    Seq("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")

  // above days zipped with itself to make a list suitable for constructing a <select> input
  val select: Seq[(String, String)] = ("" -> "") +: days.zip(days)
}
