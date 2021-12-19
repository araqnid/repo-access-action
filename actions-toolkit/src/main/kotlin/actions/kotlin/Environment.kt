package actions.kotlin

import kotlin.reflect.KProperty

@JsModule("process")
private external object Process {
    val env: dynamic
}

private object ExpectedEnvironment {
    operator fun getValue(owner: Any?, property: KProperty<*>): String {
        val name = property.name
        return Process.env[name].unsafeCast<String?>() ?: error("$name not set")
    }
}

private object OptionalEnvironment {
    operator fun getValue(owner: Any?, property: KProperty<*>): String? {
        val name = property.name
        return Process.env[name].unsafeCast<String?>()
    }
}

// see https://docs.github.com/en/actions/learn-github-actions/environment-variables
/** The name of the workflow. */
val GITHUB_WORKFLOW by ExpectedEnvironment

/** A unique number for each run within a repository. This number does not change if you re-run the workflow run. */
val GITHUB_RUN_ID by ExpectedEnvironment

/** A unique number for each run of a particular workflow in a repository. This number begins at 1 for the workflow's first run, and increments with each new run. This number does not change if you re-run the workflow run. */
val GITHUB_RUN_NUMBER by ExpectedEnvironment

/** The [https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions#jobsjob_id][job_id] of the current job. */
val GITHUB_JOB by ExpectedEnvironment

/** The unique identifier (id) of the action */
val GITHUB_ACTION by ExpectedEnvironment

/** The path where your action is located. You can use this path to access files located in the same repository as your action. This variable is only supported in composite actions. */
val GITHUB_ACTION_PATH by ExpectedEnvironment

/** Always set to true when GitHub Actions is running the workflow. You can use this variable to differentiate when tests are being run locally or by GitHub Actions. */
val GITHUB_ACTIONS by OptionalEnvironment

/** The name of the person or app that initiated the workflow. For example, octocat. */
val GITHUB_ACTOR by ExpectedEnvironment

/** The owner and repository name. For example, `octocat/Hello-World`. */
val GITHUB_REPOSITORY by ExpectedEnvironment

/** The name of the webhook event that triggered the workflow. */
val GITHUB_EVENT_NAME by ExpectedEnvironment

/** The path of the file with the complete webhook event payload. For example, `/github/workflow/event.json`. */
val GITHUB_EVENT_PATH by ExpectedEnvironment

/** The GitHub workspace directory path, initially empty. For example, `/home/runner/work/my-repo-name/my-repo-name`. The actions/checkout action will check out files, by default a copy of your repository, within this directory. */
val GITHUB_WORKSPACE by ExpectedEnvironment

/** The commit SHA that triggered the workflow. For example, `ffac537e6cbbf934b08745a378932722df287a53`. */
val GITHUB_SHA by ExpectedEnvironment

/** The branch or tag ref that triggered the workflow. For example, `refs/heads/feature-branch-1`. If neither a branch or tag is available for the event type, the variable will not exist. */
val GITHUB_REF by OptionalEnvironment

/** The branch or tag name that triggered the workflow run. */
val GITHUB_REF_NAME by OptionalEnvironment

/** `true` if branch protections are configured for the ref that triggered the workflow run. */
val GITHUB_REF_PROTECTED by OptionalEnvironment

/** The type of ref that triggered the workflow run. Valid values are branch or tag. */
val GITHUB_REF_TYPE by OptionalEnvironment

/** The name of the head branch. Only set for pull request events. */
val GITHUB_HEAD_REF by OptionalEnvironment

/** The name of the base branch. Only set for pull request events. */
val GITHUB_BASE_REF by OptionalEnvironment

/** The URL of the GitHub server. For example: `https://github.com` */
val GITHUB_SERVER_URL by ExpectedEnvironment

/** The API URL of the GitHub server. For example: `https://api.github.com`. */
val GITHUB_API_URL by ExpectedEnvironment

/** The GraphQL API URL of the Github server. For example: `https://api.github.com/graphql`. */
val GITHUB_GRAPHQL_URL by ExpectedEnvironment

/** The name of the runner executing the job. */
val RUNNER_NAME by ExpectedEnvironment

/** The operating system of the runner executing the job. Possible values are `Linux`, `Windows`, or `macOS`. */
val RUNNER_OS by ExpectedEnvironment

/** The architecture of the runner executing the job. Possible values are `X86`, `X64`, `ARM`, and `ARM64`. */
val RUNNER_ARCH by ExpectedEnvironment

/** The path to a temporary directory on the runner. This directory is emptied at the beginning and end of each job. Note that files will not be removed if the runner's user account does not have permission to delete them. */
val RUNNER_TEMP by ExpectedEnvironment

/** The path to the directory containing preinstalled tools for GitHub-hosted runners. For more information, see [https://docs.github.com/en/actions/reference/specifications-for-github-hosted-runners/#supported-software][Specifications for GitHub-hosted runners]. */
val RUNNER_TOOL_CACHE by ExpectedEnvironment
