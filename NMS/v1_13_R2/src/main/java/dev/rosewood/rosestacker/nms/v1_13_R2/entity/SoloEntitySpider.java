package dev.rosewood.rosestacker.nms.v1_13_R2.entity;

import net.minecraft.server.v1_13_R2.*;

public class SoloEntitySpider extends EntitySpider {

    public SoloEntitySpider(EntityTypes<? extends EntitySpider> var0, World var1) {
        super(var0, var1);
    }

    @Override
    public GroupDataEntity prepare(DifficultyDamageScaler var0, GroupDataEntity var1, NBTTagCompound var2) {
        return null;
    }

}
