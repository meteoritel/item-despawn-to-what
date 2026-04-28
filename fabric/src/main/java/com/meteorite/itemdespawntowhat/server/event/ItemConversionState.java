package com.meteorite.itemdespawntowhat.server.event;

public interface ItemConversionState {

    boolean itemdespawntowhat$isTracked();

    void itemdespawntowhat$setTracked(boolean tracked);

    int itemdespawntowhat$getCheckTimer();

    void itemdespawntowhat$setCheckTimer(int timer);

    String itemdespawntowhat$getSelectedConfigId();

    void itemdespawntowhat$setSelectedConfigId(String selectedConfigId);

    boolean itemdespawntowhat$isConversionLocked();

    void itemdespawntowhat$setConversionLocked(boolean locked);

    default void itemdespawntowhat$resetProgress() {
        itemdespawntowhat$setCheckTimer(0);
        itemdespawntowhat$setSelectedConfigId("");
        itemdespawntowhat$setConversionLocked(false);
    }

    default void itemdespawntowhat$clearConversionState() {
        itemdespawntowhat$setTracked(false);
        itemdespawntowhat$resetProgress();
    }
}
