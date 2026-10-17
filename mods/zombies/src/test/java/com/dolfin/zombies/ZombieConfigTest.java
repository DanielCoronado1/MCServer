package com.dolfin.zombies;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ZombieConfigTest {
    @TempDir Path root;
    List<String> warnings=new ArrayList<>();
    ZombieConfig config;
    @BeforeEach void setup(){config=new ZombieConfig(root,warnings::add);}
    Path minero(){return root.resolve("zombies/minero_zombie.json");}
    JsonObject read(Path path)throws Exception{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
    void edit(String field,JsonElement value)throws Exception{var raw=read(minero());raw.add(field,value);Files.writeString(minero(),raw.toString());}
    @Test void createsAllDefaultsAndDiagnosticArtifact()throws Exception{
        assertEquals(16,Files.list(root.resolve("zombies")).count());assertTrue(Files.exists(root.resolve("general.json")));
        var report=config.reload();assertEquals(17,report.files());assertEquals(0,report.created());assertTrue(report.problems().isEmpty());
        assertEquals(16,read(root.resolve("diagnostics/last-reload.json")).getAsJsonObject("zombies").size());
        assertEquals(24,config.current().zombie(ZombieKind.MINERO).get("vida").getAsDouble());
        assertEquals(0,config.current().zombie(ZombieKind.MIMO).get("peso").getAsInt());
    }
    @Test void malformedJsonFallsBackWithExactFileAndParserReason()throws Exception{
        Files.writeString(minero(),"{broken");var report=config.reload();assertEquals(1,report.problems().size());
        assertEquals("zombies/minero_zombie.json",report.problems().getFirst().file());assertEquals("$",report.problems().getFirst().field());
        assertEquals(24,config.current().zombie(ZombieKind.MINERO).get("vida").getAsDouble());assertFalse(warnings.isEmpty());
        assertEquals("{broken",Files.readString(minero()));
    }
    @Test void invalidFieldsKeepValidSiblings()throws Exception{
        var raw=read(minero());raw.addProperty("vida",-1);raw.addProperty("danio",7);raw.addProperty("activo","false");raw.add("grupo",JsonParser.parseString("[4,1]"));raw.addProperty("peso",1.5);
        raw.add("biomas",JsonParser.parseString("[\"BAD ID\"]"));raw.addProperty("loot","../bad");raw.getAsJsonObject("lunaDeSangre").addProperty("bonusStats",0);
        Files.writeString(minero(),raw.toString());var report=config.reload();assertEquals(7,report.problems().size());var result=config.current().zombie(ZombieKind.MINERO);
        assertEquals(24,result.get("vida").getAsDouble());assertEquals(7,result.get("danio").getAsDouble());assertTrue(result.get("activo").getAsBoolean());
        assertTrue(report.problems().stream().anyMatch(e->e.field().equals("lunaDeSangre.bonusStats")));
    }
    @Test void reloadOnlyChangesNewSnapshots()throws Exception{
        var old=config.current();edit("vida",new JsonPrimitive(77));config.reload();
        assertEquals(24,old.zombie(ZombieKind.MINERO).get("vida").getAsDouble());assertEquals(77,config.current().zombie(ZombieKind.MINERO).get("vida").getAsDouble());
        var external=config.current().zombie(ZombieKind.MINERO);external.addProperty("vida",1);assertEquals(77,config.current().zombie(ZombieKind.MINERO).get("vida").getAsDouble());
    }
    @Test void variantAndGlobalSwitchesAreIndependentAndPersist()throws Exception{
        config.setActive(ZombieKind.MINERO,false);assertFalse(config.current().enabled(ZombieKind.MINERO));assertTrue(config.current().enabled(ZombieKind.LADRON));
        assertFalse(read(minero()).get("activo").getAsBoolean());
        var general=read(root.resolve("general.json"));general.addProperty("activo",false);Files.writeString(root.resolve("general.json"),general.toString());config.reload();
        for(var kind:ZombieKind.values())assertFalse(config.current().enabled(kind));
    }
    @Test void abilitySwitchOnlyDisablesThatAbility()throws Exception{
        assertTrue(config.current().abilityEnabled(ZombieKind.MINERO,"romperBloques"));
        var raw=read(minero());raw.getAsJsonObject("habilidades").getAsJsonObject("romperBloques").addProperty("activo",false);Files.writeString(minero(),raw.toString());config.reload();
        assertFalse(config.current().abilityEnabled(ZombieKind.MINERO,"romperBloques"));assertTrue(config.current().enabled(ZombieKind.MINERO));
        assertTrue(config.current().abilityEnabled(ZombieKind.LADRON,"robar"));assertFalse(config.current().abilityEnabled(ZombieKind.GRANJERO,"destruirCultivos"));assertFalse(config.current().abilityEnabled(ZombieKind.MINERO,"missing"));
    }
    @Test void extraAbilityRequiresExplicitSwitch()throws Exception{
        var raw=read(minero());raw.getAsJsonObject("habilidades").add("custom",JsonParser.parseString("{\"radio\":4}"));Files.writeString(minero(),raw.toString());
        assertEquals("habilidades.custom",config.reload().problems().getFirst().field());assertFalse(config.current().abilityEnabled(ZombieKind.MINERO,"custom"));
    }
    @Test void missingFileIsRegeneratedAndMalformedTogglePreservesInput()throws Exception{
        Files.delete(minero());assertEquals(1,config.reload().created());Files.writeString(minero(),"{broken");
        assertThrows(RuntimeException.class,()->config.setActive(ZombieKind.MINERO,false));assertEquals("{broken",Files.readString(minero()));
    }
    @Test void identifiersGroupsAndIntegerLimitsValidate()throws Exception{
        edit("alturaY",JsonParser.parseString("[-64,39]"));assertTrue(config.reload().problems().isEmpty());
        edit("luzMax",new JsonPrimitive(16));assertEquals("luzMax",config.reload().problems().getFirst().field());
        var raw=read(minero());raw.addProperty("luzMax",0);raw.addProperty("vida","24");Files.writeString(minero(),raw.toString());assertEquals("vida",config.reload().problems().getFirst().field());
    }
    @Test void savedSettingsMigrateMissingFieldsAndRejectMalformedValues(){
        var saved=config.validateSaved(ZombieKind.MINERO,"{\"vida\":42,\"xp\":\"invalid\"}");
        assertEquals(42,saved.get("vida").getAsDouble());assertEquals(5,saved.get("xp").getAsInt());
        assertEquals("dolfinzombies:entities/minero_zombie",saved.get("loot").getAsString());
        assertEquals(24,config.validateSaved(ZombieKind.MINERO,"{broken").get("vida").getAsDouble());
    }
    @Test void permissionFallbackRespectsExplicitDeny(){
        assertTrue(ZombiePermissions.resolve(null,true));assertFalse(ZombiePermissions.resolve(null,false));assertFalse(ZombiePermissions.resolve(false,true));assertTrue(ZombiePermissions.resolve(true,false));
    }
}
