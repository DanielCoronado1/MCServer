package com.dolfin.space.assets;
import com.dolfin.space.planet.PlanetCatalog;
import com.dolfin.space.mobs.FaunaRules;
import com.dolfin.space.ships.*;
import com.dolfin.space.travel.OxygenStatusBridge;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Source/catalog audit avoids bootstrapping Minecraft registries without Fabric mixins in JUnit. */
class SpaceAssetsAuditTest {
 static final Path R=Path.of("src/main/resources"), A=R.resolve("assets/dolfinspace");
 static JsonObject json(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
 static Set<String> registrations()throws Exception{
  var ids=new TreeSet<String>();ids.addAll(PlanetCatalog.BLOCKS);ids.addAll(PlanetCatalog.ITEMS);
  for(var mob:FaunaRules.MOBS)ids.add(mob+"_spawn_egg");for(var tier:ShipTier.values())ids.add(tier.itemId());
  for(String file:List.of("SpaceContent.java","machines/MachinesModule.java","ships/ShipsModule.java","oxygen/OxygenModule.java","mobs/MobsModule.java")){
   String s=Files.readString(Path.of("src/main/java/com/dolfin/space").resolve(file));
   var matcher=Pattern.compile("(?:registerItem|registerBlockWithItem|machine)\\(\\s*\"([a-z_]+)\"").matcher(s);while(matcher.find())ids.add(matcher.group(1));
   // Module loops register each literal in List.of; do not infer completeness from asset files.
   matcher=Pattern.compile("for\\s*\\(String id\\s*:\\s*List.of\\((.*?)\\)\\)",Pattern.DOTALL).matcher(s);
   while(matcher.find()){var names=Pattern.compile("\"([a-z_]+)\"").matcher(matcher.group(1));while(names.find())ids.add(names.group(1));}
  }return ids;
 }
 void model(String id,Set<String> seen)throws Exception{
  if(!id.startsWith("dolfinspace:")||!seen.add(id))return;
  var m=json(A.resolve("models/"+id.substring(12)+".json"));
  if(m.has("parent"))model(m.get("parent").getAsString(),seen);
  if(m.has("textures"))for(var texture:m.getAsJsonObject("textures").entrySet()){
   String value=texture.getValue().getAsString();if(value.startsWith("dolfinspace:"))assertTrue(Files.isRegularFile(A.resolve("textures/"+value.substring(12)+".png")),value);
  }
 }
 void itemTree(JsonElement tree)throws Exception{
  if(tree.isJsonArray())for(var c:tree.getAsJsonArray())itemTree(c);
  if(!tree.isJsonObject())return;var o=tree.getAsJsonObject();
  if(o.has("model")&&o.get("model").isJsonPrimitive())model(o.get("model").getAsString(),new HashSet<>());
  for(var e:o.entrySet())if(e.getValue().isJsonObject()||e.getValue().isJsonArray())itemTree(e.getValue());
 }
 @Test void everyRegisteredItemBlockAndModelResolves()throws Exception{
  var ids=registrations();assertTrue(ids.size()>50);
  for(var id:ids){var item=A.resolve("items/"+id+".json");assertTrue(Files.exists(item),id);itemTree(json(item));}
  try(var files=Files.list(A.resolve("blockstates"))){for(var p:files.toList())itemTree(json(p));}
 }
 @Test void everyEntityRendererHasTextureOrExplicitParticleItemRenderer()throws Exception{
  for(var mob:FaunaRules.MOBS)assertTrue(Files.exists(A.resolve("textures/entity/"+mob+".png")),mob);
  for(var tier:ShipTier.values())assertTrue(Files.exists(A.resolve("textures/entity/"+tier.itemId()+".png")));
  assertTrue(Files.exists(A.resolve("textures/entity/flecha_cristal.png")));assertTrue(Files.exists(A.resolve("textures/entity/ship_flame.png")));
  String renderers=Files.readString(Path.of("src/client/java/com/dolfin/space/client/mobs/MobsClient.java"));
  assertTrue(renderers.contains("NoopRenderer"));assertTrue(renderers.contains("ThrownItemRenderer"));
 }
 @Test void localesHaveEveryRegisteredKeyAndMatchingFormatArguments()throws Exception{
  var en=json(A.resolve("lang/en_us.json"));
  for(String locale:List.of("es_es","es_mx","es_ar","en_us")){
   var lang=json(A.resolve("lang/"+locale+".json"));assertEquals(en.keySet(),lang.keySet(),locale);
   for(String id:registrations())assertTrue(lang.has("item.dolfinspace."+id)||lang.has("block.dolfinspace."+id),locale+":"+id);
   for(String id:FaunaRules.MOBS)assertTrue(lang.has("entity.dolfinspace."+id));
   for(var key:en.keySet()){assertFalse(lang.get(key).getAsString().isBlank(),key);assertEquals(en.get(key).getAsString().replace("%%","").chars().filter(c->c=='%').count(),lang.get(key).getAsString().replace("%%","").chars().filter(c->c=='%').count(),key);}
  }
 }
 @Test void soundRegistrationsHaveVorbisFilesAndSubtitles()throws Exception{
  var sounds=json(A.resolve("sounds.json"));String source=Files.readString(Path.of("src/main/java/com/dolfin/space/assets/SpaceSounds.java"));var names=Pattern.compile("register\\(\"([^\"]+)\"\\)").matcher(source);
  int count=0;while(names.find()){count++;assertTrue(sounds.has(names.group(1)));}
  assertEquals(sounds.size(),count);
  for(var entry:sounds.entrySet()){
   var event=entry.getValue().getAsJsonObject();assertTrue(json(A.resolve("lang/en_us.json")).has(event.get("subtitle").getAsString()));
   for(var sample:event.getAsJsonArray("sounds")){String id=sample.getAsJsonObject().get("name").getAsString();byte[] bytes=Files.readAllBytes(A.resolve("sounds/"+id.substring(12)+".ogg"));assertTrue(bytes.length>200);assertEquals("OggS",new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII));assertTrue(new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1).contains("vorbis"));}
  }
 }
 @Test void mergedTagsMixinsAndTranslationsResolve()throws Exception{
  var items=registrations();
  for(String tag:List.of("lingotes_titanio","cristales_energia")){
   var values=json(R.resolve("data/dolfinspace/tags/item/"+tag+".json")).getAsJsonArray("values");assertFalse(values.isEmpty());
   for(var v:values){String id=v.isJsonPrimitive()?v.getAsString():v.getAsJsonObject().get("id").getAsString();if(id.startsWith("dolfinspace:"))assertTrue(items.contains(id.substring(12)),id);}
  }
  for(var mixin:json(R.resolve("fabric.mod.json")).getAsJsonArray("mixins")){
   var config=json(R.resolve(mixin.getAsString()));for(String group:List.of("client","mixins","server"))if(config.has(group))for(var cls:config.getAsJsonArray(group)){
    String file=config.get("package").getAsString().replace('.','/')+"/"+cls.getAsString().replace('.','/')+".java";
    assertTrue(Files.exists(Path.of("src/main/java").resolve(file))||Files.exists(Path.of("src/client/java").resolve(file)),file);
   }
  }
 }
 @Test void threeSilhouettesFitTheirHitboxesAndBoardingRejectsWalls(){
  var shapes=new HashSet<>();var colors=new HashSet<>();
  for(var tier:ShipTier.values()){var d=ShipDesign.of(tier);shapes.add(d.parts());colors.add(d.theme());for(var p:d.parts()){assertTrue(p.y()>=0&&p.y()+p.h()<=d.height()*16);assertTrue(Math.max(Math.abs(p.x()),Math.abs(p.x()+p.w()))<=d.width()*8);assertTrue(Math.max(Math.abs(p.z()),Math.abs(p.z()+p.d()))<=d.width()*8);}}
  assertEquals(3,shapes.size());assertEquals(3,colors.size());assertFalse(ShipDesign.boarding(false,true,1));assertFalse(ShipDesign.boarding(true,false,1));assertFalse(ShipDesign.boarding(true,true,37));assertTrue(ShipDesign.boarding(true,true,36));
 }
 @Test void equippedOxygenTelemetryPreservesSeconds(){var s=OxygenStatusBridge.measure(true,301,600);assertEquals(301,s.seconds());assertEquals(50,s.percent());assertTrue(s.equipped());assertEquals(0,OxygenStatusBridge.measure(false,0,0).percent());}
}
