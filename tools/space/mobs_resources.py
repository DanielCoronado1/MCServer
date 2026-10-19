#!/usr/bin/env python3
from resources import *
import zlib,struct
MOBS=['parasito','acechador','gusano','centinela','zombi_espacial','esqueleto_espacial','creeper_espacial']
def entity_texture(name,color,width=64,height=32):
    # Original procedural placeholder art laid over vanilla model UV dimensions.
    raw=b''
    for y in range(height):
        raw+=b'\0'
        for x in range(width):
            bright=(x%16 in (4,5,10,11) and y%16 in (5,6))
            n=((x*31+y*13)%29)-14
            c=(70,245,190) if bright else tuple(max(0,min(255,c+n)) for c in color)
            raw+=bytes((*c,255))
    def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
    p=ROOT/f'assets/dolfinspace/textures/entity/{name}.png';p.parent.mkdir(parents=True,exist_ok=True)
    p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
def entity_loot(name,drop):
    write(f'data/dolfinspace/loot_table/entities/{name}.json',{'type':'minecraft:entity','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':drop}]}]})
def mobs():
    for name,color in zip(MOBS,[(85,130,65),(65,55,100),(135,100,75),(45,125,150),(95,140,120),(110,185,190),(85,120,155)]):
        entity_texture(name,color,64,64 if name=='zombi_espacial' else 32);item(name+'_spawn_egg',color)
    entity_texture('flecha_cristal',(60,200,220))
    for name,color in [('quitina_alienigena',(115,90,145)),('diente_gusano',(205,190,165)),('nucleo_energia',(70,210,235))]:item(name,color)
    for name,drop in zip(MOBS,['dolfinspace:quitina_alienigena','dolfinspace:quitina_alienigena','dolfinspace:diente_gusano','dolfinspace:nucleo_energia','minecraft:rotten_flesh','minecraft:bone','minecraft:gunpowder']):entity_loot(name,drop)
    # Useful recipes using each fauna drop, with standard vanilla equipment behavior.
    write('data/dolfinspace/recipe/escudo_quitina.json',{'type':'minecraft:crafting_shaped','pattern':['QCQ','QQQ',' Q '],'key':{'Q':'dolfinspace:quitina_alienigena','C':'dolfinspace:cristal_energia'},'result':{'id':'minecraft:shield'}})
    write('data/dolfinspace/recipe/pico_diente.json',{'type':'minecraft:crafting_shaped','pattern':['DDD',' S ',' S '],'key':{'D':'dolfinspace:diente_gusano','S':'minecraft:stick'},'result':{'id':'minecraft:iron_pickaxe'}})
    write('data/dolfinspace/recipe/cristales_del_nucleo.json',{'type':'minecraft:crafting_shapeless','ingredients':['dolfinspace:nucleo_energia'],'result':{'id':'dolfinspace:cristal_energia','count':8}})
    write('data/dolfinspace/damage_type/parasito.json',{'message_id':'dolfinspace.parasito','scaling':'never','exhaustion':.1})
    biomes=['kepler_plains','kepler_craters','kepler_crystal_forest','kepler_acid_marshes','kepler_abyssal_caverns']
    for biome in biomes:
        p=ROOT/f'data/dolfinspace/worldgen/biome/{biome}.json';data=json.loads(p.read_text())
        spawns=[]
        for name,weight in zip(MOBS,[100 if biome=='kepler_abyssal_caverns' else 35,20,12,6,18,15,8]):
            spawns.append({'type':'dolfinspace:'+name,'weight':weight,'count':{'type':'minecraft:uniform','min_inclusive':2 if name=='acechador' else 1,'max_inclusive':3 if name=='acechador' else 2}})
        data['attributes']['minecraft:gameplay/natural_mob_spawns']['argument']['spawns_by_category']['monster']=spawns
        write(str(p.relative_to(ROOT)),data)
    lang({**{f'entity.dolfinspace.{name}':name.replace('_',' ').capitalize() for name in MOBS+['proyectil_energia','flecha_cristal','nube_acida']},**{f'item.dolfinspace.{name}_spawn_egg':'Huevo de '+name.replace('_',' ') for name in MOBS},**{f'item.dolfinspace.{name}':text for name,text in [('quitina_alienigena','Quitina alienígena'),('diente_gusano','Diente de gusano'),('nucleo_energia','Núcleo de energía')]},'death.attack.dolfinspace.parasito':'%1$s fue devorado por un parásito','death.attack.dolfinspace.parasito.player':'%1$s fue devorado por un parásito mientras huía de %2$s'})
if __name__=='__main__':mobs()
