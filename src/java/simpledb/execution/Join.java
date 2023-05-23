package simpledb.execution;

import simpledb.transaction.TransactionAbortedException;
import simpledb.common.DbException;
import simpledb.storage.Tuple;
import simpledb.storage.TupleDesc;

import java.util.*;

/**
 * The Join operator implements the relational join operation.
 */
public class Join extends Operator {

    private static final long serialVersionUID = 1L;

    private JoinPredicate joinPredicate;
    private OpIterator child1;
    private OpIterator child2;

    private Tuple childTuple1 = null;
    private Tuple childTuple2 = null;

    /**
     * Constructor. Accepts two children to join and the predicate to join them
     * on
     * 
     * @param p
     *            The predicate to use to join the children
     * @param child1
     *            Iterator for the left(outer) relation to join
     * @param child2
     *            Iterator for the right(inner) relation to join
     */
    public Join(JoinPredicate p, OpIterator child1, OpIterator child2) {
        // some code goes here
        this.joinPredicate = p;
        this.child1 = child1;
        this.child2 = child2;
    }

    public JoinPredicate getJoinPredicate() {
        // some code goes here
        return joinPredicate;
    }

    /**
     * @return
     *       the field name of join field1. Should be quantified by
     *       alias or table name.
     * */
    public String getJoinField1Name() {
        // some code goes here
        return child1.getTupleDesc().getFieldName(joinPredicate.getField1());
    }

    /**
     * @return
     *       the field name of join field2. Should be quantified by
     *       alias or table name.
     * */
    public String getJoinField2Name() {
        // some code goes here
        return child2.getTupleDesc().getFieldName(joinPredicate.getField2());
    }

    /**
     * @see TupleDesc#merge(TupleDesc, TupleDesc) for possible
     *      implementation logic.
     */
    public TupleDesc getTupleDesc() {
        // some code goes here
        return TupleDesc.merge(child1.getTupleDesc(), child2.getTupleDesc());
    }

    public void open() throws DbException, NoSuchElementException,
            TransactionAbortedException {
        // some code goes here
        super.open();
        this.child1.open();
        this.child2.open();
    }

    public void close() {
        // some code goes here
        super.close();
        this.child1.close();
        this.child2.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        // some code goes here
        super.open();
        child1.rewind();
        child2.rewind();
    }

    /**
     * Returns the next tuple generated by the join, or null if there are no
     * more tuples. Logically, this is the next tuple in r1 cross r2 that
     * satisfies the join predicate. There are many possible implementations;
     * the simplest is a nested loops join.
     * <p>
     * Note that the tuples returned from this particular implementation of Join
     * are simply the concatenation of joining tuples from the left and right
     * relation. Therefore, if an equality predicate is used there will be two
     * copies of the join attribute in the results. (Removing such duplicate
     * columns can be done with an additional projection operator if needed.)
     * <p>
     * For example, if one tuple is {1,2,3} and the other tuple is {1,5,6},
     * joined on equality of the first column, then this returns {1,2,3,1,5,6}.
     * 
     * @return The next matching tuple.
     * @see JoinPredicate#filter
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        // some code goes here
        if (childTuple1 == null) {
            if (child1.hasNext()) {
                childTuple1 = child1.next();
            } else {
                return null;
            }
        }

        if (childTuple2 == null) {
            if (child2.hasNext()) {
                childTuple2 = child2.next();
            } else {
                childTuple1 = null;
                child2.rewind();
                return fetchNext();
            }
        }

        if (joinPredicate.filter(childTuple1, childTuple2)) {
            Tuple res = new Tuple(TupleDesc.merge(childTuple1.getTupleDesc(), childTuple2.getTupleDesc()));
            int index = 0;
            for (; index < childTuple1.getTupleDesc().numFields(); index++) {
                res.setField(index, childTuple1.getField(index));
            }
            for (int i = 0; i < childTuple2.getTupleDesc().numFields(); i++, index++) {
                res.setField(index, childTuple2.getField(i));
            }
            childTuple2 = null;
            return res;
        } else {
            childTuple2 = null;
            return fetchNext();
        }
    }

    @Override
    public OpIterator[] getChildren() {
        // some code goes here
        OpIterator[] children = new OpIterator[2];
        children[0] = child1;
        children[1] = child2;
        return children;
    }

    @Override
    public void setChildren(OpIterator[] children) {
        // some code goes here
        child1 = children[0];
        child2 = children[1];
    }

}