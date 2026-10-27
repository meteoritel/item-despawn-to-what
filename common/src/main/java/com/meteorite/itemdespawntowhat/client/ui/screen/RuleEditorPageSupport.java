package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.core.type.condition.CatalystPresentCondition;
import com.meteorite.itemdespawntowhat.core.type.condition.FluidPresentCondition;
import net.minecraft.resources.ResourceLocation;

/**
 * 页面共享的布局常量、界面文案前缀与存在条件类型 id。
 *
 * <p>本类不持有界面状态、不装配控件；业务工具按关注点分在 {@link PresenceLeaves}（存在条件叶）、
 * {@link CatalystQuantities}（催化剂数量联动）与 {@link RuleValueReads}（RuleValue 读取）</p>
 */
final class RuleEditorPageSupport {

    // 工具类，禁止实例化
    private RuleEditorPageSupport() {
    }

    // 页内留白与行高
    static final int PAD = 4;
    static final int TREE_OVERLAY_MARGIN = 8;
    static final int ROW_H = 20;
    // 按钮高度按用途区分：表单区按钮与对话框按钮；条件树摘要工具条更矮的高度属于 form 包（见 ConditionTreeControl）
    static final int FORM_BUTTON_H = 20;
    static final int DIALOG_BUTTON_H = 18;
    static final int BUTTON_GAP = 4;
    static final int CARD_PAD = 8;
    static final int CARD_GAP = 8;
    static final int CARD_HEADER_H = 20;
    static final int LINE_H = 10;

    // 界面文案前缀
    static final String UI = TypeLabels.UI_PREFIX;

    // 存在条件叶的类型 id
    static final ResourceLocation CATALYST_PRESENT_ID = CatalystPresentCondition.ID;
    static final ResourceLocation FLUID_PRESENT_ID = FluidPresentCondition.ID;
}
