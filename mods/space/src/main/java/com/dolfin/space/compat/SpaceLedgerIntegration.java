package com.dolfin.space.compat;

import com.dolfin.space.*;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/**
 * Optional Ledger 1.3.25 adapter. Its native block rollback log is retained alongside our audit.
 */
public final class SpaceLedgerIntegration {
    public enum Action {
        BREAK("space-break"),
        PLACE("space-place"),
        CONTAINER("space-open");
        public final String id;

        Action(String id) {
            this.id = id;
        }
    }

    public record Audit(
            Action action,
            String dimension,
            long position,
            UUID actor,
            String name,
            String object,
            boolean successful) {}

    @FunctionalInterface
    public interface Backend {
        void write(Audit event) throws Exception;
    }

    private static Backend backend;

    public static boolean route(Audit audit, Backend sink) throws Exception {
        if (!CompatPolicy.audit(
                        true, audit.dimension().equals("dolfinspace:kepler"), audit.successful())
                || sink == null) return false;
        sink.write(audit);
        return true;
    }

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("ledger")) {
            status("absent", "");
            return;
        }
        try {
            Class<?> actionApi =
                    Class.forName("com.github.quiltservertools.ledger.actions.ActionType");
            Class<?> ledger = Class.forName("com.github.quiltservertools.ledger.Ledger");
            Method getApi = ledger.getMethod("getApi"),
                    log =
                            Class.forName("com.github.quiltservertools.ledger.api.LedgerApi")
                                    .getMethod("logAction", actionApi);
            Class<?> registry =
                    Class.forName("com.github.quiltservertools.ledger.registry.ActionRegistry");
            Object instance = registry.getField("INSTANCE").get(null);
            for (Action action : Action.values())
                registry.getMethod("registerActionType", Supplier.class)
                        .invoke(
                                instance,
                                (Supplier<Object>)
                                        () ->
                                                LedgerActionProxy.create(
                                                        actionApi, action.id, defaults()));
            backend =
                    a -> {
                        var values = defaults();
                        values.put("Timestamp", Instant.now());
                        values.put("Pos", BlockPos.of(a.position()));
                        values.put("World", Identifier.parse(a.dimension()));
                        values.put("ObjectIdentifier", Identifier.parse(a.object()));
                        values.put("SourceName", a.name());
                        values.put("SourceProfile", new NameAndId(a.actor(), a.name()));
                        values.put(
                                "ExtraData",
                                "{\"module\":\"dolfinspace\",\"action\":\"" + a.action() + "\"}");
                        log.invoke(
                                getApi.invoke(null),
                                LedgerActionProxy.create(actionApi, a.action().id, values));
                    };
            status("active", "");
        } catch (ReflectiveOperationException | LinkageError e) {
            backend = null;
            status("unavailable", e.toString());
        }
    }

    private static Map<String, Object> defaults() {
        var v = new HashMap<String, Object>();
        v.put("Timestamp", Instant.now());
        v.put("Pos", BlockPos.ZERO);
        v.put("World", Identifier.withDefaultNamespace("overworld"));
        v.put("ObjectIdentifier", Identifier.withDefaultNamespace("air"));
        v.put("OldObjectIdentifier", Identifier.withDefaultNamespace("air"));
        v.put("ObjectState", "");
        v.put("OldObjectState", "");
        v.put("SourceName", "dolfinspace");
        v.put("ExtraData", "{}");
        v.put("TranslationType", "space.audit");
        v.put("Message", Component.translatable("space.audit"));
        return v;
    }

    public static void record(
            Action action, Player p, ServerLevel world, BlockPos pos, String object) {
        try {
            route(
                    new Audit(
                            action,
                            world.dimension().identifier().toString(),
                            pos.asLong(),
                            p.getUUID(),
                            p.getName().getString(),
                            object,
                            true),
                    backend);
        } catch (Exception | LinkageError e) {
            backend = null;
            status("unavailable", e.toString());
        }
    }

    public static void container(Player p, ServerLevel world, BlockPos pos) {
        record(
                Action.CONTAINER,
                p,
                world,
                pos,
                BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos).getBlock()).toString());
    }

    private static void status(String state, String reason) {
        DolfinSpace.LOGGER.info("event=integration name=ledger state={} reason={}", state, reason);
    }

    private SpaceLedgerIntegration() {}
}
