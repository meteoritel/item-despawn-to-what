package com.meteorite.itemdespawntowhat.server.event;

/**
 * Fabric ItemEntity Mixin 持有的转换状态。
 */
public interface ItemConversionState {

    boolean itemdespawntowhat$isTracked();

    void itemdespawntowhat$setTracked(boolean tracked);

    int itemdespawntowhat$getCheckTimer();

    void itemdespawntowhat$setCheckTimer(int timer);

    String itemdespawntowhat$getSelectedConfigId();

    void itemdespawntowhat$setSelectedConfigId(String selectedConfigId);

    boolean itemdespawntowhat$isConversionLocked();

    void itemdespawntowhat$setConversionLocked(boolean locked);
}
