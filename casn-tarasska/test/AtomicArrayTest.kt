import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.paramgen.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.jetbrains.kotlinx.lincheck.verifier.*
import org.junit.*

@Param.Params(
    Param(name = "index", gen = IntGen::class, conf = "0:4"),
    Param(name = "value", gen = IntGen::class, conf = "1:3")
)
class AtomicArrayTest {
    private val a = AtomicArray<Int>(5)

    @Operation(params = ["index"])
    fun get(index: Int) =
        a.get(index)

    @Operation(params = ["index", "value"])
    fun set(index: Int, value: Int) =
        a.set(index, value)

    @Operation(params = ["index", "value", "value"])
    fun cas(index: Int, expected: Int, update: Int) =
        a.cas(index, expected, update)

    @Operation(params = ["index", "value", "value",
                         "index", "value", "value"])
    fun cas2(index1: Int, expected1: Int, update1: Int,
             index2: Int, expected2: Int, update2: Int) =
        a.cas2(index1, expected1, update1, index2, expected2, update2)

    @Test
    fun stressTest() = StressOptions()
        .iterations(100)
        .invocationsPerIteration(50_000)
        .actorsBefore(0)
        .actorsAfter(0)
        .threads(3)
        .actorsPerThread(3)
        .sequentialSpecification(AtomicArrayIntSequential::class.java)
        .check(this::class.java)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions()
        .iterations(100)
        .invocationsPerIteration(50_000)
        .actorsBefore(0)
        .actorsAfter(0)
        .threads(3)
        .actorsPerThread(3)
        .sequentialSpecification(AtomicArrayIntSequential::class.java)
        .checkObstructionFreedom(true)
        .check(this::class.java)
}

class AtomicArrayIntSequential : VerifierState() {
    private val a = arrayOfNulls<Int>(5)

    fun get(index: Int): Int? = a[index]

    fun set(index: Int, value: Int) {
        a[index] = value
    }

    fun cas(index: Int, expected: Int, update: Int): Boolean {
        if (a[index] != expected) return false
        a[index] = update
        return true
    }

    fun cas2(index1: Int, expected1: Int, update1: Int,
             index2: Int, expected2: Int, update2: Int): Boolean {
        if (a[index1] != expected1 || a[index2] != expected2) return false
        a[index1] = update1
        a[index2] = update2
        return true
    }

    override fun extractState() = a.toList()
}