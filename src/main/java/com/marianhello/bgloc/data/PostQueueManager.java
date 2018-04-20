package com.marianhello.bgloc.data;

import com.marianhello.utils.Convert;

public class PostQueueManager implements com.marianhello.utils.concurrent.QueueManager<BackgroundLocation> {
    private LocationDAO mDao;
    private int mMaxLocations;

    public PostQueueManager(LocationDAO dao, int maxLocations) {
        mDao = dao;
        mMaxLocations = maxLocations;
    }

    @Override
    public long insert(BackgroundLocation location) {
        return mDao.persistLocation(location, mMaxLocations);
    }

    @Override
    public int clear() {
        return mDao.deleteUnpostedLocations();
    }

    @Override
    public int getCount() {
        return Convert.safeLongToInt(mDao.getUnpostedLocationsCount());
    }

    @Override
    public BackgroundLocation get(long id) {
        return mDao.getLocationById(id);
    }

    @Override
    public BackgroundLocation getHead() {
        return mDao.getFirstUnpostedLocation();
    }

    @Override
    public BackgroundLocation getNext(long fromId) {
        return mDao.getNextUnpostedLocation(fromId);
    }

    @Override
    public boolean contains(BackgroundLocation location) {
        return mDao.getLocationById(location.getLocationId()) != null;
    }

    @Override
    public BackgroundLocation removeHead() {
        return mDao.deleteFirstUnpostedLocation();
    }

    @Override
    public BackgroundLocation remove(BackgroundLocation location) {
        mDao.deleteLocationById(location.getLocationId());
        return location;
    }

    @Override
    public long getMaxId() {
        throw new UnsupportedOperationException();
    }

    public int getQueueSize() {
        return mMaxLocations;
    }

    public void setQueueSize(int size) {
        mMaxLocations = size;
    }
}
