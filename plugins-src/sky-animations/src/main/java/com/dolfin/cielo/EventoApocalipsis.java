package com.dolfin.cielo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * El evento completo, inspirado en el video de referencia. Se ejecuta una vez
 * por tick (20 por segundo) y, segun el tick, hace avanzar cada fase:
 *
 *   1. Mancha     (1 s)   aparece una mancha oscura en el cielo y se extiende
 *                         hasta cubrirlo todo con un remolino rojo que gira
 *   2. Rayo      (12 s)   cae un rayo rojo y fino donde se abrira la grieta
 *   3. Grieta    (14 s)   el rayo se abre en una grieta con bordes de neon
 *   4. Dragon    (18 s)   un dragon asoma la cabeza por la grieta
 *   5. Cierre             el dragon vuelve, la grieta se cierra y el remolino
 *                         se encoge hasta desaparecer
 *
 * Las imagenes (remolino, nubes, grieta, rayo) vienen del resource pack.
 */
public final class EventoApocalipsis extends BukkitRunnable {

    // ---------- Linea de tiempo (en ticks: 20 ticks = 1 segundo) ----------
    private static final int T_MANCHA = 20;
    private static final int CRECER = 200;      // lo que tarda el remolino en cubrir el cielo
    private static final int T_RAYO = 240;
    private static final int T_GRIETA = 290;
    private static final int ABRIR = 60;        // lo que tarda la grieta en abrirse
    private static final int T_DRAGON = 370;
    private static final int SALIR = 90;        // lo que tarda el dragon en asomarse
    private static final int ENCOGER = 120;     // lo que tarda el remolino en irse
    private static final int PASO_GIRO = 10;    // cada cuanto se actualiza el giro del remolino
    private static final float GRADOS_POR_PASO = 4.5f;
    private static final int CANTIDAD_PAREDES = 18;
    private static final int T_PAREDES = T_MANCHA + 90;  // las nubes del horizonte suben despues
    private static final int CRECER_PAREDES = 130;

    // Letras de la fuente dolfin:cielo (las genera scripts/generar_texturas.py).
    private static final char LETRA_NUBES = '';
    private static final char LETRA_BALDOSA = '';  // primera baldosa del remolino
    private static final int BALDOSAS = 8;               // el remolino son 8x8 baldosas
    private static final float BLOQUES_POR_BALDOSA = 32 * 0.025f;

    private final CieloPlugin plugin;
    private final FileConfiguration config;
    private final World mundo;
    private final Location centro;
    private final Vector delante;               // direccion (horizontal) hacia la grieta
    private final Location origenGrieta;
    private final ThreadLocalRandom azar = ThreadLocalRandom.current();

    private final int tCierre;
    private final int tEncoger;
    private final int tFin;
    private int tick = 0;

    // Medidas leidas de config.yml
    private final float radioCielo;
    private final float anchoGrieta;
    private final float altoGrieta;

    // Hora del cielo
    private final long horaInicial;
    private final long horaEvento;
    private long horaFinal;
    private final Set<UUID> afectados = new HashSet<>();

    // Todo lo que creamos, para poder borrarlo al final.
    private final List<Entity> entidades = new ArrayList<>();
    private final List<TextDisplay> vortice = new ArrayList<>();
    private final List<TextDisplay> paredes = new ArrayList<>();
    private final List<Float> girosParedes = new ArrayList<>();
    private ItemDisplay grieta;
    private ItemDisplay rayoCentral;
    private final List<ItemDisplay> rayosLaterales = new ArrayList<>();
    private EnderDragon dragon;
    private Location dragonInicio;
    private Location dragonFin;
    private float anguloVortice = 0;

    public EventoApocalipsis(CieloPlugin plugin, Location lugar) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.mundo = lugar.getWorld();
        this.centro = lugar.clone();

        this.radioCielo = (float) config.getDouble("radio-cielo", 130);
        this.anchoGrieta = (float) config.getDouble("ancho-brecha", 26);
        this.altoGrieta = (float) config.getDouble("alto-brecha", 40);

        // La grieta aparece delante (hacia donde mira quien lanzo el evento) y en lo alto.
        Vector direccion = lugar.getDirection().setY(0);
        if (direccion.lengthSquared() < 0.01) {
            direccion = new Vector(0, 0, -1); // norte
        }
        this.delante = direccion.normalize();
        this.origenGrieta = centro.clone().add(delante.clone().multiply(config.getDouble("distancia-brecha", 45)));
        this.origenGrieta.setY(Math.min(centro.getY() + config.getDouble("altura-brecha", 24), mundo.getMaxHeight() - 10));

        this.horaInicial = mundo.getTime() % 24000;
        long hora = config.getLong("hora-evento", 12800);
        this.horaEvento = hora >= horaInicial ? hora : hora + 24000;

        this.tCierre = T_DRAGON + 2 + SALIR + config.getInt("segundos-dragon", 20) * 20;
        this.tEncoger = tCierre + 100;
        this.tFin = tEncoger + ENCOGER + 5;
    }

    @Override
    public void run() {
        actualizarJugadores();

        // ---- Fase 1: mancha y remolino ----
        if (tick == 0) {
            sonido(Sound.AMBIENT_CAVE, 1f, 0.5f);
        }
        if (tick == T_MANCHA) {
            crearCielo();
            sonido(Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f);
            sonido(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 0.5f);
        }
        if (tick == T_PAREDES) {
            crearParedes();
        }
        if (tick == T_PAREDES + 4) {
            crecerParedes(true);
        }
        if (!vortice.isEmpty() && tick >= T_MANCHA + 4 && (tick - T_MANCHA - 4) % PASO_GIRO == 0) {
            girarVortice();
        }
        if (tick == T_MANCHA + CRECER / 2) {
            sonido(Sound.BLOCK_PORTAL_AMBIENT, 1f, 0.5f);
        }

        // ---- Fase 2: rayo ----
        if (tick == T_RAYO) {
            crearRayo();
        }

        // ---- Fase 3: grieta ----
        if (tick == T_GRIETA) {
            crearGrieta();
        }
        if (tick == T_GRIETA + 2) {
            abrirGrieta();
        }
        if (tick == T_GRIETA + 30 && rayoCentral != null) {
            rayoCentral.remove();
        }

        // ---- Fase 4: dragon ----
        if (tick == T_DRAGON) {
            crearDragon();
        }
        if (dragon != null && tick > T_DRAGON + 1 && tick < tCierre + 60) {
            moverDragon();
        }
        if (tick == T_DRAGON + 2) {
            sonido(Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.7f);
            mostrarTitulo();
        }
        if (tick > T_DRAGON + SALIR && tick < tCierre && (tick - T_DRAGON) % 120 == 0) {
            sonido(azar.nextBoolean() ? Sound.ENTITY_ENDER_DRAGON_GROWL : Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1f, 0.6f);
        }

        // ---- Fase 5: cierre ----
        if (tick == tCierre) {
            sonido(Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.5f);
        }
        if (tick == tCierre + 60) {
            borrar(dragon);
            dragon = null;
            cerrarGrieta();
        }
        if (tick == tCierre + 100) {
            borrar(grieta);
            rayosLaterales.forEach(this::borrar);
        }
        if (tick == tEncoger) {
            horaFinal = mundo.getTime() % 24000;
            crecerParedes(false);
            sonido(Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.6f);
        }
        if (tick >= tFin) {
            detener();
            return;
        }

        tick++;
    }

    // =====================================================================
    //  Jugadores: hora del cielo y sonidos
    // =====================================================================

    /** Quien este cerca ve la hora del evento; a quien se aleja se le devuelve la normal. */
    private void actualizarJugadores() {
        if (tick % 20 == 0) {
            double radio = radioCielo + 60;
            for (Player jugador : mundo.getPlayers()) {
                boolean cerca = jugador.getLocation().distanceSquared(centro) < radio * radio;
                if (cerca) {
                    afectados.add(jugador.getUniqueId());
                } else if (afectados.remove(jugador.getUniqueId())) {
                    jugador.resetPlayerTime();
                }
            }
        }
        long hora = horaActual();
        jugadoresAfectados().forEach(jugador -> jugador.setPlayerTime(hora, false));
    }

    /** La hora pasa a "hora-evento" mientras crece el remolino y vuelve cuando se encoge. */
    private long horaActual() {
        if (tick >= tEncoger) {
            // Avanzamos hasta la hora real del mundo (el cielo "amanece" hacia adelante).
            long destino = horaFinal;
            while (destino < horaEvento) {
                destino += 24000;
            }
            float q = suave((tick - tEncoger) / (float) ENCOGER);
            return horaEvento + (long) ((destino - horaEvento) * q);
        }
        float p = suave((tick - T_MANCHA) / (float) CRECER);
        return horaInicial + (long) ((horaEvento - horaInicial) * p);
    }

    private void sonido(Sound sonido, float volumen, float tono) {
        for (Player jugador : jugadoresAfectados()) {
            jugador.playSound(jugador.getLocation(), sonido, SoundCategory.AMBIENT, volumen, tono);
        }
    }

    private List<Player> jugadoresAfectados() {
        List<Player> lista = new ArrayList<>();
        for (UUID id : afectados) {
            Player jugador = Bukkit.getPlayer(id);
            if (jugador != null) {
                lista.add(jugador);
            }
        }
        return lista;
    }

    private void mostrarTitulo() {
        String titulo = config.getString("titulo", "");
        String subtitulo = config.getString("subtitulo", "");
        if (titulo.isEmpty() && subtitulo.isEmpty()) {
            return;
        }
        MiniMessage mm = MiniMessage.miniMessage();
        Title pantalla = Title.title(mm.deserialize(titulo), mm.deserialize(subtitulo),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1)));
        jugadoresAfectados().forEach(jugador -> jugador.showTitle(pantalla));
    }

    // =====================================================================
    //  Fase 1: el remolino del cielo y la pared de nubes
    // =====================================================================

    private void crearCielo() {
        // El remolino es un disco horizontal gigante sobre el centro. Nace pequeno
        // en un punto del cielo (la "mancha") y crece hasta cubrirlo todo.
        // Esta hecho de 8x8 baldosas (si fuera una sola imagen gigante, la niebla la volveria gris).
        Location arriba = centro.clone().add(0, config.getDouble("altura-cielo", 60), 0);
        for (int fila = 0; fila < BALDOSAS; fila++) {
            for (int columna = 0; columna < BALDOSAS; columna++) {
                char letra = (char) (LETRA_BALDOSA + fila * BALDOSAS + columna);
                TextDisplay baldosa = Sprites.letra(arriba, letra, formaBaldosa(T_MANCHA, fila, columna));
                vortice.add(baldosa);
                entidades.add(baldosa);
            }
        }
    }

    /** Pared de nubes en circulo para tapar el horizonte (sube cuando el remolino ya se extiende). */
    private void crearParedes() {
        float radio = radioCielo * 0.75f;
        for (int i = 0; i < CANTIDAD_PAREDES; i++) {
            double angulo = 2 * Math.PI * i / CANTIDAD_PAREDES;
            Location lugar = centro.clone().add(Math.cos(angulo) * radio, -8, Math.sin(angulo) * radio);
            float giro = (float) Math.atan2(-Math.cos(angulo), -Math.sin(angulo)); // mirando al centro
            TextDisplay pared = Sprites.letra(lugar, LETRA_NUBES, formaPared(giro, 0.01f));
            paredes.add(pared);
            girosParedes.add(giro);
            entidades.add(pared);
        }
    }

    /**
     * Forma de una baldosa del remolino: la forma del disco completo, desplazada
     * a la posicion de esa baldosa (y girando junto con el disco).
     */
    private Transformation formaBaldosa(int enTick, int fila, int columna) {
        Transformation disco = formaVortice(enTick);
        float lado = disco.getScale().x * Sprites.BLOQUES_POR_LETRA / BALDOSAS; // bloques por baldosa
        Vector3f desplazamiento = new Vector3f(
                (columna - (BALDOSAS - 1) / 2f) * lado,
                ((BALDOSAS - 1) / 2f - fila) * lado,
                0);
        disco.getLeftRotation().transform(desplazamiento);
        float escala = lado / BLOQUES_POR_BALDOSA * 1.06f; // algo mas grande: sin rendijas al girar
        return Sprites.forma(
                new Vector3f(disco.getTranslation()).add(desplazamiento),
                new Quaternionf(disco.getLeftRotation()),
                new Vector3f(escala, escala, 1));
    }

    /**
     * Donde esta y que tamano tiene el remolino en un tick dado.
     * Crece de la mancha al cielo completo y, al final, se encoge de vuelta.
     */
    private Transformation formaVortice(int enTick) {
        Vector3f mancha = new Vector3f((float) delante.getX() * 35, 0, (float) delante.getZ() * 35);
        float tamanoMancha = 6;
        float tamanoTotal = radioCielo * 2;

        float p;
        if (enTick >= tEncoger) {
            p = 1 - suave((enTick - tEncoger) / (float) ENCOGER);
        } else {
            p = suave((enTick - T_MANCHA - 4) / (float) CRECER);
        }
        Vector3f posicion = new Vector3f(mancha).lerp(new Vector3f(), p);
        float tamano = Math.max(0.01f, tamanoMancha + (tamanoTotal - tamanoMancha) * p);
        if (enTick >= tEncoger) {
            tamano *= Math.min(1, p * 4); // al final se desvanece del todo
        }

        Quaternionf giro = new Quaternionf()
                .rotateY((float) Math.toRadians(anguloVortice))
                .rotateX((float) Math.toRadians(90)); // tumbado, mirando al suelo
        float escala = tamano / Sprites.BLOQUES_POR_LETRA;
        return Sprites.forma(posicion, giro, new Vector3f(escala, escala, 1));
    }

    /** Gira un poco el remolino y lo lleva a su forma del siguiente paso. */
    private void girarVortice() {
        anguloVortice += GRADOS_POR_PASO;
        for (int i = 0; i < vortice.size(); i++) {
            Sprites.animar(vortice.get(i), formaBaldosa(tick + PASO_GIRO, i / BALDOSAS, i % BALDOSAS), PASO_GIRO);
        }
    }

    private Transformation formaPared(float giro, float alto) {
        float ancho = (float) (2 * Math.PI * radioCielo * 0.75 / CANTIDAD_PAREDES * 1.03);
        // La imagen de las nubes mide el doble de ancho que de alto.
        return Sprites.forma(
                new Vector3f(0, alto / 2, 0), // crece desde abajo
                new Quaternionf().rotateY(giro),
                new Vector3f(ancho / (2 * Sprites.BLOQUES_POR_LETRA), alto / Sprites.BLOQUES_POR_LETRA, 1));
    }

    private void crecerParedes(boolean subir) {
        float alto = subir ? 72 : 0.01f;
        int duracion = subir ? CRECER_PAREDES : ENCOGER;
        for (int i = 0; i < paredes.size(); i++) {
            Sprites.animar(paredes.get(i), formaPared(girosParedes.get(i), alto), duracion);
        }
    }

    // =====================================================================
    //  Fases 2 y 3: rayo y grieta
    // =====================================================================

    private void crearRayo() {
        // El rayo de la textura ya parpadea solo (es una textura animada).
        rayoCentral = Sprites.crear(origenGrieta, "rayo", Display.Billboard.VERTICAL,
                Sprites.plano(0, 0, 0.2f, 7, altoGrieta * 0.95f));
        entidades.add(rayoCentral);

        for (int i = 0; i < 3; i++) {
            Location suelo = centro.clone().add(azar.nextDouble(-45, 45), 0, azar.nextDouble(-45, 45));
            mundo.strikeLightningEffect(mundo.getHighestBlockAt(suelo).getLocation().add(0, 1, 0));
        }
        sonido(Sound.ITEM_TRIDENT_THUNDER, 1f, 0.6f);
    }

    private void crearGrieta() {
        // VERTICAL: la grieta siempre mira a cada jugador, pero sigue de pie.
        grieta = Sprites.crear(origenGrieta, "grieta", Display.Billboard.VERTICAL,
                Sprites.plano(0, 0, 0, anchoGrieta * 0.04f, altoGrieta));
        entidades.add(grieta);

        // Rayos a los lados que chisporrotean mientras esta abierta.
        float lado = anchoGrieta / 2 + 4;
        rayosLaterales.add(Sprites.crear(origenGrieta, "rayo", Display.Billboard.VERTICAL,
                Sprites.plano(-lado, 4, -0.3f, 0.01f, 0.01f)));
        rayosLaterales.add(Sprites.crear(origenGrieta, "rayo", Display.Billboard.VERTICAL,
                Sprites.plano(lado, -6, -0.3f, 0.01f, 0.01f)));
        entidades.addAll(rayosLaterales);

        sonido(Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);
    }

    private void abrirGrieta() {
        Sprites.animar(grieta, Sprites.plano(0, 0, 0, anchoGrieta, altoGrieta), ABRIR);
        float lado = anchoGrieta / 2 + 4;
        Sprites.animar(rayosLaterales.get(0), Sprites.plano(-lado, 4, -0.3f, 9, 26), ABRIR);
        Sprites.animar(rayosLaterales.get(1), Sprites.plano(lado, -6, -0.3f, 9, 26), ABRIR);
        sonido(Sound.BLOCK_END_PORTAL_SPAWN, 1f, 0.5f);
    }

    private void cerrarGrieta() {
        Sprites.animar(grieta, Sprites.plano(0, 0, 0, anchoGrieta * 0.04f, altoGrieta), 40);
        rayosLaterales.forEach(rayo -> Sprites.animar(rayo, Sprites.plano(0, 0, -0.3f, 0.01f, 0.01f), 20));
        sonido(Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.5f);
    }

    // =====================================================================
    //  Fase 4: el dragon
    // =====================================================================

    private void crearDragon() {
        // El dragon empieza detras de la grieta (escondido) mirando hacia los jugadores.
        Vector haciaAtras = delante.clone();
        Location base = origenGrieta.clone().add(0, -4, 0);
        dragonInicio = base.clone().add(haciaAtras.clone().multiply(config.getDouble("dragon-distancia-inicial", 20)));
        dragonFin = base.clone().add(haciaAtras.clone().multiply(config.getDouble("dragon-distancia-final", -18)));

        // Ojo: el Ender Dragon tiene la cabeza al reves que los demas mobs, de ahi el +180.
        float mirada = guinada(delante.clone().multiply(-1)) + (float) config.getDouble("dragon-giro", 180);
        dragonInicio.setRotation(mirada, 0);
        dragonFin.setRotation(mirada, 0);

        dragon = mundo.spawn(dragonInicio, EnderDragon.class, d -> {
            d.setAI(false);              // no vuela ni ataca: solo se mueve donde le digamos
            d.setInvulnerable(true);
            d.setSilent(true);           // los sonidos los ponemos nosotros
            d.setGravity(false);
            d.setPersistent(false);      // si el servidor se apaga, no se queda en el mundo
            d.getPersistentDataContainer().set(plugin.marcaDragon(), PersistentDataType.BOOLEAN, true);
        });
        if (dragon.getBossBar() != null) {
            dragon.getBossBar().setVisible(false);
        }
        entidades.add(dragon);
    }

    /** Cada tick coloca al dragon: sale, se balancea mirando a los lados y vuelve a entrar. */
    private void moverDragon() {
        float avance;
        if (tick >= tCierre) {
            avance = 1 - suave((tick - tCierre) / 60f);
        } else {
            avance = suave((tick - T_DRAGON - 2) / (float) SALIR);
        }
        Location lugar = dragonInicio.clone().add(dragonFin.toVector().subtract(dragonInicio.toVector()).multiply(avance));

        // Balanceo suave: mira a un lado y a otro, y sube y baja un poco.
        double fase = tick / 40.0;
        lugar.setYaw(dragonFin.getYaw() + (float) (Math.sin(fase) * 10 * avance));
        lugar.add(0, Math.sin(fase * 0.7) * 0.6 * avance, 0);
        dragon.teleport(lugar);
    }

    /** Guinada (yaw) en grados para mirar en una direccion horizontal. */
    private static float guinada(Vector direccion) {
        return (float) Math.toDegrees(Math.atan2(-direccion.getX(), direccion.getZ()));
    }

    // =====================================================================
    //  Utilidades y limpieza
    // =====================================================================

    /** Curva suave entre 0 y 1 (empieza y termina despacio). */
    private static float suave(float x) {
        float t = Math.clamp(x, 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private void borrar(Entity entidad) {
        if (entidad != null && entidad.isValid()) {
            entidad.remove();
        }
    }

    /** Termina el evento: borra todo y devuelve el cielo normal a los jugadores. */
    public void detener() {
        if (!isCancelled()) {
            cancel();
        }
        entidades.forEach(this::borrar);
        jugadoresAfectados().forEach(Player::resetPlayerTime);
        afectados.clear();
        plugin.eventoTerminado();
    }
}
