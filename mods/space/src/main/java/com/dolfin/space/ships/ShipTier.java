package com.dolfin.space.ships;

import java.util.List;

public enum ShipTier {
    BASICA(
            1,
            List.of(
                    new Part("punta_conica", 1),
                    new Part("placa_casco", 8),
                    new Part("aleta_nave", 3),
                    new Part("motor_nave", 1),
                    new Part("tanque_combustible_nave", 1),
                    new Part("asiento_piloto", 1))),
    CARGA(
            2,
            List.of(
                    new Part("punta_conica", 1),
                    new Part("placa_casco", 14),
                    new Part("aleta_nave", 4),
                    new Part("motor_nave", 2),
                    new Part("tanque_combustible_nave", 2),
                    new Part("asiento_piloto", 2),
                    new Part("minecraft:chest", 1))),
    RAPIDA(
            1,
            List.of(
                    new Part("punta_titanio", 1),
                    new Part("placa_casco_titanio", 6),
                    new Part("aleta_nave", 2),
                    new Part("motor_avanzado", 1),
                    new Part("tanque_combustible_nave", 1),
                    new Part("asiento_piloto", 1),
                    new Part("circuito_avanzado", 2)));

    public record Part(String item, int count) {
        public String id() {
            return item.contains(":") ? item : "dolfinspace:" + item;
        }
    }

    public final int seats;
    public final List<Part> template;

    ShipTier(int seats, List<Part> template) {
        this.seats = seats;
        this.template = template;
    }

    public int radius() {
        return this == CARGA ? 2 : 1;
    }

    public String itemId() {
        return "nave_" + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static ShipTier safe(int n) {
        return values()[Math.clamp(n, 0, 2)];
    }

    public boolean accepts(int slot, String id) {
        return slot >= 0 && slot < template.size() && template.get(slot).id().equals(id);
    }

    public boolean complete(List<String> ids, List<Integer> counts) {
        for (int i = 0; i < 7; i++)
            if (i < template.size()
                    ? !accepts(i, ids.get(i)) || counts.get(i) < template.get(i).count()
                    : counts.get(i) > 0) return false;
        return true;
    }
}
