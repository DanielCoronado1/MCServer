package com.dolfin.creepers.dungeon.entrada;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/** Dónde estaba y en qué modo jugaba un jugador antes de entrar a una dungeon. */
public record RetornoGuardado(
        ResourceKey<Level> dimension,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        GameType modo) {
    public static final Codec<RetornoGuardado> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Level.RESOURCE_KEY_CODEC
                                                    .fieldOf("dimension")
                                                    .forGetter(RetornoGuardado::dimension),
                                            Codec.DOUBLE.fieldOf("x").forGetter(RetornoGuardado::x),
                                            Codec.DOUBLE.fieldOf("y").forGetter(RetornoGuardado::y),
                                            Codec.DOUBLE.fieldOf("z").forGetter(RetornoGuardado::z),
                                            Codec.FLOAT
                                                    .fieldOf("yaw")
                                                    .forGetter(RetornoGuardado::yaw),
                                            Codec.FLOAT
                                                    .fieldOf("pitch")
                                                    .forGetter(RetornoGuardado::pitch),
                                            GameType.CODEC
                                                    .fieldOf("modo")
                                                    .forGetter(RetornoGuardado::modo))
                                    .apply(i, RetornoGuardado::new));

    /** Captura la situación actual del jugador. */
    public static RetornoGuardado de(ServerPlayer jugador) {
        return new RetornoGuardado(
                jugador.level().dimension(),
                jugador.getX(),
                jugador.getY(),
                jugador.getZ(),
                jugador.getYRot(),
                jugador.getXRot(),
                jugador.gameMode());
    }
}
