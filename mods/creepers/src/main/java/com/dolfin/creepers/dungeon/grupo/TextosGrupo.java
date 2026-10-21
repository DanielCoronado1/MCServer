package com.dolfin.creepers.dungeon.grupo;

import com.dolfin.creepers.dungeon.grupo.Grupos.Error;

/** Textos de {@code /party} sin Minecraft, para poder probarlos. */
final class TextosGrupo {
    private TextosGrupo() {}

    /** Por qué falló una operación de grupo, en lenguaje de jugador. */
    static String error(Error error) {
        return switch (error) {
            case YA_EN_GRUPO -> "Ya estás en un grupo.";
            case SIN_GRUPO -> "No estás en ningún grupo.";
            case NO_ES_LIDER -> "Solo el líder del grupo puede hacer eso.";
            case A_UNO_MISMO -> "No puedes hacerlo contigo mismo.";
            case EN_PARTIDA -> "No se puede cambiar el grupo mientras hay una partida en curso.";
            case EN_READY_CHECK -> "No se puede cambiar el grupo durante un ready check.";
            case GRUPO_LLENO -> "El grupo está lleno (máximo " + Grupos.MAX_MIEMBROS + ").";
            case OBJETIVO_EN_GRUPO -> "Ese jugador ya está en un grupo.";
            case OBJETIVO_OCUPADO -> "Ese jugador está en una partida o en una fila.";
            case OBJETIVO_NO_ESTA -> "Ese jugador no está en tu grupo.";
            case OCUPADO -> "No puedes unirte a un grupo estando en una partida o en una fila.";
            case SIN_INVITACION -> "No tienes ninguna invitación pendiente.";
        };
    }

    /** Aviso al grupo cuando un miembro lo deja. */
    static String salida(String nombre, Grupos.MotivoSalida motivo) {
        return switch (motivo) {
            case VOLUNTARIO -> nombre + " salió del grupo.";
            case EXPULSADO -> nombre + " fue expulsado del grupo.";
            case DESCONEXION -> nombre + " lleva demasiado tiempo desconectado y sale del grupo.";
        };
    }
}
