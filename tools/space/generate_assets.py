#!/usr/bin/env python3
"""Deterministic placeholder models and recipe unlock advancements for the industrial/ship chain."""
import json
from pathlib import Path
R=Path(__file__).resolve().parents[2]/'mods/space/src/main/resources'
def write(path,data):
    p=R/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def content(items,blocks):
    for name,texture in items.items():
        write(Path(f'assets/dolfinspace/items/{name}.json'),{'model':{'type':'minecraft:model','model':f'dolfinspace:item/{name}'}})
        write(Path(f'assets/dolfinspace/models/item/{name}.json'),{'parent':'minecraft:item/generated','textures':{'layer0':f'minecraft:item/{texture}'}})
    for name,texture in blocks.items():
        write(Path(f'assets/dolfinspace/items/{name}.json'),{'model':{'type':'minecraft:model','model':f'dolfinspace:block/{name}'}})
        write(Path(f'assets/dolfinspace/blockstates/{name}.json'),{'variants':{'':{'model':f'dolfinspace:block/{name}'}}})
        write(Path(f'assets/dolfinspace/models/block/{name}.json'),{'parent':'minecraft:block/cube_all','textures':{'all':f'minecraft:block/{texture}'}})
        write(Path(f'data/dolfinspace/loot_table/blocks/{name}.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'dolfinspace:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for lang in ['en_us','es_mx','es_es','es_ar']:
        p=R/f'assets/dolfinspace/lang/{lang}.json';data=json.loads(p.read_text())
        data.update({f'item.dolfinspace.{x}':x.replace('_',' ').capitalize() for x in items})
        data.update({f'block.dolfinspace.{x}':x.replace('_',' ').capitalize() for x in blocks});write(p.relative_to(R),data)
def recipe(name,ingredients,result,count=1):
    write(Path(f'data/dolfinspace/recipe/{name}.json'),{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':ingredients,'result':{'id':f'dolfinspace:{result}','count':count}})
    unlock(name)
def unlock(name):
    write(Path(f'data/dolfinspace/advancement/recipes/{name}.json'),{'criteria':{'tick':{'trigger':'minecraft:tick'}},'requirements':[['tick']],'rewards':{'recipes':[f'dolfinspace:{name}']}})
if __name__=='__main__':
    content(dict(mezcla_acero='iron_ingot',lingote_acero='netherite_ingot',placa_acero='iron_ingot',placa_cobre='copper_ingot',placa_oro='gold_ingot',placa_titanio='netherite_ingot',circuito_basico='repeater',circuito_avanzado='comparator',bidon_vacio='bucket',bidon_combustible='lava_bucket'),dict(bloque_acero='iron_block',compresor='piston_top',fabricador_circuitos='crafter_top',refineria='blast_furnace_top'))
    recipe('mezcla_acero',['minecraft:iron_ingot','minecraft:coal'],'mezcla_acero')
    write(Path('data/dolfinspace/recipe/acero_alto_horno.json'),{'type':'minecraft:blasting','category':'misc','ingredient':'dolfinspace:mezcla_acero','result':{'id':'dolfinspace:lingote_acero'},'experience':0.5,'cookingtime':100});unlock('acero_alto_horno')
    recipe('bloque_acero',['dolfinspace:lingote_acero']*9,'bloque_acero')
    recipe('lingotes_acero',['dolfinspace:bloque_acero'],'lingote_acero',9)
    recipe('bidon_vacio',['dolfinspace:placa_acero']*3,'bidon_vacio')
    recipe('compresor',['dolfinspace:lingote_acero']*4+['minecraft:piston']*2+['minecraft:furnace'],'compresor')
    recipe('fabricador_circuitos',['dolfinspace:placa_acero']*4+['minecraft:repeater','minecraft:diamond'],'fabricador_circuitos')
    recipe('refineria',['dolfinspace:placa_acero']*4+['minecraft:cauldron','dolfinspace:circuito_basico','minecraft:blast_furnace'],'refineria')
    for name,fallback,optional in [('cristales_energia','minecraft:amethyst_shard','cristal_energia'),('lingotes_titanio','minecraft:iron_ingot','lingote_titanio')]:
        write(Path(f'data/dolfinspace/tags/item/{name}.json'),{'replace':False,'values':[fallback,{'id':f'dolfinspace:{optional}','required':False}]})
