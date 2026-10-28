package com.dolfin.lobby.mixin;

import com.dolfin.lobby.StationProtection;
import com.dolfin.lobby.StationSessions;
import com.mojang.brigadier.ParseResults;

import net.minecraft.commands.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(Commands.class)
public abstract class CommandIsolationMixin {
    // EasyAuth congela al jugador hasta /login; bloquearlos lo deja atrapado en la estación.
    private static final Set<String> ALLOWED =
            Set.of("lobby", "exhibicion", "login", "l", "register", "reg", "logout", "account");

    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void guard(ParseResults<CommandSourceStack> parse, String text, CallbackInfo ci) {
        var p = parse.getContext().getSource().getPlayer();
        if (p != null && StationProtection.isolated(p)) {
            String root = text.strip().split(" ", 2)[0].replaceFirst("^/", "");
            if (!ALLOWED.contains(root)) {
                StationSessions.message(
                        p,
                        "Durante la demostración usa /lobby o /exhibicion. Vuelve a supervivencia"
                                + " para usar otros comandos.");
                ci.cancel();
            }
        }
    }
}
