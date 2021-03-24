import kotlin.concurrent.thread

fun main(args: Array<String>) {
    val q = FCPriorityQueue<Int>()
    print(q.peek())
    q.add(1)
    print(q.poll())
    print(q.poll())
}