package models

import org.joda.time.DateTime
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BaseImageTest extends AnyFlatSpec with Matchers {

  def makeBaseImage(eolDate: DateTime): BaseImage =
    BaseImage(
      BaseImageId("discworld-image"),
      "deteriorated cabbage",
      AmiId("ami-123"),
      List(),
      "Detritus",
      DateTime.now,
      "Rincewind",
      DateTime.now,
      None,
      Some(eolDate)
    )

  "eolStatus" should "determinee correct eol status" in {
    val baseImageLongSupport = makeBaseImage(DateTime.now.plusMonths(24))
    val baseImageExpiresSoon = makeBaseImage(DateTime.now.plusMonths(1))
    val baseImageExpired = makeBaseImage(DateTime.now.minusMonths(1))
    BaseImage.eolStatus(baseImageLongSupport) should be(Supported)
    BaseImage.eolStatus(baseImageExpiresSoon) should be(EndOfLifeSoon)
    BaseImage.eolStatus(baseImageExpired) should be(EndOfLife)
  }

}
