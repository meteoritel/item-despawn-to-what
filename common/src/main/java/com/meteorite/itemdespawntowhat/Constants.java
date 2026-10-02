package com.meteorite.itemdespawntowhat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 模组标识与持久化实体标记；性能配置由 core/config/ServerConfig 管理。 */
public final class Constants {
    public static final String MOD_ID = "itemdespawntowhat";
    public static final String MOD_NAME = "Item Despawn To What";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
    public static final String CHECK_LOCK_TAG = MOD_ID + ":check_lock";
    public static final String CONVERTED_TAG = MOD_ID + ":converted";
    private Constants() {}
}
