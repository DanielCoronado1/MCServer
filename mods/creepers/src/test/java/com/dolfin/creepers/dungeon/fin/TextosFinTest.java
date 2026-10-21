package com.dolfin.creepers.dungeon.fin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.modelo.Dificultad;

import org.junit.jupiter.api.Test;

import java.util.List;

class TextosFinTest {
    @Test
    void duracionConYSinHoras() {
        assertEquals("0:00", TextosFin.duracion(0));
        assertEquals("12:34", TextosFin.duracion(754));
        assertEquals("1:02:03", TextosFin.duracion(3723));
        assertEquals("0:00", TextosFin.duracion(-5));
        assertEquals("12:34.056", TextosFin.duracionExacta(754_056));
    }

    @Test
    void listaDeNombres() {
        assertEquals("Ana", TextosFin.lista(List.of("Ana")));
        assertEquals("Ana y Beto", TextosFin.lista(List.of("Ana", "Beto")));
        assertEquals("Ana, Beto y Cleo", TextosFin.lista(List.of("Ana", "Beto", "Cleo")));
    }

    @Test
    void barraMuestraNombreDificultadYTiempo() {
        String barra = TextosFin.barraJugando("Cripta", Dificultad.HARDCORE, 754, 45);
        assertEquals("Cripta · Hardcore · 12:34 / 45:00", barra);
        assertTrue(TextosFin.barraConAviso(barra, 5).startsWith("¡Quedan 5 min!"));
    }

    @Test
    void anunciosIncluyenMiembrosDificultadYTiempo() {
        assertEquals(
                "¡Ana y Beto completó Cripta en Medio en 12:34!",
                TextosFin.anuncioVictoria(List.of("Ana", "Beto"), "Cripta", Dificultad.MEDIO, 754));
        assertEquals("Queda 1 minuto para que se agote el tiempo", TextosFin.avisoTiempo(1));
        assertEquals("Quedan 10 minutos para que se agote el tiempo", TextosFin.avisoTiempo(10));
    }
}
