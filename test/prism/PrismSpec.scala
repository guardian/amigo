package prism

import org.scalatest.{ FlatSpec, Matchers }
import play.api.libs.json.Json

class PrismSpec extends FlatSpec with Matchers {

  it should "extract AWS account numbers from a Prism JSON response" in {
    val json = Json.parse(
      """
        |{
        |  "status": "success",
        |  "lastUpdated": "2016-05-16T11:34:54.935Z",
        |  "stale": false,
        |  "staleSources": [],
        |  "data": [{
        |    "resource": "instance",
        |    "origin": {
        |      "accountName": "foo",
        |      "region": "eu-west-1",
        |      "accountNumber": "1234",
        |      "vendor": "aws",
        |      "credentials": "***"
        |    },
        |    "status": "success",
        |    "state": {
        |      "status": "success",
        |      "createdAt": "2016-05-16T11:35:12.573Z",
        |      "itemCount": 1,
        |      "age": 41,
        |      "stale": false
        |    }
        |  }, {
        |    "resource": "instance",
        |    "origin": {
        |      "accountName": "bar",
        |      "region": "eu-west-1",
        |      "accountNumber": "5678",
        |      "vendor": "aws",
        |      "credentials": "***"
        |    },
        |    "status": "success",
        |    "state": {
        |      "status": "success",
        |      "createdAt": "2016-05-16T11:35:15.493Z",
        |      "itemCount": 0,
        |      "age": 38,
        |      "stale": false
        |    }
        |  }]
        |}
      """.stripMargin
    )
    Prism.extractAccountNumbers(json) should be(Some(Seq("1234", "5678")))
  }

}
