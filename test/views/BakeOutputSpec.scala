package views

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BakeOutputSpec extends AnyFlatSpec with Matchers {
  "bakeOutput" should "escape log content without generated default imports" in {
    html.fragments
      .bakeOutput(List("<script>unsafe</script>"))
      .body should include("&lt;script&gt;unsafe&lt;/script&gt;")
  }
}
