"""Style source: the user's arcanic-wear-old assets, including original UV artwork."""
import json
from pathlib import Path
from PIL import Image, ImageEnhance
from wearlib import *
import collection_v2 as c

OLD = Path(__file__).resolve().parent / 'reference_old'
CAT = {e['n']:e for e in json.loads((OLD/'wear/index.json').read_text())}
FACE={'left':'west','right':'east','bottom':'down','top':'up','front':'north','back':'south'}


def recolour(image, variant, source):
    """Retain original pixels, highlights and logos; remap only the material colour."""
    image=image.copy(); px=image.load()
    for y in range(image.height):
        for x in range(image.width):
            r,g,b,a=px[x,y]
            if not a: continue
            if source==29:
                if r>g*1.5 and r>b*1.3:
                    k=r/220; col=(30,54,79) if variant==0 else (77,90,55)
                    px[x,y]=(*[min(255,int(t*k)) for t in col],a)
            elif source==37:
                # Cream monogram canvas / black eclipse; keep LV flowers intact.
                v=(r+g+b)/3
                if variant==0:
                    if r>g*1.17 and g>b*1.08:
                        k=max(.25,min(1.2,v/100)); px[x,y]=(int(188*k),int(166*k),int(135*k),a)
                else:
                    if r>g*1.1:
                        k=max(.18,min(1,v/130));px[x,y]=(int(88*k),int(95*k),int(109*k),a)
            elif source==36:
                if variant==0:
                    if max(r,g,b)<80: px[x,y]=(int(r*.8+45),int(g*.8+36),int(b*.7+25),a)
                elif g>r*1.2: px[x,y]=(r,int(g*.72),b,a)
            elif source==35:
                if max(r,g,b)<110:
                    k=(r+g+b)/3/80
                    col=(30,65,117) if variant==0 else (57,83,64)
                    px[x,y]=(*[min(255,int(t*k)) for t in col],a)
            elif source==32:
                if variant==0:
                    if max(r,g,b)<90: px[x,y]=(int(r*.8+10),int(g*.8+13),int(b*.8+18),a)
                else:
                    if min(r,g,b)>130: px[x,y]=(int(r*.85),int(g*.8),int(b*.66),a)
            elif source==34 and variant:
                if max(r,g,b)<95:
                    k=max(.55,(r+g+b)/3/65);px[x,y]=(min(255,int(142*k)),min(255,int(125*k)),min(255,int(151*k)),a)
    return image


def import_old(n,source,name,type_,variant=None):
    e=CAT[source]
    doc=json.loads((OLD/f'wear/cosmetic_{source}.json').read_text())
    geo=doc['model']['minecraft:geometry'][0]
    desc=geo['description']; atlas=Image.open(OLD/f'textures/cosmetic_{source}.png').convert('RGBA')
    if variant is not None: atlas=recolour(atlas,variant,source)
    sc=e.get('scale',1); yy=-1.5 if type_=='hat' else -1.2
    off=(16*e.get('x',0),16*(e.get('y',0)-yy),16*e.get('z',0))
    def coord(v): return (-v[0]*sc+off[0],v[1]*sc+off[1],v[2]*sc+off[2])
    m=Model(n,name,type_,D=4,view='front' if type_=='hat' else 'back')
    ax=atlas.width/desc['texture_width']; ay=atlas.height/desc['texture_height']
    for bj in geo['bones']:
        r=bj.get('rotation');b=m.bone(bj['name'],pivot=coord(bj.get('pivot',[0,0,0])),parent=bj.get('parent'),rot=(-r[0],-r[1],r[2]) if r else None)
        for cj in bj.get('cubes',[]):
            o=cj['origin'];s=cj['size'];uvs=cj.get('uv',{})
            if not isinstance(uvs,dict):raise ValueError('Expected per-face UV')
            painted={}
            for side,uv in uvs.items():
                u,v=uv['uv'];du,dv=uv['uv_size']
                if du==0 or dv==0:continue
                crop=atlas.crop((round(min(u,u+du)*ax),round(min(v,v+dv)*ay),round(max(u,u+du)*ax),round(max(v,v+dv)*ay)))
                if du<0:crop=crop.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
                if dv<0:crop=crop.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
                if uv.get('uv_rotation'):crop=crop.rotate(-uv['uv_rotation'],expand=True)
                # For exporter face orientation, reverse horizontal crop on back/left.
                painted[side]=crop
            def painter(face,wd,ht,D,rnd,pics=painted):
                im=pics.get(FACE[face])
                return Image.new('RGBA',(wd,ht)) if im is None else im.resize((wd,ht),Image.Resampling.NEAREST)
            origin=(-(o[0]+s[0])*sc+off[0],o[1]*sc+off[1],o[2]*sc+off[2])
            r=cj.get('rotation')
            b.add(box(origin,tuple(v*sc for v in s),painter,rot=(-r[0],-r[1],r[2]) if r else None,pivot=coord(cj.get('pivot',[0,0,0])),only=tuple(k for k in FACE if FACE[k] in painted)))
    return m


def backpack(n,brand,version):
    source={'Louis Vuitton':37,'Gucci':36,'Adidas':35,'Off White':32,'Chrome Hearts':34,'Supreme':29}.get(brand)
    if source is None:
        m=c.backpack(n,brand,version)
        # Clean, large pixels like the source. Avoid the generator's dense stitched borders.
        for b in m.bones:
            for cube in b.cubes:
                original=cube.paint
                def paint(f,wd,ht,D,rnd,original=original):
                    im=original(f,wd,ht,D,rnd)
                    return im.resize((max(1,wd//2),max(1,ht//2)),Image.Resampling.NEAREST).resize((wd,ht),Image.Resampling.NEAREST)
                cube.paint=paint
        return m
    names={'Louis Vuitton':['Cream Monogram Voyage','Eclipse Double Cargo'],'Gucci':['Sand GG Explorer','Forest GG Rolltop'],
           'Adidas':['Navy Originals Campus','Forest Three Stripe'],'Off White':['Graphite Diagonal','Sand Industrial Cargo'],
           'Chrome Hearts':['Black Cross Harness','Lilac Silver Cemetery'],'Supreme':['Navy Box Expedition','Olive Field Utility']}
    m=import_old(n,source,brand+' '+names[brand][version],'bodywear',version)
    b=m.bones[0]
    # New construction: the first has two prominent cargo buckles, the second a tall
    # folded lid, side utility pockets and suspended tag, all real geometry.
    accent={'Louis Vuitton':'#c89e51','Gucci':'#cba64f','Adidas':'#dddddc','Off White':'#e4c237','Chrome Hearts':'#c4c4ce','Supreme':'#d92b37'}[brand]
    dark=fill('#25252d',noise=0,grad=.04,edge=.1)
    hardware=c.p.metal(accent)
    if version==0:
        for x in (-2.7,2.1):
            b.add(box((x,16.8,7.65),(.6,4.8,.35),dark))
            b.add(box((x-.15,17.5,8),(.9,.65,.25),hardware))
        b.add(box((-1,24.1,3.6),(2,.65,.65),dark))
    else:
        b.add(box((-4.2,24,2),(8.4,1.15,4.8),dark))
        for s in (-1,1):
            b.add(box((s*4.8-.6,14,3.2),(1.2,5,3),dark))
            b.add(box((s*4.8-.7,18.7,3.1),(1.4,.5,3.2),hardware))
        b.add(box((2.3,18.8,7.6),(.6,4.7,.35),hardware))
        b.add(box((2.1,18,7.8),(1,.8,.25),dark))
    return m


def cute(n,name,base,accent,animal):
    source=53 if animal=='frog' else 57
    m=import_old(n,source,name,'hat')
    # Recolour the source hood without changing its pixel forms, face or material shading.
    target=C(base); inner=C(accent)
    for b in m.bones:
        for cube in b.cubes:
            old=cube.paint
            def painter(f,wd,ht,D,rnd,old=old):
                im=old(f,wd,ht,D,rnd);px=im.load()
                for y in range(ht):
                    for x in range(wd):
                        r,g,bl,a=px[x,y]
                        if not a:continue
                        if source==53 and g>r*1.25 and g>bl*1.1:
                            k=g/190;px[x,y]=(*[min(255,int(v*k)) for v in target[:3]],a)
                        elif source==57 and r>g*1.08 and g>bl*1.06 and r>65:
                            k=(r+g+bl)/3/115;px[x,y]=(*[min(255,int(v*k)) for v in target[:3]],a)
                return im
            cube.paint=painter
    b=m.bones[0]
    if animal not in ('frog','bear'):
        # Replace bear ears with species-specific ears/gills, preserving source hood.
        b.cubes=b.cubes[:2]
        for s in (-1,1):
            x=s*4.7
            if animal=='bunny':
                b.add(box((x-1.1,35.4,-1),(2.2,6.5,1.5),fill(base),rot=(0,0,-s*12),pivot=(x,35.5,0)))
                b.add(box((x-.5,36.5,-1.2),(1,4.3,.3),fill(accent)))
            elif animal=='axolotl':
                for j in range(3): b.add(box((s*5.5-.5,28+j*1.5,-1.2),(2.2,.7,.7),fill(accent),rot=(0,0,s*(j-1)*25),pivot=(s*5,30,0)))
            else:
                for j in range(3): b.add(box((x-1.5+j*.4,35.2+j,-1.5),(3-j*.8,1.2,1.5),fill(accent if j==1 else base)))
    return m


_mask=c.mask
def mask(n,name,kind):
    if kind=='ghost':
        m=import_old(n,65,name,'hat')
        b=m.bones[1]
        # New hood seam/fasteners and trailing strips, source face retained exactly.
        for s in (-1,1):
            b.add(box((s*4.1-.2,24,-4.5),(.4,6,.3),fill('#46454a')))
        return m
    return _mask(n,name,kind)


def factories():
    c.backpack=backpack_source
    c.cute=cute
    c.mask=mask
    return c.factories()


# Keep access to the original Arcanic design function without recursion.
_backpack=c.backpack
def backpack_source(n,brand,v):
    if brand=='Arcanic':
        m=_backpack(n,brand,v)
        return m
    return backpack(n,brand,v)
