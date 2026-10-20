package com.dolfin.zombies;

import com.dolfin.zombies.kinds.CombatParams;
import com.google.gson.*;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.*;
import net.minecraft.util.random.*;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;

import java.util.*;
import java.util.function.IntPredicate;

/**
 * Natural spawning of the special kinds. Rules are cached per config generation (swapped on
 * reload). {@link #pool} rewrites the MONSTER spawn list of every position (live weights/biomes,
 * blood-moon weights, soloEspeciales), and the SpawnPlacements predicate is the authoritative
 * per-position check including the per-player cap. Each rejection is counted and logged at DEBUG:
 * {@code event=spawn_rejected kind=.. reason=.. stage=placement}.
 */
public final class SpawnRules {
    private SpawnRules() {}

    /** Rejection reasons, in evaluation order. The ids are part of the log/inspect contract. */
    public enum Reason {
        DISABLED("disabled"),
        NEVER_NATURAL("mimo"),
        WORLD("world"),
        DAY("day"),
        HEIGHT("height"),
        LIGHT("light"),
        DWARF("dwarf"),
        BIOME("biome"),
        SNOW("snow"),
        WATER_DEPTH("water_depth"),
        PLAYER_CAP("player_cap");
        public final String id;

        Reason(String id) {
            this.id = id;
        }
    }

    public static final int DWARF_MAX_Y_EXCLUSIVE = 40, MIN_WATER_DEPTH = 5, CAP_RADIUS = 64;

    private static boolean isVanillaZombie(EntityType<?> t) {
        return t == EntityTypes.ZOMBIE
                || t == EntityTypes.ZOMBIE_VILLAGER
                || t == EntityTypes.HUSK
                || t == EntityTypes.DROWNED;
    }

    record Rule(
            boolean enabled,
            int weight,
            double bloodWeight,
            int min,
            int max,
            int low,
            int high,
            int light,
            List<String> include,
            List<String> exclude,
            int waterDepth) {
        static Rule from(ZombieKind k, JsonObject j, boolean enabled) {
            var kc = KindConfig.from(j);
            // Abismo may ask for deeper water through its grab ability setting, never shallower
            // than the ticket's 5 blocks.
            int depth =
                    k == ZombieKind.ABISMO
                            ? Math.max(
                                    MIN_WATER_DEPTH,
                                    CombatParams.Abismo.from(j).profundidadMinima())
                            : MIN_WATER_DEPTH;
            return new Rule(
                    enabled,
                    kc.peso(),
                    kc.bloodMoonPesoExtra(),
                    kc.minGrupo(),
                    kc.maxGrupo(),
                    kc.minAlturaY(),
                    kc.maxAlturaY(),
                    kc.luzMax(),
                    kc.biomas(),
                    kc.biomasExcluidos(),
                    depth);
        }
    }

    /** Lazily computed facts about one candidate position; a fake implements it in unit tests. */
    interface Probe {
        boolean worldAllowed();

        boolean night();

        int y();

        int blockLight();

        int skyLight();

        boolean biome(List<String> selectors);

        boolean snowy();

        int waterDepth(int needed);

        boolean capAllows();
    }

    /** Pure rule evaluation; null means eligible. */
    static Reason evaluate(ZombieKind k, Rule r, Probe p, boolean checkCap) {
        if (!r.enabled()) return Reason.DISABLED;
        if (k == ZombieKind.MIMO)
            return Reason.NEVER_NATURAL; // spawns only from player deaths (#7)
        if (!p.worldAllowed()) return Reason.WORLD;
        // Enano lives in total darkness below Y=40 where the clock is irrelevant; everyone else is
        // nocturnal.
        if (k != ZombieKind.ENANO && !p.night()) return Reason.DAY;
        if (p.y() < r.low() || p.y() > r.high()) return Reason.HEIGHT;
        if (p.blockLight() > r.light()) return Reason.LIGHT;
        if (k == ZombieKind.ENANO
                && (p.y() >= DWARF_MAX_Y_EXCLUSIVE || p.blockLight() != 0 || p.skyLight() != 0))
            return Reason.DWARF;
        if (!p.biome(r.include()) || p.biome(r.exclude())) return Reason.BIOME;
        if (k == ZombieKind.CONGELADO && !p.snowy()) return Reason.SNOW;
        if (k == ZombieKind.ABISMO && p.waterDepth(r.waterDepth()) < r.waterDepth())
            return Reason.WATER_DEPTH;
        if (checkCap && !p.capAllows()) return Reason.PLAYER_CAP;
        return null;
    }

    /**
     * Contiguous water column through the position (offset 0), counted up then down, stopping at
     * {@code needed}.
     */
    static int columnDepth(IntPredicate waterAt, int needed) {
        if (!waterAt.test(0)) return 0;
        int n = 1;
        for (int d = 1; n < needed && waterAt.test(d); d++) n++;
        for (int d = 1; n < needed && waterAt.test(-d); d++) n++;
        return n;
    }

    /**
     * Every non-spectator player within range of the position must be below the cap; cap 0 disables
     * special spawns.
     */
    static boolean capAllows(int cap, int[] nearbyCounts) {
        if (cap <= 0) return false;
        for (int n : nearbyCounts) if (n >= cap) return false;
        return true;
    }

    public static int effectiveWeight(int base, double multiplier, boolean blood) {
        return (int) Math.min(100000000, Math.round(base * (blood ? multiplier : 1)));
    }

    /**
     * soloEspeciales: the removed vanilla zombie weight is shared among eligible specials
     * proportionally to their weights.
     */
    static int[] substitute(int removed, int[] weights) {
        int[] out = weights.clone();
        long sum = 0;
        for (int w : weights) sum += w;
        if (removed <= 0 || sum == 0) return out;
        for (int i = 0; i < out.length; i++)
            out[i] =
                    (int)
                            Math.min(
                                    100000000,
                                    out[i] + Math.round((double) removed * weights[i] / sum));
        return out;
    }

    private static long generation = -1;
    private static final EnumMap<ZombieKind, Rule> rules = new EnumMap<>(ZombieKind.class);
    private static List<String> worlds = List.of();
    private static int cap;
    private static final long[][] placementCounts =
            new long[ZombieKind.values().length][Reason.values().length + 1];
    private static final long[][] poolCounts =
            new long[ZombieKind.values().length][Reason.values().length + 1];
    private static final Map<String, Long> spawnCounts = new TreeMap<>();
    private static final ArrayDeque<JsonObject> samples = new ArrayDeque<>();
    private static final Map<UUID, Integer> capCache = new HashMap<>();
    private static long capTick = Long.MIN_VALUE;
    private static final Map<EntityType<?>, ZombieKind> kinds = new IdentityHashMap<>();

    private static void refresh() {
        var s = DolfinZombies.config().current();
        if (s.generation() == generation) return;
        if (generation >= 0)
            DolfinZombies.LOGGER.info(
                    "event=spawn_counters generation={} counters={}", generation, counters());
        for (var row : placementCounts) Arrays.fill(row, 0);
        for (var row : poolCounts) Arrays.fill(row, 0);
        spawnCounts.clear();
        generation = s.generation();
        worlds = s.generalConfig().mundosPermitidos();
        cap = s.generalConfig().maxZombiesEspecialesPorJugador();
        var weights = new StringJoiner(",");
        for (var k : ZombieKind.values()) {
            var r = Rule.from(k, s.zombie(k), s.enabled(k));
            rules.put(k, r);
            weights.add(k.id() + "=" + (r.enabled() ? r.weight() : 0));
        }
        DolfinZombies.LOGGER.info(
                "event=spawn_rules_reloaded generation={} cap={} worlds={} weights={}",
                generation,
                cap,
                worlds,
                weights);
    }

    public static Map<String, Long> counters() {
        var out = new TreeMap<String, Long>(spawnCounts);
        var reasons = Reason.values();
        for (var k : ZombieKind.values())
            for (int i = 0; i <= reasons.length; i++) {
                String id = i == reasons.length ? "eligible" : reasons[i].id;
                if (placementCounts[k.ordinal()][i] > 0)
                    out.put(k.id() + ":" + id, placementCounts[k.ordinal()][i]);
                if (poolCounts[k.ordinal()][i] > 0)
                    out.put(k.id() + ":pool_" + id, poolCounts[k.ordinal()][i]);
            }
        return out;
    }

    public static List<JsonObject> samples() {
        return List.copyOf(samples);
    }

    public static void spawned(ConfiguredZombie z, EntitySpawnReason reason) {
        var o = new JsonObject();
        o.addProperty("id", z.kind().id());
        o.addProperty("uuid", z.getUUID().toString());
        o.addProperty("reason", reason.toString());
        o.addProperty("dimension", z.level().dimension().identifier().toString());
        o.addProperty("pos", z.blockPosition().toString());
        o.addProperty("generation", z.configGeneration());
        o.addProperty("health", z.getMaxHealth());
        o.addProperty("bloodmoon", BloodMoon.active());
        if (samples.size() == 32) samples.removeFirst();
        samples.addLast(o);
        spawnCounts.merge(z.kind().id() + ":spawned_" + reason, 1L, Long::sum);
        capCache.clear(); // the next candidate this tick must see this zombie
    }

    public static boolean worldAllowed(ServerLevel l) {
        refresh();
        return worlds.contains(l.dimension().identifier().toString());
    }

    private static boolean matches(Holder<Biome> biome, List<String> selectors) {
        for (var id : selectors)
            if (id.startsWith("#")
                    ? biome.is(TagKey.create(Registries.BIOME, Identifier.parse(id.substring(1))))
                    : biome.is(ResourceKey.create(Registries.BIOME, Identifier.parse(id))))
                return true;
        return false;
    }

    public static void init() {
        refresh();
        for (var k : ZombieKind.values()) {
            kinds.put(DolfinZombies.type(k), k);
            com.dolfin.zombies.mixin.SpawnPlacementsInvoker.register(
                    DolfinZombies.type(k),
                    k == ZombieKind.ABISMO
                            ? SpawnPlacementTypes.IN_WATER
                            : SpawnPlacementTypes.ON_GROUND,
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, l, reason, pos, random) ->
                            l.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                                    && check(l.getLevel(), pos, k) == null);
        }
        // Startup registration (ticket's first step). pool() replaces these entries per position
        // with the live
        // generation, so later reloads of weights/biomes apply without restart; only tools that
        // read the raw
        // biome spawn lists keep seeing the startup values.
        for (var k : ZombieKind.values()) {
            var r = rules.get(k);
            if (k != ZombieKind.MIMO && r.weight() > 0)
                BiomeModifications.addSpawn(
                        c ->
                                matches(c.getBiomeHolder(), r.include())
                                        && !matches(c.getBiomeHolder(), r.exclude()),
                        MobCategory.MONSTER,
                        DolfinZombies.type(k),
                        r.weight(),
                        r.min(),
                        r.max());
        }
        DolfinZombies.LOGGER.info(
                "event=spawn_rules_registered placements={} startupGeneration={}",
                kinds.size(),
                generation);
    }

    public static ZombieKind kind(EntityType<?> type) {
        return kinds.get(type);
    }

    private record WorldProbe(ServerLevel l, BlockPos p, Holder<Biome>[] biome) implements Probe {
        WorldProbe(ServerLevel l, BlockPos p) {
            this(l, p, newBiome());
        }

        @SuppressWarnings("unchecked")
        private static Holder<Biome>[] newBiome() {
            return new Holder[1];
        }

        private Holder<Biome> b() {
            if (biome[0] == null) biome[0] = l.getBiome(p);
            return biome[0];
        }

        public boolean worldAllowed() {
            return SpawnRules.worldAllowed(l);
        }

        public boolean night() {
            return BloodMoonSchedule.isNight(l.getOverworldClockTime());
        }

        public int y() {
            return p.getY();
        }

        public int blockLight() {
            return l.getBrightness(LightLayer.BLOCK, p);
        }

        public int skyLight() {
            return l.getBrightness(LightLayer.SKY, p);
        }

        public boolean biome(List<String> s) {
            return matches(b(), s);
        }

        public boolean snowy() {
            return b().value().coldEnoughToSnow(p, l.getSeaLevel());
        }

        public int waterDepth(int needed) {
            return columnDepth(d -> l.getFluidState(p.above(d)).is(FluidTags.WATER), needed);
        }

        public boolean capAllows() {
            return belowCap(l, p);
        }
    }

    /** Authoritative per-position check (placement predicate): counted and DEBUG-logged. */
    private static Reason check(ServerLevel l, BlockPos p, ZombieKind k) {
        refresh();
        var why = evaluate(k, rules.get(k), new WorldProbe(l, p), true);
        placementCounts[k.ordinal()][why == null ? Reason.values().length : why.ordinal()]++;
        if (why != null && DolfinZombies.LOGGER.isDebugEnabled())
            DolfinZombies.LOGGER.debug(
                    "event=spawn_rejected kind={} reason={} stage=placement dimension={} pos={}"
                            + " bloodMoon={} generation={}",
                    k.id(),
                    why.id,
                    l.dimension().identifier(),
                    p,
                    BloodMoon.active(l),
                    generation);
        return why;
    }

    /** Reason id or null; used by /zombies inspeccionar. */
    public static String rejection(ServerLevel l, BlockPos p, ZombieKind k, boolean checkCap) {
        refresh();
        var why = evaluate(k, rules.get(k), new WorldProbe(l, p), checkCap);
        return why == null ? null : why.id;
    }

    public static String inspect(ServerLevel l, BlockPos p, ZombieKind k) {
        refresh();
        var r = rules.get(k);
        String reason = rejection(l, p, k, true);
        return "id="
                + k.id()
                + " pos="
                + p
                + " dimension="
                + l.dimension().identifier()
                + " biome="
                + l.getBiome(p).unwrapKey().map(v -> v.identifier().toString()).orElse("unknown")
                + " blockLight="
                + l.getBrightness(LightLayer.BLOCK, p)
                + " skyLight="
                + l.getBrightness(LightLayer.SKY, p)
                + " generation="
                + generation
                + " reason="
                + (reason == null ? "eligible" : reason)
                + " weight="
                + (r.enabled() && k != ZombieKind.MIMO
                        ? effectiveWeight(r.weight(), r.bloodWeight(), BloodMoon.active(l))
                        : 0)
                + " group="
                + r.min()
                + ","
                + r.max()
                + " placement="
                + SpawnPlacements.isSpawnPositionOk(DolfinZombies.type(k), l, p)
                + " cap="
                + cap
                + " capAllows="
                + belowCap(l, p)
                + " bloodMoon="
                + BloodMoon.active(l);
    }

    /**
     * Special zombies near each player, recounted once per server tick (and after each special
     * spawn).
     */
    private static int nearby(ServerLevel l, ServerPlayer player) {
        long tick = l.getServer().getTickCount();
        if (tick != capTick) {
            capTick = tick;
            capCache.clear();
        }
        return capCache.computeIfAbsent(
                player.getUUID(),
                id ->
                        l.getEntitiesOfClass(
                                        ConfiguredZombie.class,
                                        player.getBoundingBox().inflate(CAP_RADIUS),
                                        z ->
                                                z.isAlive()
                                                        && z.distanceToSqr(player)
                                                                <= CAP_RADIUS * CAP_RADIUS)
                                .size());
    }

    public static boolean belowCap(ServerLevel l, BlockPos p) {
        refresh();
        var counts = new ArrayList<Integer>();
        for (var player : l.players())
            if (!player.isSpectator()
                    && player.distanceToSqr(p.getX() + .5, p.getY(), p.getZ() + .5)
                            <= CAP_RADIUS * CAP_RADIUS) counts.add(nearby(l, player));
        return capAllows(cap, counts.stream().mapToInt(Integer::intValue).toArray());
    }

    public static WeightedList<MobSpawnSettings.SpawnerData> pool(
            ServerLevel l, BlockPos p, WeightedList<MobSpawnSettings.SpawnerData> original) {
        refresh();
        var entries = new ArrayList<Weighted<MobSpawnSettings.SpawnerData>>();
        boolean blood = BloodMoon.active(l);
        boolean only = blood && BloodMoon.options().onlySpecials();
        int removed = 0;
        for (var e : original.unwrap()) {
            var t = e.value().type();
            if (kind(t) != null) continue; // startup entries; replaced by live ones below
            if (only && isVanillaZombie(t)) {
                removed += e.weight();
                continue;
            }
            entries.add(e);
        }
        var eligible = new ArrayList<ZombieKind>();
        var weights = new ArrayList<Integer>();
        var probe = new WorldProbe(l, p);
        for (var k : ZombieKind.values()) {
            var r = rules.get(k);
            int weight = effectiveWeight(r.weight(), r.bloodWeight(), blood);
            if (weight <= 0 || !r.enabled()) continue;
            var why = evaluate(k, r, probe, false);
            poolCounts[k.ordinal()][why == null ? Reason.values().length : why.ordinal()]++;
            if (why != null) {
                if (DolfinZombies.LOGGER.isTraceEnabled())
                    DolfinZombies.LOGGER.trace(
                            "event=spawn_rejected kind={} reason={} stage=pool pos={}",
                            k.id(),
                            why.id,
                            p);
                continue;
            }
            eligible.add(k);
            weights.add(weight);
        }
        int[] w = substitute(removed, weights.stream().mapToInt(Integer::intValue).toArray());
        for (int i = 0; i < w.length; i++) {
            var r = rules.get(eligible.get(i));
            entries.add(
                    new Weighted<>(
                            new MobSpawnSettings.SpawnerData(
                                    DolfinZombies.type(eligible.get(i)),
                                    UniformInt.of(r.min(), r.max())),
                            w[i]));
        }
        if (removed > 0 && DolfinZombies.LOGGER.isTraceEnabled())
            DolfinZombies.LOGGER.trace(
                    "event=spawn_pool_substituted removedVanillaWeight={} specials={} pos={}",
                    removed,
                    eligible.size(),
                    p);
        return WeightedList.of(entries);
    }
}
