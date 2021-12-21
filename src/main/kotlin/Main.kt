package action

import actions.kotlin.getInput
import actions.kotlin.runAction
import github.Github
import github.useGithub
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
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
            ?: kotlin.error("Main team \"$mainTeamName\" not found in org \"$orgName\"")

            val resolvedAccessConfig = accessConfig.mapValues { (repoName, repoAccessConfig) ->
                repoAccessConfig.teams.mapKeys { (teamName, _) ->
                    val team = teams.find { it.slug == teamName } ?: teams.find { it.name == teamName }
                    ?: kotlin.error("Team \"$teamName\" for repo \"$repoName\" not found in org \"$orgName\"")
                    team.slug
                }
            }

            val seenRepos = mutableSetOf<String>()
            var errorsSeen = 0
            fun contributeError(vararg parts: Any) {
                ++errorsSeen
                error(*parts)
            }
            github.getOrgTeamRepos(org, mainTeam)
                .filter { it.permissions.admin }
                .filterNot { it.isArchived }
                .collect { repo ->
                    seenRepos += repo.name
                    val repoAccessConfig = resolvedAccessConfig[repo.name]
                    if (repoAccessConfig == null) {
                        contributeError(repo, "Team has admin access to repo, but there is no config for it")
                        return@collect
                    }
                    debug(repo, "accessConfig=$repoAccessConfig")
                    try {
                        val repoTeams = github.getRepoTeams(repo).toList()
                        for (command in syncRepoAccess(repo, repoTeams, mainTeam, repoAccessConfig)) {
                            when (command) {
                                is RepoCommand.RemoveTeam ->
                                    warning(repo, command.team, "TODO: Revoking team access from repo")
                                is RepoCommand.SetTeamPermission ->
                                    warning(repo, command.team, "TODO: Setting access to ${command.accessType}")
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
                kotlin.error("Encountered $errorsSeen error(s), see above")
            }
        }
    }
}

private sealed interface RepoCommand {
    data class RemoveTeam(val team: Github.Team) : RepoCommand
    data class SetTeamPermission(val team: Github.Team, val accessType: AccessType) : RepoCommand
}

private fun syncRepoAccess(
    repo: Github.Repository,
    repoTeams: Collection<Github.Team>,
    mainTeam: Github.Team,
    repoAccessConfig: Map<String, AccessType>
): Sequence<RepoCommand> {
    return sequence {
        val currentPermissionByTeam = repoTeams.filterNot { it.slug == mainTeam.slug }
            .associate { team -> team.slug to team.permission.toAccessType() }
        for ((teamName, currentAccess, wantedAccess) in mergeMaps(currentPermissionByTeam, repoAccessConfig)) {
            val team = repoTeams.single { it.slug == teamName }

            when {
                wantedAccess == AccessType.ADMIN ->
                    warning(repo, team, "Additional team has admin access- resolve by completing transfer")
                currentAccess == wantedAccess ->
                    info(repo, team, "$currentAccess permission unchanged")
                wantedAccess == null ->
                    yield(RepoCommand.RemoveTeam(team))
                else ->
                    yield(RepoCommand.SetTeamPermission(team, wantedAccess))
            }
        }
    }
}
