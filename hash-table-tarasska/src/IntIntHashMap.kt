import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.lang.IllegalStateException

/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
 *
 */
class IntIntHashMap {
    private val core = atomic(Core(INITIAL_CAPACITY))

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        while (true) {
            val actualCore = core.value
            when (val res = actualCore.getInternal(key)) {
                TRY_AGAIN -> continue
                else -> return toValue(res)
            }
        }
    }

    /**
     * Changes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key   a positive key.
     * @param value a positive value.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key or value are not positive, or value is equal to
     * [Integer.MAX_VALUE] which is reserved.
     */
    fun put(key: Int, value: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        require(isValue(value)) { "Invalid value: $value" }
        return toValue(putAndRehashWhileNeeded(key, value))
    }

    /**
     * Removes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key a positive key.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    fun remove(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE))
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
        while (true) {
            val actualCore = core.value
            when (val oldValue = actualCore.putInternal(key, value)) {
                NEEDS_REHASH -> core.compareAndSet(actualCore, actualCore.rehash())
                else -> return oldValue
            }
        }
    }

    private class Core internal constructor(val capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val doubleCapacity = capacity * 2
        val map = AtomicIntArray(doubleCapacity)
        val shift: Int
        val next: AtomicRef<Core?> = atomic(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) {
                val (curKey, curVal) = getEntry(index)

                when (curKey) {
                    NULL_KEY -> return if (curVal == MOVED_VALUE) TRY_AGAIN else NULL_VALUE
                    key -> return when (curVal) {
                        MOVED_VALUE -> TRY_AGAIN
                        setMovingState(curVal) -> unsetMovingState(curVal)
                        else -> curVal
                    }
                    else -> {
                        if (++probes >= MAX_PROBES) return NULL_VALUE
                        index = nextIndInLoop(index)
                    }
                }
            }
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) {
                val (curKey, curVal) = getEntry(index)

                when (curKey) {
                    NULL_KEY -> {
                        if (isMoving(curVal)) return NEEDS_REHASH
                        if (curVal == DEL_VALUE) return NULL_VALUE
                        if (map[index].compareAndSet(curKey, key)
                                && map[index + 1].compareAndSet(curVal, value)) return curVal
                    }
                    key -> {
                        if (isMoving(curVal)) return NEEDS_REHASH
                        if (map[index + 1].compareAndSet(curVal, value)) return curVal
                    }
                    else -> {
                        if (++probes >= MAX_PROBES) return NEEDS_REHASH
                        index = nextIndInLoop(index)
                    }
                }
            }
        }

        fun rehash(): Core {
            if (next.value == null) {
                next.compareAndSet(null, Core(capacity * 2))
            } // map.length is twice the current capacity
            val newCore = next.value!!
            var index = 0
            while (index < doubleCapacity) {
                val (curKey, curVal) = getEntry(index)
                if (curVal != MOVED_VALUE) {
                    if (isMoving(curVal)) {
                        val clearVal = unsetMovingState(curVal)
                        if (isValue(clearVal)) {
                            if (!newCore.copy(curKey, clearVal)) {
                                throw IllegalStateException("Unable to extend hashtable")
                            }
                            map[index + 1].value = MOVED_VALUE
                        }
                    } else {
                        map[index + 1].compareAndSet(curVal, setMovingState(curVal))
                        continue
                    }
                }
                index += 2
            }
            return newCore
        }

        fun copy(key: Int, value: Int): Boolean {
            var probes = 0
            var index = index(key)
            while (true) {
                val (curKey, _) = getEntry(index)

                when (curKey) {
                    NULL_KEY -> if (map[index].compareAndSet(curKey, key)) {
                        // either I or someone has already helped, the result is not important
                        map[index + 1].compareAndSet(NULL_VALUE, value)
                        return true
                    }
                    key -> {
                        // either I or someone has already helped, the result is not important
                        map[index + 1].compareAndSet(NULL_VALUE, value)
                        return true
                    }
                    else -> {
                        if (++probes >= MAX_PROBES) return false
                        index = nextIndInLoop(index)
                    }
                }
            }
        }

        fun getEntry(index: Int): Pair<Int, Int> {
            val value = map[index + 1].value
            return map[index].value to value
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2

        fun nextIndInLoop(ind: Int): Int = (ind + 2) % doubleCapacity
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val MOVED_VALUE = Int.MIN_VALUE // mark for moved value
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed
private const val TRY_AGAIN = -2 // if unable to get the correct result from the current table, need to check next

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0

// Check move-bit
private fun isMoving(value: Int): Boolean = (value and MOVED_VALUE) != 0

// Set move-bit
private fun setMovingState(value: Int): Int = value or MOVED_VALUE

// Unset move-bit
private fun unsetMovingState(value: Int): Int = value and MOVED_VALUE.inv()
