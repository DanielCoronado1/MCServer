package com.dolfin.space.mixin;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.KeplerDimension;
import com.dolfin.space.SpaceTravelAccess;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.ExecuteCommand;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExecuteCommand.class)
public abstract class ExecuteCommandMixin {
    @Inject(method = "lambda$register$11", at = @At("RETURN"))
    private static void dolfinspace$markExecuteInDimension(
            CommandContext<CommandSourceStack> context,
            CallbackInfoReturnable<CommandSourceStack> cir) {
        CommandSourceStack returned = cir.getReturnValue();
        if (returned != null && KeplerDimension.isKepler(returned.getLevel().dimension())) {
            SpaceTravelAccess.markExecuteInKeplerActive();
            DolfinSpace.LOGGER.info(
                    "event=kepler_execute_in actor={} dimension={}",
                    returned.getTextName(),
                    returned.getLevel().dimension().identifier());
        }
    }
}
