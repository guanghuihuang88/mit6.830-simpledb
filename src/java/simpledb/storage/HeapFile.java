package simpledb.storage;

import simpledb.common.Database;
import simpledb.common.DbException;
import simpledb.common.Debug;
import simpledb.common.Permissions;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;

import javax.xml.crypto.Data;
import java.io.*;
import java.util.*;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 *
 * @see HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {

    private final int fileId;
    private final TupleDesc td;
    private final File file;

    /**
     * Constructs a heap file backed by the specified file.
     *
     * @param f the file that stores the on-disk backing store for this heap
     *          file.
     */
    public HeapFile(File f, TupleDesc td) {
        // some code goes here
        this.td = td;
        this.fileId = f.getAbsoluteFile().hashCode();
        this.file = f;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     *
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        // some code goes here
        return file;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere to ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     *
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        // some code goes here
        return this.fileId;
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     *
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        // some code goes here
        return this.td;
    }

    // see DbFile.java for javadocs
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

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // some code goes here
        // not necessary for lab1
        // 若bufferPool中没有可用于insert的page页，则向文件中写入新的page页
        HeapPageId pid = (HeapPageId) page.getId();
        int pgNo = pid.getPageNumber();
        int tableId = pid.getTableId();
        byte[] data = page.getPageData();

        HeapFile dbFile = (HeapFile) Database.getCatalog().getDatabaseFile(tableId);
        RandomAccessFile raf = new RandomAccessFile(dbFile.getFile(), "rw");

        try {
            raf.seek((long) pgNo * BufferPool.getPageSize());
            raf.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            raf.close();
        }

    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        // some code goes here

        int pageSize = BufferPool.getPageSize();
        long fileSize = file.length();
        if ((int) (fileSize % (long) pageSize) == 0) {
            return  (int) (fileSize / (long) pageSize);
        } else {
            return (int) (fileSize / (long) pageSize) + 1;
        }
    }

    // see DbFile.java for javadocs
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

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        ArrayList<Page> res = new ArrayList<>();
        // 先读到内存缓冲区的page
        HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, t.getRecordId().getPageId(), null);
        // 在内存缓冲区更新page
        page.deleteTuple(t);
        res.add(page);
        return res;
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        // some code goes here
        return new HeapFile.Itr();
    }

    private class Itr implements DbFileIterator {

        private int pgNoCursor = 0;
        Iterator<Tuple> pageIterator = null;

        Itr() {
        }

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

        @Override
        public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
            if (pageIterator == null) {
                throw new NoSuchElementException();
            }
            return pageIterator.next();
        }

        @Override
        public void rewind() throws DbException, TransactionAbortedException {
            open();
        }

        @Override
        public void close() {
            pageIterator = null;
        }
    }
//
//        @Override
//        // 打开page页的迭代器
//        public void open() throws DbException, TransactionAbortedException {
//            HeapPage page = (HeapPage) Database.getBufferPool().getPage(null, new HeapPageId(fileId, pgNoCursor), null);
//            if (page == null) {
//                throw new DbException("null");
//            } else {
//                pageIterator = page.iterator();
//            }
//            pgNoCursor++;
//        }
//
//        @Override
//        public boolean hasNext() throws TransactionAbortedException, DbException {
//            if (pageIterator == null)
//                return false;       // Not open yet
//
//            if (pageIterator.hasNext()) {
//                return true;
//            } else if (pgNoCursor < numPages) {
//                open();
//                return hasNext();
//            } else
//                return false;
//        }
//
//        @Override
//        public Tuple next() {
//            if (pageIterator == null)
//                throw new NoSuchElementException();
//            return pageIterator.next();
//        }
//
//        @Override
//        public void rewind() throws DbException, TransactionAbortedException {
//            pgNoCursor = 0;
//        }
//
//        @Override
//        public void close() {
//            pageIterator = null;
//        }
//    }

}



