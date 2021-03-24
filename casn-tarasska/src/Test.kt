import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.AtomicRef
import java.util.concurrent.atomic.AtomicReference

class Box(val x: Int = 0);

fun main(args: Array<String>) {
    val value = Box()
    val val2  = Box(2)
    val one = AtomicReference(value)
    val two = AtomicReference(value)
    println(one.compareAndSet(value, val2))
    println(two.get().x)
}