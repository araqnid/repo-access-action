package action

import github.Github

private fun composeMessage(parts: Array<out Any>): String = parts.joinToString(": ") {
    when (it) {
        is Github.Repository -> "repo \"${it.owner.login}/${it.name}\""
        is Github.Environment -> "environment \"${it.name}\""
        is Github.Team -> "team \"${it.slug}\""
        is Github.Organization -> "org \"${it.name}\""
        is Github.Branch -> "branch \"${it.name}\""
        is Github.Artifact -> "artifact \"${it.name}\""
        is Github.User -> "user \"${it.login}\""
        else -> it.toString()
    }
}

fun debug(vararg parts: Any) {
    actions.core.debug(composeMessage(parts))
}

fun info(vararg parts: Any) {
    actions.core.info(composeMessage(parts))
}

fun notice(vararg parts: Any) {
    actions.core.notice(composeMessage(parts))
}

fun warning(vararg parts: Any) {
    actions.core.warning(composeMessage(parts))
}

fun error(vararg parts: Any) {
    actions.core.error(composeMessage(parts))
}
