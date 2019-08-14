package housekeeping

import com.amazonaws.services.ec2.model.Instance
import housekeeping.utils.{ BakesRepo, PackerEC2Client }
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ FlatSpec, Matchers }

class DeleteLongRunningEC2InstancesSpec extends FlatSpec with Matchers with MockitoSugar {

  trait Mocks {
    val bakesRepo: BakesRepo = mock[BakesRepo]
    val packerEC2Client: PackerEC2Client = mock[PackerEC2Client]
    val housekeepingJob = new DeleteLongRunningEC2Instances(bakesRepo, packerEC2Client)
  }

  "getBakeIdFromInstance" should "successfully parse a bake id from a tag" in {

  }

  "runHouseKeeping" should "terminate all instances running for over an hour" in new Mocks {

    val now = DateTime.now()

    val longRunningInstance1 = mock[Instance]
    when(longRunningInstance1.getLaunchTime).thenReturn(now.minusHours(2).toDate)
    when(longRunningInstance1.getInstanceId).thenReturn("lr1")

    val longRunningInstance2 = mock[Instance]
    when(longRunningInstance2.getLaunchTime).thenReturn(now.minusMinutes(61).toDate)
    when(longRunningInstance2.getInstanceId).thenReturn("lr2")

    val instance = mock[Instance]
    when(instance.getLaunchTime).thenReturn(now.minusMinutes(24).toDate)
    when(instance.getInstanceId).thenReturn("r1")

    when(packerEC2Client.getRunningPackerInstances()).thenReturn(
      List(longRunningInstance1, longRunningInstance2, instance)
    )

    housekeepingJob.runHouseKeeping(now.minusHours(1))

    verify(packerEC2Client, times(1)).terminateEC2Instance(ArgumentMatchers.eq("lr1"))
    verify(packerEC2Client, times(1)).terminateEC2Instance(ArgumentMatchers.eq("lr2"))
    verify(packerEC2Client, times(0)).terminateEC2Instance(ArgumentMatchers.eq("r1"))
  }
}
