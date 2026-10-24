package com.meteorite.itemdespawntowhat.core.runtime;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** 开发夹具在真实 SavedData 边界构造中断库存，不扩大生产账本的写接口。 */
public final class ProbeLedgerAccess {
    private static final String ID = Constants.ID_NAMESPACE + "_probe_restart";

    private ProbeLedgerAccess() {}

    /** 停服前登记七件待交付库存；下次进程由正式恢复器读取并交付。 */
    public static void prepare(MinecraftServer server) {
        ItemStack stack = new ItemStack(Items.PAPER, 7);
        CompoundTag marker = new CompoundTag();
        marker.putBoolean(ID, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        var writer = SettlementRecord.Writer.register(SettlementLedger.get(server.overworld()), ID,
                Constants.ID_NAMESPACE + "_probe:restart", "minecraft:overworld", stack, 7, 1, true, 8, 100, 8);
        writer.interrupt("probe_restart_fixture", server.overworld().getGameTime());
    }

    /** 读取真实重启后的账目快照，守恒式与实物数由调用方独立核对。 */
    public static JsonObject snapshot(MinecraftServer server) {
        JsonObject result = new JsonObject();
        var record = SettlementLedger.get(server.overworld()).find(ID);
        result.addProperty("exists", record != null);
        if (record != null) {
            result.addProperty("initial", record.initialCount());
            result.addProperty("consumed", record.consumedSources());
            result.addProperty("delivered", record.deliveredReturns());
            result.addProperty("pending", record.pendingDelivery());
            result.addProperty("completed", record.status() == SettlementRecord.Status.COMPLETED);
        }
        return result;
    }

    /** 仅识别本夹具返还模板，不给实体增加影响合并的唯一组件。 */
    public static boolean marked(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag marker = new CompoundTag();
        marker.putBoolean(ID, true);
        return data != null && data.matchedBy(marker);
    }
}
