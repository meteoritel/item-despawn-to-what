package com.meteorite.itemdespawntowhat.core.extension;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import net.minecraft.server.MinecraftServer;

/**
 * 动态引用校验上下文：打包一次遍历中定位到的当前效果/条件叶所需的校验输入。
 *
 * @param server    当前服务端，用于查询维度键集合、动态注册表与已加载的战利品表
 * @param issues    议题收集通道；已按当前规则来源做来源继承，实现写入议题时 origin 传 null 即可
 * @param fieldPath 当前效果或条件叶相对规则根的 JSON 字段路径，实现据此拼接自身字段
 */
public record DynamicReferenceContext(MinecraftServer server, IssueCollector issues, String fieldPath) {
}
