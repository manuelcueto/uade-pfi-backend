package unit

import org.cueto.pfi.domain.Template
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TemplateSpec extends AnyWordSpec with Matchers {

  "specialized" should {
    "replace a template with provided values" in {
      val userName   = "manu"
      val userId     = 1
      val campaignId = 2
      val expectedResponse =
        s"""<img src="http://localhost:9999/api/events/pixel/$campaignId/$userId/pixel.png" alt="img" /> $userName sos un puto"""
      val template = Template(
        1,
        "template",
        "subject",
        """<img src="http://localhost:9999/api/events/pixel/{{campaignId}}/{{userId}}/pixel.png" alt="img" /> {{nombre}} sos un puto"""
      )

      template.specialized(userName, userId, campaignId) shouldBe expectedResponse
    }
  }

}
