import java.lang.ThreadLocal.withInitial

/**
 * @author :Скаженик Тарас
 */
class Solution: AtomicCounter {
    // объявите здесь нужные вам поля
    private val initNodeValue: Node = Node(0)
    private val initIntValue: Int = 0

    private val oldValue = withInitial{ initIntValue }
    private val curNode = withInitial{ initNodeValue } // doesn't work if use withInitial{ Node(0) } ???
    private val decisionNode = withInitial{ initNodeValue }


    private fun isCurNodeChosen(): Boolean {
        return decisionNode.get() == curNode.get()
    }

    private fun makeCandidate(delta: Int): Node {
        oldValue.set(decisionNode.get().value)
        curNode.set(Node(oldValue.get() + delta))
        return curNode.get()
    }

    override fun getAndAdd(x: Int): Int {
        do {
            decisionNode.set(decisionNode.get().judge.decide(makeCandidate(x)))
        } while (!isCurNodeChosen())
        return oldValue.get()
    }

    // вам наверняка потребуется дополнительный класс
    class Node(val value: Int) {
        val judge: Consensus<Node> = Consensus()
    }
}