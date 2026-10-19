package com.dolfin.zombies.griefing;

import com.dolfin.zombies.DolfinZombies;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;

/**
 * Optional Ledger bridge, by reflection so Ledger stays a soft dependency. Fires Ledger's own
 * callbacks ({@code
 * com.github.quiltservertools.ledger.callbacks.Block{Break,Place,Change}Callback.EVENT}) with the
 * zombie id as the {@code source}, so rows show {@code @minero_zombie} etc. and {@code /ledger
 * rollback source:@minero_zombie} reverts them. Verified against ledger-1.3.25 (26.3).
 */
final class LedgerLog {
    private LedgerLog() {}

    enum State {
        PRESENT,
        ABSENT,
        ERROR
    }

    private static State state = State.ABSENT;
    private static Object breakEvent, placeEvent, changeEvent;
    private static Method breakM, placeM, changeM;
    private static boolean runtimeErrorLogged;
    private static final String P = "com.github.quiltservertools.ledger.callbacks.";

    static State init() {
        if (!FabricLoader.getInstance().isModLoaded("ledger")) {
            state = State.ABSENT;
            return state;
        }
        try {
            Class<?> b = Class.forName(P + "BlockBreakCallback"),
                    p = Class.forName(P + "BlockPlaceCallback"),
                    c = Class.forName(P + "BlockChangeCallback");
            breakEvent = b.getField("EVENT").get(null);
            placeEvent = p.getField("EVENT").get(null);
            changeEvent = c.getField("EVENT").get(null);
            breakM =
                    b.getMethod(
                            "breakBlock",
                            Level.class,
                            BlockPos.class,
                            BlockState.class,
                            BlockEntity.class,
                            String.class,
                            Player.class);
            placeM =
                    p.getMethod(
                            "place",
                            Level.class,
                            BlockPos.class,
                            BlockState.class,
                            BlockEntity.class,
                            String.class,
                            Player.class);
            changeM =
                    c.getMethod(
                            "changeBlock",
                            Level.class,
                            BlockPos.class,
                            BlockState.class,
                            BlockState.class,
                            BlockEntity.class,
                            BlockEntity.class,
                            String.class,
                            Player.class);
            state = State.PRESENT;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            state = State.ERROR;
            DolfinZombies.LOGGER.warn(
                    "event=integration_error name=ledger reason={} effect=no_ledger_rows",
                    e.toString());
        }
        return state;
    }

    static State state() {
        return state;
    }

    static void broke(Level level, BlockPos pos, BlockState old, BlockEntity be, String source) {
        call(breakEvent, breakM, level, pos.immutable(), old, be, source, null);
    }

    static void placed(Level level, BlockPos pos, BlockState now, String source) {
        call(placeEvent, placeM, level, pos.immutable(), now, null, source, null);
    }

    static void changed(
            Level level,
            BlockPos pos,
            BlockState old,
            BlockState now,
            BlockEntity oldBe,
            String source) {
        call(changeEvent, changeM, level, pos.immutable(), old, now, oldBe, null, source, null);
    }

    private static void call(Object event, Method m, Object... args) {
        if (state != State.PRESENT) return;
        try {
            m.invoke(((Event<?>) event).invoker(), args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!runtimeErrorLogged) {
                runtimeErrorLogged = true;
                DolfinZombies.LOGGER.warn(
                        "event=integration_error name=ledger phase=log reason={}", e.toString());
            }
        }
    }
}
