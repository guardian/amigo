package data

import models._

object BaseImages {

  private val images = Map(
    BaseImageId("ubuntu-wily") -> BaseImage(
      id = BaseImageId("ubuntu-wily"),
      description = "Ubuntu 15.10 (Wily) hvm:ebs release 20160204 eu-west-1",
      amiId = AmiId("ami-cda312be"),
      builtinRoles = Seq(CustomisedRole(RoleId("ubuntu-wily-init"), Map.empty))
    )
  )

  def list(): Iterable[BaseImage] = images.values

  def findById(id: BaseImageId): Option[BaseImage] = images.get(id)

}
