package notification

import com.amazonaws.services.s3.AmazonS3
import play.api.libs.json._

object LambdaDistributionBucket {
  def updateBucketPolicy(s3Client: AmazonS3, bucketName: String, stage: String, accountNumbers: Seq[String]): Unit = {
    val policyText = Option(s3Client.getBucketPolicy(bucketName).getPolicyText).filter(_.nonEmpty)
    val copierStatement = LambdaDistributionBucket.createCopierStatement(bucketName, stage, accountNumbers)
    val newPolicy = LambdaDistributionBucket.updateCopierStatement(stage, policyText, copierStatement)
    s3Client.setBucketPolicy(bucketName, newPolicy)
  }

  /*
    Implement the policy grammar (https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_grammar.html)
   */
  case class Statement(
    Sid: Option[String],
    Effect: String,
    Principal: Option[JsValue] = None, NotPrincipal: Option[JsValue] = None,
    Action: Option[JsValue] = None, NotAction: Option[JsValue] = None,
    Resource: Option[JsValue] = None, NotResource: Option[JsValue] = None,
    Condition: Option[JsValue] = None)
  case class BucketPolicy(Id: Option[String], Version: Option[String], Statement: List[Statement])

  implicit val statementFormat: Format[Statement] = Json.format[Statement]
  implicit val bucketPolicyFormat: Format[BucketPolicy] = Json.format[BucketPolicy]

  def parsePolicyText(policyText: String): BucketPolicy = Json.fromJson[BucketPolicy](Json.parse(policyText)).get
  def createPolicyText(bucketPolicy: BucketPolicy): String = Json.toJson(bucketPolicy).toString

  def imageCopierDistributionSid(stage: String) = s"ImageCopierDistribution$stage"
  def createCopierStatement(bucketName: String, stage: String, accounts: Seq[String]): Statement = {
    Statement(
      Sid = Some(imageCopierDistributionSid(stage)),
      Effect = "Allow",
      Principal = Some(Json.obj(
        "AWS" -> accounts
      )),
      Action = Some(JsString("s3:GetObject")),
      Resource = Some(JsArray(Seq(
        JsString(s"arn:aws:s3:::$bucketName/deploy/$stage/imagecopier/*"),
        JsString(s"arn:aws:s3:::$bucketName/deploy/$stage/housekeeping-lambda/*")
      )))
    )
  }

  /*
   Add the provided statement, removing prior statement with the image copier SID
   */
  def updateCopierStatement(stage: String, maybeBucketPolicyText: Option[String], newStatement: Statement): String = {
    val bucketPolicy = maybeBucketPolicyText.map(parsePolicyText).getOrElse(BucketPolicy(None, None, Nil))
    val newPolicy = bucketPolicy.copy(
      Statement = newStatement :: bucketPolicy.Statement.filterNot(_.Sid.contains(imageCopierDistributionSid(stage)))
    )
    createPolicyText(newPolicy)
  }
}
