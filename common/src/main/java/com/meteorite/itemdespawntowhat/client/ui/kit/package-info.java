/**
 * 与业务无关的客户端 UI kit；公开能力由 {@code kit.api} 下的职责包提供。
 *
 * <p>宿主通过公开类型注入内容、主题、文案、时间与平台能力。公开具体类可以直接持有状态和算法，
 * 同包非公开 helper 保留包内协作；跨能力共享实现放在 {@code kit.internal}，仅供 kit 自身使用。</p>
 * <p>逐目录 {@code package-info.java} 登记 host/library/example 消费角色。
 * 依赖白名单、API 导出、消费角色和 preview 单向依赖由 {@code tools/check-ui-kit-boundaries.py} 核验。</p>
 */
package com.meteorite.itemdespawntowhat.client.ui.kit;
