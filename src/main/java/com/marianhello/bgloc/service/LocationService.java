package com.marianhello.bgloc.service;

import com.marianhello.bgloc.Config;

public interface LocationService {
    void start();
    void stop();
    void startForeground();
    void stopForeground();
    void configure(Config config);
    void registerHeadlessTask(String jsFunction);
    void startHeadlessTask();
    void executeProviderCommand(int command, int arg);
}
