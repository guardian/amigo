package housekeeping

import com.amazonaws.services.ec2.model.Instance
import housekeeping.utils.{ BakesRepo, PackerEC2Client }
import models.{ Bake, BakeId, BakeStatus, RecipeId }
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ FlatSpec, Matchers }

class TimeOutLongRunningBakesSpec extends FlatSpec with Matchers with MockitoSugar {

  trait Mocks {
    val bakesRepo: BakesRepo = mock[BakesRepo]
    val packerEC2Client: PackerEC2Client = mock[PackerEC2Client]
    val housekeepingJob = new TimeOutLongRunningBakes(bakesRepo, packerEC2Client)
  }

  "getBakesToTimeOut" should "should retrieve all bakes from database and filter the result to include only bakes that need to be timed out" in new Mocks {

    val now = DateTime.now()

    when(bakesRepo.getBakes)
      .thenReturn(
        List(
          Bake.DbModel(
            recipeId = RecipeId("identity"),
            buildNumber = 1,
            status = BakeStatus.Running,
            amiId = None,
            startedBy = "amigo-test",
            startedAt = now.minusHours(2),
            deleted = None
          ),
          Bake.DbModel(
            recipeId = RecipeId("identity"),
            buildNumber = 3,
            status = BakeStatus.Running,
            amiId = None,
            startedBy = "amigo-test",
            startedAt = now.minusMinutes(30),
            deleted = None
          ),
          Bake.DbModel(
            recipeId = RecipeId("dev-x"),
            buildNumber = 1,
            status = BakeStatus.Complete,
            amiId = None,
            startedBy = "amigo-test",
            startedAt = now.minusMinutes(90),
            deleted = None
          ),
          Bake.DbModel(
            recipeId = RecipeId("dev-x"),
            buildNumber = 2,
            status = BakeStatus.Running,
            amiId = None,
            startedBy = "amigo-test",
            startedAt = now.minusMinutes(61),
            deleted = None
          )
        )
      )

    val bakesToTimeOut = housekeepingJob.getBakesToTimeOut(earliestStartedAt = now.minusHours(1))

    bakesToTimeOut shouldEqual List(
      Bake.DbModel(
        recipeId = RecipeId("identity"),
        buildNumber = 1,
        status = BakeStatus.Running,
        amiId = None,
        startedBy = "amigo-test",
        startedAt = now.minusHours(2),
        deleted = None
      ),
      Bake.DbModel(
        recipeId = RecipeId("dev-x"),
        buildNumber = 2,
        status = BakeStatus.Running,
        amiId = None,
        startedBy = "amigo-test",
        startedAt = now.minusMinutes(61),
        deleted = None
      )
    )
  }

  "runHouseKeeping" should "update the status to TimedOut and delete the respective EC2 instance for each bake running over an hour" in new Mocks {

    val now = DateTime.now()

    when(bakesRepo.getBakes)
      .thenReturn(
        List(
          Bake.DbModel(
            recipeId = RecipeId("identity"),
            buildNumber = 1,
            status = BakeStatus.Running,
            amiId = None,
            startedBy = "amigo-test",
            startedAt = now.minusHours(2),
            deleted = None
          ),
          Bake.DbModel(
            recipeId = RecipeId("identity"),
            buildNumber = 3,
            status = BakeStatus.Running,
            amiId = None,
            startedBy = "amigo-test",
            startedAt = now.minusMinutes(30),
            deleted = None
          )
        )
      )

    val overrunningBakeId: BakeId = BakeId(RecipeId("identity"), buildNumber = 1)
    val overrunningInstance: Instance = mock[Instance]
    when(overrunningInstance.getInstanceId).thenReturn("overrunning-instance-id")

    when(packerEC2Client.getBakeInstance(ArgumentMatchers.eq(overrunningBakeId)))
      .thenReturn(Some(overrunningInstance))

    when(packerEC2Client.getRunningPackerInstances()).thenReturn(List.empty)

    housekeepingJob.runHouseKeeping(earliestStartedAt = now.minusHours(1))

    // Check that we get overrunning instance and terminate it.
    verify(packerEC2Client).getBakeInstance(ArgumentMatchers.eq(overrunningBakeId))
    verify(packerEC2Client).terminateEC2Instance(ArgumentMatchers.eq("overrunning-instance-id"))

    // Check we don't terminate the instances that aren't overruning.
    verifyNoMoreInteractions(packerEC2Client)

    // Check we set the status of overrunning bakes to timed out
    verify(bakesRepo, times(1)).getBakes
    verify(bakesRepo, times(1))
      .updateStatusToTimedOutIfRunning(ArgumentMatchers.eq(BakeId(RecipeId("identity"), buildNumber = 1)))

    // Check that the status of bakes that aren't overruning aren't updated.
    verifyNoMoreInteractions(bakesRepo)
  }

  "runHouseKeeping" should "update the status to TimedOut of a bake running over an hour, even if the respective EC2 instance can't be found" in new Mocks {

    val now = DateTime.now()

    when(bakesRepo.getBakes)
      .thenReturn(
        List(
          Bake.DbModel(
            recipeId = RecipeId("identity"),
            buildNumber = 1,
            status = BakeStatus.Running,
            amiId = None,
            startedBy = "amigo-test",
            startedAt = now.minusHours(2),
            deleted = None
          )
        )
      )

    val overrunningBakeId: BakeId = BakeId(RecipeId("identity"), buildNumber = 1)

    // Simulate EC2 instance not being found.
    when(packerEC2Client.getBakeInstance(ArgumentMatchers.eq(overrunningBakeId)))
      .thenReturn(None)

    housekeepingJob.runHouseKeeping(earliestStartedAt = now.minusHours(1))

    // Check that we get overrunning instance and terminate it.
    verify(packerEC2Client).getBakeInstance(ArgumentMatchers.eq(overrunningBakeId))

    // Check we still set the status of overrunning bakes to timed out.
    verify(bakesRepo, times(1)).getBakes
    verify(bakesRepo, times(1))
      .updateStatusToTimedOutIfRunning(ArgumentMatchers.eq(BakeId(RecipeId("identity"), buildNumber = 1)))
  }
}
