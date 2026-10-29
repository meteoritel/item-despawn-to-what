/**
 * 界面中立子包：{@code client/ui/screen} 与 {@code client/ui/screen/form} 共享的纯值与展示类型。
 *
 * <p><b>职责</b>：承载两侧都需要、且不依赖任一方的展示逻辑——
 * 目录图标分发与各目录类型的预览图标（{@link com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons}、
 * {@link com.meteorite.itemdespawntowhat.client.ui.shared.FluidPreviewIcons}、
 * {@link com.meteorite.itemdespawntowhat.client.ui.shared.TagPreviewIcons}）、
 * 展示名称查询（{@link com.meteorite.itemdespawntowhat.client.ui.shared.RuleDisplayLabels}）、
 * 字段到目录类型的映射与可选标签过滤
 * （{@link com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes}、
 * {@link com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogChoices}）、
 * 条件摘要的通用格式化与内建条件摘要
 * （{@link com.meteorite.itemdespawntowhat.client.ui.shared.SummaryFormats}、
 * {@link com.meteorite.itemdespawntowhat.client.ui.shared.BuiltinConditionSummaries}）、
 * 悬停说明面板的语义行模型（{@link com.meteorite.itemdespawntowhat.client.ui.shared.DescriptionLine}）。</p>
 *
 * <p><b>边界</b>：本包只依赖 {@code client.edit}（字段描述符与编辑器注册表）、
 * {@code client.ui.kit}、{@code client.ui.theme}（取默认主题给目录图标用色）、
 * {@code core.api} / {@code core.model} / {@code core.network.protocol} 的公开类型、
 * 原版客户端类型与 JDK；不得依赖 {@code client.ui.screen}、{@code client.ui.screen.form}
 * 与 {@code client.ui.widget}，以保证 {@code screen} 与 {@code screen/form} 都只向下依赖本包、互不反向依赖。
 * 本包不含界面状态与交互逻辑：屏幕、表单与叶子面板分别留在 {@code screen} 与 {@code screen/form}；
 * 说明行只描述「这是什么信息」，两列对齐、缩进与配色由 {@code client.ui.widget.UiDescriptionTooltip} 负责。</p>
 *
 * <p><b>扩展落点</b>：新增可被目录选择的类型时，在
 * {@link com.meteorite.itemdespawntowhat.client.ui.shared.FieldCatalogTypes#catalogTypeOf} 增加字段名兜底、
 * 在 {@link com.meteorite.itemdespawntowhat.client.ui.shared.CatalogPreviewIcons#iconFor} 增加图标分支；
 * 新增展示主体时由 {@code PreviewIcons} 接入统一场景；结构资源由 {@code StructurePreviewResources} 枚举和读取。</p>
 */
package com.meteorite.itemdespawntowhat.client.ui.shared;
