package models

/*
 * See http://docs.scala-lang.org/overviews/core/value-classes.html
 * for an explanation of why this trait needs to extend Any.
 *
 * Note that calling toString will result in object allocation.
 */
trait StringId extends Any {
  def value: String
  override def toString = value
}
