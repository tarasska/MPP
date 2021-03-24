import kotlinx.atomicfu.*
import sun.security.krb5.internal.crypto.Des

class AtomicArray<E>(size: Int) {
    private abstract inner class Descriptor {
        abstract fun complete()
    }

    @Suppress("UNCHECKED_CAST")
    private inner class Ref<T>(init: T) {
        val ref = atomic<Any?>(init)

        var value: T
            get() {
                while (true) {
                    when (val cur = ref.value) {
                        is AtomicArray<*>.Descriptor -> cur.complete()
                        else -> return cur as T
                    }
                }
            }
            set(upd) {
                while (true) {
                    when (val cur = ref.value) {
                        is AtomicArray<*>.Descriptor -> cur.complete()
                        else -> if (ref.compareAndSet(cur, upd)) return
                    }
                }
            }
    }

    private inner class RDCSSDescriptor<A, B>(
        val a: Ref<A>, val expectA: A, val updateA: A,
        val b: Ref<B>, val expectB: B
    ) : Descriptor() {
        override fun complete() {
            val update = if (b.value === expectB) {
                updateA
            } else {
                expectA
            }
            a.ref.compareAndSet(this, update)
        }

    }


    private val a = atomicArrayOfNulls<E>(size)

    fun get(index: Int) =
        a[index].value

    fun set(index: Int, value: E) {
        a[index].value = value
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // todo this implementation is not linearizable,
        // todo a multi-word CAS algorithm should be used here.
        if (a[index1].value != expected1 || a[index2].value != expected2) return false
        a[index1].value = update1
        a[index2].value = update2
        return true
    }
}