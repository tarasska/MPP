import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SynchronousQueueMS<E> : SynchronousQueue<E> {
    private val head: AtomicRef<Segment<E>>
    private val tail: AtomicRef<Segment<E>>

    init {
        val firstNode = Segment<E>()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    override suspend fun send(element: E) {
        doAction(OperationType.SEND, element, { return@doAction })
    }

    override suspend fun receive(): E {
        return doAction(OperationType.RECEIVE, null, {next -> next.elem.value!!})
    }

    private suspend fun <T> doAction(type: OperationType, element: E?, retFunc: (Segment<E>) -> T): T {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            val segment = Segment(type, element)
            if (curHead == curTail || curTail.type.value == type) {
                val res = suspendCoroutine<OperationVerdict> sc@{ cont ->
                    segment.cont.value = cont
                    if (curTail.next.compareAndSet(null, segment)) {
                        tail.compareAndSet(curTail, segment)
                    } else {
                        tail.compareAndSet(curTail, curTail.next.value!!)
                        cont.resume(OperationVerdict.RETRY)
                        return@sc
                    }
                }
                when (res) {
                    OperationVerdict.RETRY   -> continue
                    OperationVerdict.SUCCESS -> return retFunc(segment)
                }
            } else {
                val curNext = curHead.next.value ?: continue
                if (curNext.cont.value != null && head.compareAndSet(curHead, curNext)) {
                    if (element != null) {
                        curNext.elem.value = element
                    }
                    curNext.cont.value!!.resume(OperationVerdict.SUCCESS)
                    return retFunc(curNext)
                }
            }
        }
    }
}

enum class OperationType {
    SEND,
    RECEIVE
}

enum class OperationVerdict {
    SUCCESS,
    RETRY
}

class Segment<E> {
    val next: AtomicRef<Segment<E>?> = atomic(null)
    val type: AtomicRef<OperationType?> = atomic(null)
    val cont: AtomicRef<Continuation<OperationVerdict>?> = atomic(null)
    val elem: AtomicRef<E?> = atomic(null)

    constructor() // for the first segment creation

    constructor(type: OperationType, elem: E?) {
        this.type.value = type
        this.elem.value = elem
    }
}

