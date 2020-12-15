package data

import com.amazonaws.services.s3.{ AmazonS3, AmazonS3Client }
import models.BakeId

object PackageList {

  val unavailableText: String = "Package list unavailable"

  def removeNonPackageLines(packages: List[String]): List[String] = {
    packages.filter { p =>
      !(p.contains("Listing") || p.contains("Installed Packages") || p.contains("Loaded plugins"))
    }
  }

  def getPackageList(s3Client: AmazonS3, bakeId: BakeId, bucket: String): List[String] = {
    val packageListKey = s"packagelists/${BakeId.toFilename(bakeId)}"

    if (s3Client.doesObjectExist(bucket, packageListKey)) {
      val list = s3Client.getObjectAsString(bucket, packageListKey)
      removeNonPackageLines(list.split("\n").toList)
    } else List(unavailableText)
  }
}