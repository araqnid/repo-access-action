package action

import org.araqnid.kotlin.assertthat.assertThat
import org.araqnid.kotlin.assertthat.equalTo
import kotlin.test.Test

class MergeMapsTest {
    @Test
    fun merges_two_maps() {
        val left = mapOf("team-A" to "pull", "team-B" to "pull", "team-D" to "push", "team-E" to "pull")
        val right = mapOf("team-A" to "pull", "team-C" to "push", "team-D" to "push", "team-F" to "pull")
        val results =
            mergeMaps(left, right)
                .map { (key, leftValue, rightValue) -> "$key:${leftValue ?: ""}:${rightValue ?: ""}" }
                .toList()
        assertThat(
            results,
            equalTo(
                listOf(
                    "team-A:pull:pull",
                    "team-B:pull:",
                    "team-C::push",
                    "team-D:push:push",
                    "team-E:pull:",
                    "team-F::pull"
                )
            )
        )
    }
}
