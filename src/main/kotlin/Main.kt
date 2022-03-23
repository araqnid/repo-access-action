package action

import actions.kotlin.getInput
import actions.kotlin.runAction
import github.Github
import github.useGithub
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

fun main() {
    runAction {
        val accessFileName = getInput("accessFile", required = true)
        val orgName = getInput("org", required = true)
        val mainTeamName = getInput("team", required = true)
        val accessConfig = invertAccessConfig(readAccessConfigFile(accessFileName))
        useGithub { github ->
            val org = github.getOrganization(orgName)
            val teams = github.getOrgTeams(org).toList()
            val mainTeam = teams.find { it.slug == mainTeamName } ?: teams.find { it.name == mainTeamName }
            ?: error("Main team \"$mainTeamName\" not found in org \"$orgName\"")

            val resolvedAccessConfig = accessConfig.mapValues { (repoName, repoAccessConfig) ->
                repoAccessConfig.teams.mapKeys { (teamName, _) ->
                    val team = teams.find { it.slug == teamName } ?: teams.find { it.name == teamName }
                    ?: error("Team \"$teamName\" for repo \"$repoName\" not found in org \"$orgName\"")
                    team.slug
                }
            }

            val seenRepos = mutableSetOf<String>()
            var errorsSeen = 0
            fun contributeError(vararg parts: Any) {
                ++errorsSeen
                GithubMessages.error(*parts)
            }
            github.getOrgTeamRepos(org, mainTeam)
                .filter { it.permissions.admin }
                .collectConcurrently(4) { repo ->
                    seenRepos += repo.name
                    val repoAccessConfig = resolvedAccessConfig[repo.name]
                    if (repo.isArchived) {
                        if (repoAccessConfig != null) {
                            GithubMessages.warning(repo, "Archived repo is still configured")
                        }
                        return@collectConcurrently
                    }
                    if (repoAccessConfig == null) {
                        contributeError(repo, "Team has admin access to repo, but there is no config for it")
                        return@collectConcurrently
                    }
                    GithubMessages.debug(repo, "accessConfig=$repoAccessConfig")
                    try {
                        val repoTeams = github.getRepoTeams(repo).toList()
                        for (command in syncRepoAccess(teams, repo, repoTeams, mainTeam, repoAccessConfig)) {
                            when (command) {
                                is RepoCommand.RemoveTeam -> {
                                    GithubMessages.notice(repo, command.team, "Removing permission")
                                    github.deleteRepoTeamPermission(org, command.team, repo)
                                }
                                is RepoCommand.SetTeamPermission -> {
                                    GithubMessages.notice(repo, command.team, "Updating permission to ${command.accessType}")
                                    github.updateRepoTeamPermission(org, command.team, repo, command.accessType.githubName)
                                }
                            }
                        }
                    } catch (ex: Throwable) {
                        contributeError(repo, ex.toString())
                    }
                }

            for (configuredRepoName in accessConfig.keys) {
                if (configuredRepoName !in seenRepos) {
                    contributeError(
                        org,
                        mainTeam,
                        "Config mentions repo \"$configuredRepoName\", but team does not have admin access"
                    )
                }
            }

            if (errorsSeen > 0) {
                error("Encountered $errorsSeen error(s), see above")
            }
        }
    }
}

@OptIn(FlowPreview::class)
private suspend fun <T> Flow<T>.collectConcurrently(concurrency: Int, target: suspend (T) -> Unit) {
    flatMapMerge(concurrency = concurrency) { item ->
        flow<Nothing> {
            target(item)
        }
    }.collect()
}

private sealed interface RepoCommand {
    data class RemoveTeam(val team: Github.Team) : RepoCommand
    data class SetTeamPermission(val team: Github.Team, val accessType: AccessType) : RepoCommand
}

private fun syncRepoAccess(
    orgTeams: Collection<Github.Team>,
    repo: Github.Repository,
    repoTeams: Collection<Github.Team>,
    mainTeam: Github.Team,
    repoAccessConfig: Map<String, AccessType>
): Sequence<RepoCommand> {
    return sequence {
        val currentPermissionByTeam = repoTeams.filterNot { it.slug == mainTeam.slug }
            .associate { team -> team.slug to team.permission.toAccessType() }
        for ((teamName, currentAccess, wantedAccess) in mergeMaps(currentPermissionByTeam, repoAccessConfig)) {
            val team = orgTeams.single { it.slug == teamName }

            when {
                wantedAccess == AccessType.ADMIN ->
                    GithubMessages.warning(repo, team, "Additional team has admin access- resolve by completing transfer")
                currentAccess == wantedAccess ->
                    GithubMessages.info(repo, team, "$currentAccess permission unchanged")
                wantedAccess == null ->
                    yield(RepoCommand.RemoveTeam(team))
                else ->
                    yield(RepoCommand.SetTeamPermission(team, wantedAccess))
            }
        }
    }
}
