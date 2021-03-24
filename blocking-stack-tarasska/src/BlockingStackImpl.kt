import kotlinx.atomicfu.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class BlockingStackImpl<E> : BlockingStack<E> {

    // ==========================
    // Segment Queue Synchronizer
    // ==========================

    private val enqIdx: AtomicRef<Segment<E>>
    private val deqIdx: AtomicRef<Segment<E>>

    init {
        val firstSegment = Segment<E>()
        enqIdx = atomic(firstSegment)
        deqIdx = atomic(firstSegment)
    }

    private suspend fun suspend(): E {
        return suspendCoroutine { continuation ->
            while (true) {
                val tail = deqIdx.value
                val segment = Segment(continuation)
                if (tail.next.compareAndSet(null, segment)) {
                    deqIdx.compareAndSet(tail, segment)
                    break
                }
            }
        }
    }

    private fun resume(element: E) {
        while (true) {
            val top = enqIdx.value
            val tail = deqIdx.value
            val next = top.next.value
            if (top != tail && next != null && enqIdx.compareAndSet(top, next)) {
                next.action?.resume(element)
                break
            }
        }
    }

    // ==============
    // Blocking Stack
    // ==============


    private val head = atomic<Node<E>?>(null)
    private val elements = atomic(0)

    override fun push(element: E) {
        val elements = this.elements.getAndIncrement()
        if (elements >= 0) {
            // push the element to the top of the stack
            while (true) {
                val curHead = head.value
                val isDone = when (curHead?.element) {
                    null -> head.compareAndSet(null, Node(element, null))
                    SUSPENDED -> {
                        val update = curHead.next
                        if (head.compareAndSet(curHead, update)) {
                            resume(element)
                            true
                        } else {
                            false
                        }
                    }
                    else -> head.compareAndSet(curHead, Node(element, curHead))
                }
                if (isDone) return
            }
        } else {
            // resume the next waiting receiver
            resume(element)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun pop(): E {
        val elements = this.elements.getAndDecrement()
        if (elements > 0) {
            // remove the top element from the stack
            while (true){
                return when (val curHead = head.value) {
                    null -> if (head.compareAndSet(curHead, Node(SUSPENDED, null))) suspend() else continue
                    else -> if (head.compareAndSet(curHead, curHead.next)) curHead.element as E else continue
                }
            }
        } else {
            return suspend()
        }
    }
}

private class Node<E>(val element: Any?, val next: Node<E>?)

private class Segment<E>(val action: Continuation<E>? = null, val next: AtomicRef<Segment<E>?> = atomic(null))

private val SUSPENDED = Any() //
