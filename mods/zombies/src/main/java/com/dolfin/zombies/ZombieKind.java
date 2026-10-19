package com.dolfin.zombies;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Registry identity and editable defaults, never a runtime stat source. */
public enum ZombieKind {
    MINERO("Minero",24,3,.9,8,1,1,"romperBloques"),
    LADRON("Ladrón",16,1,1.4,6,1,1,"robar"),
    BOMBERO("Bombero",22,4,1,6,1,1,"quemarBloques"),
    ABISMO("Ahogado del abismo",26,4,1,8,1,2,"agarre"),
    ESCUDERO("Escudero",28,4,.85,6,1,1,"bloquear"),
    HAMBRIENTO("Hambriento",16,2,1.35,8,1,3,"hambre"),
    GRITON("Gritón",18,2,1,4,1,1,"llamada"),
    BRUJA("Bruja zombie",22,2,.95,4,1,1,"curacion"),
    GRANJERO("Granjero",20,3,1,5,1,1,"destruirCultivos"),
    CONGELADO("Congelado",24,3,.95,15,1,2,"congelar"),
    JINETE("Jinete",20,3,1,3,1,1,"montura"),
    BILIOSO("Bilioso",50,5,.6,5,1,1,"nube"),
    ENANO("Enano",8,2,1.2,10,3,5,"cuerpoPequeno"),
    CONTAGIADO("Contagiado",20,3,1,7,1,1,"convertirAlMorir"),
    MIMO("Mimo",30,4,1,0,1,1,"aparicionPorMuerte"),
    EXPLORADOR("Explorador nocturno",18,4,1.1,4,1,1,"invisibilidad");

    public final String displayName;
    private final double health, damage, speed;
    private final int weight, min, max;
    private final String ability;
    ZombieKind(String name,double health,double damage,double speed,int weight,int min,int max,String ability) {
        this.displayName=name;this.health=health;this.damage=damage;this.speed=speed;
        this.weight=weight;this.min=min;this.max=max;this.ability=ability;
    }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT)+"_zombie"; }
    public static ZombieKind byId(String id) {
        for (var k:values()) if(k.id().equals(id)) return k;
        throw new IllegalArgumentException("Zombie desconocido: "+id);
    }
    public JsonObject defaults() {
        JsonObject o=JsonParser.parseString("""
            {"activo":true,"vida":20,"danio":3,"velocidad":1,"armadura":2,"escala":1,"peso":10,
            "grupo":[1,1],"biomas":["#minecraft:is_overworld"],"biomasExcluidos":["minecraft:mushroom_fields"],
            "alturaY":[-64,320],"luzMax":0,"quemaConSol":true,"lunaDeSangre":{"pesoExtra":3,"bonusStats":1.25},
            "xp":5,"loot":"dolfinzombies:entities/zombie","habilidades":{}}
            """).getAsJsonObject();
        o.addProperty("vida",health);o.addProperty("danio",damage);o.addProperty("velocidad",speed);o.addProperty("peso",weight);
        o.add("grupo",JsonParser.parseString("["+min+","+max+"]"));o.addProperty("loot","dolfinzombies:entities/"+id());
        JsonObject a=JsonParser.parseString(switch(this){
            case MINERO -> "{\"durezaMax\":3,\"tiempoPorBloque\":40,\"distanciaMaxRompiendo\":16,\"soltarBloques\":false,\"maxBloquesPorMinuto\":20,\"listaNegra\":[\"minecraft:chest\",\"minecraft:trapped_chest\",\"minecraft:ender_chest\",\"#minecraft:copper_chests\",\"#minecraft:beds\",\"minecraft:barrel\",\"#minecraft:shulker_boxes\",\"minecraft:furnace\",\"minecraft:blast_furnace\",\"minecraft:smoker\",\"minecraft:crafting_table\",\"minecraft:enchanting_table\",\"minecraft:cartography_table\",\"minecraft:fletching_table\",\"minecraft:smithing_table\",\"minecraft:nether_portal\",\"minecraft:end_portal\",\"minecraft:end_portal_frame\",\"minecraft:end_gateway\"]}";
            case LADRON -> "{\"probabilidadRobo\":0.35,\"velocidadHuida\":1.6,\"listaNegra\":[],\"soloHotbar\":true,\"minutosParaSoltar\":10}";
            case BOMBERO -> "{\"probabilidadPorPaso\":0.08,\"quemarJugador\":4,\"segundosEnAgua\":3}";
            case ABISMO -> "{\"fuerzaArrastre\":0.12,\"duracionAgarre\":60,\"enfriamiento\":200,\"profundidadMinima\":5}";
            case ESCUDERO -> "{\"anguloBloqueo\":90,\"hachaDesactivaEscudo\":5,\"bloqueaProyectiles\":true,\"probabilidadSoltarEscudo\":0.08}";
            case HAMBRIENTO -> "{\"hambrePorGolpe\":3,\"saturacionPorGolpe\":2,\"efectoHambre\":5,\"distraidoPorComida\":true}";
            case GRITON -> "{\"radioLlamada\":30,\"enfriamiento\":400,\"maxZombiesLlamados\":15,\"incluyeVanilla\":true,\"refuerzosLunaDeSangre\":2}";
            case BRUJA -> "{\"curacion\":6,\"radioCuracion\":8,\"enfriamientoPocion\":80,\"distanciaPreferida\":8}";
            case GRANJERO -> "{\"radioBusqueda\":24,\"maxBloquesPorMinuto\":30}";
            case CONGELADO -> "{\"lentitudSegundos\":4,\"nivelLentitud\":1,\"congelacionPolvo\":true,\"bloqueAlMorir\":\"minecraft:ice\",\"radioHielo\":1}";
            case JINETE -> "{\"vidaMontura\":20}";
            case BILIOSO -> "{\"radioNube\":3,\"duracionNube\":100,\"resistenciaEmpuje\":0.8}";
            case ENANO -> "{}";
            case CONTAGIADO -> "{\"probabilidadInfeccion\":0.25,\"duracionInfeccion\":6000}";
            case MIMO -> "{\"probabilidadAparecer\":0.30,\"minutosVentana\":10,\"copiarEncantamientos\":false}";
            case EXPLORADOR -> "{\"visibleAlRecibirDanio\":3,\"pasosSilenciosos\":true,\"revelarConLuz\":true}";
        }).getAsJsonObject();a.addProperty("activo",this!=GRANJERO);
        if(this==BRUJA){a.addProperty("efectoLanzado","minecraft:slowness");a.addProperty("duracionEfecto",6);}o.getAsJsonObject("habilidades").add(ability,a);
        if(this==GRANJERO){JsonObject b=new JsonObject();b.addProperty("activo",false);b.add("animales",JsonParser.parseString("[\"minecraft:cow\",\"minecraft:sheep\",\"minecraft:pig\",\"minecraft:chicken\"]"));o.getAsJsonObject("habilidades").add("atacarAnimales",b);}
        if(this==ENANO){o.addProperty("escala",.5);o.add("alturaY",JsonParser.parseString("[-64,39]"));}
        if(this==BOMBERO||this==CONGELADO||this==EXPLORADOR)o.addProperty("quemaConSol",false);
        com.dolfin.zombies.kinds.CombatDefaults.extend(this,o.getAsJsonObject("habilidades")); // #5 combat kinds
        String biomes=switch(this){
            case BOMBERO -> "[\"#minecraft:is_forest\",\"#minecraft:is_savanna\"]";
            case ABISMO -> "[\"#minecraft:is_ocean\",\"#minecraft:is_river\"]";
            case GRANJERO -> "[\"minecraft:plains\",\"minecraft:sunflower_plains\"]";
            case CONGELADO -> "[\"minecraft:snowy_plains\",\"minecraft:ice_spikes\",\"minecraft:snowy_taiga\",\"minecraft:frozen_peaks\",\"minecraft:jagged_peaks\",\"minecraft:snowy_slopes\",\"minecraft:grove\",\"minecraft:frozen_river\",\"minecraft:snowy_beach\",\"minecraft:frozen_ocean\",\"minecraft:deep_frozen_ocean\"]";
            default -> "[\"#minecraft:is_overworld\"]";
        };o.add("biomas",JsonParser.parseString(biomes));return o;
    }
}
