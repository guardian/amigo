package data

import models.{ BaseImageId, AmiId, RoleId, BaseImage }

import scala.concurrent.Future

object BaseImages {

  private val images = Map(
    BaseImageId("ubuntu-wily") -> BaseImage(
      id = BaseImageId("ubuntu-wily"),
      description = "Ubuntu 15.10 (Wily) hvm:ebs release 20160204 eu-west-1",
      amiId = AmiId("ami-cda312be"),
      builtinRoles = Seq(RoleId("ubuntu-wily-init"))
    )
  )

  def list(): Future[Iterable[BaseImage]] = Future.successful(images.values)

  def findById(id: BaseImageId): Future[Option[BaseImage]] = Future.successful(images.get(id))

}
