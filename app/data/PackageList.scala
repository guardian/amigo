package data

import com.amazonaws.services.s3.AmazonS3
import models.BakeId
import services.Loggable

import scala.util.control.NonFatal

object PackageList extends Loggable {

  def removeNonPackageLines(packages: List[String]): List[String] = {
    packages.filter { p =>
      !(p.contains("Listing") || p.contains("Installed Packages") || p.contains("Loaded plugins"))
    }
  }

  def getPackageList(s3Client: AmazonS3, bakeId: BakeId, bucket: String): List[String] = {
    val packageListKey = s"packagelists/${BakeId.toFilename(bakeId)}"
    try {
        val list = s3Client.getObjectAsString(bucket, packageListKey)
        removeNonPackageLines(list.split("\n").toList)
    } catch {
      case NonFatal(e) =>
        val message = s"Failed to fetch package list from S3 bucket $bucket, key $packageListKey"
        log.warn(message, e)
        List(message)
    }


  }
}