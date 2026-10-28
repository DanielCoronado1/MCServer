package com.dolfin.lobby;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.impl.attachment.AttachmentRegistryImpl;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Persistent Fabric attachments that hold items outside the vanilla inventory (a worn Traveler's
 * Backpack). The escrow snapshot already serializes them, but {@code readAdditionalSaveData} does
 * not read attachments back, so they are detached during a session and decoded from the snapshot on
 * restore. Absent mods are skipped.
 */
final class IsolatedAttachments {
    private static final List<Identifier> IDS =
            List.of(Identifier.fromNamespaceAndPath("travelersbackpack", "travelers_backpack"));

    private IsolatedAttachments() {}

    static void clear(ServerPlayer p) {
        for (var id : IDS) {
            var type = AttachmentRegistryImpl.get(id);
            if (type != null) p.removeAttached(type);
        }
        synchronise(p);
    }

    static void restore(ServerPlayer p, CompoundTag survival) {
        var saved = survival.getCompoundOrEmpty(AttachmentTarget.NBT_ATTACHMENT_KEY);
        var ops = p.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (var id : IDS) {
            var type = AttachmentRegistryImpl.get(id);
            var tag = saved.get(id.toString());
            if (type != null && type.isPersistent() && tag != null) decode(p, type, ops, tag);
        }
        synchronise(p);
    }

    private static <A> void decode(
            ServerPlayer p,
            AttachmentType<A> type,
            com.mojang.serialization.DynamicOps<Tag> ops,
            Tag tag) {
        A value =
                type.persistenceCodec()
                        .parse(ops, tag)
                        .getOrThrow(
                                e ->
                                        new IllegalStateException(
                                                "No se pudo restaurar "
                                                        + type.identifier()
                                                        + ": "
                                                        + e));
        p.setAttached(type, value);
    }

    /** Traveler's Backpack keeps the worn backpack in sync through its own packet. */
    private static void synchronise(ServerPlayer p) {
        try {
            Class.forName("com.tiviacz.travelersbackpack.attachment.AttachmentUtils")
                    .getMethod("synchronise", net.minecraft.world.entity.player.Player.class)
                    .invoke(null, p);
        } catch (ClassNotFoundException absent) {
            // Mod not installed.
        } catch (ReflectiveOperationException e) {
            DolfinLobby.LOGGER.warn("event=attachment_sync_failed player={}", p.getUUID(), e);
        }
    }
}
