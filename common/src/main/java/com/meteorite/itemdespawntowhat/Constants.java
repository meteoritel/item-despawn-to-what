package com.meteorite.itemdespawntowhat;

import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Constants {

    public static final String MOD_ID = "itemdespawntowhat";
    public static final String MOD_NAME = "Item Despawn To What";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    public static final String CHECK_LOCK_TAG = MOD_ID + ":check_lock";

    public static int blockPlaceIntervalTicks = 1;
    public static int lightningIntervalTicks = 8;
    public static int explosionIntervalTicks = 8;
    public static int arrowIntervalTicks = 2;
    public static List<String> entityScaleOverrides = List.of();

    public static float getEntityScale(ResourceLocation id, float defaultScale) {
        String prefix = id.toString() + "=";
        for (String entry : entityScaleOverrides) {
            String cleanEntry = entry.replaceAll("\\s+", "");
            if (cleanEntry.startsWith(prefix)) {
                return SafeParseUtil.parseFloat(cleanEntry.substring(prefix.length()).trim(), defaultScale);
            }
        }
        return defaultScale;
    }
}
