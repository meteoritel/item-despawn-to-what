package com.meteorite.itemdespawntowhat.client.ui.kit.preview;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 原版结构格式的不可变快照；多调色板固定使用第一组。 */
public record PreviewStructure(BlockPos size, List<BlockState> palette, List<Cell> blocks,
                               List<Actor> entities) {
    // 外部可达性搜索也占内存，在分配占位数组前约束体积。
    static final int MAX_CELLS = 1_048_576;

    public PreviewStructure {
        size = size.immutable();
        validateSize(size);
        palette = List.copyOf(palette);
        blocks = List.copyOf(blocks);
        entities = List.copyOf(entities);
        HashSet<BlockPos> occupied = new HashSet<>();
        for (Cell cell : blocks) {
            BlockPos p = cell.pos();
            if (p.getX() < 0 || p.getY() < 0 || p.getZ() < 0 || p.getX() >= size.getX()
                    || p.getY() >= size.getY() || p.getZ() >= size.getZ())
                throw new IllegalArgumentException("方块坐标越界: " + p);
            if (cell.state() < 0 || cell.state() >= palette.size())
                throw new IllegalArgumentException("调色板引用越界: " + cell.state());
            if (!occupied.add(p)) throw new IllegalArgumentException("重复方块坐标: " + p);
        }
    }

    /** 逐格记录保留完整 NBT；位置为结构局部坐标。 */
    public record Cell(BlockPos pos, int state, @Nullable CompoundTag data) {
        public Cell { pos = pos.immutable(); data = data == null ? null : data.copy(); }
        /** 返回独立方块实体载荷。 */
        @Override public @Nullable CompoundTag data() { return data == null ? null : data.copy(); }
    }

    /** 实体位置、锚点和包含 Rotation 的原始载荷。 */
    public record Actor(Vec3 pos, BlockPos blockPos, CompoundTag data) {
        public Actor {
            blockPos = blockPos.immutable();
            data = data.copy();
            if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z))
                throw new IllegalArgumentException("实体位置包含非有限数值");
        }
        /** 返回独立实体载荷。 */
        @Override public CompoundTag data() { return data.copy(); }
    }

    /** 解码 Minecraft 1.21.1 structure NBT，在索引前校验尺寸、坐标与调色板引用。 */
    public static PreviewStructure decode(CompoundTag root) {
        BlockPos size = position(root.getList("size", Tag.TAG_INT));
        validateSize(size);
        ListTag rawPalette = root.contains("palettes", Tag.TAG_LIST)
                ? root.getList("palettes", Tag.TAG_LIST).getList(0)
                : root.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < rawPalette.size(); i++) {
            CompoundTag state = rawPalette.getCompound(i);
            if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(state.getString("Name"))))
                throw new IllegalArgumentException("未注册方块: " + state.getString("Name"));
            palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), state));
        }
        List<Cell> blocks = new ArrayList<>();
        for (Tag tag : root.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag cell = (CompoundTag) tag;
            blocks.add(new Cell(position(cell.getList("pos", Tag.TAG_INT)), cell.getInt("state"),
                    cell.contains("nbt", Tag.TAG_COMPOUND) ? cell.getCompound("nbt") : null));
        }
        List<Actor> entities = new ArrayList<>();
        for (Tag tag : root.getList("entities", Tag.TAG_COMPOUND)) {
            CompoundTag actor = (CompoundTag) tag;
            ListTag pos = actor.getList("pos", Tag.TAG_DOUBLE);
            if (pos.size() != 3 || !actor.contains("nbt", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("结构实体缺少位置或载荷");
            entities.add(new Actor(new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2)),
                    position(actor.getList("blockPos", Tag.TAG_INT)), actor.getCompound("nbt")));
        }
        return new PreviewStructure(size, palette, blocks, entities);
    }

    private static BlockPos position(ListTag values) {
        if (values.size() != 3) throw new IllegalArgumentException("结构坐标必须包含三个整数");
        return new BlockPos(values.getInt(0), values.getInt(1), values.getInt(2));
    }

    private static void validateSize(BlockPos size) {
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0
                || size.getX() > MAX_CELLS || size.getY() > MAX_CELLS || size.getZ() > MAX_CELLS
                || (long) size.getX() * size.getY() > MAX_CELLS
                || (long) size.getX() * size.getY() * size.getZ() > MAX_CELLS)
            throw new IllegalArgumentException("结构尺寸超限: " + size);
    }
}
