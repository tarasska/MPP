package msqueue;

import kotlinx.atomicfu.AtomicRef;

public class MSQueue implements Queue {
    private AtomicRef<Node> head;
    private AtomicRef<Node> tail;

    public MSQueue() {
        Node dummy = new Node(0);
        this.head = new AtomicRef<>(dummy);
        this.tail = new AtomicRef<>(dummy);
    }

    @Override
    public void enqueue(int x) {
        Node newTail = new Node(x);
        while (true) {
            Node curTail = tail.getValue();
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail);
                return;
            } else {
                tail.compareAndSet(curTail, tail.getValue().next.getValue());
            }
        }
    }

    @Override
    public int dequeue() {
        while (true) {
            Node curHead = head.getValue();
            Node curTail = tail.getValue();
            Node firstNode = head.getValue().next.getValue();
            if (curHead == curTail) {
                if (firstNode == null) {
                    return Integer.MIN_VALUE;
                }
                if (tail.compareAndSet(curTail, curTail.next.getValue())) {
                    if (head.compareAndSet(curHead, firstNode)) {
                        return firstNode.x;
                    }
                }
            } else {
                if (head.compareAndSet(curHead, firstNode)) {
                    return firstNode.x;
                }
            }
        }
    }

    @Override
    public int peek() {
        Node curHead = head.getValue();
        Node curTail = tail.getValue();
        Node next = curHead.next.getValue();
        if (curHead == curTail) {
            return Integer.MIN_VALUE;
        }
        return next.x;
    }

    private class Node {
        final int x;
        AtomicRef<Node> next;

        Node(int x) {
            this.x = x;
            next = new AtomicRef<>(null);
        }
    }
}