#!/usr/bin/env python3
"""Locale parity and explicit English/Spanish Space UI strings."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "mods/space/src/main/resources/assets/dolfinspace"
PAIRS = {
    "launch": ("LANZAR", "LAUNCH"),
    "passengers": ("Pasajeros", "Passengers"),
    "passengers_on": ("Pasajeros: sí", "Passengers: yes"),
    "passengers_off": ("Pasajeros: no", "Passengers: no"),
    "shake": ("Vibración: %s%%", "Camera shake: %s%%"),
    "destination": ("Destino: %s", "Destination: %s"),
    "kepler": ("Kepler", "Kepler"),
    "overworld": ("Overworld", "Overworld"),
    "fuel": ("Combustible: %s%% / mínimo %s%%", "Fuel: %s%% / minimum %s%%"),
    "oxygen": ("%s s (%s%%)", "%s s (%s%%)"),
    "no_tank": ("sin tanque equipado", "no equipped tank"),
    "no_passenger": ("sin pasajero", "no passenger"),
    "pilot": ("Oxígeno piloto: %s", "Pilot oxygen: %s"),
    "passenger": ("Pasajero: %s", "Passenger: %s"),
    "platform": ("%s Plataforma y ascenso libres", "%s Platform and ascent clear"),
    "ground": ("%s Suelo y ascenso libres", "%s Ground and ascent clear"),
    "fuel_check": ("%s Combustible de viaje", "%s Flight fuel"),
    "controls": ("R: cabina · Shift: bajar/cancelar", "R: cockpit · Shift: exit/cancel"),
    "countdown": ("Despegue: %s s", "Liftoff: %s s"),
    "tank_ok": ("✓ Tanque equipado", "✓ Tank equipped"),
    "tank_warning": ("! Equipa un tanque de oxígeno", "! Equip an oxygen tank"),
    "cockpit": ("Panel de mando", "Cockpit"),
    "transit": ("Tránsito estelar", "Star transit"),
    "assemble": ("Ensamblar", "Assemble"),
    "empty_table": ("Vacía la mesa para cambiar", "Empty the table to change tier"),
    "cargo": ("Carga de nave", "Ship cargo"),
    "leave_first": (
        "Baja a los pasajeros antes de recoger la nave.",
        "Disembark passengers before collecting the ship.",
    ),
    "ship_help": (
        "Carga: clic con cofre. Recoger: agachado + clic.",
        "Cargo: click with chest. Collect: sneak + click.",
    ),
    "fuel_tip": ("Combustible: %s%%", "Fuel: %s%%"),
    "guide0": (
        "2 lingotes → placa (acero/cobre/oro/titanio). Carbón al costado.",
        "2 ingots → plate (steel/copper/gold/titanium). Coal at the side.",
    ),
    "guide1": (
        "Redstone + cuarzo + placa de oro → circuito básico; básico + diamante + cristal → avanzado.",
        "Redstone + quartz + gold plate → basic circuit; basic + diamond + crystal → advanced.",
    ),
    "guide2": (
        "Lava + blaze + bidón vacío → combustible + cubeta. Carbón al costado.",
        "Lava + blaze powder + empty canister → fuel + bucket. Coal at the side.",
    ),
}
static = {
    "Panel de mando": "cockpit",
    "Tránsito estelar": "transit",
    "Ensamblar": "assemble",
    "Carga de nave": "cargo",
    "Baja a los pasajeros antes de recoger la nave.": "leave_first",
    "Carga: clic con cofre. Recoger: agachado + clic.": "ship_help",
}
for p in (ROOT / "mods/space/src").rglob("*.java"):
    if "/test/" in str(p) or "/smoke/" in str(p):
        continue
    s = p.read_text()
    for text, key in static.items():
        s = s.replace(
            "Component.literal(" + json.dumps(text, ensure_ascii=False) + ")",
            f'Component.translatable("space.ui.{key}")',
        )
    s = s.replace(
        'Component.literal("Combustible: "+stored(s).fuel()+"%")',
        'Component.translatable("space.ui.fuel_tip",stored(s).fuel())',
    )
    s = s.replace(
        'Component.literal("Despegue en " + j.ticks() / 20)',
        'Component.translatable("space.ui.countdown",j.ticks()/20)',
    )
    s = s.replace(
        'Component.literal("Pasajeros: "+(passengersAllowed?"permitidos":"bloqueados"))',
        'Component.translatable(passengersAllowed?"space.ui.passengers_on":"space.ui.passengers_off")',
    )
    s = s.replace(
        'Component.literal("Mesa de ensamblaje")',
        'Component.translatable("block.dolfinspace.mesa_ensamblaje")',
    ).replace(
        'Component.literal("Cargador de combustible")',
        'Component.translatable("block.dolfinspace.cargador_combustible")',
    )
    s = s.replace(
        'g.text(font,"Vacía la mesa para cambiar",',
        'g.text(font,Component.translatable("space.ui.empty_table"),',
    )
    s = s.replace(
        "Component.literal(ShipTier.safe(i).name())",
        'Component.translatable("space.tier."+ShipTier.safe(i).name())',
    )
    s = s.replace(
        "Component.literal(part.count()+\" × \"+part.item().replace('_',' '))",
        "Component.translatable(\"space.part\",part.count(),Component.translatable(\"item.\"+part.id().replace(':','.')))",
    )
    s = s.replace("Component.literal(guide)", 'Component.translatable("space.ui.guide"+menu.kind)')
    p.write_text(s)
# Explicit display names, with an English entry for every registered asset.
english = {
    "regolito": "Regolith",
    "roca_lunar": "Lunar rock",
    "bloque_acero": "Steel block",
    "mezcla_acero": "Steel mixture",
    "lingote_acero": "Steel ingot",
    "compresor": "Compressor",
    "refineria": "Refinery",
    "fabricador_circuitos": "Circuit fabricator",
    "compresor_oxigeno": "Oxygen compressor",
    "mesa_ensamblaje": "Assembly table",
    "cargador_combustible": "Fuel loader",
    "bloque_plataforma": "Launch pad",
    "punta_conica": "Nose cone",
    "punta_titanio": "Titanium nose cone",
    "placa_casco": "Hull plate",
    "placa_casco_titanio": "Titanium hull plate",
    "aleta_nave": "Ship fin",
    "motor_nave": "Ship engine",
    "motor_avanzado": "Advanced engine",
    "tanque_combustible_nave": "Ship fuel tank",
    "asiento_piloto": "Pilot seat",
    "nave_basica": "Scout ship",
    "nave_carga": "Cargo ship",
    "nave_rapida": "Interceptor ship",
    "bidon_vacio": "Empty fuel canister",
    "bidon_combustible": "Fuel canister",
    "circuito_basico": "Basic circuit",
    "circuito_avanzado": "Advanced circuit",
    "cristal_energia": "Energy crystal",
    "nucleo_energia": "Energy core",
    "quitina_alienigena": "Alien chitin",
    "diente_gusano": "Worm tooth",
    "parasito": "Parasite",
    "acechador": "Stalker",
    "gusano": "Worm",
    "centinela": "Sentinel",
    "zombi_espacial": "Space zombie",
    "esqueleto_espacial": "Space skeleton",
    "creeper_espacial": "Space creeper",
    "proyectil_energia": "Energy projectile",
    "flecha_cristal": "Crystal arrow",
    "nube_acida": "Acid cloud",
    "nave": "Spaceship",
    "lodo_acido": "Acid mud",
    "hongo_bioluminiscente": "Bioluminescent fungus",
    "bloque_cristal_energia": "Energy crystal block",
}
for material, en in [
    ("acero", "Steel"),
    ("cobre", "Copper"),
    ("oro", "Gold"),
    ("titanio", "Titanium"),
]:
    english["placa_" + material] = en + " plate"
for material, en in [
    ("titanio", "Titanium"),
    ("hierro_meteorico", "Meteoric iron"),
    ("cristal_energia", "Energy crystal"),
]:
    english["mena_" + material] = en + " ore"
    english["lingote_" + material] = en + " ingot"
    english[material + "_crudo"] = "Raw " + en.lower()
for size, en in [("chico", "Small"), ("mediano", "Medium"), ("grande", "Large")]:
    english["tanque_oxigeno_" + size] = en + " oxygen tank"
locales = {
    x: json.loads((A / f"lang/{x}.json").read_text()) for x in ["en_us", "es_es", "es_mx", "es_ar"]
}
base = locales["es_es"]
for locale, data in locales.items():
    for key, value in base.items():
        data.setdefault(key, value)
    for key, (es, en) in PAIRS.items():
        data["space.ui." + key] = en if locale == "en_us" else es
    for tier, es, en in [
        ("BASICA", "Básica", "Scout"),
        ("CARGA", "Carga", "Cargo"),
        ("RAPIDA", "Rápida", "Fast"),
    ]:
        data["space.tier." + tier] = en if locale == "en_us" else es
    data["space.part"] = "%s × %s"
    for event in ["cockpit_hum", "check_beep", "fuel_alarm", "engine", "parasite"]:
        data["space.sound." + event] = {
            "cockpit_hum": ("Zumbido de cabina", "Cockpit hum"),
            "check_beep": ("Pitido de control", "Control beep"),
            "fuel_alarm": ("Alarma de combustible", "Low fuel alarm"),
            "engine": ("Motor de nave", "Ship engine"),
            "parasite": ("Parásito", "Parasite"),
        }[event][locale == "en_us"]
    if locale == "en_us":
        for key in data:
            name = key.split(".")[-1]
            if name in english and key.startswith(("item.", "block.", "entity.")):
                data[key] = english[name]
            elif name.endswith("_spawn_egg"):
                data[key] = english[name.removesuffix("_spawn_egg")] + " spawn egg"
        data.update(
            {
                "biome.dolfinspace.kepler_plains": "Regolith plains",
                "biome.dolfinspace.kepler_craters": "Meteor craters",
                "biome.dolfinspace.kepler_crystal_forest": "Crystal forest",
                "biome.dolfinspace.kepler_acid_marshes": "Acid marshes",
                "biome.dolfinspace.kepler_abyssal_caverns": "Abyssal caverns",
                "tag.item.dolfinspace.cristales_energia": "Energy crystals",
                "tag.item.dolfinspace.lingotes_titanio": "Titanium ingots",
                "tag.item.dolfinspace.placas_tanque": "Tank plates",
                "tag.item.dolfinspace.circuitos_compresor": "Compressor circuits",
                "tag.item.dolfinspace.plantas_compresor": "Oxygen compressor plants",
                "death.attack.dolfinspace.parasito": "%1$s was devoured by a parasite",
                "death.attack.dolfinspace.parasito.player": "%1$s was devoured by a parasite while escaping %2$s",
                "death.attack.dolfinspace.oxygen": "%1$s ran out of oxygen",
                "death.attack.dolfinspace.oxygen.player": "%1$s ran out of oxygen while escaping %2$s",
                "itemGroup.dolfinspace.space": "Space",
                "dolfinspace.command.reloaded": "DolfinSpace configuration reloaded (generation %s, warnings: %s).",
                "dolfinspace.command.info": "DolfinSpace active — dimension: %s, gravity: %s%%, generation: %s.",
                "dolfinspace.travel.blocked": "You can only travel to Kepler aboard a spaceship.",
            }
        )
    write = lambda: None
    (A / f"lang/{locale}.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
# Guarantee parity even for keys that previously existed in a single locale.
union = {k: v for data in locales.values() for k, v in data.items()}
for locale, data in locales.items():
    for k, v in union.items():
        data.setdefault(k, v)
    (A / f"lang/{locale}.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
