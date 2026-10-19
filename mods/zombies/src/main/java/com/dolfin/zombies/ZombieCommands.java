package com.dolfin.zombies;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import java.util.*;

public final class ZombieCommands {
    private ZombieCommands(){}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        var root=Commands.literal("zombies");
        root.then(Commands.literal("recargar").requires(s->allowed(s,"admin.recargar",false)).executes(c->reload(c.getSource())));
        root.then(Commands.literal("invocar").requires(s->allowed(s,"admin.invocar",false))
            .then(kindArgument().executes(c->summon(c,1,null))
                .then(Commands.argument("cantidad",IntegerArgumentType.integer(1,64)).executes(c->summon(c,IntegerArgumentType.getInteger(c,"cantidad"),null))
                    .then(Commands.argument("jugador",EntityArgument.player()).executes(c->summon(c,IntegerArgumentType.getInteger(c,"cantidad"),EntityArgument.getPlayer(c,"jugador")))))));
        for(boolean active:new boolean[]{true,false})root.then(Commands.literal(active?"activar":"desactivar").requires(s->allowed(s,"admin.config",false)).then(kindArgument().executes(c->toggle(c,active))));
        root.then(Commands.literal("info").requires(s->allowed(s,"info",true)).then(kindArgument().executes(c->info(c))));
        root.then(Commands.literal("lunadesangre").requires(s->allowed(s,"admin.luna",false))
            .then(Commands.literal("iniciar").executes(c->moon(c.getSource(),true)))
            .then(Commands.literal("detener").executes(c->moon(c.getSource(),false))));
        root.then(Commands.literal("inspeccionar").requires(s->allowed(s,"admin.luna",false)).then(kindArgument().executes(c->{try{var result=SpawnRules.inspect(c.getSource().getLevel(),net.minecraft.core.BlockPos.containing(c.getSource().getPosition()),kind(c));c.getSource().sendSuccess(()->Component.literal(result),false);return 1;}catch(IllegalArgumentException e){c.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}})));
        root.then(Commands.literal("diagnostico").requires(s->allowed(s,"admin.luna",false)).executes(c->{BloodMoon.report();c.getSource().sendSuccess(()->Component.literal(BloodMoon.status()+" counters="+SpawnRules.counters()+" report=config/dolfinzombies/diagnostics/runtime.json"),false);return 1;}));
        root.then(Commands.literal("dondeesta").requires(s->allowed(s,"dondeesta",true)).executes(c->deferred(c.getSource(),7)));
        var node=dispatcher.register(root);dispatcher.register(Commands.literal("zb").redirect(node));
    }
    private static boolean allowed(CommandSourceStack s,String node,boolean open){return ZombiePermissions.allowed(s,"dolfinzombies."+node,open);}
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,String> kindArgument(){
        return Commands.argument("zombie",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(ZombieKind.values()).map(ZombieKind::id),b));
    }
    private static ZombieKind kind(CommandContext<CommandSourceStack> c){return ZombieKind.byId(StringArgumentType.getString(c,"zombie"));}
    private static int reload(CommandSourceStack source){
        var r=DolfinZombies.config().reload();DolfinZombies.LOGGER.info("event=config_reload actor={} {}",source.getTextName(),r.summary());
        source.sendSuccess(()->Component.literal("Config recargada: "+r.summary()+". Los zombies vivos conservan sus stats. Informe: config/dolfinzombies/diagnostics/last-reload.json"),true);
        for(var p:r.problems())source.sendFailure(Component.literal(p.toString()));return r.files();
    }
    private static int toggle(CommandContext<CommandSourceStack> c,boolean active){
        try{var k=kind(c);DolfinZombies.config().setActive(k,active);DolfinZombies.LOGGER.info("event=config_toggle actor={} id={} active={} generation={}",c.getSource().getTextName(),k.id(),active,DolfinZombies.config().current().generation());c.getSource().sendSuccess(()->Component.literal(k.id()+" activo="+active+" (guardado)"),true);return 1;}
        catch(Exception e){DolfinZombies.LOGGER.error("event=config_toggle_failed actor={} reason={}",c.getSource().getTextName(),e.toString(),e);c.getSource().sendFailure(Component.literal("No se pudo guardar: "+e.getMessage()));return 0;}
    }
    private static int summon(CommandContext<CommandSourceStack> c,int count,ServerPlayer target){
        var source=c.getSource();
        try{
            var k=kind(c);var snapshot=DolfinZombies.config().current();
            if(!snapshot.enabled(k)){source.sendFailure(Component.literal(k.id()+" está desactivado (activo en general.json o su archivo)."));return 0;}
            var level=target==null?source.getLevel():target.level();var pos=target==null?source.getPosition():target.position();int spawned=0;
            for(int i=0;i<count;i++){
                var zombie=DolfinZombies.type(k).create(level,EntitySpawnReason.COMMAND);if(zombie==null)continue;
                zombie.snapTo(pos.x+2+(i%8),pos.y,pos.z+2+(i/8));
                zombie.finalizeSpawn(level,level.getCurrentDifficultyAt(zombie.blockPosition()),EntitySpawnReason.COMMAND,null);
                if(level.addFreshEntity(zombie))spawned++;
            }
            int result=spawned;DolfinZombies.LOGGER.info("event=command_summon actor={} id={} requested={} spawned={} dimension={} pos={} generation={}",source.getTextName(),k.id(),count,result,level.dimension().identifier(),pos,snapshot.generation());
            source.sendSuccess(()->Component.literal("Invocados "+result+"/"+count+" "+k.id()+" generation="+snapshot.generation()),true);return result;
        }catch(IllegalArgumentException e){source.sendFailure(Component.literal(e.getMessage()));return 0;}
    }
    private static int info(CommandContext<CommandSourceStack> c){
        try{var k=kind(c);var s=DolfinZombies.config().current();var data=s.zombie(k);
            c.getSource().sendSuccess(()->Component.literal(k.displayName+" ("+k.id()+") generation="+s.generation()+" efectivoActivo="+s.enabled(k)+"\n"+data+"\nContra: "+counter(k)+". Mecánicas especiales pendientes (#4–#7); combate zombie normal por ahora."),false);return 1;
        }catch(IllegalArgumentException e){c.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}
    }
    private static String counter(ZombieKind k){return switch(k){
        case MINERO -> "eliminarlo antes de que entre; bloques duros";
        case BOMBERO -> "agua o resistencia al fuego";
        case GRANJERO -> "cercas e iluminar la granja";
        case CONGELADO -> "fuego y armadura de cuero";
        case ESCUDERO -> "atacar por detrás o usar hacha";
        case GRITON,BRUJA -> "priorizarlo antes del grupo";
        case LADRON -> "recuperar el objeto al derrotarlo";
        case ABISMO -> "golpear para romper el agarre";
        case BILIOSO -> "mantener distancia al morir";
        case EXPLORADOR -> "iluminar el área";
        default -> "distancia, escudo y zona iluminada";
    };}
    private static int moon(CommandSourceStack s,boolean start){
        var r=start?BloodMoon.start(s.getTextName()):BloodMoon.stop(s.getTextName());
        String text=switch(r){
            case STARTED -> "Luna de sangre iniciada.";
            case STOPPED -> "Luna de sangre detenida.";
            case CANCELLED -> "Luna de sangre de esta noche cancelada.";
            case ALREADY_ACTIVE -> "La luna de sangre ya está activa.";
            case NOT_ACTIVE -> "No hay luna de sangre activa ni programada para esta noche.";
            case DISABLED -> "La luna de sangre está desactivada (activo en general.json o lunaDeSangre.activo).";
            case NOT_LOADED -> "El servidor aún no ha cargado el estado de la luna de sangre.";
        }+" "+BloodMoon.status();
        boolean ok=r==BloodMoon.CommandResult.STARTED||r==BloodMoon.CommandResult.STOPPED||r==BloodMoon.CommandResult.CANCELLED;
        if(ok)s.sendSuccess(()->Component.literal(text),true);else s.sendFailure(Component.literal(text));return ok?1:0;
    }
    private static int deferred(CommandSourceStack s,int issue){s.sendFailure(Component.literal("Función pendiente del issue #"+issue+"; todavía no está implementada."));return 0;}
}
