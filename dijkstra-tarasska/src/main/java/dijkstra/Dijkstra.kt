package dijkstra

import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.Comparator
import kotlin.concurrent.thread

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> Integer.compare(o1!!.distance, o2!!.distance) }

// Returns `Integer.MAX_VALUE` if a path has not been found.
fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()
    // The distance to the start node is `0`
    start.distance = 0
    // Create a priority (by distance) queue and add the start node into it
    val q = MultiQueue(workers, NODE_DISTANCE_COMPARATOR)
    q.add(start)
    // Run worker threads and wait until the total work is done
    val onFinish = Phaser(workers + 1) // `arrive()` should be invoked at the end by each worker
    val activeNodes = AtomicInteger(1)
    repeat(workers) {
        thread {
            while (true) {
                val cur: Node? = q.poll()
                if (cur == null) {
                    if (activeNodes.get() == 0) break else continue
                }
                for (e in cur.outgoingEdges) {
                    while (true) {
                        val to = e.to.distance
                        val from = cur.distance
                        if (to > from + e.weight) {
                            if (e.to.casDistance(to, from + e.weight)) {
                                if (q.add(e.to)) {
                                    activeNodes.getAndIncrement()
                                }
                                break
                            }
                        } else {
                            break
                        }
                    }
                }
                activeNodes.getAndDecrement()
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}

class MultiQueue(initCapacity: Int, comp: Comparator<Node>) {
    private val QUEUES_SIZE : Int = 100
    private var queues : Array<PriorityQueue<Node>>
    private var lockers : Array<ReentrantLock>
    private var random : Random
    private var comparator : Comparator<Node>

    init {
        this.queues = Array(QUEUES_SIZE) {PriorityQueue(initCapacity, comp)}
        this.lockers = Array(QUEUES_SIZE) {ReentrantLock()}
        this.random = Random()
        this.comparator = comp
    }

    fun poll() : Node? {
        while (true) {
            val i1 = random.nextInt(QUEUES_SIZE)
            val i2 = random.nextInt(QUEUES_SIZE)
            if (lockers[i1].tryLock()) {
                try {
                    if (lockers[i2].tryLock()) {
                        try {
                            if (queues[i1].size == 0 && queues[i2].size == 0) {
                                return null
                            }
                            if (queues[i1].size == 0) {
                                return queues[i2].poll()
                            } else if (queues[i2].size == 0) {
                                return queues[i1].poll()
                            }
                            return if (comparator.compare(queues[i1].peek(), queues[i2].peek()) < 0) {
                                queues[i1].poll()
                            } else {
                                queues[i2].poll()
                            }
                        } finally {
                            lockers[i2].unlock()
                        }
                    }
                } finally {
                    lockers[i1].unlock()
                }
            }
        }
    }

    fun add(v : Node) : Boolean {
        while (true) {
            val i = random.nextInt(QUEUES_SIZE)
            if (lockers[i].tryLock()) {
                try {
                    return queues[i].add(v)
                } finally {
                    lockers[i].unlock()
                }
            }
        }
    }
}