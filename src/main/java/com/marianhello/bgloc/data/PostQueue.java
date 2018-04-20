package com.marianhello.bgloc.data;

import com.marianhello.utils.concurrent.ManagedQueue;

/**
 * Non blocking Location Queue stores locations in sqlite
 *
 * There is non standard behaviour of some methods:
 *
 * clear - don't actually delete elements but mark all for sync
 * @see PostQueueManager#clear
 *
 */
public class PostQueue extends ManagedQueue<BackgroundLocation> {
    private QueueCallback mCallback;

    public interface QueueCallback {
        void onPostQueueCleared();
    }

    public PostQueue(PostQueueManager manager) {
        super(manager);
    }

    public PostQueue(LocationDAO dao, int queueSize) {
        this(new PostQueueManager(dao, queueSize));
    }

    public int getQueueSize() {
        return ((PostQueueManager) mQueueManager).getQueueSize();
    }

    public void setQueueSize(int size) {
        ((PostQueueManager) this.mQueueManager).setQueueSize(size);
    }

    public void setCallback(PostQueue.QueueCallback callback) {
        mCallback = callback;
    }

    @Override
    public void onCleared() {
        if (mCallback != null) {
            mCallback.onPostQueueCleared();
        }
    }
}
