# Guía de estilo tétrico

Todo lo que sigue sale de comparar la primera versión de los gigantes ("se ven baratos") con la segunda
(aprobada). Las reglas explican el porqué para que puedas aplicarlas a mobs que no se parecen a estos.

## Lo que hace que un mob se vea tétrico

1. **Silueta rota.** Lo inquietante es que la forma conocida esté *mal*: extremidades demasiado largas
   (patas de araña del creeper), asimetría (un brazo enorme y otro atrofiado), postura encorvada, cabeza
   hundida o ladeada, algo creciendo donde no debe (segunda cabeza, hongos, cristales, púas de hueso).
   Una o dos ideas fuertes valen más que diez detalles pequeños.
2. **Cara que mira mal.** Cuencas negras profundas, un ojo rojo y otro blanco muerto (o los dos rojos),
   boca abierta con dientes, mandíbula colgando. La cara es lo primero que el jugador mira.
3. **Cuerpo abierto.** Costillas o vértebras expuestas con carne oscura alrededor, heridas con forma
   (manchas con borde), cráneo asomando. Siempre como *formas legibles*, nunca como salpicaduras.
4. **Historia.** Restos de lo que era: camisa rota, overol, casco, traje espacial, herramienta clavada.
   Lo familiar destruido inquieta más que lo totalmente ajeno.
5. **Tétrico, no gore.** Sangre seca y carne oscura (rampa `CARNE`), no rojo brillante ni vísceras.

## Lo que lo hace ver "barato" (evitar)

- **Ruido de píxel suelto**: colores al azar por píxel = estática de tele. Usa `pintar()`, que agrupa
  el ruido en zonas suaves.
- **Caras planas**: sin luz arriba / sombra abajo el volumen desaparece. `pintar()` ya lo hace; usa
  `sombra()` en uniones (cuello, cadera, donde una pieza tapa a otra).
- **Garabatos**: venas o grietas como líneas aleatorias. Mejor una herida con `mancha()` bien ubicada.
- **Arcoíris**: muchos colores saturados con el mismo brillo compiten y ensucian la silueta.
- **Simetría perfecta y pose de maniquí**: se ve a juguete. Rompe con rotaciones de 5–15° en cabeza y brazos.

## Paleta

- **Un material dominante** (piel podrida, verde creeper, traje) + **carne** + **hueso** + como mucho
  **un acento** temático (amatista, hongo, naranja del traje). Todo lo demás, en la misma familia.
- Cada material es una **rampa** de 5–6 tonos de oscuro a claro, con **sombras más frías** (hacia azul/morado)
  y **luces más cálidas** (hacia amarillo). Así se ve rico sin agregar colores. Para crear una rampa nueva,
  copia el patrón de las que hay en `motor.py`.
- Los valores de la base del material rondan `base=0.45–0.6`: lo bastante oscuro para verse sucio, con luces
  que dibujen el volumen.

## Checklist antes de mostrar una lámina

- [ ] ¿Se reconoce el mob y su mutación solo por la silueta (imagina la lámina en negro)?
- [ ] ¿La cara se lee a 1×: ojos, cuencas, dientes?
- [ ] ¿Hay como mucho 3 materiales + 1 acento?
- [ ] ¿Toda pieza nace de otra (nada flota, nada atraviesa de forma rara)? Revisa la vista de espalda también.
- [ ] ¿Las heridas y crecimientos son formas con borde, no ruido?
- [ ] ¿Se nota luz arriba y sombra abajo en las piezas grandes?
- [ ] ¿Da un poco de miedo, o al menos incomoda? Si da ternura, vuelve al paso de concepto.
- [ ] Si es gigante/jefe: ¿la escala tiene sentido junto al jugador ("Tamaño real" en el visor)?
