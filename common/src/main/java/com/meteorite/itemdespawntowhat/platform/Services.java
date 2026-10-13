package com.meteorite.itemdespawntowhat.platform;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

/** 平台服务的 ServiceLoader 装配入口。 */
public class Services {

    /** 当前平台的服务实现，由 {@link #load(Class)} 在类初始化时装配。 */
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    /**
     * 通过 ServiceLoader 装配指定类型的平台服务实现。
     *
     * @param clazz 服务接口类型
     * @param <T> 服务接口类型参数
     * @return 类路径上找到的第一个实现
     * @throws NullPointerException 类路径上没有该服务的实现时
     */
    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
