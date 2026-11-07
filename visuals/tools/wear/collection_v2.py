"""Arcanic volume collection. Native cuboids, original painters and export pipeline."""
import math
from PIL import ImageDraw
from wearlib import *
from kit import Part, chain, art
import packs as p
import hats as h
import wings as w


def label(s, base, ink, face='back'):
    return over(fill(base, noise=.04), p.M_text(s, ink, 2, where=face), L_stitch(ink, inset=1, dash=1))


def backpack(n, brand, version):
    palettes = {
        'Louis Vuitton': [('#553620', '#c99351', '#e4b95e'), ('#25262b', '#777d87', '#ccd0d6')],
        'Gucci': [('#c9b48d', '#573723', '#b5273c'), ('#183e31', '#18241c', '#dcad56')],
        'Adidas': [('#134493', '#eee9dd', '#ffffff'), ('#ece7dc', '#283e31', '#318458')],
        'Off White': [('#e5e2d8', '#16171c', '#ebd023'), ('#24262b', '#8d9b9c', '#f38332')],
        'Chrome Hearts': [('#19171e', '#777582', '#d8dce6'), ('#eee7db', '#37323b', '#b5bdcc')],
        'Supreme': [('#c5243e', '#242328', '#fff9ef'), ('#333c39', '#171c1b', '#f53c47')],
        'Arcanic': [('#1b1f31', '#443566', '#b69aff'), ('#d4e3e8', '#285568', '#55e1e0')],
    }
    base, trim, accent = palettes[brand][version]
    suffix = {
        'Louis Vuitton': ['Monogram Expedition', 'Eclipse Trunk'], 'Gucci': ['Ophidia Journey', 'Emerald Bamboo'],
        'Adidas': ['Blue Campus', 'Sage Rolltop'], 'Off White': ['Diagonal Cargo', 'Arrow Utility'],
        'Chrome Hearts': ['Gothic Cemetery', 'Ivory Dagger'], 'Supreme': ['Crimson Duffle', 'Field Cargo'],
        'Arcanic': ['Obsidian Rune', 'Glacier Core'],
    }[brand][version]
    m = Model(n, brand + ' ' + suffix, 'bodywear', D=5)
    body = over(fill(base, noise=.065), L_stitch(trim, inset=1))
    if brand == 'Louis Vuitton':
        body = over(p.lv_canvas(base, accent), L_frame(trim, 1))
    elif brand == 'Gucci':
        body = over(p.gg_canvas(base, trim), L_hstripes(['#1b633b', '#b5263a', '#1b633b'], [3, 3, 3], start=12, vertical=True))
    elif brand == 'Off White':
        body = over(body, L_diag(trim, 13, 5), p.M_text_box('OFF WHITE', base, trim, y=4, pad=1))
    elif brand == 'Chrome Hearts':
        body = over(body, p.M_sprite(p.CROSS_BIG, {'#': accent}, y=8), p.M_studs(accent, 7))
    elif brand == 'Adidas':
        body = over(body, p.M_text('ADIDAS', accent, 7), L_hstripes([accent, None, accent, None, accent], [2, 2, 2, 2, 2], start=17))
    elif brand == 'Supreme':
        body = over(body, p.M_text_box('SUPREME', '#d4243b', '#ffffff', y=6, pad=1))
    else:
        body = over(body, p.M_text('ARCANIC', accent, 5), p.M_sprite(['...#...', '..#.#..', '.#...#.', '#######', '#.....#'], {'#': accent}, y=15))
    pocket = over(fill(trim, noise=.06), L_stitch(accent, inset=1), p.M_text(brand.upper() if brand in ['Gucci', 'Adidas'] else 'ARC' if brand == 'Arcanic' else 'LV' if brand == 'Louis Vuitton' else 'CH' if brand == 'Chrome Hearts' else 'OW' if brand == 'Off White' else 'SUP', accent, 7))
    width, height, depth = ((8.4, 9.8, 4.4) if version == 0 else (7.6, 10.8, 4.8))
    # Different silhouette per brand and version, beyond recolouring the material.
    if brand == 'Louis Vuitton' and version: width, height, depth = 8.6, 9, 4.6
    if brand == 'Supreme' and version == 0: width, height = 9.2, 8.2
    if brand == 'Chrome Hearts' and version: width, height = 7.2, 11
    b = p.pack(m, {'body': body, 'pocket': pocket, 'side': fill(trim), 'strap': p.strap(trim, accent),
                  'trim': fill(trim), 'buckle': p.metal(accent), 'pull': p.metal(accent)},
               width=width, height=height, depth=depth, pocket=(version == 0), sides=(brand != 'Louis Vuitton' or not version))
    z = 2.3 + depth
    if version == 0:
        for x in (-2.8, 2.1):
            b.add(box((x, 19, z+.05), (.65, 4.1, .4), p.strap(trim, accent)))
            b.add(box((x-.12, 19.2, z+.4), (.9, .7, .3), p.metal(accent)))
        b.add(box((-3.2, 13.2, z+.1), (6.4, .4, .5), fill(accent)))
    else:
        # Two real pouch volumes, a folded roll top or rigid trunk corners.
        for x in (-3.3, .2):
            b.add(box((x, 14, z), (3.1, 3.6, 1.1), pocket))
            b.add(box((x+1.2, 16.9, z+1.1), (.65, .7, .25), p.metal(accent)))
        if brand == 'Louis Vuitton':
            for x in (-4.45, 3.65):
                for y in (13, 20.4): b.add(box((x, y, 2.2), (.8, 1.6, 4.9), p.metal(accent)))
        else:
            b.add(box((-width/2-.2, 23.8, 2.3), (width+.4, 1.35, 4.4), fill(trim)))
            b.add(box((-.45, 20.5, z+.2), (.9, 4.5, .45), p.strap(accent, trim)))
    if brand == 'Off White':
        tag = m.bone('zip_tag', pivot=(3, 18, z+1.4), parent='pack')
        tag.add(box((2.7, 15, z+1.2), (.5, 3, .35), fill('#e34036')))
        m.anim('zip_tag', 'rotation', lambda t: (0, 0, 7*math.sin(t*math.tau)), 3)
    if brand == 'Chrome Hearts':
        for x in (-3, 2.5):
            b.add(box((x, 20, z+.2), (.5, 2.2, .35), p.metal(accent)))
            b.add(box((x-.5, 21, z+.2), (1.5, .5, .35), p.metal(accent)))
    if brand == 'Arcanic':
        b.add(box((-.9, 19, z+.2), (1.8, 1.8, .8), ramp([accent, trim]), rot=(0, 0, 45), pivot=(0, 19.9, z+.6)))
        for x in (-3.2, 2.7): b.add(box((x, 14.4, z+1.25), (.45, 2.8, .3), fill(accent)))
    return m


def cap(n, name, base, accent, style):
    m = Model(n, name, 'hat', D=3, view='front')
    b = m.bone('cap')
    fabric = over(fill(base, noise=.06), L_stitch(accent, inset=1, dash=1))
    b.add(box((-4.65, 30.5, -4.65), (9.3, 2.5, 9.3), fabric))
    b.add(box((-4.1, 33, -4.1), (8.2, .7, 8.2), fill(base)))
    b.add(box((-3.8, 30.45, -7.4), (7.6, .55, 3.5), fill(accent), rot=(-7,0,0), pivot=(0,30.5,-4)))
    b.add(box((-.5,33.7,-.5),(1,.4,1),fill(accent)))
    mark = ['...#...', '..#.#..', '.#####.', '#.....#'] if style != 'heart' else ['.##.##.','#######','.#####.','..###..','...#...']
    b.add(box((-1.7,31,-4.85),(3.4,1.8,.25),over(fill(base),art(mark,{'#':accent},y=0))))
    if style == 'trucker':
        b.add(box((-4.7,31,1),(9.4,1.7,3.7), over(fill('#e8dfce'),L_diag(base,4,1))))
    elif style == 'backwards':
        b.rot = (0,180,0)
    elif style == 'horn':
        for s in (-1,1): b.add(box((s*3-.5,33.3,-2),(1,2.3,1),fill(accent),rot=(0,0,-s*22),pivot=(s*3,33.3,-1.5)))
    elif style == 'chain':
        for x in range(-3,4): b.add(box((x,30.5,-5),(.6,.6,.45),p.metal()))
    return m


def beanie(n, name, base, accent, style):
    m=Model(n,name,'hat',D=3,view='front'); b=m.bone('beanie')
    knit=over(fill(base,noise=.04),L_diag(accent,5,1))
    b.add(box((-4.7,29.8,-4.7),(9.4,3.5,9.4),knit))
    b.add(box((-4,33.3,-4),(8,1,8),knit))
    b.add(box((-4.9,29.5,-4.9),(9.8,1.4,9.8),fill(accent)))
    if style=='pom': b.add(box((-1.4,34.3,-1.4),(2.8,2.4,2.8),fill(accent)))
    if style=='slouch': b.add(box((-3,34.1,-1),(6,1.5,5),knit,rot=(15,0,0),pivot=(0,34,0)))
    if style=='cat':
        for s in (-1,1): b.add(box((s*3.2-.8,33.7,-1),(1.6,2,2),fill(accent),rot=(0,0,-s*20),pivot=(s*3.2,34,0)))
    b.add(box((-1.5,29.6,-5.08),(3,1.2,.2),over(fill(base),art(['#.#','###','#.#'],{'#':accent},y=0))))
    return m


def ears(n,name,base,inner,kind):
    m=Model(n,name,'hat',D=3,view='front'); b=m.bone('ears')
    b.add(box((-4.8,31.7,-1),(9.6,.6,2),fill(base)))
    for s in (-1,1):
        x=s*3
        if kind=='bunny':
            b.add(box((x-1,32,-.8),(2,6.8,1.6),fill(base),rot=(0,0,-s*12),pivot=(x,32,0)))
            b.add(box((x-.45,33,-.9),(.9,4.7,.25),fill(inner),rot=(0,0,-s*12),pivot=(x,32,0)))
        elif kind=='bear':
            b.add(box((x-1.5,32,-1),(3,2.8,2),fill(base)))
            b.add(box((x-.75,32.5,-1.2),(1.5,1.5,.3),fill(inner)))
        else:
            for j in range(3):
                wd=3-j*.75
                b.add(box((x-wd/2,32+j*1.1,-1),(wd,1.2,2),fill(base)))
                if j<2: b.add(box((x-wd/2+.5,32+j*1.1,-1.18),(wd-1,1,.2),fill(inner)))
    if kind=='cyber':
        for s in (-1,1): b.add(box((s*4.8-.65,27,-1.5),(1.3,4,3),fill('#262b3c')))
    return m


def cute(n,name,base,accent,animal):
    m=Model(n,name,'hat',D=3,view='front')
    def face(img,d,width,band):
        for x in (width//3,width*2//3): h.eye(img,x,2,s=2)
        d.rectangle((width//2-1,5,width//2+1,6),fill=C(accent))
    b=h.hood(m,base,top=33.8,draw=face)
    for s in (-1,1):
        x=s*3.2
        if animal=='frog':
            b.add(box((x-1.4,33,-2.6),(2.8,2.5,2.5),fill(base)))
            b.add(box((x-.65,33.6,-2.8),(1.3,1.3,.25),fill('#f2f3d8')))
            b.add(box((x-.3,33.9,-3),(.6,.6,.2),fill('#20232a')))
        elif animal=='bunny':
            b.add(box((x-1,33,-1),(2,6,2),fill(base),rot=(0,0,-s*10),pivot=(x,33,0)))
            b.add(box((x-.45,34,-1.2),(.9,3.8,.25),fill(accent)))
        elif animal in ('cat','fox'):
            for j in range(3): b.add(box((x-1.5+j*.4,33+j,-1.8),(3-j*.8,1.2,2.4),fill(accent if j==1 else base)))
        elif animal=='axolotl':
            for j in range(3): b.add(box((s*5-.5,28+j*1.5,-1),(2.5, .7, .7),fill(accent),rot=(0,0,s*(j-1)*25),pivot=(s*4.8,29,0)))
        else:
            b.add(box((x-1.4,33,-1.6),(2.8,2.6,2.8),fill(base)))
            b.add(box((x-.7,33.5,-1.8),(1.4,1.3,.25),fill(accent)))
    if animal=='bear': b.add(box((-1.3,31.3,-5.1),(2.6,1,.6),fill(accent)))
    if animal=='frog':
        for s in (-1,1): b.add(box((s*3-.6,30.5,-4.96),(1.2,.5,.2),fill('#f39fbb')))
    return m


def mask(n,name,kind):
    fn={'jason':h.hockey,'ghost':h.scream,'freddy':h.krueger,'oni':h.oni,'kitsune':h.kitsune_mask,'plague':h.plague}[kind]
    m=fn(n); m.name=name
    b=m.bones[0]
    if kind=='jason':
        # Riveted cheek armour and a scored forehead, preserving real eye cutouts.
        for x in (-2.9,1.1):
            b.add(box((x,28.2,-5.6),(1.8,1.25,.2),fill('#1c1b1b',noise=0,edge=.1)))
        b.add(box((-.45,26.7,-5.65),(.9,1,.35),fill('#d1cbbd')))
        for x in (-2,-.8,.8,2):
            b.add(box((x,25.1,-5.6),(.3,.3,.15),fill('#34302c',noise=0)))
        for s in (-1,1):
            b.add(box((s*3.3-.4,25,-5.35),(.8,4.7,.4),p.metal('#a9a797')))
            for y in (25.5,29): b.add(box((s*3.3-.15,y,-5.8),(.3,.3,.3),p.metal()))
    elif kind=='freddy':
        # Burnt face under the recognisable fedora; mask is volumetric too.
        scar=over(fill('#ab6851',noise=.16),L_diag('#6a352f',5,1),art(['..kk.....kk..','..kk.....kk..','.............','.....rrr.....','..kkkkkkkkk..'],{'k':'#211518','r':'#e5a77f'},y=5))
        b.add(box((-3.9,24.2,-4.9),(7.8,7.2,.8),scar))
        b.add(box((-1,26.6,-5.45),(2,1.1,.6),fill('#cb8970')))
    elif kind=='ghost':
        b.add(box((-1.4,23.5,-5.65),(2.8,2,.8),fill('#e5e3dc')))
    elif kind=='oni':
        for s in (-1,1): b.add(box((s*2.8-.5,24,-6),(1,2,.65),fill('#f4dfbd'),rot=(0,0,-s*17),pivot=(s*2.8,25,-5.7)))
    elif kind=='kitsune':
        for s in (-1,1):
            tail=m.bone('ribbon'+str(s),pivot=(s*4,27,0),parent=b.name)
            tail.add(box((s*4-.3,22,0),(.6,5,.3),fill('#bd3449')))
            m.anim(tail.name,'rotation',lambda t,s=s:(8*math.sin(t*math.tau+s),0,0),3)
    else: b.add(box((-2,30.9,-5),(4,.65,.6),p.metal('#b6a17a')))
    return m


def cyber(n,name,base,glow,kind):
    m=Model(n,name,'hat',D=3,view='front'); b=m.bone('vr')
    shell=fill(base); light=ramp([glow,'#557bab','#252e55'],edge=None)
    b.add(box((-4.8,26.7,-6.4),(9.6,3.6,2),shell))
    b.add(box((-4.2,27.2,-6.65),(8.4,2.4,.35),over(light,L_diag('#e4faff',12,1))))
    for s in (-1,1):
        b.add(box((s*5.2-.8,26.2,-1.6),(1.6,4.6,3.5),shell))
        b.add(box((s*6-.25,26.8,-1.1),(.5,3.2,2.5),fill(glow)))
        b.add(box((s*4.8-.3,29.5,-4.2),(.6,1,5.6),shell))
    b.add(box((-5,32,-.5),(10,.8,1.4),shell))
    if kind=='antenna':
        b.add(box((5.2,30.5,0),(.45,5,.45),p.metal()))
        b.add(box((4.9,35.4,-.3),(1,.8,1),fill(glow)))
    elif kind=='cat':
        for s in (-1,1): b.add(box((s*3.6-.8,32.8,-.5),(1.6,2.5,1.5),light,rot=(0,0,-s*20),pivot=(s*3.6,33,0)))
    elif kind=='mic':
        b.add(box((5,25.8,-5),(.5,.5,4.5),shell))
        b.add(box((3.4,25.6,-5.5),(2,.8,1),fill(glow)))
    else:
        b.add(box((-1,30.3,-5.5),(2,1.2,1),light))
    return m


def back(n,name): return Model(n,name,'wings',D=3,view='wings')


def tentacles(n,mechanical=False):
    m=back(n,'Mechano Octopus' if mechanical else 'Pudge Feast of Abscession Tendrils')
    b=m.bone('core',pivot=(0,19,3)); dark=fill('#27333b' if mechanical else '#48222b')
    accent=fill('#5be7e8' if mechanical else '#e7a158')
    b.add(box((-2.8,15,2.3),(5.6,7.5,2),dark))
    b.add(box((-1.7,17.4,4.4),(3.4,3.4,.8),accent,rot=(0,0,45),pivot=(0,19,4.8)))
    skin=over(ramp(['#a8b4bb','#53646e','#253039'] if mechanical else ['#b75d66','#783441','#391d29']),L_diag('#263a43' if mechanical else '#542632',6,1))
    for i in range(6):
        a=math.radians(-135+i*54)
        start=(math.cos(a)*2.4,19+math.sin(a)*2.8,4)
        step=(math.cos(a)*1.8,math.sin(a)*1.8,.38)
        def tip(part,end):
            x,y,z=end
            if mechanical:
                part.add((x-.8,y-.5,z-.8),(1.6,1.3,1.6),accent)
                for s in (-1,1): part.add((x+s*.8-.25,y,z-.3),(.5,2,.6),dark,rot=(0,0,-s*25),pivot=end)
            else:
                for k in range(5):
                    part.add((x+k*.5-.3,y+k*.45,z-.3),(.6,1.8-k*.25,.6),p.metal('#e5c29a'),rot=(0,0,-35-k*19),pivot=(x+k*.5,y+k*.45,z))
        names=chain(m,f'tendril{i}_',None,'core',start,step,9,2.1,.6,skin,tip=tip,phase=i*.7,amp=(4,5),speed=3.6,curl=9 if i%2 else -9)
        # Segmented collars or fleshy nodules follow their own animated bone.
        for k,bn in enumerate(names):
            bone=next(t for t in m.bones if t.name==bn)
            x,y,z=bone.pivot; size=2.2-k*.16
            bone.add(box((x-size/2,y,z-size/2),(size,.35,size),accent))
    return m


def katana(n):
    m=back(n,'Deadpool Double Katanas'); b=m.bone('harness')
    black=fill('#1c1e25'); red=fill('#b62d40'); metal=p.metal('#b5bfca')
    b.add(box((-3.1,14.8,2.2),(6.2,8,1.3),black))
    for s in (-1,1):
        pv=(s*1.2,19,4)
        rot=(0,0,-s*30)
        b.add(box((s*1.2-.65,8,3.4),(1.3,18,1.4),black,rot=rot,pivot=pv))
        b.add(box((s*1.2-.72,9,4.8),(1.44,15,.25),red,rot=rot,pivot=pv))
        b.add(box((s*1.2-1.5,25.5,3.1),(3,.5,2),metal,rot=rot,pivot=pv))
        b.add(box((s*1.2-.5,26,3.5),(1,5.4,1),over(red,L_diag('#25232a',5,2)),rot=rot,pivot=pv))
        b.add(box((s*1.2-.65,31.4,3.35),(1.3,.6,1.3),metal,rot=rot,pivot=pv))
    emblem=over(fill('#c72b3d'),art(['.kk...kk.','..kw.wk..','..kw.wk..','.kk...kk.'],{'k':'#17191e','w':'#ffffff'},faces_=('back',),y=2))
    b.add(box((-1.8,17.7,5),(3.6,3.6,.5),emblem))
    return m


def protein(n):
    m=back(n,'Arcanic Whey Protein XL'); m.D=4; b=m.bone('tub')
    black=fill('#22242c'); cream=fill('#e2ded5'); red=fill('#d23750')
    b.add(box((-3.5,12.8,2.8),(7,10,5.8),black))
    b.add(box((-3.5,14,2.5),(7,7.2,6.4),over(cream,p.M_text('WHEY', '#24212c',3),p.M_text('PROTEIN','#b62943',12),p.M_text('ARCANIC','#24212c',23))))
    for s in (-1,1):
        b.add(box((s*3.65-.35,14,3.1),(.7,7.2,5.2),cream))
        b.add(box((s*3.65-.35,21.8,3.1),(.7,1.4,5.2),black))
    b.add(box((-3.5,22.8,2.5),(7,1.3,6.4),over(red,L_diag('#8f263b',4,1))))
    b.add(box((-3.8,22.8,3.1),(7.6,1.3,5.2),red))
    b.add(box((-3,24.1,3),(6,.35,5.4),black))
    for s in (-1,1): b.add(box((s*3.65-.2,13.2,8.45),(.4,10,.4),p.strap('#383b46','#9fa2ac')))
    b.add(box((4.2,15.5,4),(1.8,5,1.8),red))
    b.add(box((4.35,20.5,4.15),(1.5,.8,1.5),black))
    return m


def new_wings(n,kind):
    names=['Lunar Moth Wings','Sakura Seraph','Glacier Blades','Solar Phoenix','Obsidian Gargoyle','Circuit Dragon']
    m=back(n,names[kind])
    palettes=[('#e6dac0','#9c83ba','#5a426d'),('#ffe5ee','#e7a6c1','#925c8d'),('#bdf9ff','#5ebad1','#385d91'),('#fff0a4','#f3a154','#ad4656'),('#666a80','#353349','#b56cda'),('#8af2d5','#349ba1','#283650')]
    a,b,c=palettes[kind]
    if kind in (0,4,5):
        from kit import voxels
        def membrane(sd):
            if kind==0:
                poly=[(1,22),(5,29),(15,34),(23,32),(25,27),(23,23),(15,19),(7,19)]
                lower=[(2,21),(13,23),(19,19),(18,12),(14,8),(9,11),(5,16)]
                voxels(sd,poly,4,.8,[a,a,b,b,c],cell=.8)
                voxels(sd,lower,4.1,.8,[b,a,b,c],cell=.8)
                for x,y in ((17,27),(12,16)):
                    sd.add((x-2.4,y-2.4,5),(4.8,4.8,.4),fill(c))
                    sd.add((x-1.5,y-1.5,5.5),(3,3,.35),fill(a))
                    sd.add((x-.65,y-.65,5.9),(1.3,1.3,.3),fill(b))
                for t in range(3): sd.add((14+t*.4,6-t*1.7,4),(1.1,3.4,.7),fill(b))
            elif kind==4:
                poly=[(1,22),(6,31),(15,34),(24,30),(21,20),(18,23),(14,11),(11,19),(6,12),(2,16)]
                voxels(sd,poly,4,.9,[a,b,b,c],cell=.8)
                for x,y in ((24,30),(14,11),(6,12)):
                    sd.seg((6,29),(x,y),.7,1.3,fill(c),z=4.5)
                sd.seg((1,22),(6,31),1.6,1.6,fill(a),z=4.4)
                sd.seg((6,31),(15,34),1.1,1.2,fill(a),z=4.4)
            else:
                poly=[(1,21),(7,30),(20,32),(24,28),(19,23),(17,14),(13,19),(9,12),(5,17)]
                voxels(sd,poly,4,1.1,[c,b,b,c],cell=1,colour_at=lambda x,y:a if int(x+y)%9==0 else None)
                for j in range(4):
                    sd.seg((5,26),(12+j*3,13+j*4),.75,1.3,fill(a),z=5)
                sd.add((5,26,4.4),(3,3,2),p.metal('#929ea7'))
        w.both(m,membrane,sweep=20,flap=8,speed=3.6)
    elif kind==2:
        def blades(sd):
            sd.seg((1,22),(8,29),1.5,1.5,fill(c),z=4)
            for j in range(6):
                x,y=5+j*2,25+j*.5
                sd.add((x,y-12+j*.7,4),(1.5,13-j*.5,.8),ramp([a,b,c]),rz=18+j*6,pivot=(x,y,4))
                sd.add((x+.2,y-11+j*.7,4.8),(.35,10-j*.5,.3),fill(a),rz=18+j*6,pivot=(x,y,4))
        w.both(m,blades,sweep=20,flap=7,speed=3.6)
    else:
        w.feathered(m,fill(c),ramp([a,b]),ramp([a,b,c]),ramp([b,c]),prim_n=11 if kind==1 else 7,mid_n=8,cover_n=8,long_=18 if kind==1 else 13,sweep=22,flap=10,speed=3.6)
    return m


def unusual(n,kind):
    names=['Neon Skate Deck','Arcade Portal','Alchemy Moon Vials','Astral Jellyfish','Clockwork Coffer','Boba Reactor','Mimic Grimoire','Koi Spirit Orbit']
    m=back(n,names[kind]); b=m.bone('core',pivot=(0,19,3))
    dark=fill('#292739'); purple=fill('#9779cf'); gold=p.metal('#e4b871'); cyan=fill('#83e2db')
    if kind==0:
        b.add(box((-2.6,10,3),(5.2,17,1.2),over(fill('#7765b4'),L_diag('#e4acbe',12,3),p.M_text('ARCANIC','#f5e5cc',10))))
        for y in (11.8,24):
            b.add(box((-3,y,2.3),(6,.7,.7),gold))
            for s in (-1,1): b.add(box((s*3.1-.6,y-.4,2),(1.2,1.5,1.5),cyan))
        for y in (9.2,27): b.add(box((-1.8,y,3),(3.6,.8,1.2),dark))
    elif kind==1:
        for j in range(12):
            a=j*math.tau/12; x,y=7*math.cos(a),19+7*math.sin(a)
            b.add(box((x-1,y-1,3),(2,2,1.2),dark,rot=(0,0,j*30),pivot=(x,y,3)))
            b.add(box((x-.4,y-.4,4.25),(.8,.8,.35),cyan if j%2 else purple))
        m.anim('core','rotation',lambda t:(0,0,t*360),6)
    elif kind==2:
        b.add(box((-4,14,2.4),(8,9,1.2),dark))
        for j in range(3):
            x=-3+j*2.4
            b.add(box((x,14.6,3.7),(1.9,5+j,2),fill(['#b7a0ed','#8cd4bc','#edb289'][j])))
            b.add(box((x-.1,19.6+j,3.6),(2.1,.8,2.2),gold))
        b.add(box((-4,14.8,5.8),(8,.7,.4),gold))
    elif kind==3:
        for j,wd in enumerate((9,11,9,5)):
            b.add(box((-wd/2,22+j,3-(wd-5)*.15),(wd,1.2,wd*.55),fill('#cdb8e9' if j%2 else '#a99bdc')))
        for i in range(7):
            x=-4+i*1.3
            chain(m,f'jelly{i}',None,'core',(x,22,5),(0,-1.4,.15),6,.5,.25,cyan,phase=i,amp=(5,5),speed=4,curl=(i-3)*2)
    elif kind==4:
        b.add(box((-4,14,2.3),(8,10,4.7),over(fill('#58412c'),L_stitch('#bd9563'))))
        for x in (-3.7,2.9): b.add(box((x,14,7),(.8,10,.45),gold))
        for j in range(8):
            a=j*math.tau/8;x,y=2.3*math.cos(a),19+2.3*math.sin(a)
            b.add(box((x-.6,y-.6,7.5),(1.2,1.2,.6),gold,rot=(0,0,j*45),pivot=(x,y,7.5)))
        b.add(box((-.3,17.5,8),(.6,3,.3),dark))
    elif kind==5:
        b.add(box((-3.3,13,2.5),(6.6,10,5.7),fill('#eed0b5')))
        b.add(box((-3.7,23,2.1),(7.4,.7,6.5),purple))
        b.add(box((1.4,23.7,5),(1,6,1),fill('#ab90ce'),rot=(0,0,-12),pivot=(1.9,23.7,5.5)))
        for i in range(9): b.add(box((-2.8+(i%3)*2,13.5+(i//3)*1.3,8.25),(.9,.9,.3),dark))
        b.add(box((-1.5,19,8.2),(.55,.7,.3),dark)); b.add(box((1,19,8.2),(.55,.7,.3),dark))
    elif kind==6:
        b.add(box((-4.4,13,2.5),(8.8,11,1.4),dark))
        b.add(box((-4,13.4,3.9),(8,10.2,2),fill('#dfcaa3')))
        b.add(box((-4.4,13,5.9),(8.8,11,.7),purple))
        for s in (-1,1):
            b.add(box((s*2.1-.8,19.3,6.7),(1.6,1.2,.5),gold))
            b.add(box((s*2.1-.25,19.3,7.2),(.5,1.2,.2),dark))
        for x in (-2,-.7,.7,2): b.add(box((x,16,6.7),(.6,1.2,.4),fill('#f2e3cc')))
        for y in (14,22): b.add(box((-4.7,y,2.3),(1.2,.7,4.7),gold))
    else:
        for s in (-1,1):
            fish=m.bone('koi'+str(s),pivot=(0,19,4),parent='core')
            fish.add(box((s*7-1.2,17,4),(2.4,5,1.5),over(fill('#f4dfc7'),L_diag('#d86e55',8,3))))
            fish.add(box((s*7-.6,15,3.8),(1.2,2,2),gold))
            fish.add(box((s*7-.3,21.2,5.6),(.6,.6,.3),dark))
            m.anim(fish.name,'rotation',lambda t,s=s:(0,s*15*math.sin(t*math.tau),s*12*math.cos(t*math.tau)),4)
        b.add(box((-1,18,3),(2,2,2),cyan,rot=(0,0,45),pivot=(0,19,4)))
    return m


def factories():
    packs=[]
    for brand in ['Louis Vuitton','Gucci','Adidas','Off White','Chrome Hearts','Supreme','Arcanic']:
        for v in range(2): packs.append(lambda n,brand=brand,v=v:backpack(n,brand,v))
    hats=[]
    for name,k in [('Voorhees Riveted Mask','jason'),('Krueger Burnt Mask and Fedora','freddy'),('Ghostface Mourning Hood','ghost'),('Crimson Oni Fangs','oni'),('Kitsune Ribbon Mask','kitsune'),('Brass Plague Doctor','plague')]:
        hats.append(lambda n,name=name,k=k:mask(n,name,k))
    for name,base,accent,k in [('Arcanic Violet Snapback','#302b48','#c4a5ff','plain'),('Supreme Red Cap','#c62d43','#f1e9d9','trucker'),('Midnight Reverse Cap','#252b35','#8ca7be','backwards'),('Devil Horn Cap','#2d252d','#df6f80','horn'),('Chrome Chain Cap','#24232b','#d4d3dc','chain'),('Strawberry Heart Cap','#eed4df','#c65a87','heart')]:
        hats.append(lambda n,name=name,base=base,accent=accent,k=k:cap(n,name,base,accent,k))
    for name,base,accent,k in [('Lavender Pom Beanie','#937da8','#d5c6df','pom'),('Sage Slouch Beanie','#668c78','#b7c9ad','slouch'),('Black Cat Knit','#282b39','#b08fca','cat'),('Cream Fisher Beanie','#cabd9f','#f3e8ca','plain'),('Candy Pom Beanie','#c97f9b','#f2c7d9','pom'),('Ocean Slouch Beanie','#456d88','#87bdc5','slouch')]:
        hats.append(lambda n,name=name,base=base,accent=accent,k=k:beanie(n,name,base,accent,k))
    for name,base,accent,k in [('Snow Bunny Ears','#eadfcf','#df9eb4','bunny'),('Goth Bunny Ears','#2c293c','#9e83bd','bunny'),('Caramel Teddy Ears','#9b7158','#e1b78d','bear'),('Pink Cat Ears','#e5afc1','#946885','cat'),('Arctic Fox Ears','#d8dfe3','#718999','fox'),('Cyber Cat Audio Ears','#333a4d','#7ce5de','cyber')]:
        hats.append(lambda n,name=name,base=base,accent=accent,k=k:ears(n,name,base,accent,k))
    for name,base,accent,k in [('Honey Teddy Hood','#a87956','#e2b88e','bear'),('Matcha Frog Hood','#83ad79','#e0d2a0','frog'),('Strawberry Frog Hood','#cda1bd','#fff0d8','frog'),('Marshmallow Bunny Hood','#e7dfd8','#d9a8b6','bunny'),('Sleepy Lavender Cat','#a594b6','#e0b8cc','cat'),('Autumn Fox Hood','#bd8259','#e7ccab','fox'),('Pink Axolotl Hood','#e9b5cb','#ba76a8','axolotl'),('Cocoa Teddy Hood','#705044','#b58c6b','bear')]:
        hats.append(lambda n,name=name,base=base,accent=accent,k=k:cute(n,name,base,accent,k))
    for name,base,glow,k in [('Neon VR Headphones','#272d40','#6cddd9','mic'),('Pink Cyber VR Cat','#352b45','#ef9ed7','cat'),('Arcanic Signal VR','#e0e4df','#8dc7f4','antenna'),('Obsidian VR Scanner','#252a30','#c4a0f0','scanner')]:
        hats.append(lambda n,name=name,base=base,glow=glow,k=k:cyber(n,name,base,glow,k))
    backs=[lambda n:tentacles(n,False),lambda n:tentacles(n,True),katana,protein]
    backs.extend(lambda n,k=k:new_wings(n,k) for k in range(6))
    backs.extend(lambda n,k=k:unusual(n,k) for k in range(8))
    return packs,hats,backs
