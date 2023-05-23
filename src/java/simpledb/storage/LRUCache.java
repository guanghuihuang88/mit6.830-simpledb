package simpledb.storage;

import simpledb.common.DbException;

import java.util.HashMap;
import java.util.Iterator;

public class LRUCache<K, V> {

    private final HashMap<K, DLinkedNode> cache = new HashMap<>();

    private final int capacity;

    private int size;

    private final DLinkedNode head;

    private final DLinkedNode tail;

    class DLinkedNode {
        K key;
        V value;
        DLinkedNode prev;
        DLinkedNode next;

        DLinkedNode() {}

        DLinkedNode(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    public LRUCache(int capacity) {
        this.size = 0;
        this.capacity = capacity;
        this.head = new DLinkedNode();
        this.tail = new DLinkedNode();
        head.next = tail;
        head.prev = tail;
        tail.prev = head;
        tail.next = head;
    }

    public int getSize() {
        return size;
    }

    public int getCapacity() {
        return capacity;
    }

    public void put(K key, V value) {
        DLinkedNode node = new DLinkedNode(key, value);
        cache.put(key, node);
        if (size < capacity) {
            DLinkedNode last = tail.prev;
            last.next = node;
            node.next = tail;
            node.prev = last;
            tail.prev = node;
            size++;
        } else {
            DLinkedNode eliminateNode = tail.prev;
            node.next = eliminateNode.next;
            node.prev = eliminateNode.prev;
            tail.prev = node;
            eliminateNode.prev.next = node;
            cache.remove(eliminateNode.key);
        }
    }

    public V get(K key) {
        DLinkedNode node = cache.get(key);
        if (node != null) {
            // 将node移动到头部
            node.prev.next = node.next;
            node.next.prev = node.prev;
            node.next = head.next;
            node.prev = head;
            head.next.prev = node;
            head.next = node;

            return node.value;
        } else {
            return null;
        }
    }

    public void remove(K key) throws DbException {
        DLinkedNode node = cache.get(key);
        if (node != null) {
            // 将node删除
            cache.remove(key);
            node.prev.next = node.next;
            node.next.prev = node.prev;
            size--;
        } else {
            throw new DbException("page is not exist!");
        }
    }

    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }

    public Iterator<K> iterator() {
        return cache.keySet().iterator();
    }

    public DLinkedNode evictNode() throws DbException {
        DLinkedNode node = tail.prev;
        remove(node.key);
        return node;
    }

}
