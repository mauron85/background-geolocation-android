package com.marianhello.bgloc.data;

import com.marianhello.logging.LoggerManager;
import com.marianhello.utils.concurrent.ManagedQueue;

public class SyncQueue extends ManagedQueue<BackgroundLocation> {
    private LocationDAO mLocationDAO;
    private QueueCallback mCallback;
    private int mQueueSize;

    private org.slf4j.Logger logger;

    public interface QueueCallback {
        void onSyncQueueFull();
    }

    public SyncQueue(SyncQueueManager manager) {
        super(manager);
        logger = LoggerManager.getLogger(SyncQueue.class);
    }

    public SyncQueue(LocationDAO dao, int queueSize) {
        this(new SyncQueueManager(dao, queueSize));
        mLocationDAO = dao;
        mQueueSize = queueSize;
    }

    public int getQueueSize() {
        return mQueueSize;
    }

    public void setQueueSize(int queueSize) {
        mQueueSize = queueSize;
        ((SyncQueueManager) this.mQueueManager).setQueueSize(queueSize);
    }

    public void setCallback(QueueCallback callback) {
        mCallback = callback;
    }

    @Override
    public void onAdded(BackgroundLocation location) {
        logger.debug("Location added to sync queue. sync threshold: {}", mQueueSize);
        if (mCallback != null) {
            long locationsCount = mLocationDAO.getLocationsForSyncCount(System.currentTimeMillis());
            if (locationsCount >= mQueueSize) {
                mCallback.onSyncQueueFull();
            }
        }
    }
}
