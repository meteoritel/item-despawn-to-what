/**
 * 客户端草稿持久化：未应用的编辑草稿落盘到 config 目录，支持重启/断线恢复与冲突判定。
 * <p>只存本机编辑中间态，不写服务端目录、不写 overlay 目录；格式损坏时跳过而不是抛异常。
 * <p>依赖约束：允许依赖 core 与原版客户端类型，禁止引入两端 loader 专有 API。
 */
package com.meteorite.itemdespawntowhat.client.edit.draft;
