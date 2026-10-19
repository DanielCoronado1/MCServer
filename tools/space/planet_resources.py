#!/usr/bin/env python3
from resources import *
COLORS={'roca_lunar':(64,68,85),'bloque_cristal_energia':(55,225,220),'lodo_acido':(107,145,39),'hongo_bioluminiscente':(125,230,180),'mena_titanio':(125,133,168),'mena_hierro_meteorico':(150,85,66),'mena_cristal_energia':(57,148,150)}
BIOMES=['kepler_plains','kepler_craters','kepler_crystal_forest','kepler_acid_marshes','kepler_abyssal_caverns']
def fortune_loot(ore,drop):
    write(f'data/dolfinspace/loot_table/blocks/{ore}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:alternatives','children':[{'type':'minecraft:item','condition':'minecraft:tool/can_silk_touch','name':f'dolfinspace:{ore}'},{'type':'minecraft:item','name':f'dolfinspace:{drop}','modifier':[{'type':'minecraft:apply_bonus','enchantment':'minecraft:fortune','formula':'minecraft:ore_drops'},{'type':'minecraft:explosion_decay'}]}]}]}]})
def feature(name,data,placement):
    write(f'data/dolfinspace/worldgen/feature/{name}.json',data)
    write(f'data/dolfinspace/worldgen/placed_feature/{name}.json',{'feature':f'dolfinspace:{name}','placement':placement})
def surface_place(count):return [{'type':'minecraft:count','count':count},{'type':'minecraft:in_square'},{'type':'minecraft:heightmap','heightmap':'WORLD_SURFACE_WG'},{'type':'minecraft:biome'}]
def planet():
    for name,color in COLORS.items():block(name,color,name=='hongo_bioluminiscente')
    for i,name in enumerate(['titanio_crudo','lingote_titanio','hierro_meteorico_crudo','lingote_hierro_meteorico','cristal_energia']):item(name,[(160,165,195),(185,190,215),(170,100,80),(195,120,95),(65,225,220)][i])
    for ore,drop,size,count,top in [('mena_titanio','titanio_crudo',9,12,65),('mena_hierro_meteorico','hierro_meteorico_crudo',12,10,75),('mena_cristal_energia','cristal_energia',8,14,40)]:
        fortune_loot(ore,drop)
        feature(ore,{'type':'minecraft:ore','size':size,'discard_chance_on_air_exposure':0.0,'targets':[{'target':{'predicate_type':'minecraft:tag_match','tag':'dolfinspace:terreno_kepler'},'state':f'dolfinspace:{ore}'}]},[{'type':'minecraft:count','count':count},{'type':'minecraft:in_square'},{'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':-55},'max_inclusive':{'absolute':top}}},{'type':'minecraft:biome'}])
    for ore in ['titanio','hierro_meteorico']:
        for method,ticks in [('smelting',200),('blasting',100)]:
            write(f'data/dolfinspace/recipe/lingote_{ore}_{method}.json',{'type':f'minecraft:{method}','ingredient':f'dolfinspace:{ore}_crudo','cookingtime':ticks,'experience':0.7,'result':{'id':f'dolfinspace:lingote_{ore}'}})
    shaped('bloque_cristal_energia',['CC','CC'],{'C':'dolfinspace:cristal_energia'})
    for name,value in [('cristales_energia','cristal_energia'),('lingotes_titanio','lingote_titanio')]:write(f'data/dolfinspace/tags/item/{name}.json',{'replace':False,'values':[f'dolfinspace:{value}']})
    terrain=['regolito','roca_lunar']
    write('data/dolfinspace/tags/block/terreno_kepler.json',{'values':[f'dolfinspace:{v}' for v in terrain]})
    for tag,values in [('mineable/pickaxe',[v for v in COLORS if v not in ['hongo_bioluminiscente','lodo_acido']]),('needs_iron_tool',['mena_titanio','mena_cristal_energia','bloque_cristal_energia']),('needs_stone_tool',['mena_hierro_meteorico']),('mineable/shovel',['lodo_acido'])]:
        path=f'data/minecraft/tags/block/{tag}.json';p=ROOT/path
        prev=json.loads(p.read_text()) if p.exists() else {'replace':False,'values':[]}
        prev['values']=list(dict.fromkeys(prev['values']+[f'dolfinspace:{v}' for v in values]));write(path,prev)
    # Flat columns create small luminous crystal groves without extra code-driven generators.
    feature('crystal_spires',{'type':'minecraft:block_column','direction':'up','allowed_placement':{'type':'minecraft:matching_blocks','blocks':'minecraft:air'},'prioritize_tip':False,'layers':[{'height':{'type':'minecraft:uniform','min_inclusive':2,'max_inclusive':5},'provider':{'id':'dolfinspace:bloque_cristal_energia'}}]},surface_place(7))
    feature('glowing_fungi',{'type':'minecraft:simple_block','to_place':{'id':'dolfinspace:hongo_bioluminiscente'}},surface_place(24))
    # Deeper layer placement reaches actual cave floors as well as the surface.
    feature('cave_fungi',{'type':'minecraft:simple_block','to_place':{'id':'dolfinspace:hongo_bioluminiscente'}},[{'type':'minecraft:count_on_every_layer','count':12},{'type':'minecraft:biome'}])
    palettes=['regolito','roca_lunar','roca_lunar','lodo_acido','roca_lunar']
    sky=['#100e22','#27121b','#072a38','#243014','#080b24'];fog=['#403949','#503444','#135563','#506529','#171938']
    particles=['white_ash','ash','end_rod','spore_blossom_air','warped_spore']
    ores=[['mena_titanio'],['mena_hierro_meteorico'],['mena_cristal_energia'],['mena_titanio'],['mena_cristal_energia']]
    for i,name in enumerate(BIOMES):
        features=[[] for _ in range(11)];features[6]=[f'dolfinspace:{v}' for v in ores[i]]
        if i==2:features[9]=['dolfinspace:crystal_spires','dolfinspace:glowing_fungi']
        if i==4:features[9]=['dolfinspace:cave_fungi']
        write(f'data/dolfinspace/worldgen/biome/{name}.json',{'has_precipitation':False,'temperature':0.2,'downfall':0.0,'attributes':{'minecraft:visual/sky_color':sky[i],'minecraft:visual/fog_color':fog[i],'minecraft:visual/water_fog_color':fog[i],'minecraft:visual/ambient_particles':{'modifier':'append','argument':[{'particle':{'type':f'minecraft:{particles[i]}'},'probability':.015 if i!=3 else .04}]},'minecraft:gameplay/natural_mob_spawns':{'modifier':'overlay','argument':{'spawn_costs':{},'spawns_by_category':{'monster':[]}}}},'effects':{'water_color':'#40605a'},'carvers':[],'features':features})
    climates=[]
    for i,name in enumerate(BIOMES):
        climates.append({'biome':f'dolfinspace:{name}','parameters':{'temperature':[-1,1] if i==4 else ([-1,-.35] if i==0 else [-.35,.1] if i==1 else [.1,.5] if i==2 else [.5,1]),'humidity':[-1,1],'continentalness':[-1,1],'erosion':[-1,1],'weirdness':[-1,1],'depth':[.4,1.5] if i==4 else [-1,.25],'offset':0}})
    write('data/dolfinspace/dimension/kepler.json',{'type':'dolfinspace:kepler','generator':{'type':'minecraft:noise','settings':'dolfinspace:kepler','biome_source':{'type':'minecraft:multi_noise','biomes':climates}}})
    p=ROOT/'data/dolfinspace/worldgen/noise_settings/kepler.json';noise=json.loads(p.read_text())
    router=noise['noise_router'];router['temperature']='minecraft:overworld/temperature';router['vegetation']='minecraft:overworld/vegetation'
    router['depth']={'type':'minecraft:gradient','axis':'y','from_coordinate':-64,'to_coordinate':64,'from_value':1.0,'to_value':0.0}
    base=router['final_density']
    if base.get('type')!='minecraft:min':router['final_density']={'type':'minecraft:min','left':base,'right':{'type':'minecraft:add','left':.32,'right':{'type':'minecraft:noise','noise':'minecraft:cave_layer','xz_scale':1.5,'y_scale':2.0}}}
    write(str(p.relative_to(ROOT)),noise)
    p=ROOT/'data/dolfinspace/worldgen/material_rule/kepler.json';rule=json.loads(p.read_text());bedrock=rule['sequence'][0]
    seq=[bedrock]
    for biome,palette in zip(BIOMES,palettes):
        seq.append({'type':'minecraft:condition','if_true':{'type':'minecraft:biome','biome_is':f'dolfinspace:{biome}'},'then_run':{'type':'minecraft:condition','if_true':'minecraft:on_floor','then_run':{'type':'minecraft:block','result_state':f'dolfinspace:{palette}'}}})
    write(str(p.relative_to(ROOT)),{'type':'minecraft:sequence','sequence':seq})
    lang({**{f'block.dolfinspace.{v}':v.replace('_',' ').capitalize() for v in COLORS},**{f'item.dolfinspace.{v}':v.replace('_',' ').capitalize() for v in ['titanio_crudo','lingote_titanio','hierro_meteorico_crudo','lingote_hierro_meteorico','cristal_energia']},**{f'biome.dolfinspace.{n}':t for n,t in zip(BIOMES,['Llanuras de regolito','Cráteres meteóricos','Bosque de cristal','Pantanos ácidos','Cavernas abisales'])}})
if __name__=='__main__':planet()
