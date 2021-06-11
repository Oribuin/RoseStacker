package dev.rosewood.rosestacker.nms.v1_17_R1.entity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumMobSpawn;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.monster.EntityStrider;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;

public class SoloEntityStrider extends EntityStrider {

    public SoloEntityStrider(EntityTypes<? extends EntityStrider> var0, World var1) {
        super(var0, var1);
    }

    @Override
    public GroupDataEntity prepare(WorldAccess var0, DifficultyDamageScaler var1, EnumMobSpawn var2, GroupDataEntity var3, NBTTagCompound var4) {
        return null;
    }

}
