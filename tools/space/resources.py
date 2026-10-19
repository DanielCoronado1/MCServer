#!/usr/bin/env python3
"""Deterministic placeholder pixel art and JSON helpers for Space tickets 29–31."""
from pathlib import Path
import json, struct, zlib
ROOT=Path(__file__).resolve().parents[2]/'mods/space/src/main/resources'
def write(path,data):
    p=ROOT/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def png(path,color,tank=False):
    p=ROOT/path;p.parent.mkdir(parents=True,exist_ok=True)
    raw=b''
    for y in range(16):
        raw+=b'\0'
        for x in range(16):
            visible=not tank or (4<=x<=11 and 3<=y<=14) or (6<=x<=9 and 1<=y<=3)
            n=((x*19+y*7)%23)-11
            c=tuple(max(0,min(255,v+n)) for v in color)
            if tank and (x==4 or x==11 or y==14):c=(65,75,85)
            raw+=bytes((*c,255 if visible else 0))
    def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
    p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
def item(name,color,tank=False):
    png(f'assets/dolfinspace/textures/item/{name}.png',color,tank)
    write(f'assets/dolfinspace/models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'dolfinspace:item/{name}'}})
    write(f'assets/dolfinspace/items/{name}.json',{'model':{'type':'minecraft:model','model':f'dolfinspace:item/{name}'}})
def block(name,color,cross=False):
    png(f'assets/dolfinspace/textures/block/{name}.png',color)
    write(f'assets/dolfinspace/blockstates/{name}.json',{'variants':{'':{'model':f'dolfinspace:block/{name}'}}})
    write(f'assets/dolfinspace/models/block/{name}.json',{'parent':'minecraft:block/cross' if cross else 'minecraft:block/cube_all','textures':{'cross' if cross else 'all':f'dolfinspace:block/{name}'}})
    write(f'assets/dolfinspace/models/item/{name}.json',{'parent':f'dolfinspace:block/{name}'})
    write(f'assets/dolfinspace/items/{name}.json',{'model':{'type':'minecraft:model','model':f'dolfinspace:block/{name}'}})
    loot(name,name)
def loot(name,drop,fortune=False):
    entry={'type':'minecraft:item','name':f'dolfinspace:{drop}'}
    if fortune:entry['functions']=[{'function':'minecraft:apply_bonus','enchantment':'minecraft:fortune','formula':'minecraft:ore_drops'}]
    write(f'data/dolfinspace/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[entry],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
def shaped(name,pattern,key,result=None,components=None):
    out={'id':f'dolfinspace:{result or name}','count':1}
    if components:out['components']=components
    write(f'data/dolfinspace/recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':key,'result':out})
def lang(keys):
    for locale in ['en_us','es_mx','es_es','es_ar']:
        p=ROOT/f'assets/dolfinspace/lang/{locale}.json';j=json.loads(p.read_text());j.update(keys);write(str(p.relative_to(ROOT)),j)
def oxygen():
    for tier,color in [('chico',(95,180,200)),('mediano',(70,130,220)),('grande',(170,100,230))]:item(f'tanque_oxigeno_{tier}',color,True)
    png('assets/dolfinspace/textures/gui/sprites/container/slot/tank.png',(100,110,120),True)
    block('compresor_oxigeno',(80,110,120))
    write('data/dolfinspace/damage_type/oxygen.json',{'message_id':'dolfinspace.oxygen','scaling':'never','exhaustion':0.0,'effects':'drowning'})
    write('data/minecraft/tags/damage_type/bypasses_armor.json',{'replace':False,'values':['dolfinspace:oxygen']})
    write('data/dolfinspace/tags/item/plantas_compresor.json',{'replace':False,'values':['minecraft:short_grass','minecraft:tall_grass','minecraft:fern','minecraft:large_fern','minecraft:moss_block','minecraft:vine','minecraft:azalea','minecraft:flowering_azalea','minecraft:dandelion','minecraft:poppy']})
    # Optional external tags retain the machine-chain recipes after merge; vanilla iron is a usable standalone fallback.
    write('data/dolfinspace/tags/item/placas_tanque.json',{'values':['minecraft:iron_ingot',{'id':'dolfinspace:placas_acero','required':False},{'id':'dolfinspace:placa_acero','required':False}]})
    write('data/dolfinspace/tags/item/circuitos_compresor.json',{'values':['minecraft:redstone',{'id':'dolfinspace:circuito_basico','required':False}]})
    shaped('compresor_oxigeno',['PPP','LCL','PPP'],{'P':'#dolfinspace:placas_tanque','L':'#minecraft:leaves','C':'#dolfinspace:circuitos_compresor'})
    for tier,capacity in [('chico',300),('mediano',600),('grande',1200)]:
        shaped(f'tanque_oxigeno_{tier}',['PGP','PGP','PPP'],{'P':'#dolfinspace:placas_tanque','G':{'chico':'minecraft:glass','mediano':'minecraft:copper_ingot','grande':'minecraft:diamond'}[tier]},components={'minecraft:damage':capacity})
    lang({'block.dolfinspace.compresor_oxigeno':'Compresor de oxígeno','item.dolfinspace.tanque_oxigeno_chico':'Tanque de oxígeno chico','item.dolfinspace.tanque_oxigeno_mediano':'Tanque de oxígeno mediano','item.dolfinspace.tanque_oxigeno_grande':'Tanque de oxígeno grande','death.attack.dolfinspace.oxygen':'%1$s se quedó sin oxígeno'})
if __name__=='__main__':oxygen()
