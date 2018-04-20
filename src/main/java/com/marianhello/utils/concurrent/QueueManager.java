package com.marianhello.utils.concurrent;

public interface QueueManager<E> {
    long insert(E value);

    int clear();

    int getCount();

    E get(long id);

    E getHead();

    E getNext(long fromThis);

    boolean contains(E value);

    E removeHead();

    E remove(E s);

    long getMaxId();
}
