/**
 * 客户端工作区：协议视图、类型编辑器注册表、规则草稿与撤销栈。
 * <p>本包只做客户端数据组织，不发起网络请求，也不打开界面：
 * 网络收发由 client/net 负责，界面由 client/ui 负责。
 * <p>依赖约束：允许依赖 core（core.api / core.model 的公开类型）与原版客户端类型，
 * 禁止被 core 反向依赖，禁止引入两端 loader 专有 API。
 */
package com.meteorite.itemdespawntowhat.client.edit;
