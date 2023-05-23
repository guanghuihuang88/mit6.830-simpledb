# MIT-6.830-Lab1

> SimpleDB 包括如下模块：
>
> 1. 用来表达 fields、tuples 和 schemas 的类
> 2. 将 predicates 和 conditions 应用于 tuples 的类
> 3. 一种或多种访问方法（例如，堆文件），将关系表存储在磁盘上并提供一种遍历这些关系表中元组的方法
> 4. 一系列处理元组的算子类（例如，select, join, insert, delete 等）
> 5. 一个缓冲池，在内存中缓存活动的元组和页面，并处理并发控制和事务
> 6. 存储有关可用表及其 schemas 的信息的 catalog

Lab 1主要实现 SimpleDB 的基础架构，包括但不限于如下内容：

<img src="https://guanghuihuang-1315055500.cos.ap-guangzhou.myqcloud.com/%E5%AD%A6%E4%B9%A0%E7%AC%94%E8%AE%B0/%E6%95%B0%E6%8D%AE%E5%BA%93/mit6.830/01.png" alt="iamge" style="zoom:150%;" />

注意：用IDEA打开项目后，终端输入`ant`完成编译。注意跑单元测试`TupleDescTest`时，可能报找不到Zql等依赖包，但项目的`lib`目录下能找到这些依赖包。需要到 File->Project Structure->Modules->Dependencies->add->Library...添加依赖包目录

### Exercise1: Fields and Tuples

**TupleDesc 类**该类实现了数据库中的关系模式。对于关系模式中的每一对(字段名，字段类型)，源码中实现了`TDItem`子类，`TupleDesc`可视为一个填充`TDItem`的数组。因此，需要为`TupleDesc`类实现一个数组：

```java
private final transient ArrayList<TDItem> schemaArr = new ArrayList<>();
```

后续依次实现两个构造器、`merge`、`equals`、`toString`等方法即可。其中需要注意，`merge`方法需要`new`一个新的`TupleDesc`返回

在终端输入`ant runtest -Dtest=TupleDescTest`调用测试用例，全部通过会打印`BUILD SUCCESSFUL`

**Tuple 类**该类实现了数据库中的元组(记录)。易知，元组是由多个 value 组成的数组。因此，需要为`Tuple`类实现一个数组；每一个元组都有一种关系模式与之对应，因此，`Tuple`类需要一个`TupleDesc`类型的属性：

```java
private final TupleDesc tupleDesc;
private final ArrayList<Field> fields = new ArrayList<>();
```

后续依次实现构造器、`getField`、`setFieldd`等方法即可，需要注意，在构造方法中，`fields`需要填充 null 初始化属性值

注意：`setRecordId`后续实现

### Exercise2: Catalog & BufferPool

`Catalog`是一个目录，记录所有 SimpleDB 中的 Table表，需要我们实现 add 和 get 方法。根据`addTable`注释和函数签名，可以抽象出 table 由 file，name, pkeyFiled 三个属性构成，因此我们可以自定义一个 table 类。其实这是一个简单的**映射**，所以**Catalog 可以使用 HashMap 储存 table**，key 为 tableid, value 为 table。另外`Catalog`是一个单例对象，在使用的时候应该通过`Database.getCatalog()`获取。

注意！我的重名处理方法如下：通过捕捉`getTableId`方法的异常，来判断是否重名

```java
public void addTable(DbFile file, String name, String pkeyField) {
    // some code goes here
    try {
        // 重名则删去重名表
        tableMap.remove(getTableId(name));
    }catch (NoSuchElementException e) {
    }
    this.tableMap.put(file.getId(), new Table(file, name, pkeyField));
}
```

在读取数据时，我们需要调用`BufferPool`中的`getPage()`方法，而不能直接从磁盘读取。当调用`getPage`时，我们先尝试在 BufferPool 中找到对应 Page，若未找到，则调用 HeapFile 的`readPage`方法从磁盘中找到对应 HeapFile 的 Page，并将此 Page 加入 BufferPool 中。

`Page`与`PageId`绑定，所以 **BufferPool 也使用 HashMap 储存 Page**，键为 PageId，值为 Page。完成`getPage`方法之前，**一定要记得重写实现了 PageId 接口的类的 hashcode 方法。**

```java
public Page getPage(TransactionId tid, PageId pid, Permissions perm) {
        // some code goes here
        if (!bufferPool.containsKey(pid)) {
            HeapFile dbFile = (HeapFile) Database.getCatalog().getDatabaseFile(pid.getTableId());
            HeapPage page = (HeapPage) dbFile.readPage(pid);
            bufferPool.put(pid, page);
        }
        return bufferPool.get(pid);
    }
```

### Exercise3: HeapFile access method

> 引导

access method 提供了从磁盘读或写数据的途径，常见的 access method 包括 heap flies 和 B-trees。本节将实现简单的 heap flies access method

HeapFile -> HeapPage -> Tuple：

- 数据库中的每一张表 table 对应一个 HeapFile 对象，HeapFile 被划分为固定大小的 Page 页

- 每个 Page 页是实现了 Page 接口的 HeapPage 对象，Page 页的大小`page_size`由`BufferPool.DEFAULT_PAGE_SIZE`定义，存储固定数量的 tuples 和一个头部字段 header，它作为一个 bitmap 用来标识哪些位置的 tuple 是有效的。Page 页存放在 BufferPool 中，通过 HeapFile 类提供的 access method 被读取和写入

- 每一个 Tuple 需要`tuple_size * 8` bits 来存放数据，1 bit 用于 header 标识位。因此，一个 Page 页中包含的 Tuple 数为：`floor((page_size * 8) / (tuple_size * 8 + 1))`，floor 表示向下取整；于是，header 的大小为：`headerBytes = ceiling(tupsPerPage / 8)`，ceiling 表示向上取整
- 对于 header，每个字节的低（最低有效）位表示文件中较早的插槽的状态。因此，第一个字节的最低位表示页面中的第一个槽是否在使用中。第一个字节的第二低位表示页面中的第二个槽是否正在使用，依此类推。另外请注意，最后一个字节的高位可能与文件中实际存在的槽不对应，因为槽的数量可能不是 8 的倍数。所有 Java 虚拟机都是大端法

> 实战1

从 **HeapPage 类**入手，源码给出了 HeapPage 包含的成员变量和构造方法。通过阅读构造方法中的逻辑和上述公式，可以完成`getNumTuples`、`getHeaderSize`方法；根据大端法的规则，可以完成`getNumEmptySlots` and `isSlotUsed`方法，其中`isSlotUsed`方法需要补码和位运算的知识（Java 的数据类型是以补码的形式存放在内存）

**HeapPageId 类**可以视为一个 Page 页的身份证，指明了该 Page 页在 Table(File) 表中的位置（`pgNo`）以及所属的 Table(File) 表（`tableId`），自定义类自然需要实现它的`hashCode`和`equals`方法；**RecordId 类**同理，可以视为一个 Tuple 元组的身份证，指明了该 Tuple 元组在 Page 页中的位置（`tupleno`）以及所属的 Page 页（`pid`）

在终端输入`ant runtest -Dtest=HeapPageIdTest`调用测试用例，本节需通过`HeapPageIdTest`, `RecordIDTest`, `HeapPageReadTest`

🌟**难点！！！**在`HeapPageReadTest`的`getSlot()`测试方法中，`Byte[]`数据`EXAMPLE_DATA`中前3个值分别是-1、-1、15，将他们转化为补码：`11111111 11111111 00001111`。由于他们是`header[]`的前三个值，所以他们标记了前24个 tuples 的有效情况，根据大端法可知，其含义为前20个 slot 位有效，第21～24无效；该方法测试了`isSlotUsed()`方法，需要补码和位运算的知识，我实现的方法如下：

```java
public boolean isSlotUsed(int i) {
        // some code goes here
        int headerIndex = i / 8;
        int byteIndex = i % 8;
        byte b = this.header[headerIndex];
        // 方法一：
  			// 若b为正整数：n位原码(b)=n位补码(b)；若b为负整数：n位原码(b)=n位原码(2^n+b)
        if (b < 0) {
            int tmp =  256 + b;		// 2^8 = 256
            tmp = tmp >> byteIndex;
            return (tmp % 2) == 1;
        } else {
            int tmp = b;
            tmp = tmp >> byteIndex;
            return (tmp % 2) == 1;
        }
        // 方法二：
//        byte a = (byte) Math.pow(2, byteIndex);
//        return (a & b) != 0;
    }
```

同样的位运算方法可以实现`getNumEmptySlots`方法：

```java
public int getNumEmptySlots() {
    // some code goes here
    int numSlots = 0;
    int numEmptySlots = 0;
    for (byte b : this.header) {
        for (int i = 0; i < 8; i++) {
            numSlots++;
            if(numSlots > getNumTuples())
                return numEmptySlots;
            byte a = (byte) Math.pow(2, i);
            if ((a & b) == 0) {
                numEmptySlots++;
            }
        }
    }
    return numEmptySlots;
}
```

HeapPage 还需要实现一个自定义迭代器，其特点是需要跳过那些无效的 slot：

```java
public Iterator<Tuple> iterator() {
    // some code goes here
    return new HeapPage.Itr();
}

private class Itr implements Iterator<Tuple> {
    int cursor;
    int lastRet = -1;

    Itr() {}
    public boolean isSlotUsed(int i) {
        return false;
    }

    @Override
    public boolean hasNext() {
        while (cursor < numSlots && !isSlotUsed(cursor)) {
            cursor++;
        }
        return cursor < numSlots;
    }

    @Override
    public Tuple next() {
        int i = cursor;
        if (i >= numSlots)
            throw new NoSuchElementException();
        Tuple[] tuples = HeapPage.this.tuples;
        while (i < numSlots && !isSlotUsed(i)) {
            i++;
        }
        cursor = i+1;
        lastRet = i;
        return tuples[lastRet];
    }
}
```

**另外注意！**`getHeaderSize`方法需要考虑是否整除的情况

> 实战2

**HeapFile 类是难点！！！**

根据定义，HeapFile 被划分为固定大小的 Page 页，所以**需要一个`HeapPage[]`数组封装 Page 页，这是个容易想当然的误区！！！**试想一个HeapFile对象就装下了整个文件的内容，内存不得崩溃。HeapFile 仅仅只是对文件`fileId`等基本属性的简单封装，文件内容是以 Page 页的形式存放在 BufferPool 中的，通过调用前面 BufferPool 实现的`getPage`方法访问特定的 Page 页

需要在构造方法中初始化一个`fileId`，可以借助`File.getAbsoluteFile().hashCode()`方法获取一个唯一的 id（有文件的绝对路径决定）

```java
private final int fileId;
private final TupleDesc td;
private final int numPages;
private final File file;
```

在读取数据时，我们需要调用`BufferPool`中的`getPage()`方法尝试在 BufferPool 中找到对应 Page，若未找到，则调用 HeapFile 的`readPage`方法从磁盘中找到对应 HeapFile 的 Page（这里**需要利用 RandomAccessFile 类随机访问文件**），并将此 Page 加入 BufferPool 中

```java
public Page readPage(PageId pid) throws IllegalArgumentException {
    // some code goes here
    // 若bufferPool中没有page页，则从文件中读取page页
    HeapPage page = null;
    try {
        int pgNo = pid.getPageNumber();
        int tableId = pid.getTableId();

        HeapFile dbFile = (HeapFile) Database.getCatalog().getDatabaseFile(tableId);
        RandomAccessFile raf = new RandomAccessFile(dbFile.getFile(), "r");

        raf.seek((long) pgNo * BufferPool.getPageSize());
        byte[] buff = new byte[BufferPool.getPageSize()];

        if (raf.read(buff) != -1) {
            page = new HeapPage((HeapPageId) pid, buff);
        }
        raf.close();
    } catch (IOException e) {
        e.printStackTrace();
    }
    return page;
}
```

**难点！！！迭代器的实现！！！**

HeapFile 的迭代器通过遍历文件中的 Page 页来遍历文件中一条条的 Tuple 记录，并且它需要跳过 page 页中哪些无效的记录。迭代器方法返回一个 DbFileIterator 类型的迭代器，因此需要自定义迭代器，并重写`open`、`hashNext`、`next`、`rewind`等方法。

`open`方法：由于 HeapFile 迭代器本身又是通过调用 HeapPage 的迭代器来实现的，因此需要`open`方法初始化一个指明 page 页的游标和该 page 页迭代器的引用。其初始化游标指向第 0 个 page 页，并获取第 0 个 page 页的迭代器

```java
@Override
public void open() throws DbException, TransactionAbortedException {

    pgNoCursor = 0;
    HeapPageId heapPageId = new HeapPageId(getId(), pgNoCursor);

    HeapPage page = (HeapPage) Database.getBufferPool().getPage(null, heapPageId, null);
    if (page == null) {
        throw new DbException("null");
    } else {
        pageIterator = page.iterator();
    }
}
```

`hasNext`方法：判断当前 page 页中是否存在未遍历的 tuple，不存在则调用`nextPage`方法尝试定位到下一个存在未遍历 tuple 的 page 页

```java
@Override
public boolean hasNext() throws DbException, TransactionAbortedException {
    if (pageIterator == null) {
        return false;
    }
    if (pageIterator.hasNext()) {
        return true;
    } else {
        return nextPage();
    }
}
```

`nextPage`方法：尝试定位到下一个存在未遍历 tuple 的 page 页，并更新游标和 page 页迭代器

```java
public boolean nextPage() throws TransactionAbortedException, DbException {
    while (true) {
        pgNoCursor = pgNoCursor + 1;
        if (pgNoCursor >= numPages()) {
            return false;
        }
        HeapPageId heapPageId = new HeapPageId(getId(), pgNoCursor);
        HeapPage page = (HeapPage) Database.getBufferPool().getPage(null, heapPageId, null);
        if (page == null) {
            continue;
        }
        pageIterator = page.iterator();
        if (pageIterator.hasNext()) {
            return true;
        }
    }
}
```

`next`方法：简单调用当前 page 页迭代器的`next`方法

`rewind`方法：简单调用`open`方法（即重新从第一个 page 页开始）

`close`方法：简单将 page 页迭代器置为 null

### Exercise4: Operators

> 引导

Operators 负责查询计划的实际执行，他们实现了关系代数的运算。在 SimpleDB 中，Operators 是基于迭代器实现的；每个 Operators 都封装了 DbFileIterator 迭代器

通过将较低级别的 Operators 传递给较高级别 Operators 的构造方法，即通过“将它们链接在一起”，Operators 被连接到一棵执行计划树中。执行计划树的叶子 Operators 是特殊的 Access Method Operators，它们负责从磁盘读取数据（因此其没有任何子 Operators）

在执行计划树的顶部，与 SimpleDB 交互的程序只需在根 Operator 上调用 getNext 方法；然后这个 Operator 在它的子 Operator 上调用 getNext，以此类推，直到这些叶子 Operator 被调用。这些叶子 Operators 从磁盘中获取元组并将它们传递到父 Operators 上（作为 getNext 的返回参数）。元组以这种方式向上传播计划，直到它们在根处输出或被计划中的另一个 Operator 处理

> 实战

本节简单实现一个 Operator：`SeqScan`，简单对上述 HeapFile 的迭代器进行封装即可

在进行`ScanTest`测试时，有两个注意点：

1. `testTupleDesc`测试需要在`getTupleDesc()`方法中为`tupleDesc`的`fieldName`加上表的别名前缀，即`alias.fieldName`。不能直接返回原`tupleDesc`
2. `testCache`测试要求 BUfferPool 实现缓存功能，可以将 BUfferPool 实现为一个 LRU；
