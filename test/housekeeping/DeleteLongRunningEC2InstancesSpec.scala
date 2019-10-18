package housekeeping

import com.amazonaws.services.ec2.model.{ Instance, Tag }
import housekeeping.utils.{ BakesRepo, PackerEC2Client }
import models.{ BakeId, RecipeId }
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ FlatSpec, Matchers, OptionValues }

import scala.collection.JavaConversions._

class DeleteLongRunningEC2InstancesSpec extends FlatSpec with Matchers with MockitoSugar with OptionValues {

  trait Mocks {
    val bakesRepo: BakesRepo = mock[BakesRepo]
    val packerEC2Client: PackerEC2Client = mock[PackerEC2Client]
    val housekeepingJob = new DeleteLongRunningEC2Instances(bakesRepo, packerEC2Client)
  }

  "getBakeIdFromInstance" should "successfully parse a bake id from a tag" in {
    val instance = mock[Instance]
    when(instance.getTags).thenReturn(List(new Tag("BakeId", "recipe #2")))
    DeleteLongRunningEC2Instances.getBakeIdFromInstance(instance).value shouldEqual BakeId(RecipeId("recipe"), 2)
  }

  "runHouseKeeping" should "terminate all instances running for over an hour" in new Mocks {

    val now = DateTime.now()

    val longRunningInstance1 = mock[Instance]
    when(longRunningInstance1.getLaunchTime).thenReturn(now.minusHours(2).toDate)
    when(longRunningInstance1.getInstanceId).thenReturn("1")

    val longRunningInstance2 = mock[Instance]
    when(longRunningInstance2.getLaunchTime).thenReturn(now.minusMinutes(61).toDate)
    when(longRunningInstance2.getInstanceId).thenReturn("2")

    val instance = mock[Instance]
    when(instance.getLaunchTime).thenReturn(now.minusMinutes(24).toDate)
    when(instance.getInstanceId).thenReturn("3")

    when(packerEC2Client.getRunningPackerInstances()).thenReturn(
      List(longRunningInstance1, longRunningInstance2, instance)
    )

    housekeepingJob.runHouseKeeping(now.minusHours(1))

    // Check tha requests were made to delete long running instances.
    verify(packerEC2Client, times(1)).terminateEC2Instance(ArgumentMatchers.eq("1"))
    verify(packerEC2Client, times(1)).terminateEC2Instance(ArgumentMatchers.eq("2"))

    // Check that a request wasn't made to delete the instance which isn't considered as long running.
    verify(packerEC2Client, times(0)).terminateEC2Instance(ArgumentMatchers.eq("3"))
  }
}
