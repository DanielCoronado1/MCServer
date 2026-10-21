package com.dolfin.creepers.dungeon.modelo;

/** Posición con orientación dentro de la dimensión de dungeons (entrada, checkpoint o salida). */
public record Punto(double x, double y, double z, float yaw, float pitch) {}
