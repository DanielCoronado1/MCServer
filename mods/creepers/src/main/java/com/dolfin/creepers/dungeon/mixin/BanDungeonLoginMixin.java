package com.dolfin.creepers.dungeon.mixin;

import com.dolfin.creepers.dungeon.muerte.BanHardcore;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserBanListEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;
import java.time.Instant;

/**
 * Quien intenta entrar con un ban de hardcore vigente ve el motivo y los minutos que le faltan, en
 * vez de la fecha de vencimiento de la pantalla vanilla. Los demás rechazos no cambian.
 */
@Mixin(PlayerList.class)
public abstract class BanDungeonLoginMixin {
    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void dolfin$tiempoRestanteDeBan(
            SocketAddress direccion, NameAndId usuario, CallbackInfoReturnable<Component> cir) {
        if (cir.getReturnValue() == null) {
            return;
        }
        UserBanListEntry ban = ((PlayerList) (Object) this).getBans().get(usuario);
        if (ban != null && BanHardcore.esDeDungeon(ban)) {
            cir.setReturnValue(BanHardcore.mensajeAlConectar(ban, Instant.now()));
        }
    }
}
