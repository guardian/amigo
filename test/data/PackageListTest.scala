package data

import models.{ BakeId, RecipeId }
import org.scalatest.{ FlatSpec, Matchers }

class PackageListTest extends FlatSpec with Matchers {

  "s3Url" should "return valid S3 url of expected pattern" in {
    val url = PackageList.s3Url(BakeId(RecipeId("cauldron-cake"), 1), "mr-hole")
    url should be("s3://mr-hole/packagelists/cauldron-cake--1.txt")
  }

  "removeNonPackageLines" should "remove redundant output" in {
    val packageList = List(
      "Installed Packages",
      "p1",
      "p2,"
    )
    val removed = PackageList.removeNonPackageLines(packageList)
    removed.head should be("p1")
    removed.length should be(2)
  }
}
