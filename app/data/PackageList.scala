package data

import com.amazonaws.services.s3.AmazonS3
import models.BakeId
import services.Loggable

import scala.util.control.NonFatal

object PackageList extends Loggable {

  val unavailable: List[String] = List("Package list unavailable")

  def removeNonPackageLines(packages: List[String]): List[String] = {
    packages.filter { p =>
      !(p.contains("Listing") || p.contains("Installed Packages") || p.contains("Loaded plugins"))
    }
  }

  def getPackageList(s3Client: AmazonS3, bakeId: BakeId, bucket: String): List[String] = {
    val packageListKey = s"packagelists/${BakeId.toFilename(bakeId)}"
    try {
      if (s3Client.doesObjectExist(bucket, packageListKey)) {
        val list = s3Client.getObjectAsString(bucket, packageListKey)
        removeNonPackageLines(list.split("\n").toList)
      } else unavailable
    } catch {
      case NonFatal(e) =>
        log.warn(s"Failed to fetch package list from S3 bucket $bucket, key $packageListKey", e)
        unavailable
    }


  }
}