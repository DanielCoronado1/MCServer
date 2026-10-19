package com.dolfin.zombies.kinds;

import static org.junit.jupiter.api.Assertions.*;

import com.dolfin.zombies.ZombieConfig;
import com.dolfin.zombies.ZombieKind;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class CombatKindsTest {

    @Test
    void shieldMathConeAndDecisions() {
        // Yaw 0 in Minecraft faces +Z (south): fx = -sin(0) = 0, fz = cos(0) = 1.
        assertEquals(0.0, ShieldMath.angleBetween(0, 0, 0, 0, 5), 1e-6);
        assertEquals(90.0, ShieldMath.angleBetween(0, 0, 0, 5, 0), 1e-6);
        assertEquals(180.0, ShieldMath.angleBetween(0, 0, 0, 0, -5), 1e-6);
        assertTrue(Double.isNaN(ShieldMath.angleBetween(0, 2, 3, 2, 3)));

        // 90-degree full cone means <= 45 degrees to either side of facing direction.
        assertTrue(ShieldMath.inCone(0, 0, 0, 0, 4, 90));
        assertTrue(ShieldMath.inCone(0, 0, 0, 3, 3, 90)); // exactly 45 deg
        assertFalse(ShieldMath.inCone(0, 0, 0, 3.1, 3, 90)); // > 45 deg
        assertFalse(ShieldMath.inCone(0, 0, 0, 0, -4, 90)); // behind (180 deg)

        // Yaw -90 faces +X (east): fx = -sin(-90) = 1, fz = 0.
        assertTrue(ShieldMath.inCone(-90, 10, 10, 15, 10, 90));
        assertFalse(ShieldMath.inCone(-90, 10, 10, 5, 10, 90));

        // Edge cone apertures.
        assertFalse(ShieldMath.inCone(0, 0, 0, 0, 5, 0));
        assertTrue(ShieldMath.inCone(0, 0, 0, 0, -5, 360));
        assertFalse(ShieldMath.inCone(0, 0, 0, 0, 0, 90));

        // Decision priority order.
        assertEquals(
                ShieldMath.Decision.ABILITY_OFF,
                ShieldMath.decide(false, false, true, false, true, false, true));
        assertEquals(
                ShieldMath.Decision.BYPASS,
                ShieldMath.decide(true, true, true, false, true, false, true));
        assertEquals(
                ShieldMath.Decision.NO_SOURCE_POSITION,
                ShieldMath.decide(true, false, false, false, true, false, true));
        assertEquals(
                ShieldMath.Decision.PROJECTILE_IGNORED,
                ShieldMath.decide(true, false, true, true, false, false, true));
        assertEquals(
                ShieldMath.Decision.SHIELD_DISABLED,
                ShieldMath.decide(true, false, true, false, true, true, true));
        assertEquals(
                ShieldMath.Decision.OUTSIDE_CONE,
                ShieldMath.decide(true, false, true, false, true, false, false));
        assertEquals(
                ShieldMath.Decision.BLOCK,
                ShieldMath.decide(true, false, true, true, true, false, true));
    }

    @Test
    void foodChoicePicksNearestValidWithinRadius() {
        double[] distSq = {25.0, 4.0, 9.0, 4.0};
        boolean[] valid = {true, false, true, true};
        assertEquals(3, FoodChoice.nearest(distSq, valid, 100.0));
        assertEquals(-1, FoodChoice.nearest(distSq, valid, 3.0));
        assertEquals(
                1,
                FoodChoice.nearest(
                        new double[] {10.0, 5.0, 5.0}, new boolean[] {true, true, true}, 20.0));
    }

    @Test
    void cloudMathCylinderAndParticleCap() {
        assertTrue(CloudMath.contains(0, 64, 0, 3.0, 2.0, 64.0, 2.0));
        assertFalse(CloudMath.contains(0, 64, 0, 3.0, 2.5, 64.0, 2.5)); // 2.5^2 + 2.5^2 = 12.5 > 9
        assertTrue(CloudMath.contains(0, 64, 0, 3.0, 0, 64 - CloudMath.BELOW, 0));
        assertTrue(CloudMath.contains(0, 64, 0, 3.0, 0, 64 + CloudMath.HEIGHT, 0));
        assertFalse(CloudMath.contains(0, 64, 0, 3.0, 0, 64 - CloudMath.BELOW - 0.01, 0));
        assertFalse(CloudMath.contains(0, 64, 0, 3.0, 0, 64 + CloudMath.HEIGHT + 0.01, 0));

        assertEquals(4, CloudMath.particleCount(0.2));
        assertEquals(15, CloudMath.particleCount(3.0));
        assertEquals(24, CloudMath.particleCount(10.0));
    }

    @Test
    void waterColumnDepthAndCaps() {
        // Water from y=55 to y=62 (8 blocks total).
        assertEquals(8, WaterColumn.depth(y -> y >= 55 && y <= 62, 60, -64, 320, 32));
        assertEquals(
                5,
                WaterColumn.depth(y -> y >= 55 && y <= 62, 58, -64, 320, 2)); // 1 + 2 up + 2 down
        assertEquals(
                0,
                WaterColumn.depth(
                        y -> y >= 55 && y <= 62, 63, -64, 320, 32)); // origin not in water
        assertEquals(0, WaterColumn.depth(y -> true, -65, -64, 320, 32)); // out of world bounds
    }

    @Test
    void grabRulesReleaseAndDamageThresholds() {
        var ok = new GrabRules.Inputs(30, true, true, true, true, true, true, true, 4.0, 12.25);
        assertNull(GrabRules.releaseReason(ok));

        assertEquals(
                GrabRules.ZOMBIE_DEAD,
                GrabRules.releaseReason(
                        new GrabRules.Inputs(
                                30, true, false, true, true, true, true, true, 4.0, 12.25)));
        assertEquals(
                GrabRules.TARGET_GONE,
                GrabRules.releaseReason(
                        new GrabRules.Inputs(
                                30, true, true, true, false, true, true, true, 4.0, 12.25)));
        assertEquals(
                GrabRules.TARGET_DEAD,
                GrabRules.releaseReason(
                        new GrabRules.Inputs(
                                30, true, true, true, true, false, true, true, 4.0, 12.25)));
        assertEquals(
                GrabRules.TARGET_INVALID,
                GrabRules.releaseReason(
                        new GrabRules.Inputs(
                                30, true, true, true, true, true, false, true, 4.0, 12.25)));
        assertEquals(
                GrabRules.ABILITY_OFF,
                GrabRules.releaseReason(
                        new GrabRules.Inputs(
                                30, false, true, true, true, true, true, true, 4.0, 12.25)));
        assertEquals(
                GrabRules.ZOMBIE_OUT_OF_WATER,
                GrabRules.releaseReason(
                        new GrabRules.Inputs(
                                30, true, true, false, true, true, true, true, 4.0, 12.25)));
        assertEquals(
                GrabRules.TARGET_LEFT_WATER,
                GrabRules.releaseReason(
                        new GrabRules.Inputs(
                                30, true, true, true, true, true, true, false, 4.0, 12.25)));
        assertEquals(
                GrabRules.TOO_FAR,
                GrabRules.releaseReason(
                        new GrabRules.Inputs(
                                30, true, true, true, true, true, true, true, 13.0, 12.25)));
        assertEquals(
                GrabRules.DURATION,
                GrabRules.releaseReason(
                        new GrabRules.Inputs(
                                0, true, true, true, true, true, true, true, 4.0, 12.25)));

        // Any hit by the grabbed player breaks the grab; external damage must be >= threshold (4).
        assertEquals(GrabRules.HIT_BY_TARGET, GrabRules.damageReason(true, 0.5, 4.0));
        assertNull(GrabRules.damageReason(false, 3.9, 4.0));
        assertEquals(GrabRules.DAMAGE, GrabRules.damageReason(false, 4.0, 4.0));
        assertEquals(GrabRules.DAMAGE, GrabRules.damageReason(false, 8.0, 4.0));
    }

    @Test
    void dragMathPullsDownAndClamps() {
        // Within HOLD_RADIUS: no horizontal pull, only downward pull.
        double[] close = DragMath.drag(0, 60, 0, 0.2, 58, 0.2, 0.12);
        assertEquals(0.0, close[0], 1e-9);
        assertEquals(-0.12, close[1], 1e-9);
        assertEquals(0.0, close[2], 1e-9);

        // Outside HOLD_RADIUS: horizontal pull of strength * 0.5 toward the zombie.
        double[] far = DragMath.drag(0, 60, 0, 3, 58, 4, 0.20); // distance = 5, pull = 0.10
        assertEquals(0.06, far[0], 1e-9);
        assertEquals(-0.20, far[1], 1e-9);
        assertEquals(0.08, far[2], 1e-9);

        double[] clamped =
                DragMath.addClamped(
                        new double[] {0.5, -0.55, -0.9}, new double[] {0.3, -0.2, 0.1}, 0.6);
        assertArrayEquals(new double[] {0.6, -0.6, -0.6}, clamped, 1e-9);
    }

    @Test
    void tickCooldownTracksRemainingTicks() {
        var cd = new TickCooldown();
        assertTrue(cd.ready(0));
        cd.start(100, 200);
        assertFalse(cd.ready(299));
        assertEquals(1, cd.remaining(299));
        assertTrue(cd.ready(300));
        assertEquals(0, cd.remaining(350));
        cd.reset();
        assertTrue(cd.ready(0));
    }

    @Test
    void weightedPickParsesAndSelectsProportionally() {
        JsonObject raw =
                JsonParser.parseString(
                                "{\"zombie_horse\":60,\"spider\":40,\"zero\":0,\"neg\":-5,\"bad\":\"x\"}")
                        .getAsJsonObject();
        Map<String, Double> weights = WeightedPick.parse(raw);
        assertEquals(2, weights.size());
        assertEquals("zombie_horse", WeightedPick.pick(weights, 0.0));
        assertEquals("zombie_horse", WeightedPick.pick(weights, 0.59));
        assertEquals("spider", WeightedPick.pick(weights, 0.60));
        assertEquals("spider", WeightedPick.pick(weights, 0.999));
        assertNull(WeightedPick.pick(Map.of(), 0.5));
    }

    @Test
    void combatDefaultsAndParamsValidateAndFitConstraints(@TempDir Path dir) {
        List<String> warnings = new ArrayList<>();
        var cfg = new ZombieConfig(dir, warnings::add);
        assertTrue(warnings.isEmpty(), () -> "Unexpected config warnings: " + warnings);

        var escudero = CombatParams.Escudero.from(cfg.current().zombie(ZombieKind.ESCUDERO));
        assertEquals(90.0, escudero.anguloBloqueo());
        assertEquals(5.0, escudero.hachaSegundos());
        assertTrue(escudero.bloqueaProyectiles());
        assertEquals(0.08, escudero.probabilidadSoltarEscudo());

        var hambriento = CombatParams.Hambriento.from(cfg.current().zombie(ZombieKind.HAMBRIENTO));
        assertEquals(3, hambriento.hambrePorGolpe());
        assertEquals(2f, hambriento.saturacionPorGolpe());
        assertEquals(100, hambriento.efectoHambreTicks());
        assertTrue(hambriento.distraidoPorComida());
        assertEquals(10.0, hambriento.radioComida());

        var congelado = CombatParams.Congelado.from(cfg.current().zombie(ZombieKind.CONGELADO));
        assertEquals(80, congelado.lentitudTicks());
        assertEquals(1, congelado.nivelLentitud());
        assertTrue(congelado.congelacionPolvo());
        assertEquals(200, congelado.congelacionTicks());
        assertFalse(cfg.current().zombie(ZombieKind.CONGELADO).get("quemaConSol").getAsBoolean());

        var bilioso = CombatParams.Bilioso.from(cfg.current().zombie(ZombieKind.BILIOSO));
        assertEquals(3.0, bilioso.radioNube());
        assertEquals(100, bilioso.duracionNube());
        assertEquals(0.8, bilioso.resistenciaEmpuje());
        assertEquals(120, bilioso.nauseaTicks());
        assertEquals(40, bilioso.venenoTicks());
        assertEquals(1, bilioso.venenoNivel());

        var abismo = CombatParams.Abismo.from(cfg.current().zombie(ZombieKind.ABISMO));
        assertEquals(0.12, abismo.fuerzaArrastre());
        assertEquals(60, abismo.duracionAgarre());
        assertEquals(200, abismo.enfriamiento());
        assertEquals(5, abismo.profundidadMinima());
        assertEquals(4.0, abismo.danioParaSoltar());

        var jinete = CombatParams.Jinete.from(cfg.current().zombie(ZombieKind.JINETE));
        assertEquals(20.0, jinete.vidaMontura());
        assertEquals(60.0, jinete.monturas().get("zombie_horse"));
        assertEquals(40.0, jinete.monturas().get("spider"));

        // Enano must fit through a 1-block-high gap (alto = 0.9 < 1.0, ancho = 0.5, escala = 0.5).
        var enano = CombatParams.Enano.from(cfg.current().zombie(ZombieKind.ENANO));
        assertEquals(0.5, enano.ancho());
        assertEquals(0.9, enano.alto());
        assertEquals(0.5, enano.escala());
        assertTrue(enano.alto() < 1.0);
    }
}
