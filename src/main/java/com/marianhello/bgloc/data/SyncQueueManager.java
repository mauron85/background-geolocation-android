package com.marianhello.bgloc.data;

import com.marianhello.utils.Convert;

public class SyncQueueManager implements com.marianhello.utils.concurrent.QueueManager<BackgroundLocation> {
    private LocationDAO mDao;
    private int mMaxLocations;

    public SyncQueueManager(LocationDAO dao, int maxLocations) {
        mDao = dao;
        mMaxLocations = maxLocations;
    }

    @Override
    public long insert(BackgroundLocation location) {
        return mDao.persistLocationForSync(location, mMaxLocations);
    }

    @Override
    public int clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getCount() {
        return Convert.safeLongToInt(mDao.getLocationsForSyncCount(System.currentTimeMillis()));
    }

    @Override
    public BackgroundLocation get(long id) {
        return mDao.getLocationById(id);
    }

    @Override
    public BackgroundLocation getHead() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BackgroundLocation getNext(long fromId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(BackgroundLocation location) {
        return mDao.getLocationById(location.getLocationId()) != null;
    }

    @Override
    public BackgroundLocation removeHead() {
        throw new UnsupportedOperationException();
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
