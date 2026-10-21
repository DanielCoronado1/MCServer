/**
 * Sistema de dungeons instanciadas (épica #10). Todo corre en el hilo del servidor.
 *
 * <h2>Mapa</h2>
 *
 * <ul>
 *   <li>{@link com.dolfin.creepers.dungeon.Dungeons}: punto de entrada. Crea y conecta los módulos
 *       y da acceso a ellos: {@code config()}, {@code registro()}, {@code partidas()}, {@code
 *       entradaSalida()}, {@code eventos()}, {@code recargarConfig()}, {@code
 *       usarReiniciador(...)}.
 *   <li>{@code config}: {@code config/dolfin-dungeons.json} ({@link
 *       com.dolfin.creepers.dungeon.config.DungeonsConfig}), un record por sección y un {@code
 *       Snapshot} inmutable por recarga. {@code CodecDefinicion} lee/escribe la lista de dungeons;
 *       {@code guardarDungeon} es lo que usan {@code setzone}/{@code setentry}/{@code enable}
 *       (#13), y {@code Dungeons.guardarDungeon} además actualiza el registro.
 *   <li>{@code modelo}: estado puro, sin Minecraft y con pruebas. {@link
 *       com.dolfin.creepers.dungeon.modelo.RegistroDungeons} guarda el estado de cada dungeon
 *       ({@code LIBRE → EN_PARTIDA → REINICIANDO → LIBRE}, o {@code DESHABILITADA}), las partidas
 *       ({@code Partida} con sus {@code Miembro}: {@code VIVO}, {@code ESPECTADOR}, {@code
 *       DESCONECTADO}, {@code FUERA}) y qué ocupa a cada jugador (una partida o una fila). Es el
 *       único que modifica partidas y miembros, y publica un evento por cada cambio.
 *   <li>{@code eventos}: {@link com.dolfin.creepers.dungeon.eventos.EventoDungeon} y su bus
 *       síncrono. Los demás issues se enganchan aquí en vez de llamarse entre sí.
 *   <li>{@code entrada}: entrada y salida a prueba de caídas (#12). {@code EntradaSalida} guarda en
 *       {@code DatosDungeons} (SavedData) posición, rotación y modo de juego antes de entrar, y los
 *       restaura en toda salida, al conectarse tras una caída y al salir de la dimensión por medios
 *       ajenos.
 *   <li>{@code mundo}: la dimensión {@code dolfincreepers:dungeons} (datapack en {@code
 *       data/dolfincreepers/dimension}), los tickets de chunks durante la partida ({@code
 *       CargaChunks}) y el {@code Reiniciador} de zonas.
 *   <li>{@code muerte}: muerte, vidas, checkpoints, tumbas y hardcore (#16). {@code Muertes}
 *       cancela la muerte vanilla de los miembros ({@code ALLOW_DEATH}, respetando el tótem) y
 *       aplica {@code ReglasMuerte}: reaparecer en el checkpoint, tumba ({@code Tumbas}, bloque
 *       {@code dolfincreepers:tumba}, contenido en {@code DatosMuerte}), espectador confinado o
 *       {@code CastigosHardcore} (pérdida total y {@code BanHardcore} con fuente {@code
 *       DolfinDungeons}). {@code FinPorBajas} termina en derrota la partida sin vivos.
 *   <li>{@link com.dolfin.creepers.dungeon.ServicioPartidas}: fachada para empezar, abandonar,
 *       terminar y reiniciar partidas con jugadores reales.
 *   <li>{@code mixin}: sin portales ni aparición natural de mobs en la dimensión.
 *   <li>{@code comando}: {@code /dungeon} (#13). {@code ComandosJugador} (list, info, join, leave,
 *       queue, top) y {@code ComandosAdmin} ({@code /dungeon admin ...}); permisos en {@code
 *       PermisosDungeon} ({@code dolfin.dungeon.use}, {@code .admin}, {@code .<id>}); ediciones
 *       validadas en {@code EdicionDungeon}; {@code VaritaZona} marca las esquinas de {@code
 *       setzone}; {@code BanesDungeon} quita con {@code pardon} los bans de {@code BanHardcore}.
 *   <li>{@code fila}: {@link com.dolfin.creepers.dungeon.fila.ServicioFila}, cómo entra {@code
 *       join} (directo mientras no haya grupos). {@code estadisticas}: {@link
 *       com.dolfin.creepers.dungeon.estadisticas.Estadisticas}, tiempos para {@code info}/{@code
 *       top}. Ambos se sustituyen con {@code Dungeons.usarFila}/{@code usarEstadisticas}.
 * </ul>
 *
 * <h2>Dónde se engancha cada issue</h2>
 *
 * <ul>
 *   <li>#13 comandos: ya hechos; usan {@code Dungeons.partidas()}, {@code Dungeons.registro()},
 *       {@code Dungeons.guardarDungeon(...)} y {@code Dungeons.recargarConfig()}. {@code admin
 *       save}/{@code reset} esperan a #19 en {@code ComandosAdmin}; {@code info}/{@code top} a #22.
 *   <li>#14 grupos y fila: implementar {@code ServicioFila} e instalarlo con {@code
 *       Dungeons.usarFila} ({@code join}, {@code leave}, {@code queue}, {@code list} y {@code
 *       status} ya lo consultan); por debajo {@code registro().entrarEnFila/salirDeFila}, {@code
 *       validarInicio} antes del ready check, luego {@code partidas().iniciar}.
 *   <li>#15 reglas: {@code DimensionDungeons.es(level)} y {@code registro().partidaDe(uuid)}.
 *   <li>#16 muerte (hecho, paquete {@code muerte}): usa {@code
 *       registro().registrarMuerte/pasarAEspectador/alcanzarCheckpoint} y castiga en hardcore
 *       {@code JugadorMurio} con causa {@code DESCONEXION} (ban sin kick, vaciado al volver) y
 *       {@code JugadorSalio} con motivo {@code ABANDONO} (ban y kick) o {@code AFK} (sin ban).
 *       Basta con que {@code /dungeon leave} llame a {@code partidas().salir(jugador, ABANDONO)}.
 *   <li>#13 {@code pardon}: quitar solo bans con {@code BanHardcore.esDeDungeon(entrada)} (fuente
 *       {@code BanHardcore.FUENTE}); el nodo de bypass es {@code PermisoBypassBan.NODO}.
 *   <li>#17 fin: {@code partidas().terminar(...)}; {@code Partida.hayVivosOPorVolver()}; {@code
 *       partidas().salir(jugador, AFK)}. La derrota por bajas ya la detecta {@code FinPorBajas}
 *       ({@code ReglasMuerte.derrotaPorBajas}) y llama a {@code terminar(…, DERROTA)}; las tumbas
 *       se devuelven o pierden al publicarse {@code PartidaTerminada} según el resultado.
 *   <li>#18 escalado: {@code PartidaIniciada}, {@code Partida.jugadoresIniciales()} y {@code
 *       parametros()}.
 *   <li>#19 plantillas: {@code Dungeons.usarReiniciador(...)}; se llama al terminar, en {@code
 *       reset} y al arrancar tras una caída.
 *   <li>#22 estadísticas: {@code PartidaTerminada}.
 * </ul>
 *
 * <h2>Caídas</h2>
 *
 * Las partidas solo viven en memoria. En disco quedan el retorno de cada jugador dentro y las
 * dungeons con partida o reinicio en curso. Al arrancar, esas dungeons se reinician; al conectarse,
 * cada jugador con retorno vuelve a su sitio y modo sin castigo. Un servidor parado normalmente
 * sigue el mismo camino.
 *
 * <h2>Compatibilidad</h2>
 *
 * La dimensión no se puede reclamar con Open Parties and Claims ({@code claimableDimensionsList}) y
 * Ledger no la registra ({@code worldBlacklist}); ver {@code server/config}. Chunky solo genera si
 * se le pide; el generador vacío lo hace inofensivo, pero no hace falta pre-generar la dimensión.
 * El regreso a {@code dolfinspace:kepler} se autoriza ante el control de viajes de Space ({@code
 * entrada.AutorizacionViaje}).
 */
package com.dolfin.creepers.dungeon;
