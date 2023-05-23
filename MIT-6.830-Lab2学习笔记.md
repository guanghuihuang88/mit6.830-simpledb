

# MIT-6.830-Lab2

>  SimpleDB Lab2 概述：
>
> 1. 实现`Filter` 和 `Join` operators
> 2. 实现 `IntegerAggregator` 和 `StringAggregator`
> 3. 实现 `Aggregate` operator
> 4. 在 BufferPool 中实现与元组插入、删除和页面逐出相关的方法
> 5. 实现`Insert`和`Delete` operators
>
> 这些操作符的 Javadoc 注释包含有关它们应该如何工作的详细信息。项目中提供了`Project`和`OrderBy`算子的实现，有助于了解其他算子的工作方式
>
> 容易看出这些算子本质上是一个迭代器，都有`open`、`close`、`rewind`、`fetchNext`方法。需要注意的是，它们都扩展了`Operator`类，而不是实现`OpIterator`接口。因为`next`/`hasNext`的实现通常是重复的、烦人的和容易出错的，所以`Operator`一般地实现这个逻辑，只需要实现一个更简单的`fetchNext`方法。可以随意使用这种类型的实现，如果您愿意，也可以实现`OpIterator`接口。要实现`OpIterator`接口，请从迭代器类中删除`extends Operator`，取而代之的是`implements OpIteror`

### Exercise1: Filter & Join

- `Filter`：用于过滤满足给定 predicate 的那些元组
- `Join`：根据给定的 JoinPredicate 聚合来自它两个子 operator 的元组

<img src="https://guanghuihuang-1315055500.cos.ap-guangzhou.myqcloud.com/%E5%AD%A6%E4%B9%A0%E7%AC%94%E8%AE%B0/%E6%95%B0%E6%8D%AE%E5%BA%93/mit6.830/02.png" alt="image" style="zoom:90%;" />

Filter 算子很简单，`fetchNext`方法的关键是调用 Predicate 的`filter`方法对遍历的每一个元组进行过滤

Join 算子相对较复杂，本质思想可以理解为两层for循环，遍历每一对元组，然后调用 JoinPredicate 的`filter`方法对遍历的每一对元组进行过滤。但具体实现需要根据`Operator`类中`hasNext`和`next`方法的实现机制灵活处理

```java
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        // some code goes here
        if (childTuple1 == null) {
            if (child1.hasNext()) {
                childTuple1 = child1.next();
            } else {
              	// 全部的元组对都遍历完毕
                return null;
            }
        }

        if (childTuple2 == null) {
            if (child2.hasNext()) {
                childTuple2 = child2.next();
            } else {
                childTuple1 = null;		// 当前tuple1已经和全部tuple2匹配完毕
                child2.rewind();			// 初始化child2迭代器
                return fetchNext();		// 递归
            }
        }

        if (joinPredicate.filter(childTuple1, childTuple2)) {
            // 满足过滤条件则合并两个tuple并返回
          	...
            return res;
        } else {
            childTuple2 = null;
            return fetchNext();
        }
    }
```

### Exercise2: Aggregates

使用 GROUP BY 子句可以实现基本的 SQL 聚合操作。本节我们实现5个 SQL Aggregates 算子（`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`）并支持 GROUP BY 子句，仅考虑单个分组列和聚合列的情况

<img src="https://guanghuihuang-1315055500.cos.ap-guangzhou.myqcloud.com/%E5%AD%A6%E4%B9%A0%E7%AC%94%E8%AE%B0/%E6%95%B0%E6%8D%AE%E5%BA%93/mit6.830/03.png" alt="image" style="zoom:90%;" />

容易知道，上一节中的 Filter 和 Join 算子都采用 Valcano 执行模式，流式处理每一个元组。但是，Aggregates 算子需要输出的聚合结果是通过所有元组同时作为输入计算得到的，因此这类算子又称为堵塞算子(blocking operator)，即需要将所有的元组暂存至内存缓冲区。阻塞算子通常会有OOM问题（如何解决堵塞算子的 OOM 问题呢？这就需要这些算子能够在处理的过程中把中间的结果集(intermediate result)暂存到文件系统中(spill to disk)，这是题外话，不做过多赘述）

由于需要一个内存缓冲区，因此 Aggregate 算子的实现需要封装一个 Aggregator 容器来保存聚合结果。根据聚合数据类型的不同，本实验实现两种 Aggregator：`StringAggregator`和`IntegerAggregator`

与非阻塞算子不同的是，阻塞算子需要在`open`方法将所有元组存放到内存缓冲区的。具体到 Aggregate 算子，它将所有元组作为参数调用`Aggregator.mergeTupleIntoGroup`方法分组存放在一个`Map<Field, ArrayList<Field>> map`中

```java
public void open() throws NoSuchElementException, DbException,
        TransactionAbortedException {
    // some code goes here
    super.open();
    child.open();
    while (child.hasNext()){
        Tuple next = child.next();
        aggr.mergeTupleIntoGroup(next);
    }
    aggrItr.open();
}
```

而计算则发生在`fetchNext`方法中，它通过调用 Aggregator 迭代器的`next`方法，获取每一个 group 的聚合结果；根据 Op 聚合函数的不同，需要在`next`方法中定制化实现各个函数的计算逻辑，以`max`方法为例：

```java
public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
  Tuple res = new Tuple(this.getTupleDesc());
  curs++;
  // ------------------------------------------------------------MAX
  if (what == Aggregator.Op.MAX) {
    if (gbfield == -1) {
      IntField max = (IntField) iter.next();
      while (iter.hasNext()) {
        IntField curValue = (IntField) iter.next();
        if (max.getValue() < curValue.getValue()) {
          max = curValue;
        }
      }
      res.setField(0, max);
    } else {
      Field gbKey = (Field) iter.next();
      Iterator<Field> aggValues = map.get(gbKey).iterator();

      IntField max = (IntField) aggValues.next();
      while (aggValues.hasNext()) {
        IntField curValue = (IntField) aggValues.next();
        if (max.getValue() < curValue.getValue()) {
          max = curValue;
        }
      }
      res.setField(0, gbKey);
      res.setField(1, max);
    }
    return res;
  }
  return null;
}
```

### Exercise3: Insert and delete

> 本节实验的报告我将以我自己的思路顺序撰写，合并了实验讲义中的2.3和2.4，采用自顶向下的顺序梳理整个实现流程

主要有两种算子来实现表的修改：增加元组`Insert`和删除元组`Delete`

- 删除元组：通过实现`HeapFile.java`中的`deleteTuple`方法，来实现数据表的元组删除操作。Tuple 包含一个`RecordIDs`，可以用来定位其所在的 page，然后简单地将 tuple 在 header 中对应的 bit 置为 0 即可
- 增加元组：通过实现`HeapFile.java`中的`insertTuple`方法，来实现数据表的元组增加操作。要添加元组，需要找到一张含有空 slot 的 page 页，否则，就需要创建一张新的 page 页并将其添加到磁盘中的物理文件

我们回忆计算机系统的原理，数据库要增/删一条记录，首先需要在内存（即本实验中的 bufferPool）中找到合适的 page 页，若 page 页不在内存中，则需从磁盘读取到内存（调用`HeapFile.readPage`方法），然后对 page 页执行增/删操作，将更新过的 page 页标记为脏页。在合适的时机将内存中的脏页持久化同步到磁盘（通过`bufferPool.flushAllPages`调用`HeapFile.writePage`方法）

<img src="https://guanghuihuang-1315055500.cos.ap-guangzhou.myqcloud.com/%E5%AD%A6%E4%B9%A0%E7%AC%94%E8%AE%B0/%E6%95%B0%E6%8D%AE%E5%BA%93/mit6.830/04.png" alt="image" style="zoom:90%;" />

首先，从最顶层的`Insert`和`Delete`算子开始。容易知道，这两种算子都属于“阻塞算子”，需要增/删所有来自子算子的元组后，计算并返回增/删元组的数量。因此，算子需要封装一个 List 容器存放结果(虽然只有一条结果)。

```java
public void open() throws DbException, TransactionAbortedException {
    // some code goes here
    super.open();
    child.open();
    int num = 0;
  	// 遍历所有元组，在内存中对每一个元组进行增/删操作
    while (child.hasNext()) {
        Tuple next = child.next();
        try {
            Database.getBufferPool().insertTuple(t, tableId, next);
          	// 记录增/删的元组数量
            num++;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    Tuple tuple = new Tuple(new TupleDesc(new Type[]{Type.INT_TYPE}));
    tuple.setField(0, new IntField(num));
  	// 将结果添加到容器中
    res.add(tuple);

    iter = res.iterator();
}
```

然后，实现 BufferPool 的`insertTuple`和`deleteTuple`方法。`insertTuple`方法通过参数`tableId`找到对应的 heapFile，`deleteTuple`方法则需要针对每一个 tuple 根据 RecordId 找到对应的 heapFile。调用 HeapFile 的`insertTuple`和`deleteTuple`方法，将返回的所有脏页都做上标记

```java
public void insertTuple(TransactionId tid, int tableId, Tuple t)
    throws DbException, IOException, TransactionAbortedException {
    // some code goes here
    // not necessary for lab1
    DbFile file = Database.getCatalog().getDatabaseFile(tableId);
    List<Page> pages = file.insertTuple(tid, t);
    // 将内存缓冲区更新过的page页置为脏页
    for (Page page : pages) {
        page.markDirty(true, tid);
    }
}
```

接着，实现 HeapFile 的`insertTuple`和`deleteTuple`方法。通过`BufferPool.getPage`方法获取内存中的 page 页，然后在 page 页中增/删元组。

**难点！！！**`insertTuple`方法需要考虑文件所有 page 页已满的情况，这时需要新建一个 page 页添加到内存中，同时持久化到磁盘（这里是持久化一个空的 page 页到磁盘）。最后再在这个新 page 页上做增加操作（保证这个 page 页是脏页，从而满足事务的原子性）

```java
public List<Page> insertTuple(TransactionId tid, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
    // some code goes here
    // not necessary for lab1
    List<Page> res = new ArrayList<>();

    int pgno = 0;
    for (; pgno < numPages(); pgno++) {
        // 先读到内存缓冲区的page
        HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, new HeapPageId(fileId, pgno), null);
        try {
            // 在内存缓冲区更新page
            page.insertTuple(t);
        } catch (DbException e) {
            continue;
        }
        // 将更新的page页返回
        res.add(page);
        return res;
    }
    // 在内存创建新的page
    HeapPage newPage = new HeapPage(new HeapPageId(fileId, pgno), HeapPage.createEmptyPageData());
    Database.getBufferPool().addPage(newPage.getId(), newPage);
    writePage(newPage);
    // 最后再在内存缓冲区更新page，从而满足事务的原子性
    newPage.insertTuple(t);

    res.add(newPage);
    return res;
}
```

最后，实现 HeapPage 的`insertTuple`和`deleteTuple`方法。用`getNumEmptySlots`方法来判断 page 页是否有空的 slot，有则继续用`isSlotUsed`方法来找到空的 slot。添加 tuple 后，通过`markSlotUsed`方法修改 header 中的 bit（注意，header 底层采用大端法，每个 byte 从低位开始看：即`[-1,-1,15] -> [11111111 11111111 00001111]`表示前 20 位被占用）

```java
public void insertTuple(Tuple t) throws DbException {
    // some code goes here
    // not necessary for lab1
    if (getNumEmptySlots() == 0) {
        throw new DbException("the page is full (no empty slots) !");
    }
    else {
        // 找到第一个空位，更改header，tuples
        for (int i = 0; i < getNumTuples(); i++) {
            if (!isSlotUsed(i)) {
                markSlotUsed(i, true);
                tuples[i] = t;
                t.setRecordId(new RecordId(this.pid, i));
                break;
            }
        }
    }
}
```

### Exercise4: Page eviction

当 BufferPool 中有超过 numPages 个页面时，应在加载下一个页面之前从 BufferPool 中逐出一个页面。驱逐策略我采用 LRU 算法

请注意，BufferPool 要求您实现`flushAllPages`方法。这在缓冲池的实际实现中是不需要的。然而，我们需要这种方法进行测试。您不应该从任何实际代码调用此方法

`flushAllPages`应在 BufferPool 中的所有页面上调用`flushPage`，`flushPage`应将任何脏页面写入磁盘并标记为不脏，同时将其留在 BufferPool 中。应该从 BufferPool 中删除页面的唯一方法是驱逐页面，该方法应在其驱逐的任何脏页面上调用`flushPage`

```java
public Page getPage(TransactionId tid, PageId pid, Permissions perm) {
    // some code goes here
    if (!bufferPool.containsKey(pid)) {
        if (bufferPool.getSize() == bufferPool.getCapacity()) {
            try {
              	// 当bufferPool满了后，逐出一张page页
                evictPage();
            } catch (DbException e) {
                e.printStackTrace();
            }
        }
        DbFile dbFile = Database.getCatalog().getDatabaseFile(pid.getTableId());
        Page page = dbFile.readPage(pid);
        bufferPool.put(pid, page);
    }

    return bufferPool.get(pid);
}
```

```java
private synchronized  void evictPage() throws DbException {
    // some code goes here
    // not necessary for lab1
  	// 调用bufferPool的逐出算法：淘汰最后一个节点
    LRUCache<PageId, Page>.DLinkedNode node = bufferPool.evictNode();
    PageId pageId = node.key;
    Page page = node.value;
    DbFile databaseFile = Database.getCatalog().getDatabaseFile(pageId.getTableId());
    try {
        // 将淘汰的page页落盘
        databaseFile.writePage(page);
    } catch (IOException e) {
        e.printStackTrace();
    }

}
```

```java
public DLinkedNode evictNode() throws DbException {
    DLinkedNode node = tail.prev;
    remove(node.key);
    return node;
}
```





