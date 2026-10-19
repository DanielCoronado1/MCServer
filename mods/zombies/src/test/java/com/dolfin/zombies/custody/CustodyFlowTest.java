package com.dolfin.zombies.custody;

import static org.junit.jupiter.api.Assertions.*;

import com.dolfin.zombies.custody.CustodyLedger.*;
import com.dolfin.zombies.custody.CustodyLedger.Record;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.junit.jupiter.api.*;

import java.util.*;

/**
 * Issue #7 first step: no item is lost or duplicated between a player's death and opening the
 * Contagiado chest.
 */
class CustodyFlowTest {
    /**
     * Stand-in for ItemStack: identity key, size and the vanishing-curse flag vanilla destroys on
     * death.
     */
    record S(String id, int count, boolean vanishing) {
        static final Codec<S> CODEC =
                RecordCodecBuilder.create(
                        i ->
                                i.group(
                                                Codec.STRING.fieldOf("id").forGetter(S::id),
                                                Codec.INT.fieldOf("count").forGetter(S::count),
                                                Codec.BOOL
                                                        .fieldOf("vanishing")
                                                        .forGetter(S::vanishing))
                                        .apply(i, S::new));

        static S empty() {
            return new S("minecraft:air", 0, false);
        }
    }

    static final ItemAccounting.Stacks<S> OPS =
            new ItemAccounting.Stacks<>() {
                public boolean isEmpty(S s) {
                    return s.count() <= 0;
                }

                public int count(S s) {
                    return s.count();
                }

                public String key(S s) {
                    return s.id();
                }
            };
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final Position HERE = new Position("minecraft:overworld", 10, 64, -5);

    CustodyLedger ledger;
    List<String> log;

    @BeforeEach
    void setup() {
        ledger = new CustodyLedger();
        log = new ArrayList<>();
        ledger.listener((from, r, why) -> log.add(from + ">" + r.holder() + ":" + why));
    }

    static <T> T roundTrip(Codec<T> codec, T value) {
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        return codec.parse(
                        JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(json.toString()))
                .getOrThrow();
    }

    static List<S> fullInventory() {
        List<S> inv = new ArrayList<>(Collections.nCopies(41, S.empty()));
        for (int i = 0; i < 36; i++) inv.set(i, new S("minecraft:item_" + (i % 9), 1 + i, false));
        inv.set(3, S.empty());
        inv.set(20, new S("minecraft:cursed_sword", 1, true));
        inv.set(36, new S("minecraft:diamond_boots", 1, false));
        inv.set(37, new S("minecraft:diamond_leggings", 1, false));
        inv.set(38, new S("minecraft:diamond_chestplate", 1, false));
        inv.set(39, new S("minecraft:diamond_helmet", 1, false));
        inv.set(40, new S("minecraft:shield", 1, false));
        return inv;
    }

    Record open(UUID id, Holder holder, int items, int stacks) {
        var r =
                new Record(
                        id,
                        OWNER,
                        "Steve",
                        holder,
                        Optional.of(UUID.randomUUID()),
                        "contagiado_zombie",
                        HERE,
                        items,
                        stacks,
                        "",
                        0,
                        0);
        assertTrue(ledger.open(r, "test").ok());
        return r;
    }

    @Test
    void infectedDeathToChestKeepsEveryItemExactlyOnce() {
        var inventory = fullInventory();
        var expected =
                ItemAccounting.tally(OPS, inventory.stream().filter(s -> !s.vanishing()).toList());
        int expectedCount =
                ItemAccounting.count(OPS, inventory.stream().filter(s -> !s.vanishing()).toList());

        // Death: copy captured slots into custody, then clear them (vanishing items stay for
        // vanilla to destroy).
        var slots = ItemAccounting.capture(OPS, inventory, S::vanishing);
        List<S> held = new ArrayList<>();
        for (int i : slots) held.add(inventory.get(i));
        var id = UUID.randomUUID();
        var packet = new ItemAccounting.Packet<>(id, OWNER, "Steve", held);
        open(id, Holder.INFECTED, ItemAccounting.count(OPS, held), held.size());
        for (int i : slots) inventory.set(i, S.empty());
        assertEquals(
                1,
                ItemAccounting.count(OPS, inventory),
                "only the vanishing item remains for vanilla");

        // Server restart: entity NBT packet and SavedData ledger both round-trip.
        var restored = roundTrip(ItemAccounting.Packet.codec(S.CODEC), packet);
        var restoredLedger = roundTrip(CustodyLedger.CODEC, ledger);
        assertEquals(packet, restored);
        assertEquals(Holder.INFECTED, restoredLedger.get(id).orElseThrow().holder());

        // Zombie killed: chest placed, every held stack lands in chest halves or overflow.
        assertTrue(
                restoredLedger
                        .move(
                                id,
                                Holder.INFECTED,
                                Holder.CHEST,
                                Optional.empty(),
                                HERE,
                                100,
                                "killed")
                        .ok());
        var plan = ItemAccounting.chestPlan(OPS, restored.items(), true);
        List<S> opened = new ArrayList<>(plan.first());
        opened.addAll(plan.second());
        opened.addAll(plan.overflow());
        assertTrue(plan.needsDouble());
        assertTrue(plan.overflow().isEmpty());
        assertEquals(expected, ItemAccounting.tally(OPS, opened));
        assertEquals(expectedCount, ItemAccounting.count(OPS, opened));

        // A duplicated zombie (e.g. restored chunk) cannot release the same custody again.
        var dup =
                restoredLedger.move(
                        id,
                        Holder.INFECTED,
                        Holder.CHEST,
                        Optional.empty(),
                        HERE,
                        101,
                        "killed_again");
        assertEquals(Outcome.REJECTED, dup.outcome());
        assertEquals(Holder.CHEST, dup.previous());
    }

    @Test
    void singleChestOverflowIsAccountedNotLost() {
        List<S> held = new ArrayList<>();
        for (int i = 0; i < 40; i++) held.add(new S("minecraft:stone", 64, false));
        var plan = ItemAccounting.chestPlan(OPS, held, false);
        assertEquals(27, plan.first().size());
        assertTrue(plan.second().isEmpty());
        assertEquals(13, plan.overflow().size());
        assertEquals(
                40 * 64,
                ItemAccounting.count(OPS, plan.first())
                        + ItemAccounting.count(OPS, plan.overflow()));
    }

    @Test
    void keepInventoryCapturesNothing() {
        var inventory = fullInventory();
        // With keepInventory the conversion copies nothing: the capture filter excludes everything.
        assertTrue(ItemAccounting.capture(OPS, inventory, s -> true).isEmpty());
    }

    @Test
    void thiefSurvivesRestartAndDropsOnce() {
        var stolen = new S("minecraft:netherite_sword", 1, false);
        var id = UUID.randomUUID();
        open(id, Holder.THIEF, 1, 1);
        var packet =
                roundTrip(
                        ItemAccounting.Packet.codec(S.CODEC),
                        new ItemAccounting.Packet<>(id, OWNER, "Steve", List.of(stolen)));
        var restored = roundTrip(CustodyLedger.CODEC, ledger);
        assertEquals(List.of(stolen), packet.items());
        assertEquals(Holder.THIEF, restored.get(id).orElseThrow().holder());
        assertTrue(
                restored.move(
                                id,
                                Holder.THIEF,
                                Holder.GROUND,
                                Optional.empty(),
                                HERE,
                                12000,
                                "timeout")
                        .ok());
        assertEquals(
                Outcome.REJECTED,
                restored.move(
                                id,
                                Holder.THIEF,
                                Holder.GROUND,
                                Optional.empty(),
                                HERE,
                                12001,
                                "death")
                        .outcome());
        assertTrue(restored.active().isEmpty());
    }

    @Test
    void unsafeDeathGoesToMailboxThenReturnsOnce() {
        var id = UUID.randomUUID();
        open(id, Holder.THIEF, 1, 1);
        assertTrue(
                ledger.move(id, Holder.THIEF, Holder.MAILBOX, Optional.empty(), HERE, 5, "lava")
                        .ok());
        assertEquals(1, ledger.active().size(), "mailbox is still custody until delivered");
        assertTrue(
                ledger.move(id, Holder.MAILBOX, Holder.RETURNED, Optional.empty(), HERE, 6, "login")
                        .ok());
        assertFalse(
                ledger.move(id, Holder.MAILBOX, Holder.RETURNED, Optional.empty(), HERE, 7, "login")
                        .ok());
        assertEquals(
                List.of("PLAYER>THIEF:test", "THIEF>MAILBOX:lava", "MAILBOX>RETURNED:login"), log);
    }

    @Test
    void illegalAndUnknownTransitionsAreRefused() {
        var id = UUID.randomUUID();
        open(id, Holder.THIEF, 1, 1);
        assertEquals(
                Outcome.REJECTED,
                ledger.move(id, Holder.THIEF, Holder.CHEST, Optional.empty(), HERE, 1, "x")
                        .outcome());
        assertEquals(
                Outcome.MISSING,
                ledger.move(
                                UUID.randomUUID(),
                                Holder.THIEF,
                                Holder.GROUND,
                                Optional.empty(),
                                HERE,
                                1,
                                "x")
                        .outcome());
        assertEquals(
                Outcome.REJECTED,
                ledger.open(
                                new Record(
                                        id,
                                        OWNER,
                                        "Steve",
                                        Holder.THIEF,
                                        Optional.empty(),
                                        "k",
                                        HERE,
                                        1,
                                        1,
                                        "",
                                        0,
                                        0),
                                "again")
                        .outcome());
        assertEquals(
                Outcome.REJECTED,
                ledger.open(
                                new Record(
                                        UUID.randomUUID(),
                                        OWNER,
                                        "Steve",
                                        Holder.GROUND,
                                        Optional.empty(),
                                        "k",
                                        HERE,
                                        1,
                                        1,
                                        "",
                                        0,
                                        0),
                                "x")
                        .outcome());
    }

    @Test
    void trackingUpdatesPositionWithoutStateChange() {
        var id = UUID.randomUUID();
        open(id, Holder.INFECTED, 5, 2);
        var there = new Position("minecraft:the_nether", 1, 2, 3);
        assertTrue(ledger.track(id, there, 9));
        assertEquals(there, ledger.get(id).orElseThrow().position());
        assertEquals(1, log.size());
        assertEquals(1, ledger.visibleTo(OWNER::equals).size());
        assertTrue(ledger.visibleTo(u -> false).isEmpty());
    }

    @Test
    void terminalHistoryIsPrunedButActiveNever() {
        var keep = UUID.randomUUID();
        open(keep, Holder.INFECTED, 1, 1);
        for (int i = 0; i < CustodyLedger.TERMINAL_HISTORY + 20; i++) {
            var id = UUID.randomUUID();
            open(id, Holder.THIEF, 1, 1);
            ledger.move(id, Holder.THIEF, Holder.GROUND, Optional.empty(), HERE, i, "t");
        }
        assertTrue(ledger.get(keep).isPresent());
        assertEquals(CustodyLedger.TERMINAL_HISTORY + 1, ledger.records().size());
    }

    @Test
    void stealSlotPrefersHandThenHotbar() {
        List<Boolean> eligible = new ArrayList<>(Collections.nCopies(36, false));
        eligible.set(4, true);
        eligible.set(20, true);
        assertEquals(4, ItemAccounting.stealSlot(eligible, 4, 9, true, n -> 0));
        assertEquals(4, ItemAccounting.stealSlot(eligible, 0, 9, true, n -> 0));
        eligible.set(4, false);
        assertEquals(-1, ItemAccounting.stealSlot(eligible, 0, 9, true, n -> 0));
        assertEquals(20, ItemAccounting.stealSlot(eligible, 0, 9, false, n -> 0));
    }

    @Test
    void infectionStagesFollowTicket() {
        assertEquals(0, ItemAccounting.infectionStage(6000, 6000));
        assertEquals(0, ItemAccounting.infectionStage(3001, 6000));
        assertEquals(1, ItemAccounting.infectionStage(3000, 6000));
        assertEquals(1, ItemAccounting.infectionStage(1201, 6000));
        assertEquals(2, ItemAccounting.infectionStage(1200, 6000));
        assertEquals(2, ItemAccounting.infectionStage(1, 6000));
        assertEquals(0, ItemAccounting.infectionStage(9000, 6000));
    }

    @Test
    void protectedChestAllowsOwnerAndPartyUntilExpiryThenEveryone() {
        UUID partyMate = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        UUID stranger = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
        var second = new Position("minecraft:overworld", 11, 64, -5);
        var chest =
                roundTrip(
                        CustodyState.ProtectedChest.CODEC,
                        new CustodyState.ProtectedChest(
                                UUID.randomUUID(),
                                OWNER,
                                "Steve",
                                HERE,
                                Optional.of(second),
                                18000L));
        assertTrue(chest.matches("minecraft:overworld", 10, 64, -5));
        assertTrue(chest.matches("minecraft:overworld", 11, 64, -5));
        assertFalse(chest.matches("minecraft:the_nether", 10, 64, -5));
        assertTrue(chest.canAccess(OWNER, 100, (o, a) -> false));
        assertTrue(
                chest.canAccess(partyMate, 100, (o, a) -> o.equals(OWNER) && a.equals(partyMate)));
        assertFalse(chest.canAccess(stranger, 100, (o, a) -> false));
        assertTrue(
                chest.canAccess(stranger, 18000, (o, a) -> false), "after 15 min anyone can open");
    }

    @Test
    void chestSiteSearchAvoidsBlockedCellsAndFindsDoubleOrFallback() {
        // Center (0,64,0) is lava/claimed; (2,64,0) and (3,64,0) are valid adjacent blocks.
        var doubleSite =
                CustodyState.findChestSite(
                        0, 64, 0, 5, true, (x, y, z) -> y == 64 && (x == 2 || x == 3) && z == 0);
        assertTrue(doubleSite.isPresent());
        assertEquals(2, doubleSite.get().x());
        assertTrue(doubleSite.get().second().isPresent());
        assertArrayEquals(new int[] {3, 64, 0}, doubleSite.get().second().get());

        // No valid site within radius -> empty so caller falls back to persistent ground drop /
        // mailbox.
        assertTrue(CustodyState.findChestSite(0, 64, 0, 5, false, (x, y, z) -> false).isEmpty());
    }

    @Test
    void pendingMimoPreservesAppearanceAcrossDelayedSpawnAndRestart() {
        String profile =
                "{\"name\":\"Steve\",\"properties\":[{\"name\":\"textures\",\"value\":\"signed-skin-property\"}]}";
        var pending =
                roundTrip(
                        CustodyState.PendingMimo.codec(S.CODEC),
                        new CustodyState.PendingMimo<S>(
                                UUID.randomUUID(),
                                OWNER,
                                "Steve",
                                HERE,
                                20,
                                List.of(),
                                100,
                                12100,
                                profile));
        assertEquals(profile, pending.appearanceJson());
        var legacy =
                roundTrip(
                        CustodyState.PendingMimo.codec(S.CODEC),
                        new CustodyState.PendingMimo<S>(
                                UUID.randomUUID(),
                                OWNER,
                                "Steve",
                                HERE,
                                20,
                                List.of(),
                                100,
                                12100));
        assertEquals("", legacy.appearanceJson());
    }

    @Test
    void pendingMimoRoundTripsAndChecksRadiusAndHealth() {
        var armor = List.of(new S("minecraft:diamond_helmet", 1, false));
        var pending =
                roundTrip(
                        CustodyState.PendingMimo.codec(S.CODEC),
                        new CustodyState.PendingMimo<>(
                                UUID.randomUUID(),
                                OWNER,
                                "Steve",
                                HERE,
                                24.0,
                                armor,
                                100L,
                                12100L));
        assertFalse(pending.expired(5000L));
        assertTrue(pending.expired(12101L));
        assertTrue(pending.withinRange("minecraft:overworld", 10 + 30, 64, -5, 48.0));
        assertFalse(pending.withinRange("minecraft:overworld", 10 + 60, 64, -5, 48.0));
        assertFalse(pending.withinRange("minecraft:the_nether", 10, 64, -5, 48.0));
        assertEquals(36.0, CustodyState.mimoHealth(24.0, 30.0));
        assertEquals(30.0, CustodyState.mimoHealth(0.0, 30.0));
    }

    @Test
    void cureSelectorMatchesItemsAndTags() {
        var cures =
                List.of(
                        "minecraft:golden_apple",
                        "minecraft:enchanted_golden_apple",
                        "minecraft:milk_bucket",
                        "#c:foods/golden");
        assertTrue(ItemAccounting.matchesSelector("minecraft:milk_bucket", t -> false, cures));
        assertTrue(ItemAccounting.matchesSelector("minecraft:golden_apple", t -> false, cures));
        assertFalse(ItemAccounting.matchesSelector("minecraft:bread", t -> false, cures));
        assertTrue(
                ItemAccounting.matchesSelector(
                        "mod:custom_cure", t -> t.equals("c:foods/golden"), cures));
    }
}
