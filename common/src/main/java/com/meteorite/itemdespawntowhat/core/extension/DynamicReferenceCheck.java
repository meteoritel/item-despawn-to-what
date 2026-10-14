package com.meteorite.itemdespawntowhat.core.extension;

/**
 * 动态引用校验扩展点：类型自行申报其参数引用到的动态资源是否存在。
 * <p>
 * 「动态引用」指无法在类型参数校验期判定、必须依赖当前服务端已加载注册表才能确认的引用，
 * 例如维度是否存在于服务端的维度键集合、生物群系是否已注册、战利品表是否已加载成功。
 * 效果与条件叶的参数对象（{@code Effect} / {@code Condition} 的实现类）按需实现本接口；
 * 核心校验器只负责遍历规则中的效果与条件叶并调用本方法，不对任何具体类型做判定，
 * 因此第三方模组注册的自定义类型只要实现本接口即可获得同等的动态引用校验。
 * <p>
 * 默认实现为空操作：未声明动态引用的类型行为与不实现本接口完全一致。
 */
public interface DynamicReferenceCheck {

    /**
     * 校验本效果或条件叶自身的动态引用。
     * <p>
     * 实现应把发现的问题写入 {@link DynamicReferenceContext#issues()}，字段路径以
     * {@link DynamicReferenceContext#fieldPath()} 为前缀拼接；写入时 origin 传 null，
     * 由议题收集通道按当前规则来源统一补齐。
     *
     * @param context 当前服务端、统一议题收集通道与当前效果/条件叶的字段路径
     */
    default void checkDynamicReferences(DynamicReferenceContext context) {
        // 默认不声明任何动态引用
    }
}
