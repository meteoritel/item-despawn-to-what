package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.resources.ResourceLocation;

/**
 * 条件叶：条件树中的原子谓词（实现类本身就是它的参数对象）。
 * 叶级取反（negated）不受支持：取反一律由 {@link ConditionNode.Inverted} 节点承担；
 * NEGATED_FIELD 仅作为常量保留，供解码期识别该写法并给出明确错误。
 */
public interface Condition {

    // 条件类型 id，同时是 JSON 中 type 字段的取值
    ResourceLocation type();

    // 字段名常量引用点
    String TYPE_FIELD = RuleFields.TYPE;
    String NEGATED_FIELD = RuleFields.NEGATED;
}
