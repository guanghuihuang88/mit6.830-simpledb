package simpledb.execution;

import simpledb.common.DbException;
import simpledb.common.Type;
import simpledb.storage.Field;
import simpledb.storage.IntField;
import simpledb.storage.Tuple;
import simpledb.storage.TupleDesc;
import simpledb.transaction.TransactionAbortedException;

import java.util.*;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    // GROUP BY
    private Map<Field, ArrayList<Field>> map = new HashMap<>();
    // 无 GROUP BY
    private ArrayList<Field> singleGroup = new ArrayList<>();

    private int gbfield;
    private Type gbfieldtype;
    private int afield;
    private Op what;

    private Iterator iter;

    /**
     * Aggregate constructor
     * 
     * @param gbfield
     *            the 0-based index of the group-by field in the tuple, or
     *            NO_GROUPING if there is no grouping
     * @param gbfieldtype
     *            the type of the group by field (e.g., Type.INT_TYPE), or null
     *            if there is no grouping
     * @param afield
     *            the 0-based index of the aggregate field in the tuple
     * @param what
     *            the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        // some code goes here
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;
        this.what = what;
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     * 
     * @param tup
     *            the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // some code goes here
        if (gbfield == -1) {
            singleGroup.add(tup.getField(afield));
        }

        if (map.containsKey(tup.getField(gbfield))) {
            ArrayList<Field> group = map.get(tup.getField(gbfield));
            group.add(tup.getField(afield));
        } else {
            ArrayList<Field> group = new ArrayList<>();
            group.add(tup.getField(afield));
            map.put(tup.getField(gbfield), group);
        }
    }

    /**
     * Create a OpIterator over group aggregate results.
     * 
     * @return a OpIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public OpIterator iterator() {
        // some code goes here
        return new Itr();
        // throw new UnsupportedOperationException("please implement me for lab2");
    }

    class Itr implements OpIterator {

        private int curs = 1;
        private int len;

        private TupleDesc tupleDesc;

        Itr(){
            if (gbfield == -1) {
                tupleDesc = new TupleDesc(new Type[]{Type.INT_TYPE});
            } else {
                tupleDesc = new TupleDesc(new Type[]{gbfieldtype, Type.INT_TYPE});
            }
        }

        @Override
        public void open() throws DbException, TransactionAbortedException {
            if (gbfield == -1) {
                iter = singleGroup.iterator();
                curs = 1;
                len = 1;
            } else {
                iter = map.keySet().iterator();
                curs = 1;
                len = map.keySet().size();
            }
        }

        @Override
        public boolean hasNext() throws DbException, TransactionAbortedException {
            if (iter == null || curs > len) {
                return false;
            }
            return true;
        }

        @Override
        public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {

            Tuple res = new Tuple(this.getTupleDesc());
            curs++;

            // ------------------------------------------------------------MIN
            if (what == Aggregator.Op.MIN) {
                if (gbfield == -1) {
                    IntField min = (IntField) iter.next();
                    while (iter.hasNext()) {
                        IntField curValue = (IntField) iter.next();
                        if (curValue.getValue() < min.getValue()) {
                            min = curValue;
                        }
                    }
                    res.setField(0, min);
                } else {
                    Field gbKey = (Field) iter.next();
                    Iterator<Field> aggValues = map.get(gbKey).iterator();

                    IntField min = (IntField) aggValues.next();
                    while (aggValues.hasNext()) {
                        IntField curValue = (IntField) aggValues.next();
                        if (curValue.getValue() < min.getValue()) {
                            min = curValue;
                        }
                    }
                    res.setField(0, gbKey);
                    res.setField(1, min);
                }
                return res;
            }
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
            // ------------------------------------------------------------SUM
            if (what == Aggregator.Op.SUM) {
                if (gbfield == -1) {
                    IntField sum = (IntField) iter.next();
                    while (iter.hasNext()) {
                        IntField curValue = (IntField) iter.next();
                        sum = new IntField(sum.getValue() + curValue.getValue());
                    }
                    res.setField(0, sum);
                } else {
                    Field gbKey = (Field) iter.next();
                    Iterator<Field> aggValues = map.get(gbKey).iterator();

                    IntField sum = (IntField) aggValues.next();
                    while (aggValues.hasNext()) {
                        IntField curValue = (IntField) aggValues.next();
                        sum = new IntField(sum.getValue() + curValue.getValue());
                    }
                    res.setField(0, gbKey);
                    res.setField(1, sum);
                }
                return res;
            }
            // ------------------------------------------------------------SUM_COUNT
//            if (what == Aggregator.Op.SUM_COUNT) {
//
//            }
            // ------------------------------------------------------------AVG
            if (what == Aggregator.Op.AVG) {
                if (gbfield == -1) {
                    IntField sum = (IntField) iter.next();
                    int num = 1;
                    while (iter.hasNext()) {
                        IntField curValue = (IntField) iter.next();
                        num++;
                        sum = new IntField(sum.getValue() + curValue.getValue());
                    }
                    sum = new IntField(sum.getValue() / num);
                    res.setField(0, sum);
                } else {
                    Field gbKey = (Field) iter.next();
                    Iterator<Field> aggValues = map.get(gbKey).iterator();

                    IntField sum = (IntField) aggValues.next();
                    int num = 1;
                    while (aggValues.hasNext()) {
                        IntField curValue = (IntField) aggValues.next();
                        num++;
                        sum = new IntField(sum.getValue() + curValue.getValue());
                    }
                    sum = new IntField(sum.getValue() / num);
                    res.setField(0, gbKey);
                    res.setField(1, sum);
                }
                return res;
            }
            // ------------------------------------------------------------COUNT
            if (what == Aggregator.Op.COUNT) {
                if (gbfield == -1) {
                    int num = 1;
                    while (iter.hasNext()) {
                        iter.next();
                        num++;
                    }
                    res.setField(0, new IntField(num));
                } else {
                    Field gbKey = (Field) iter.next();
                    Iterator<Field> aggValues = map.get(gbKey).iterator();

                    int num = 1;
                    while (aggValues.hasNext()) {
                        aggValues.next();
                        num++;
                    }
                    res.setField(0, gbKey);
                    res.setField(1, new IntField(num));
                }
                return res;
            }
            // ------------------------------------------------------------SC_AVG
//            if (what == Aggregator.Op.SC_AVG) {
//
//            }
            return null;
        }

        @Override
        public void rewind() throws DbException, TransactionAbortedException {
            open();
        }

        @Override
        public TupleDesc getTupleDesc() {
            return this.tupleDesc;
        }

        @Override
        public void close() {
            iter = null;
        }
    }

}

