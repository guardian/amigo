package housekeeping

import software.amazon.awssdk.services.ec2.model.{Instance, Tag}
import housekeeping.utils.{BakesRepo, PackerEC2Client}
import models.{BakeId, RecipeId}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.OptionValues
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._

class DeleteLongRunningEC2InstancesSpec
    extends AnyFlatSpec
    with Matchers
    with MockitoSugar
    with OptionValues {

  trait Mocks {
    val bakesRepo: BakesRepo = mock[BakesRepo]
    val packerEC2Client: PackerEC2Client = mock[PackerEC2Client]
    val housekeepingJob =
      new DeleteLongRunningEC2Instances(bakesRepo, packerEC2Client)
  }

  "getBakeIdFromInstance" should "successfully parse a bake id from a tag" in {
    val instance = mock[Instance]
    when(instance.tags()).thenReturn(
      List(Tag.builder().key("BakeId").value("recipe #2").build()).asJava
    )
    DeleteLongRunningEC2Instances
      .getBakeIdFromInstance(instance)
      .value shouldEqual BakeId(RecipeId("recipe"), 2)
  }

  "runHouseKeeping" should "terminate all instances running for over an hour" in new Mocks {

    val now = DateTime.now()

    val longRunningInstance1 = mock[Instance]
    when(longRunningInstance1.launchTime()).thenReturn(
      now.minusHours(2).toDate.toInstant
    )
    when(longRunningInstance1.instanceId()).thenReturn("1")

    val longRunningInstance2 = mock[Instance]
    when(longRunningInstance2.launchTime()).thenReturn(
      now.minusMinutes(61).toDate.toInstant
    )
    when(longRunningInstance2.instanceId()).thenReturn("2")

    val instance = mock[Instance]
    when(instance.launchTime())
      .thenReturn(now.minusMinutes(24).toDate.toInstant)
    when(instance.instanceId()).thenReturn("3")

    when(packerEC2Client.getRunningPackerInstances())
      .thenReturn(List(longRunningInstance1, longRunningInstance2, instance))

    housekeepingJob.runHouseKeeping(now.minusHours(1))

    // Check tha requests were made to delete long running instances.
    verify(packerEC2Client, times(1)).terminateEC2Instance(
      ArgumentMatchers.eq("1")
    )
    verify(packerEC2Client, times(1)).terminateEC2Instance(
      ArgumentMatchers.eq("2")
    )

    // Check that a request wasn't made to delete the instance which isn't considered as long running.
    verify(packerEC2Client, times(0)).terminateEC2Instance(
      ArgumentMatchers.eq("3")
    )
  }
}
