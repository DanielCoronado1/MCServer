package com.dolfin.space.mixin;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.KeplerDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @Inject(method = "isValidSpawnPostitionForType", at = @At("HEAD"), cancellable = true)
    private static void dolfinspace$filterVanillaMobsInKepler(
            ServerLevel level,
            MobCategory category,
            StructureManager structureManager,
            ChunkGenerator generator,
            MobSpawnSettings.SpawnerData data,
            BlockPos.MutableBlockPos pos,
            double distance,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!DolfinSpace.config().current().general().activo()) {
            return;
        }
        if (!KeplerDimension.isKepler(level.dimension())
                || !DolfinSpace.config().current().dimension().sinMobsVanilla()) {
            return;
        }
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(data.type());
        if (!KeplerDimension.isAllowedKeplerNaturalSpawnId(typeId.toString())) {
            DolfinSpace.LOGGER.debug(
                    "event=kepler_vanilla_spawn_blocked entityType={} pos={} reason=non_space_mob",
                    typeId,
                    pos
            );
            cir.setReturnValue(false);
        }
    }
}
