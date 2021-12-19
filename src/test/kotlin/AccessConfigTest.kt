@file:OptIn(DelicateCoroutinesApi::class)

package action

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.araqnid.kotlin.assertthat.assertThat
import org.araqnid.kotlin.assertthat.containsSubstring
import org.araqnid.kotlin.assertthat.equalTo
import kotlin.test.Test
import kotlin.test.assertFails

// CWD: $project/build/js/packages/repo-access-action-=test
private const val EXAMPLE_FILE_NAME = "../../../../src/test/resources/example_access.json"

class AccessConfigTest {
    @Test
    fun reads_example_file() = GlobalScope.promise {
        val accessConfig = readAccessConfigFile(EXAMPLE_FILE_NAME)
        val invertedAccessConfig = invertAccessConfig(accessConfig)
        assertThat(
            invertedAccessConfig["infralogic-auto-pipeline-lambda"],
            equalTo(RepoAccessConfig(teams = mapOf("All" to AccessType.PULL)))
        )
    }

    @Test
    fun traps_repo_listed_twice_in_access_file() {
        val ex = assertFails {
            invertAccessConfig(
                listOf(
                    RepositoryGroup(
                        description = "Group 1",
                        teams = mapOf(),
                        repos = setOf("repo-A", "repo-B")
                    ),
                    RepositoryGroup(
                        description = "Group 2",
                        teams = mapOf(),
                        repos = setOf("repo-A", "repo-C")
                    )
                )
            )
        }
        assertThat(ex.message.toString(), containsSubstring("repo-A"))
    }

    @Test
    fun traps_team_access_unrecognised() {
        val ex = assertFails {
            invertAccessConfig(
                listOf(
                    RepositoryGroup(
                        description = "Group 1",
                        teams = mapOf("All" to "whatever"),
                        repos = setOf("repo-A", "repo-B")
                    ),
                )
            )
        }
        assertThat(ex.message.toString(), containsSubstring("whatever"))
        assertThat(ex.message.toString(), containsSubstring("All"))
        assertThat(ex.message.toString(), containsSubstring("Group 1"))
    }
}
