package data

import software.amazon.awssdk.services.s3.S3Client
import models.BakeId
import models.BakeId.toFilename
import models.{Bake, BakeId}
import services.Loggable
import fun.mike.dmp.{Diff, DiffMatchPatch}

import scala.jdk.CollectionConverters._
import scala.collection.{immutable, mutable}
import scala.util.control.NonFatal

case class PackageListDiff(
    previousBakeId: BakeId,
    removedPackages: List[String],
    newPackages: List[String],
    diff: List[Diff]
)

object PackageList extends Loggable {

  val s3PathSlug = "packagelists"
  def s3Path(bakeId: BakeId) = s"$s3PathSlug/${toFilename(bakeId)}"
  def s3Url(bakeId: BakeId, bucket: String) = s"s3://$bucket/${s3Path(bakeId)}"

  def removeNonPackageLines(packages: List[String]): List[String] = {
    packages.filter { p =>
      !(p.contains("Listing") || p.contains("Installed Packages") || p.contains(
        "Loaded plugins"
      ))
    }
  }

  def getPackageList(
      s3Client: S3Client,
      bakeId: BakeId,
      bucket: Option[String]
  ): Either[String, List[String]] = {
    val maybePackageList: Option[Either[String, List[String]]] = bucket.map {
      b =>
        val packageListKey = s3Path(bakeId)
        try {
          val response = s3Client.getObjectAsBytes(builder => builder.bucket(b).key(packageListKey))
          val list = new String(response.asByteArray())
          Right(removeNonPackageLines(list.split("\n").toList))
        } catch {
          case NonFatal(e) =>
            val message =
              s"Failed to fetch package list from S3 bucket $bucket, key $packageListKey. Has the bake finished?"
            log.warn(message, e)
            Left(message)
        }
    }
    maybePackageList.getOrElse(
      Left("Amigo data bucket not defined: can't fetch package list")
    )
  }

  def diffPackageLists(
      newPackageList: List[String],
      oldPackageList: List[String],
      previousBakeId: BakeId
  ): PackageListDiff = {
    val removedPackages =
      oldPackageList.filter(op => !newPackageList.contains(op))
    val newPackages = newPackageList.filter(np => !oldPackageList.contains(np))

    val dmp = new DiffMatchPatch()
    val diff =
      dmp.diff_main(removedPackages.mkString("\n"), newPackages.mkString("\n"))
    dmp.diffCleanupSemantic(diff)
    dmp.Diff_EditCost = 6
    dmp.diff_cleanupEfficiency(diff)
    diff.asScala.foreach(d => d.text = d.text.replace("\n", "<br />"))
    val scalaDiff: List[Diff] = diff.asScala.toList

    PackageListDiff(previousBakeId, removedPackages, newPackages, scalaDiff)
  }

  def getPackageListDiff(
      s3Client: S3Client,
      newPackageList: List[String],
      previousBakeId: Option[BakeId],
      bucket: Option[String]
  ): Either[String, PackageListDiff] = {
    previousBakeId.map { pid =>
      val oldPackageList = getPackageList(s3Client, pid, bucket)
      for {
        oldList <- oldPackageList
      } yield diffPackageLists(newPackageList, oldList, pid)
    }
  }.getOrElse(Left("No previous bake to diff with"))
}
