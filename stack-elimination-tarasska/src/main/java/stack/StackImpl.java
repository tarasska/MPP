package stack;

import kotlinx.atomicfu.AtomicInt;
import kotlinx.atomicfu.AtomicRef;

import java.util.Arrays;
import java.util.Random;


public class StackImpl implements Stack {
    private static class Node {
        final AtomicRef<Node> next;
        final int x;

        Node(int x, Node next) {
            this.next = new AtomicRef<>(next);
            this.x = x;
        }
    }

    private static class Cell {
        public enum State {
            None,
            Setting,
            Wait,
            InProgress,
            Done
        }

        AtomicRef<State> state;
        AtomicInt value;

        public Cell() {
            state = new AtomicRef<>(State.None);
            value = new AtomicInt(Integer.MIN_VALUE);
        }
    }


    private final int ELIMINATION_ARRAY_SIZE = 20;
    private final int ELIMINATION_ATTEMPTS = 3;
    private final long WAITING_TIME_NS = 100;
    // head pointer
    private AtomicRef<Node> head = new AtomicRef<>(null);
    private final Cell[] eliminationArray = new Cell[ELIMINATION_ARRAY_SIZE];
    private final Random random = new Random();

    public StackImpl() {
        Arrays.fill(eliminationArray, new Cell());
    }

    private boolean fastPush(int x) {
        int ind = random.nextInt(ELIMINATION_ARRAY_SIZE);
        int attempts = ELIMINATION_ATTEMPTS;
        while (attempts > 0) {
            if (eliminationArray[ind].state.compareAndSet(Cell.State.None, Cell.State.Setting)) {
                eliminationArray[ind].value.setValue(x);
                eliminationArray[ind].state.setValue(Cell.State.Wait);
                long timeBound = System.nanoTime() + WAITING_TIME_NS;
                while (true) {
                    switch (eliminationArray[ind].state.getValue()) {
                        case Wait: {
                            if (System.nanoTime() > timeBound) {
                                if (eliminationArray[ind].state.compareAndSet(Cell.State.Wait, Cell.State.None)) {
                                    return false;
                                }
                            }
                            break;
                        }
                        case Done: {
                            eliminationArray[ind].state.setValue(Cell.State.None);
                            return true;
                        }
                    }
                }
            }

            attempts--;
            ind = (ind + 1) % ELIMINATION_ARRAY_SIZE;
        }
        return false;
    }

    private Integer fastPop() {
        int ind = random.nextInt(ELIMINATION_ARRAY_SIZE);
        int attempts = ELIMINATION_ATTEMPTS;
        while (attempts > 0) {
            if (eliminationArray[ind].state.compareAndSet(Cell.State.Wait, Cell.State.InProgress)) {
                int res = eliminationArray[ind].value.getValue();
                eliminationArray[ind].state.setValue(Cell.State.Done);
                return res;
            }

            attempts--;
            ind = (ind + 1) % ELIMINATION_ARRAY_SIZE;
        }
        return null;
    }

    @Override
    public void push(int x) {
        //head.setValue(new Node(x, head.getValue()));
        while (true) {
            if (fastPush(x)) {
                return;
            }
            Node oldHeadNode = head.getValue();
            Node newHeadNode = new Node(x, oldHeadNode);
            if (head.compareAndSet(oldHeadNode, newHeadNode)) {
                return;
            }
        }
    }

    @Override
    public int pop() {
//        Node curHead = head.getValue();
//        if (curHead == null) return Integer.MIN_VALUE;
//        head.setValue(curHead.next.getValue());
//        return curHead.x;
        while (true) {
            Integer fastResult = fastPop();
            if (fastResult != null) {
                return fastResult;
            }

            Node oldHeadNode = head.getValue();

            if (oldHeadNode == null) {
                return Integer.MIN_VALUE;
            }

            if (head.compareAndSet(oldHeadNode, oldHeadNode.next.getValue())) {
                return oldHeadNode.x;
            }
        }
    }
}
