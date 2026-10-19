package com.dolfin.space.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.dolfin.space.ships.ShipTier;
import com.dolfin.space.travel.FlightLedger;
import com.mojang.brigadier.CommandDispatcher;

import org.junit.jupiter.api.Test;

import java.util.*;

class SpaceCompatTest {
    @Test
    void nativeClaimsDecisionsAreNeverInvertedAndUnavailableFailsClosed() {
        assertTrue(CompatPolicy.allowed(false, true, null));
        assertTrue(CompatPolicy.allowed(true, false, null));
        assertTrue(CompatPolicy.allowed(true, true, false));
        assertFalse(CompatPolicy.allowed(true, true, true));
        assertFalse(CompatPolicy.allowed(true, true, null));
        for (var action : CompatPolicy.Action.values()) {
            assertFalse(CompatPolicy.allowed(true, true, true), action.name());
            assertTrue(CompatPolicy.allowed(true, true, false), action.name());
        }
    }

    @Test
    void cargoPlacementChecksClaimBordersAcrossEntirePlatform() {
        assertTrue(CompatPolicy.footprint(ShipTier.BASICA, (x, z) -> Math.abs(x) <= 1));
        assertFalse(CompatPolicy.footprint(ShipTier.CARGA, (x, z) -> Math.abs(x) <= 1));
        assertFalse(CompatPolicy.footprint(ShipTier.CARGA, (x, z) -> x != 2 || z != 2));
        assertTrue(CompatPolicy.footprint(ShipTier.CARGA, (x, z) -> true));
    }

    @Test
    void pvpRequiresGlobalTeamAndClaimPermission() {
        for (boolean global : List.of(false, true))
            for (boolean team : List.of(false, true))
                for (boolean claim : List.of(false, true))
                    assertEquals(global && team && claim, CompatPolicy.pvp(global, team, claim));
    }

    @Test
    void ledgerRoutesOnlySuccessfulKeplerEvents() throws Exception {
        var received = new ArrayList<SpaceLedgerIntegration.Audit>();
        for (var action : SpaceLedgerIntegration.Action.values()) {
            var e =
                    new SpaceLedgerIntegration.Audit(
                            action,
                            "dolfinspace:kepler",
                            123,
                            UUID.randomUUID(),
                            "Tester",
                            "minecraft:stone",
                            true);
            assertTrue(SpaceLedgerIntegration.route(e, received::add));
            assertFalse(
                    SpaceLedgerIntegration.route(
                            new SpaceLedgerIntegration.Audit(
                                    action,
                                    "minecraft:overworld",
                                    123,
                                    e.actor(),
                                    e.name(),
                                    e.object(),
                                    true),
                            received::add));
            assertFalse(
                    SpaceLedgerIntegration.route(
                            new SpaceLedgerIntegration.Audit(
                                    action,
                                    e.dimension(),
                                    123,
                                    e.actor(),
                                    e.name(),
                                    e.object(),
                                    false),
                            received::add));
            assertFalse(SpaceLedgerIntegration.route(e, null));
        }
        assertEquals(3, received.size());
        assertFalse(CompatPolicy.audit(false, true, true));
    }

    public interface FakeLedgerAction {
        String getIdentifier();

        String getSourceName();

        void setSourceName(String name);

        String getExtraData();

        boolean rollback(Object server);

        boolean getRolledBack();

        int getId();
    }

    @Test
    void reflectiveLedgerActionsRoundTripBeansAndAreAuditOnly() {
        var action =
                (FakeLedgerAction)
                        LedgerActionProxy.create(
                                FakeLedgerAction.class,
                                "space-open",
                                Map.of("SourceName", "Alice", "ExtraData", "{}"));
        assertEquals("space-open", action.getIdentifier());
        assertEquals("Alice", action.getSourceName());
        action.setSourceName("Bob");
        assertEquals("Bob", action.getSourceName());
        assertFalse(action.rollback(null));
        assertFalse(action.getRolledBack());
        assertEquals(0, action.getId());
        assertEquals("{}", action.getExtraData());
    }

    @Test
    void recoveryIsAdminOnlyOwnerScopedDurableAndIdempotent() {
        var owner = UUID.randomUUID();
        var other = UUID.randomUUID();
        var entries = new HashMap<String, FlightLedger.Entry>();
        entries.put(
                "a",
                new FlightLedger.Entry(
                        "a",
                        owner.toString(),
                        "dolfinspace:kepler",
                        123,
                        true,
                        false,
                        List.of("pilot"),
                        List.of("pilot", "guest")));
        entries.put(
                "b",
                new FlightLedger.Entry(
                        "b", other.toString(), "dolfinspace:kepler", 456, false, false, List.of()));
        assertEquals(0, RecoveryRequests.queue(false, owner, entries));
        assertFalse(entries.get("a").autoReturn());
        assertEquals(1, RecoveryRequests.queue(true, owner, entries));
        assertTrue(entries.get("a").autoReturn());
        assertFalse(entries.get("b").autoReturn());
        assertEquals(List.of("pilot", "guest"), entries.get("a").crew());
        assertEquals(List.of("pilot"), entries.get("a").pending());
        assertEquals(1, RecoveryRequests.queue(true, owner, entries));
    }

    @Test
    void commandTreeExecutesAdminRecoveryAndRejectsNonAdmin() throws Exception {
        var owner = UUID.randomUUID();
        var entries = new HashMap<String, FlightLedger.Entry>();
        entries.put(
                "a",
                new FlightLedger.Entry(
                        "a", owner.toString(), "dolfinspace:kepler", 123, false, false, List.of()));
        var dispatcher = new CommandDispatcher<Boolean>();
        dispatcher.register(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.<Boolean>literal("space")
                        .then(
                                RecoveryCommandTree.build(
                                        admin -> admin,
                                        com.mojang.brigadier.arguments.StringArgumentType.word(),
                                        ctx ->
                                                RecoveryRequests.queue(
                                                        ctx.getSource(), owner, entries))));
        assertThrows(
                com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("space nave recuperar Alice", false));
        assertFalse(entries.get("a").autoReturn());
        assertEquals(1, dispatcher.execute("space nave recuperar Alice", true));
        assertTrue(entries.get("a").autoReturn());
        String source =
                java.nio.file.Files.readString(
                        java.nio.file.Path.of("src/main/java/com/dolfin/space/SpaceCommands.java"));
        assertTrue(source.contains("literal(\"reload\")"));
        assertTrue(source.contains("literal(\"recargar\")"));
        assertTrue(source.contains(".then(com.dolfin.space.compat.SpaceCompat.recoveryTree())"));
    }

    @Test
    void hooksCoverNativeMenusFuelInteractionPlacementAndPostSuccessLedger() throws Exception {
        var root = java.nio.file.Path.of("src/main/java/com/dolfin/space");
        String mixin =
                java.nio.file.Files.readString(
                        root.resolve("compat/mixin/ContainerAccessMixin.java"));
        assertTrue(mixin.contains("canOpen"));
        assertTrue(mixin.contains("stillValid"));
        assertTrue(mixin.contains("ci.getReturnValue() != null"));
        assertTrue(
                java.nio.file.Files.readString(root.resolve("ships/ShipItem.java"))
                        .contains("SpaceClaims.shipPlacement"));
        assertTrue(
                java.nio.file.Files.readString(root.resolve("ships/FuelLoaderBlock.java"))
                        .contains("SpaceCompat.container"));
        assertTrue(
                java.nio.file.Files.readString(
                                root.resolve("compat/mixin/BlockPlacementMixin.java"))
                        .contains("consumesAction()"));
        String compat = java.nio.file.Files.readString(root.resolve("compat/SpaceCompat.java"));
        assertTrue(compat.contains("PlayerBlockBreakEvents.AFTER"));
        assertTrue(compat.contains("SpacePermissions.isAdmin(source)"));
    }
}
