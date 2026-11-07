package com.meteorite.itemdespawntowhat.client.ui.screen;

import org.jetbrains.annotations.Nullable;

/**
 * 结果页「生成实体」动作的产物档位。
 * <p>档位与规则 JSON 的产出子类一一对应：掉落物 / 生物 / 其他实体 / 经验。档位本身不落盘、不进协议，
 * 切换档位只改写 variant 与该档位专属字段的默认值。
 * <p>轮询顺序即枚举声明顺序：物品、生物、经验、其他实体。经验产出没有独立画面参数，
 * 放在其他实体之前，玩家连续点击时先经过最简单的一档。
 */
public enum EntityProductStop {
    ITEM("item", "results.product.item"),
    MOB("mob", "results.product.mob"),
    EXPERIENCE("experience", "results.product.experience"),
    OTHER_ENTITY("entity", "results.product.other_entity");

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
     * 由草稿的产物子类型反推档位。
     * @param variant 草稿产物子类型；缺失或未知取值按掉落物处理
     */
    public static EntityProductStop of(@Nullable String variant) {
        for (EntityProductStop stop : values()) {
            if (stop.variant.equals(variant)) {
                return stop;
            }
        }
        return ITEM;
    }
}
