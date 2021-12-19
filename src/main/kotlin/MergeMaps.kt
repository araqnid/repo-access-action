package action

data class MergeOutput<out K, out V1 : Any, out V2 : Any>(val key: K, val left: V1?, val right: V2?)

fun <K : Comparable<K>, V1 : Any, V2 : Any> mergeMaps(
    left: Map<K, V1>,
    right: Map<K, V2>
): Sequence<MergeOutput<K, V1, V2>> {
    return sequence {
        val leftEntries = left.entries.sortedBy { it.key }.peekingIterator()
        val rightEntries = right.entries.sortedBy { it.key }.peekingIterator()

        while (leftEntries.hasNext() && rightEntries.hasNext()) {
            val n = leftEntries.peek().key.compareTo(rightEntries.peek().key)
            when {
                n < 0 -> {
                    val leftEntry = leftEntries.next()
                    yield(MergeOutput(leftEntry.key, leftEntry.value, null))
                }
                n > 0 -> {
                    val rightEntry = rightEntries.next()
                    yield(MergeOutput(rightEntry.key, null, rightEntry.value))
                }
                else -> {
                    val leftEntry = leftEntries.next()
                    val rightEntry = rightEntries.next()
                    yield(MergeOutput(leftEntry.key, leftEntry.value, rightEntry.value))
                }
            }
        }

        for (leftEntry in leftEntries) {
            yield(MergeOutput(leftEntry.key, leftEntry.value, null))
        }

        for (rightEntry in rightEntries) {
            yield(MergeOutput(rightEntry.key, null, rightEntry.value))
        }
    }
}

private fun <T> Iterable<T>.peekingIterator() = PeekingIterator(iterator())

private class PeekingIterator<T>(private val underlying: Iterator<T>) : Iterator<T> {
    private enum class State { EMPTY, FULL, FINISHED }

    private var state: State = State.EMPTY
    private var peekedValue: T? = null

    private fun fill() {
        if (state != State.EMPTY) return
        if (!underlying.hasNext()) {
            state = State.FINISHED
        } else {
            peekedValue = underlying.next()
            state = State.FULL
        }
    }

    override fun hasNext(): Boolean {
        fill()
        return state == State.FULL
    }

    override fun next(): T {
        val value = peek()
        state = State.EMPTY
        return value
    }

    fun peek(): T {
        fill()
        if (state == State.EMPTY) throw NoSuchElementException()
        return peekedValue.unsafeCast<T>()
    }
}
