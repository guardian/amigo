package data

import com.amazonaws.services.s3.AmazonS3
import models.BakeId
import models.BakeId.toFilename
import services.Loggable

import scala.util.control.NonFatal

object PackageList extends Loggable {

  val s3PathSlug = "packagelists"
  def s3Path(bakeId: BakeId) = s"$s3PathSlug/${toFilename(bakeId)}"
  def s3Url(bakeId: BakeId, bucket: String) = s"s3://$bucket/${s3Path(bakeId)}"

  def removeNonPackageLines(packages: List[String]): List[String] = {
    packages.filter { p =>
      !(p.contains("Listing") || p.contains("Installed Packages") || p.contains("Loaded plugins"))
    }
  }

  def getPackageList(s3Client: AmazonS3, bakeId: BakeId, bucket: String): List[String] = {
    val packageListKey = s3Path(bakeId)
    try {
      val list = s3Client.getObjectAsString(bucket, packageListKey)
      removeNonPackageLines(list.split("\n").toList)
    } catch {
      case NonFatal(e) =>
        val message = s"Failed to fetch package list from S3 bucket $bucket, key $packageListKey. Has the bake finished?"
        log.warn(message, e)
        List(message)
    }

  }
}