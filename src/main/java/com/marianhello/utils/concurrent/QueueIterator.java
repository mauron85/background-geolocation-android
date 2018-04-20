package com.marianhello.utils.concurrent;

import java.util.Iterator;

/**
 * An iterator to iterate through the sqlite queue Db records.
 */
class QueueIterator<T> implements Iterator<T> {
    private QueueManager<T> queueManager;
    private long mCurrentId;

    QueueIterator(QueueManager manager) {
        queueManager = manager;
        mCurrentId = -1;
    }

    @Override
    public boolean hasNext() {
        return queueManager.getCount() > 0 && mCurrentId < queueManager.getMaxId();
    }

    @Override
    public T next() {
        return queueManager.getNext(mCurrentId);
    }
}