import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val BUFFER_SIZE = 4
    private val q = PriorityQueue<E>()
    private val lock = AtomicBoolLock()
    private val waitArray = EliminationArray<E>(BUFFER_SIZE)

    private fun processConveyor() {
        for (i in 0 until BUFFER_SIZE) {
            val cell = waitArray[i]
            if (cell.state.value == State.NONE) {
                continue
            }

            when (cell.state.value) {
                State.ADD -> {
                    q.add(cell.element.value)
                }
                State.PEEK -> {
                    cell.element.value = q.peek()
                }
                State.POLL -> {
                    cell.element.value = q.poll()
                }
                else -> break
            }

            cell.state.value = State.DONE
        }
    }

    private fun runWaitingMod(state: State, elem: E?): E? {
        var index = 0
        while (!waitArray[index].state.compareAndSet(State.NONE, State.SETTING)) {
            index = (index + 1) % BUFFER_SIZE
        }
        waitArray[index].element.value = elem
        waitArray[index].state.value = state

        while (waitArray[index].state.value != State.DONE) {
            if (lock.tryLock()) {
                processConveyor()
                lock.unlock()
            }
        }
        val res = waitArray[index].element.value
        waitArray[index].state.value = State.NONE
        return res
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? = if (lock.tryLock()) {
        val res = q.poll()
        lock.unlock()
        res
    } else {
        runWaitingMod(State.POLL, null)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? = if (lock.tryLock()) {
        val res = q.peek()
        lock.unlock()
        res
    } else {
        runWaitingMod(State.PEEK, null)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (lock.tryLock()) {
            q.add(element)
            lock.unlock()
        } else {
            runWaitingMod(State.ADD, element)
        }
    }
}

private class AtomicBoolLock {
    private val lock = atomic(false)

    fun tryLock(): Boolean {
        return lock.compareAndSet(expect = false, update = true)
    }

    fun unlock() {
        lock.value = false
    }
}

private enum class State {
    NONE,
    SETTING,
    ADD,
    PEEK,
    POLL,
    DONE
}

private class EliminationArray<E>(
    val size: Int,
) {
    val array: Array<Cell<E>> = Array(size) { Cell() }

    operator fun get(i: Int): Cell<E> {
        return array[i]
    }

    operator fun set(i: Int, elem: Cell<E>) {
        array[i] = elem
    }
}

private class Cell<E>{
    val state: AtomicRef<State> = atomic(State.NONE)
    val element: AtomicRef<E?> = atomic(null)
}
