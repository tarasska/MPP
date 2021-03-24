import Core.PushState.*
import kotlinx.atomicfu.*

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<Holder<E>>(INITIAL_CAPACITY))
    private val extended: AtomicRef<Core<Holder<E>>?> = atomic(null)

    private fun moveIter(): Boolean {
        var isAllMoved = true
        for (i in 0 until size) {
            val cell = core.value[i]
            if (cell is Active && core.value.compareAndPut(i, cell, Moving())) {
                extended.value?.set(i, Active(cell.get()))
                core.value[i] = Moved()
            }
            core.value.compareAndPut(i, null, Moved())

            isAllMoved = isAllMoved && core.value[i] is Moved
        }
        return isAllMoved
    }

    override fun get(index: Int): E {
        if (index >= size || index < 0) {
            throw IllegalArgumentException("Incorrect index")
        }
        while (true) {
            val curNode = core.value[index]
            val box = if (curNode is Moved || curNode is Moving) {
                extended.value?.get(index)
            } else {
                curNode
            }
            if (box is Active) {
                return box.get()
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index >= size || index < 0) {
            throw IllegalArgumentException("Incorrect index")
        }
        while (true) {
            val cur = core.value[index]
            if (cur is Active) {
                if (core.value.compareAndPut(index, cur, Active(element))) {
                    return
                }
            } else {
                val otherCell = extended.value?.get(index)
                if (otherCell is Active) {
                    val res = extended.value?.compareAndPut(index, otherCell, Active(element));
                    if (res != null && res) {
                        return
                    }
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            when (core.value.pushBack(Active(element))) {
                Visitor -> return
                Worker  -> continue
                Owner   -> {
                    extended.getAndSet(Core(core.value.capacity * 2, size))
                    while (!moveIter()) {}
                    extended.value?.let { core.getAndSet(it) }
                }
            }
        }
    }

    override val size: Int get() {
        return core.value.size;
    }
}

private open class Holder<E>

private class Moved<E> : Holder<E>()

private class Moving<E> : Holder<E>()

private class Active<E> (
    private val value: E
) : Holder<E>() {

    fun get(): E {
        return value
    }
}

private class Core<E>(
    val capacity: Int
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val realSize: AtomicInt = atomic(0)
    private val sealed: AtomicBoolean = atomic(false)

    enum class PushState {
        Owner,
        Worker,
        Visitor
    }

    constructor(capa: Int, sz: Int) : this(capa) {
        realSize.getAndSet(sz)
    }

    operator fun get(ind: Int): E? {
        return array[ind].value
    }

    fun compareAndPut(ind: Int, old: E?, new: E): Boolean {
        return array[ind].compareAndSet(old, new)
    }

    operator fun set(ind: Int, element: E) {
        array[ind].getAndSet(element)
    }

    fun pushBack(element: E): PushState {
        while (true) {
            val curSize = size
            if (curSize < capacity) {
                if (compareAndPut(curSize, null, element)) {
                    realSize.incrementAndGet()
                    return Visitor
                }
            } else {
                return if (sealed.compareAndSet(expect = false, update = true)) {
                    Owner
                } else {
                    Worker
                }
            }
        }
    }

    val size: Int get() {
        return realSize.value
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME