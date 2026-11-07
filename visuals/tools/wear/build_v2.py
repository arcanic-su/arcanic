"""Build replacement catalogue, retaining companions. python build_v2.py SOURCE_ASSET_ROOT"""
import json
from pathlib import Path
import sys
import shutil
import hashlib
from PIL import Image, ImageDraw, ImageFont
import wearlib
from old_style import factories

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/arcanicvisuals'
SOURCE = Path(sys.argv[1]) if len(sys.argv)>1 else ASSETS
GEO = ASSETS / 'wear'
TEX = ASSETS / 'textures/wear'
for folder in (GEO,TEX,ROOT/'previews'): folder.mkdir(parents=True,exist_ok=True)
old = json.loads((SOURCE/'wear/index.json').read_text(encoding='utf-8'))
entries = [e for e in old if e['type']=='pet']
for e in entries:
    src=SOURCE/f"wear/cosmetic_{e['n']}.json"
    if src.resolve() != (GEO/src.name).resolve(): shutil.copy2(src, GEO)
    for prefix in ('cosmetic','icon'):
        src=SOURCE/f"textures/wear/{prefix}_{e['n']}.png"
        if src.resolve() != (TEX/src.name).resolve(): shutil.copy2(src, TEX)
manifest=[]
for designs,base,category in zip(factories(),(100,200,300),('backpacks','headwear','back_items')):
    thumbs=[]
    for i,fn in enumerate(designs):
        m=fn(base+i)
        paths=(GEO/f'cosmetic_{m.n}.json',TEX/f'cosmetic_{m.n}.png')
        before=[hashlib.sha256(f.read_bytes()).digest() if f.exists() else None for f in paths]
        q=wearlib.build(m,str(GEO),str(TEX))
        changed=before != [hashlib.sha256(f.read_bytes()).digest() for f in paths]
        if changed or not (TEX/f'icon_{m.n}.png').exists(): wearlib.icon(q,str(TEX/f'icon_{m.n}.png'),m.view)
        e={'n':m.n,'name':m.name,'type':m.type,'pos':m.pos,'cat':m.cat,'y':m.y}
        entries.append(e)
        manifest.append({**e,'bones':len(m.bones),'cubes':sum(len(b.cubes) for b in m.bones),'animated':bool(m.anims)})
        # Larger actual geometry render, same native rasterizer and textures.
        preview=ROOT/'previews'/f'{m.n}.png'
        if changed or not preview.exists(): wearlib.icon(q,str(preview),m.view,size=384,ss=2)
        thumbs.append((preview,m.name))
        print(m.n,m.name,flush=True)
    cols=4; cw,ch=300,340; rows=(len(thumbs)+cols-1)//cols
    sheet=Image.new('RGB',(cols*cw,rows*ch+80),'#20232c')
    d=ImageDraw.Draw(sheet)
    font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',15)
    title=ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf',25)
    d.text((25,24),f'ARCANIC / {category.upper()} / {len(thumbs)}',font=title,fill='#e3dce9')
    for j,(path,name) in enumerate(thumbs):
        x=(j%cols)*cw;y=80+(j//cols)*ch
        d.rounded_rectangle((x+8,y+8,x+cw-8,y+ch-8),radius=12,fill='#2b2f3a')
        im=Image.open(path).resize((280,280),Image.Resampling.LANCZOS)
        sheet.paste(im,(x+10,y+4),im)
        words=name.split(); line=''; lines=[]
        for word in words:
            if d.textlength(line+' '+word,font=font)>266: lines.append(line);line=word
            else:line=(line+' '+word).strip()
        lines.append(line)
        for k,line in enumerate(lines):d.text((x+18,y+286+k*18),line,font=font,fill='#e7e4ed')
    sheet.save(ROOT/'previews'/f'{category}.jpg',quality=94)
entries.sort(key=lambda e:({'wings':0,'bodywear':1,'pet':2,'hat':3}[e['type']],e['n']))
(GEO/'index.json').write_text(json.dumps(entries,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
(ROOT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print('DONE',len(manifest),'new models;',len(entries),'catalogue entries',flush=True)
