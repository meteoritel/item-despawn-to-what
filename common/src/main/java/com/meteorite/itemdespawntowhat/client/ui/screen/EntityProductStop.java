package com.meteorite.itemdespawntowhat.client.ui.screen;

import org.jetbrains.annotations.Nullable;

/**
 * 结果页「生成实体」动作的产物档位。
 * <p>档位只服务于界面与目录范围：规则 JSON 里生物与其他实体都写 variant = entity，
 * 到底是生物还是其他实体由选中的实体类型派生，因此档位不落盘、不进协议。
 * <p>轮询顺序即枚举声明顺序：掉落物、生物、其他实体、经验。
 */
public enum EntityProductStop {
    ITEM("item", "results.product.item"),
    MOB("entity", "results.product.mob"),
    OTHER_ENTITY("entity", "results.product.other_entity"),
    EXPERIENCE("experience", "results.product.experience");

    private final String variant;
    private final String labelKey;

    EntityProductStop(String variant, String labelKey) {
        this.variant = variant;
        this.labelKey = labelKey;
    }

    /** 草稿里对应的产物子类型字段值。 */
    public String variant() {
        return variant;
    }

    /** 档位名的本地化 key。 */
    public String labelKey() {
        return labelKey;
    }

    /** 下一档，按声明顺序循环。 */
    public EntityProductStop next() {
        EntityProductStop[] stops = values();
        return stops[(ordinal() + 1) % stops.length];
    }

    /**
     * 由草稿的产物子类型与选中实体的能力反推档位。
     * @param variant 草稿产物子类型；缺失按掉落物处理
     * @param entityIsMob 选中实体是否为生物；没有选中实体时按生物处理，保证空选择也能进入生物档
     */
    public static EntityProductStop of(@Nullable String variant, boolean entityIsMob) {
        if (EXPERIENCE.variant.equals(variant)) {
            return EXPERIENCE;
        }
        if (MOB.variant.equals(variant)) {
            return entityIsMob ? MOB : OTHER_ENTITY;
        }
        return ITEM;
    }
}
