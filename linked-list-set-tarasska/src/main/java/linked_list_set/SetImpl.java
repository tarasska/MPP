package linked_list_set;

import kotlinx.atomicfu.AtomicRef;


public class SetImpl implements Set {
    private abstract static class SuperNode {
        final AtomicRef<SuperNode> next;
        final int key;

        public SuperNode(int key, SuperNode next) {
            this.next = new AtomicRef<>(next);
            this.key = key;
        }

        protected abstract boolean isRemoved();

        public SuperNode get(boolean[] removed) {
            removed[0] = isRemoved();
            if (removed[0]) {
                return next.getValue();
            } else {
                return this;
            }
        }
    }

    private static class Removed extends SuperNode {
        Removed(SuperNode node) {
            super(node.key, node);
        }

        @Override
        protected boolean isRemoved() {
            return true;
        }
    }

    private static class Node extends SuperNode {
        Node(int key, SuperNode next) {
            super(key, next);
        }

        @Override
        protected boolean isRemoved() {
            return false;
        }
    }

    private static class Window {
        Node cur, next;

        Window(Node cur, Node next) {
            this.cur = cur;
            this.next = next;
        }
    }

    private static class SuperWindow {
        SuperNode cur, next;

        SuperWindow(SuperNode cur, SuperNode next) {
            this.cur = cur;
            this.next = next;
        }
    }

    private final Node head = new Node(Integer.MIN_VALUE, new Node(Integer.MAX_VALUE, null));

    private SuperWindow deleteOrMove(SuperWindow w) {
        boolean[] removed = new boolean[1];
        SuperNode node = w.next.next.getValue().get(removed);
        if (removed[0]) {
            if (w.cur.next.compareAndSet(w.next, node)) {
                w.next = node;
            } else {
                return null;
            }
        } else {
            w.cur = w.next;
            w.next = node;
        }
        return w;
    }
    /**
     * Returns the {@link Window}, where cur.key < key <= next.key
     */
    private Window findWindow(int key) {
        mainLoop:
        while (true) {
            SuperNode tmp = head;
            SuperWindow w = new SuperWindow(tmp, tmp.next.getValue());
            while (w.next.key < key) {
                w = deleteOrMove(w);
                if (w == null) {
                    continue mainLoop;
                }
            }
            if (w.next.next.getValue() instanceof Removed) {
                deleteOrMove(w);
            } else {
                return new Window((Node) w.cur, (Node) w.next);
            }
        }
    }

    @Override
    public boolean add(int key) {
        while (true) {
            Window w = findWindow(key);
            if (w.next.key == key) {
                return false;
            }
            Node node = new Node(key, w.next);
            if (w.cur.next.compareAndSet(w.next, node)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(int key) {
        while (true) {
            Window w = findWindow(key);
            if (w.next.key != key) {
                return false;
            }
            SuperNode node = w.next.next.getValue();
            if (node instanceof Node && w.next.next.compareAndSet(node, new Removed(node))) {
                w.cur.next.compareAndSet(w.next, node);
                return true;
            }
        }
    }

    @Override
    public boolean contains(int key) {
        Window w = findWindow(key);
        return w.next.key == key;
    }


}