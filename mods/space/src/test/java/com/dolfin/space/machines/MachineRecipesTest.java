package com.dolfin.space.machines;
import com.dolfin.space.SpaceConfig;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class MachineRecipesTest {
 @TempDir Path temp;
 @Test void recipesValidateCountsAndMaterials() {
  var r=MachineRecipes.ALL.getFirst();
  assertTrue(r.matches(List.of("dolfinspace:lingote_acero","",""),List.of(2,0,0)));
  assertFalse(r.matches(List.of("dolfinspace:lingote_acero","",""),List.of(1,0,0)));
  assertFalse(r.matches(List.of("minecraft:iron_ingot","",""),List.of(2,0,0)));
  assertFalse(r.matches(List.of("dolfinspace:lingote_acero","x",""),List.of(2,1,0)));
 }
 @Test void sidedHoppers() {
  for(var face:MachineRecipes.Face.values()) for(int slot=0;slot<6;slot++) {
   assertEquals(face==MachineRecipes.Face.TOP && slot<3 || face==MachineRecipes.Face.SIDE && slot==3,MachineRecipes.insert(slot,face));
   assertEquals(face==MachineRecipes.Face.BOTTOM && slot>=4,MachineRecipes.extract(slot,face));
  }
 }
 @Test void fuelConsumptionOnlyForReadyRecipes() {
  assertTrue(MachineRecipes.fuel("minecraft:coal"));assertTrue(MachineRecipes.fuel("minecraft:charcoal"));assertFalse(MachineRecipes.fuel("minecraft:lava_bucket"));
  assertTrue(MachineRecipes.consumeFuel(true,0,true));assertFalse(MachineRecipes.consumeFuel(false,0,true));assertFalse(MachineRecipes.consumeFuel(true,1,true));assertFalse(MachineRecipes.consumeFuel(true,0,false));
 }
 @Test void refineryConsumesEmptyCanisterAndReturnsBucket() {
  var r=MachineRecipes.ALL.getLast();
  assertTrue(r.matches(List.of("minecraft:lava_bucket","minecraft:blaze_powder","dolfinspace:bidon_vacio"),List.of(1,1,1)));
  assertFalse(r.matches(List.of("minecraft:lava_bucket","minecraft:blaze_powder",""),List.of(1,1,0)));
  assertEquals("minecraft:bucket",r.remainder());assertEquals("bidon_combustible",r.output());
 }
 @Test void reloadChangesLiveDuration() throws Exception {
  var c=new SpaceConfig(temp.resolve("config.json"),s->{});
  assertEquals(200,MachineRecipes.duration(MachineRecipes.Kind.COMPRESSOR,c.current().maquinas()));
  Files.writeString(c.file(),Files.readString(c.file()).replace("\"ticksCompresor\": 200","\"ticksCompresor\": 40")); c.reload();
  assertEquals(40,MachineRecipes.duration(MachineRecipes.Kind.COMPRESSOR,c.current().maquinas()));
 }
}
