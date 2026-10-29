package com.meteorite.itemdespawntowhat.client.ui.kit.preview;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/** 展示内容的值快照；调用方修改原始 stack 或 NBT 不会改变场景身份。 */
public sealed interface PreviewSubject {
    /** 原版 GUI 物品；数量和组件共同参与缓存身份。 */
    record Item(ItemStack stack) implements PreviewSubject {
        public Item { stack = stack.copy(); }
        /** 返回展示快照的副本。 */
        @Override public ItemStack stack() { return stack.copy(); }
        @Override public boolean equals(Object other) {
            return other instanceof Item item && ItemStack.matches(stack, item.stack);
        }
        @Override public int hashCode() {
            return 31 * ItemStack.hashItemAndComponents(stack) + stack.getCount();
        }
    }

    /** 放置态方块，载荷用于真实方块实体 renderer。 */
    record Block(BlockState state, @Nullable CompoundTag blockEntityData) implements PreviewSubject {
        public Block { blockEntityData = blockEntityData == null ? null : blockEntityData.copy(); }
        /** 返回独立载荷副本。 */
        @Override public @Nullable CompoundTag blockEntityData() {
            return blockEntityData == null ? null : blockEntityData.copy();
        }
    }

    /** 流体放置态，保留源流体或流动状态。 */
    record Fluid(FluidState state) implements PreviewSubject { }

    /** 含 id、朝向与展示参数的完整实体 NBT。 */
    record Entity(CompoundTag data) implements PreviewSubject {
        public Entity { data = data.copy(); }
        /** 返回独立载荷副本。 */
        @Override public CompoundTag data() { return data.copy(); }
    }

    /** 已解码的客户端结构。 */
    final class Structure implements PreviewSubject {
        private final PreviewStructure value;
        private final int hash;

        /** 结构不可变，内容哈希只在交接时计算，绘制时查缓存保持常数成本。 */
        public Structure(PreviewStructure value) { this.value = value; this.hash = value.hashCode(); }
        /** 返回已验证的不可变结构。 */
        public PreviewStructure value() { return value; }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Structure structure
                    && hash == structure.hash && value.equals(structure.value);
        }
    }
}
