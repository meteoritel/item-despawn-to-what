/**
 * 客户端草稿持久化：Journal 管理保留策略、保存基线与落盘生命周期，支持重启/断线恢复与冲突判定。
 * <p>界面只消费业务状态与恢复模型；磁盘格式、文件仓库和串行写入者为包内实现。
 * <p>只存本机编辑中间态，不写服务端目录、不写 overlay 目录；格式损坏时跳过而不是抛异常。
 * <p>依赖约束：允许依赖 core 与原版客户端类型，禁止引入两端 loader 专有 API。
 */
package com.meteorite.itemdespawntowhat.client.edit.draft;
