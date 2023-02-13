package notification

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LambdaDistributionBucketSpec extends AnyFlatSpec with Matchers {
  import LambdaDistributionBucket._
  "updateCopierStatement" should "replace the previous statement" in {
    val copierStatement = createCopierStatement("bucket", "TEST", List("234"))
    val bucketPolicy =
      BucketPolicy(Some("test"), Some("testing"), List(copierStatement))
    val newCopierStatement =
      createCopierStatement("bucket", "TEST", List("234", "456"))
    LambdaDistributionBucket.updateCopierStatement(
      "TEST",
      Some(createPolicyText(bucketPolicy)),
      newCopierStatement
    ) shouldBe
      createPolicyText(
        BucketPolicy(Some("test"), Some("testing"), List(newCopierStatement))
      )
  }

  it should "add statement if it isn't there" in {
    val bucketPolicy = BucketPolicy(Some("test"), Some("testing"), List.empty)
    val newCopierStatement = LambdaDistributionBucket.createCopierStatement(
      "bucket",
      "TEST",
      List("234", "456")
    )
    LambdaDistributionBucket.updateCopierStatement(
      "TEST",
      Some(createPolicyText(bucketPolicy)),
      newCopierStatement
    ) shouldBe
      createPolicyText(
        BucketPolicy(Some("test"), Some("testing"), List(newCopierStatement))
      )
  }

  it should "create policy if there isn't one" in {
    val bucketPolicy = BucketPolicy(Some("test"), Some("testing"), List.empty)
    val newCopierStatement = LambdaDistributionBucket.createCopierStatement(
      "bucket",
      "TEST",
      List("234", "456")
    )
    LambdaDistributionBucket.updateCopierStatement(
      "TEST",
      None,
      newCopierStatement
    ) shouldBe
      createPolicyText(BucketPolicy(None, None, List(newCopierStatement)))
  }
}
