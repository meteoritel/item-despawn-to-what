package com.meteorite.itemdespawntowhat.core.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 掉落物实体层的转化状态：临时保护、临时冷却、永久保护、永久禁转、死亡排除与转化提交。
 * 状态只挂在实体上，不进 ItemStack：拾取即实体消亡，重丢得到的是普通掉落物，不继承任何状态。
 * 时间字段是绝对值（服务端游戏刻），计时基准为 ServerLevel#getGameTime()：卸载不暂停、重启后继续。
 * 0 表示未设置：合并临时状态时取较晚到期刻，未设置的一方不参与取大。
 */
public record DropState(
        // 结构版本：只识别 1，未知版本按无状态处理
        int version,
        // 临时保护到期刻（绝对值游戏刻，0 表示未设置）
        long temporaryProtectionUntil,
        // 临时冷却到期刻（绝对值游戏刻，0 表示未设置）
        long temporaryCooldownUntil,
        // 永久保护：返还物专用，豁免三类环境伤害
        boolean permanentProtection,
        // 永久禁转：返还物专用，实体生命周期内不再启动转化
        boolean permanentConversionBan,
        // 死亡掉落排除：合并后作用于整堆，不授予环境保护
        boolean deathDropExcluded,
        // 转化已提交：原源实体生命周期内不再进入转化
        boolean conversionCommitted
) {

    // 当前支持的结构版本
    public static final int VERSION = 1;

    /** 无状态：等价于实体上没有记录。 */
    public static final DropState NONE = new DropState(VERSION, 0L, 0L, false, false, false, false);

    /** 按当前版本创建保护与冷却状态，死亡排除和提交位默认关闭。 */
    public DropState(long temporaryProtectionUntil, long temporaryCooldownUntil,
                     boolean permanentProtection, boolean permanentConversionBan) {
        this(VERSION, temporaryProtectionUntil, temporaryCooldownUntil, permanentProtection, permanentConversionBan, false, false);
    }

    /** 持久化编解码：字段名 snake_case，版本字段 v 供后续迁移。 */
    public static final Codec<DropState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("v", VERSION).forGetter(DropState::version),
            Codec.LONG.optionalFieldOf("temporary_protection_until", 0L).forGetter(DropState::temporaryProtectionUntil),
            Codec.LONG.optionalFieldOf("temporary_cooldown_until", 0L).forGetter(DropState::temporaryCooldownUntil),
            Codec.BOOL.optionalFieldOf("permanent_protection", false).forGetter(DropState::permanentProtection),
            Codec.BOOL.optionalFieldOf("permanent_conversion_ban", false).forGetter(DropState::permanentConversionBan),
            Codec.BOOL.optionalFieldOf("death_drop_excluded", false).forGetter(DropState::deathDropExcluded),
            Codec.BOOL.optionalFieldOf("conversion_committed", false).forGetter(DropState::conversionCommitted)
    ).apply(instance, DropState::new));

    /** 版本是否可识别；不可识别时调用方按无状态处理，不误读未来结构。 */
    public boolean isSupportedVersion() {
        return version == VERSION;
    }

    /** 是否与无状态等价（据此清除实体上的记录，避免写出空数据）。 */
    public boolean isEmpty() {
        return !permanentProtection && !permanentConversionBan && !deathDropExcluded && !conversionCommitted
                && temporaryProtectionUntil <= 0L && temporaryCooldownUntil <= 0L;
    }

    /** 临时保护是否仍在有效期内。 */
    public boolean protectsTemporarily(long gameTime) {
        return temporaryProtectionUntil > 0L && gameTime < temporaryProtectionUntil;
    }

    /** 是否豁免三类环境伤害：永久保护或临时保护有效（恒为三类全防，不存掩码）。 */
    public boolean isProtected(long gameTime) {
        return permanentProtection || protectsTemporarily(gameTime);
    }

    /** 剩余临时冷却刻数；0 表示当前不在冷却中。 */
    public long cooldownRemaining(long gameTime) {
        return temporaryCooldownUntil <= 0L ? 0L : Math.max(0L, temporaryCooldownUntil - gameTime);
    }

    /** 写入临时状态：各自取较晚到期刻，未设置（{@code <= 0}）的一方不参与取大。 */
    public DropState withTemporary(long protectionUntil, long cooldownUntil) {
        return new DropState(version,
                Math.max(temporaryProtectionUntil, Math.max(0L, protectionUntil)),
                Math.max(temporaryCooldownUntil, Math.max(0L, cooldownUntil)),
                permanentProtection, permanentConversionBan, deathDropExcluded, conversionCommitted);
    }

    /** 授予返还物状态：永久保护 + 永久禁转（返还交付时调用）。 */
    public DropState withPermanentReturn() {
        return new DropState(version, temporaryProtectionUntil, temporaryCooldownUntil, true, true, deathDropExcluded, conversionCommitted);
    }

    /** 标记死亡掉落排除，保留保护、冷却和转化提交状态。 */
    public DropState withDeathDropExcluded() {
        return deathDropExcluded ? this : new DropState(version, temporaryProtectionUntil, temporaryCooldownUntil,
                permanentProtection, permanentConversionBan, true, conversionCommitted);
    }

    /** 更新转化提交位；补偿撤销时只变更该位。 */
    public DropState withConversionCommitted(boolean committed) {
        return conversionCommitted == committed ? this : new DropState(version,
                temporaryProtectionUntil, temporaryCooldownUntil, permanentProtection, permanentConversionBan,
                deathDropExcluded, committed);
    }

    /** 永久标志是否完全一致：合并兼容的判据。 */
    public boolean permanentFlagsMatch(DropState other) {
        return other != null && permanentProtection == other.permanentProtection
                && permanentConversionBan == other.permanentConversionBan;
    }

    /** 合并永久标志兼容的实体状态：到期刻取较晚者，排除与提交位取并集，永久标志沿用载体。 */
    public DropState mergeCompatible(DropState other) {
        return new DropState(version,
                Math.max(temporaryProtectionUntil, other.temporaryProtectionUntil),
                Math.max(temporaryCooldownUntil, other.temporaryCooldownUntil),
                permanentProtection, permanentConversionBan,
                deathDropExcluded || other.deathDropExcluded, conversionCommitted || other.conversionCommitted);
    }
}
