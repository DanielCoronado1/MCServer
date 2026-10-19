#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow==12.1.0", "soundfile==0.13.1"]
# ///
"""Deterministic, original 64x64 zombie art and synthesized mono Vorbis cues.
Run: uv run tools/generate_zombie_assets.py (Pillow and libsndfile are installed by uv).
No downloaded textures, audio, or Minecraft assets. Preview is documentation only.
"""
import json
import math
import random
import struct
import soundfile as sf
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'mods/zombies/src/main/resources/assets/dolfinzombies'
# skin, clothing, signature accent; order follows ZombieKind.
PALETTES = {
    'minero': ('87918b', '30485c', 'f3c530'),
    'ladron': ('7e8b73', '20232e', 'bf343d'),
    'bombero': ('302e32', '171f25', 'ff731d'),
    'abismo': ('499e9d', 'd2ded0', '42f6ee'),
    'escudero': ('76866b', '6a6960', '75ce50'),
    'hambriento': ('c8bf78', '534535', 'ede1b4'),
    'griton': ('a9a999', '665b65', 'b72f3e'),
    'bruja': ('8c9c73', '643d83', 'c7dc78'),
    'granjero': ('9aab78', '3c6582', 'e5bf64'),
    'congelado': ('b6e2ef', '738b9e', 'e5fbff'),
    'jinete': ('8c9775', '855a3d', 'd4a979'),
    'bilioso': ('91ae42', '59663b', 'e6e86a'),
    'enano': ('999782', '5b535b', 'ffe073'),
    'contagiado': ('80996a', 'b9b8a0', 'ba52d5'),
    'mimo': ('c9b3a0', '334554', '141420'),
    'explorador': ('292f44', '141c2c', 'ff2636'),
}
GLOW = {'minero': 'ffe888', 'bombero': 'ff8527', 'abismo': '50fff3',
        'enano': 'ffe976', 'contagiado': 'c455f0', 'explorador': 'ff2434'}
SOUNDS = {'griton_scream': (1.25, 'Gritón screams', 'El Gritón grita'),
          'bilioso_burst': (.65, 'Bilious zombie bursts', 'El Bilioso revienta'),
          'abismo_grab': (.8, 'Abyss drowned grabs', 'El Ahogado agarra'),
          'ladron_steal': (.45, 'Thief snatches', 'El Ladrón roba')}


def rgb(h):
    return tuple(bytes.fromhex(h)) + (255,)


def make_skin(name, colors, seed):
    rng = random.Random(seed)
    skin, cloth, accent = map(rgb, colors)
    image = Image.new('RGBA', (64, 64))
    d = ImageDraw.Draw(image)
    # Proper humanoid UV islands, including independent modern left limbs.
    islands = [(0, 0, 32, 16, skin), (16, 16, 40, 32, cloth),
               (40, 16, 56, 32, skin), (0, 16, 16, 32, cloth),
               (16, 48, 32, 64, cloth), (32, 48, 48, 64, skin)]
    for x0, y0, x1, y1, base in islands:
        for y in range(y0, y1):
            for x in range(x0, x1):
                noise = rng.choice([-10, -4, 0, 0, 4, 8])
                image.putpixel((x, y), tuple(max(0, min(255, c + noise)) for c in base[:3]) + (255,))
    # Front face: dark brow, recessed eye whites and decayed mouth.
    d.rectangle((9, 10, 14, 10), fill=tuple(max(0, c-50) for c in skin[:3]))
    for x in (9, 13):
        d.rectangle((x, 11, x+1, 12), fill=(28, 32, 28, 255))
        d.point((x, 11), fill=(200, 196, 143, 255))
    d.rectangle((10, 14, 13, 15), fill=(47, 36, 33, 255))
    # Worn cuffs, boots, seam and belt across the wrapped UV regions.
    for x0, y0 in ((0,16),(16,48)):
        d.rectangle((x0, y0+13, x0+15, y0+15), fill=(38,34,32,255))
    d.rectangle((16, 29, 39, 30), fill=(54, 42, 31, 255))
    d.line((24,20,24,28), fill=tuple(max(0,c-25) for c in cloth[:3]), width=1)
    glow = Image.new('RGBA', (64,64))
    g = ImageDraw.Draw(glow)
    if name in GLOW:
        for x in (9,13):
            g.rectangle((x,11,x+1,11), fill=rgb(GLOW[name]))
    if name == 'minero':
        d.rectangle((0,8,31,10),fill=accent)
        d.rectangle((8,0,23,7),fill=accent)
        for x in (21,26): d.rectangle((x,20,x+1,27),fill=(47,61,73,255)); d.point((x,21),fill=accent)
        d.rectangle((22,25,25,28),fill=(21,28,31,255))
    elif name == 'ladron':
        d.rectangle((0,8,31,10),fill=cloth)
        d.rectangle((8,11,15,12),fill=(12,14,22,255))
        d.point((9,11),fill=skin);d.point((14,11),fill=skin)
        d.rectangle((16,20,39,21),fill=accent);d.rectangle((25,22,27,26),fill=accent)
    elif name == 'bombero':
        for _ in range(55):
            x,y=rng.choice([(rng.randrange(16,40),rng.randrange(20,29)),(rng.randrange(0,32),rng.randrange(8,16))])
            g.line((x,y,x+1,y+2),fill=accent)
            d.line((x,y,x+1,y+2),fill=(122,51,26,255))
    elif name == 'abismo':
        for y in (22,25,28):d.rectangle((16,y,39,y+1),fill=(37,64,89,255))
        for _ in range(12):
            x,y=rng.randrange(8,16),rng.randrange(8,16)
            if x not in (9,10,13,14):d.point((x,y),fill=(48,87,53,255))
        for x,y in ((17,24),(29,27),(42,22),(6,30)):
            d.rectangle((x,y,x+1,y+1),fill=(198,185,144,255))
    elif name == 'escudero':
        for y in range(20,29):
            for x in range(16,40):d.point((x,y),fill=(158,137,102,255) if (x+y)%3==0 else cloth)
        d.rectangle((0,8,31,10),fill=(109,108,92,255))
    elif name == 'hambriento':
        for y in (22,24,26):d.line((21,y,26,y),fill=accent);d.point((24,y+1),fill=(75,63,40,255))
        d.rectangle((9,13,14,15),fill=(52,28,24,255));d.line((10,13,13,13),fill=accent)
        d.point((14,15),fill=(141,194,130,255))
    elif name == 'griton':
        d.rectangle((9,12,14,15),fill=(62,20,28,255));d.rectangle((10,13,13,15),fill=accent)
        d.rectangle((23,20,25,24),fill=accent)
        for x in (9,13):d.rectangle((x,10,x+1,11),fill=(242,234,214,255));d.point((x,11),fill=(40,26,25,255))
        for x in (18,23,30,37):d.line((x,27,x+1,29),fill=skin)
    elif name == 'bruja':
        d.rectangle((16,29,39,30),fill=(48,29,53,255))
        for x,c in ((21,'58d9a4'),(25,'ca68c2'),(29,'6cb6ea')):
            d.rectangle((x,26,x+1,28),fill=rgb(c));d.point((x,25),fill=(204,193,140,255))
        d.rectangle((11,13,12,14),fill=(114,125,73,255))
    elif name == 'granjero':
        for y in range(20,25):
            for x in range(16,40):d.point((x,y),fill=(153,57,46,255) if (x//2+y//2)%2 else (206,165,118,255))
        d.rectangle((21,25,26,28),fill=cloth)
        for x in (21,26):d.line((x,20,x,26),fill=cloth);d.point((x,24),fill=accent)
    elif name == 'congelado':
        d.rectangle((16,20,39,22),fill=(205,212,199,255))
        for _ in range(55):
            x,y=rng.randrange(0,32),rng.randrange(8,16)
            d.point((x,y),fill=accent)
        for x in (21,24,27):d.line((x,23,x,28),fill=(179,214,224,255))
    elif name == 'jinete':
        d.rectangle((23,20,24,28),fill=(44,35,27,255))
        for y in (22,25,28):d.point((25,y),fill=accent)
        d.rectangle((20,29,27,30),fill=(59,44,30,255));d.rectangle((23,29,24,30),fill=(224,188,91,255))
    elif name == 'bilioso':
        for _ in range(36):
            x,y=rng.randrange(16,40),rng.randrange(20,29)
            d.rectangle((x,y,x+1,y+1),fill=accent);d.point((x,y),fill=(192,150,44,255))
        d.rectangle((21,26,26,28),fill=skin)
    elif name == 'enano':
        d.rectangle((0,8,31,10),fill=(117,104,72,255))
        d.rectangle((8,13,15,15),fill=(190,190,180,255))
        for x in (9,11,13):d.line((x,13,x,15),fill=(124,126,122,255))
    elif name == 'contagiado':
        for x0,y0,x1,y1,_ in islands:
            for _ in range(3):
                x,y=rng.randrange(x0,x1-2),rng.randrange(y0,y1-3)
                g.line((x,y,x+1,y+1,x,y+3),fill=accent)
                d.line((x,y,x+1,y+1,x,y+3),fill=(105,44,117,255))
        d.rectangle((40,25,55,27),fill=(205,198,165,255));d.rectangle((8,14,10,15),fill=(130,59,60,255))
    elif name == 'mimo':
        d.rectangle((8,8,15,9),fill=(54,40,35,255));d.rectangle((8,10,8,14),fill=(54,40,35,255))
        for x in (9,13):d.rectangle((x,11,x+1,12),fill=accent)
        d.line((10,14,13,14,14,13),fill=accent)
    elif name == 'explorador':
        d.rectangle((0,8,31,10),fill=cloth)
        d.rectangle((9,13,14,15),fill=cloth)
    image.alpha_composite(glow)
    # Accessories use an independent atlas so hats never borrow a face UV island.
    accessory=Image.new('RGBA',(64,64),accent)
    ad=ImageDraw.Draw(accessory)
    for y in range(64):
        for x in range(48):
            if (x+y*3)%13==0:ad.point((x,y),fill=tuple(max(0,c-35) for c in accent[:3]))
    ad.rectangle((0,32,47,63),fill=(96,72,47,255))
    ad.rectangle((32,32,47,47),fill=(149,151,143,255))
    ad.rectangle((0,48,15,63),fill=(52,72,49,255))
    ad.rectangle((16,48,31,63),fill=(181,148,101,255))
    if name=='bruja':ad.rectangle((0,0,47,31),fill=(66,40,84,255))
    if name=='ladron':ad.rectangle((0,0,47,31),fill=(46,39,35,255))
    if name=='escudero':
        ad.rectangle((0,0,47,31),fill=(95,110,83,255))
        # Front shield at tex 0,0: width 8, height 11, depth 2 => front x2..9,y2..12.
        ad.rectangle((3,4,8,8),fill=(151,231,104,255));ad.rectangle((4,9,7,10),fill=(151,231,104,255))
        for x in (4,7):ad.point((x,6),fill=(29,50,26,255))
    if name=='hambriento':ad.rectangle((0,0,47,31),fill=(148,114,74,255))
    ag=Image.new('RGBA',(64,64))
    if name in ('minero','enano'):
        ad.rectangle((48,0,63,15),fill=rgb(GLOW[name]));ImageDraw.Draw(ag).rectangle((48,0,63,15),fill=rgb(GLOW[name]))
    return image,glow,accessory,ag


def audio(name, seconds, seed, output):
    rng=random.Random(seed);rate=22050;phase=0.;samples=[];last=0.
    for i in range(int(seconds*rate)):
        t=i/rate;u=t/seconds;noise=rng.uniform(-1,1)
        env=min(1,u*30)*max(0,1-u)**1.6
        if name=='griton_scream':
            freq=390+130*math.sin(u*math.pi)+24*math.sin(2*math.pi*13*t)
            phase+=2*math.pi*freq/rate
            value=(math.sin(phase)+.4*math.sin(phase*2)+.2*math.sin(phase*3)+.3*noise)*env*.42
        elif name=='bilioso_burst':
            last=.74*last+.26*noise
            value=(last*2+math.sin(2*math.pi*(100*t-45*t*t))*.45)*env
        elif name=='abismo_grab':
            last=.85*last+.15*noise
            value=(last*2+math.sin(2*math.pi*125*t+4*math.sin(2*math.pi*7*t))*.35)*env
        else:
            value=(noise*.3+math.sin(2*math.pi*(900*t-600*t*t))*.35)*env*(.5+.5*math.sin(2*math.pi*17*t)**2)
        samples.append(int(max(-.95,min(.95,value))*32767))
    sf.write(str(output),[v/32768 for v in samples],rate,format='OGG',subtype='VORBIS')
    # libogg chooses a random stream serial. Canonicalize each page and recalculate
    # the Ogg CRC so rebuilding unchanged synthesis produces identical bytes.
    data=bytearray(output.read_bytes());offset=0
    while offset<len(data):
        assert data[offset:offset+4]==b'OggS'
        segments=data[offset+26];size=27+segments+sum(data[offset+27:offset+27+segments])
        data[offset+14:offset+18]=struct.pack('<I',seed)
        data[offset+22:offset+26]=b'\0'*4
        crc=0
        for byte in data[offset:offset+size]:
            crc^=byte<<24
            for _ in range(8):crc=((crc<<1)^0x04c11db7 if crc&0x80000000 else crc<<1)&0xffffffff
        data[offset+22:offset+26]=struct.pack('<I',crc);offset+=size
    output.write_bytes(data)


def main():
    textures=ASSETS/'textures/entity';textures.mkdir(parents=True,exist_ok=True)
    preview=Image.new('RGBA',(8*160,2*330),(25,29,37,255));pd=ImageDraw.Draw(preview)
    for i,(name,palette) in enumerate(PALETTES.items()):
        skin,glow,accessory,ag=make_skin(name,palette,600+i)
        skin.save(textures/f'{name}_zombie.png')
        accessory.save(textures/f'{name}_zombie_accessories.png')
        if name in GLOW:
            glow.save(textures/f'{name}_zombie_eyes.png')
            if name in ('minero','enano'):ag.save(textures/f'{name}_zombie_accessories_emissive.png')
        # 26.3 removed the old template_spawn_egg tint model: ship original colored sprites.
        eggid=f'{name}_zombie_spawn_egg'
        (ASSETS/'models/item').mkdir(parents=True,exist_ok=True)
        (ASSETS/'textures/item').mkdir(parents=True,exist_ok=True)
        egg=Image.new('RGBA',(16,16));ed=ImageDraw.Draw(egg)
        ed.polygon([(6,1),(9,1),(11,3),(13,7),(13,11),(11,14),(4,14),(2,11),(2,7),(4,3)],fill=rgb(palette[1]))
        for x,y in ((6,3),(9,6),(4,8),(10,11),(6,12)):
            ed.rectangle((x,y,x+1,y+1),fill=rgb(palette[2]))
        ed.line((4,5,4,10),fill=tuple(min(255,c+45) for c in rgb(palette[1])[:3]))
        egg.save(ASSETS/f'textures/item/{eggid}.png')
        model={'parent':'minecraft:item/generated','textures':{'layer0':f'dolfinzombies:item/{eggid}'}}
        (ASSETS/f'models/item/{eggid}.json').write_text(json.dumps(model,indent=2)+'\n')
        (ASSETS/f'items/{eggid}.json').write_text(json.dumps({'model':{'type':'minecraft:model','model':f'dolfinzombies:item/{eggid}'}},indent=2)+'\n')
        front=Image.new('RGBA',(16,32));front.paste(skin.crop((8,8,16,16)),(4,0));front.paste(skin.crop((20,20,28,32)),(4,8));front.paste(skin.crop((44,20,48,32)),(0,8));front.paste(skin.crop((36,52,40,64)),(12,8));front.paste(skin.crop((4,20,8,32)),(4,20));front.paste(skin.crop((20,52,24,64)),(8,20))
        x,y=(i%8)*160,(i//8)*330;preview.alpha_composite(front.resize((128,256),Image.Resampling.NEAREST),(x+16,y+28));pd.text((x+8,y+8),name,fill='white');pd.text((x+8,y+294),'UV front / no 3D props',fill='#a5b4c9')
    disguise=Image.new('RGBA',(64,64));dd=ImageDraw.Draw(disguise)
    for x in (9,13):dd.rectangle((x,11,x+1,12),fill=(12,12,19,255))
    dd.line((10,14,13,14,14,13),fill=(24,16,25,255))
    disguise.save(textures/'mimo_zombie_disguise.png')
    (ROOT/'docs/zombies').mkdir(parents=True,exist_ok=True);preview.save(ROOT/'docs/zombies/issue-08-skins.png')
    sounddir=ASSETS/'sounds';sounddir.mkdir(exist_ok=True)
    defs={}
    for i,(name,(duration,_,_)) in enumerate(SOUNDS.items()):
        audio(name,duration,800+i,sounddir/f'{name}.ogg')
        defs[name]={'subtitle':f'subtitles.dolfinzombies.{name}','sounds':[{'name':f'dolfinzombies:{name}','attenuation_distance':32 if name=='griton_scream' else 16}]}
    (ASSETS/'sounds.json').write_text(json.dumps(defs,indent=2)+'\n')
    for locale in ('en_us','es_mx','es_es','es_ar'):
        path=ASSETS/f'lang/{locale}.json';lang=json.loads(path.read_text())
        for name,(_,en,es) in SOUNDS.items():lang[f'subtitles.dolfinzombies.{name}']=en if locale=='en_us' else es
        path.write_text(json.dumps(lang,ensure_ascii=False,indent=2)+'\n')
    print('Generated 16 skins, 16 accessory atlases, 8 glow atlases, 16 egg palettes, 4 mono cues, 4 locales and preview.')

if __name__=='__main__':main()
