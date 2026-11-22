# -*- coding: utf-8 -*-
"""Atmos 实机演示宣传图 —— 纯程序绘制（Pillow），无外部素材依赖。
低分辨率像素场景放大模拟 MC 画面 + 高分辨率 HUD 叠加。"""
import math, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

random.seed(20261123)
W, H = 1600, 900           # 最终分辨率
SW, SH = 400, 225          # 像素场景分辨率（1/4）

REG = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
BLD = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
def F(sz, bold=False):
    return ImageFont.truetype(BLD if bold else REG, sz, index=2)

# ---------- 1. 像素场景 ----------
scene = Image.new("RGB", (SW, SH))
sd = ImageDraw.Draw(scene)

# 天空渐变（雷暴）
def lerp(a, b, t): return tuple(int(a[i] + (b[i]-a[i])*t) for i in range(3))
stops = [(0.0,(24,27,38)),(0.45,(48,52,68)),(0.72,(82,86,98)),(1.0,(110,112,116))]
for y in range(SH):
    t = y/SH
    for i in range(len(stops)-1):
        if stops[i][0] <= t <= stops[i+1][0]:
            p = (t-stops[i][0])/(stops[i+1][0]-stops[i][0])
            c = lerp(stops[i][1], stops[i+1][1], p); break
    sd.line([(0,y),(SW,y)], fill=c)

# 云层
for _ in range(46):
    cx = random.randint(-20, SW+20); cy = random.randint(8, 118)
    rw = random.randint(18, 62); rh = random.randint(6, 18)
    shade = random.randint(0, 3)
    col = [(38,41,54),(46,49,62),(56,59,72),(66,68,80)][shade]
    sd.ellipse([cx-rw, cy-rh, cx+rw, cy+rh], fill=col)

# 闪电（辉光 + 主枝）
def bolt(x0, y0, segs):
    pts=[(x0,y0)]; x,y=x0,y0
    for dy,dx in segs:
        x+=dx; y+=dy; pts.append((x,y)); sd.line([pts[-2],pts[-1]], fill=(120,120,180), width=3)
    for (a,b) in zip(pts, pts[1:]):
        sd.line([a,b], fill=(238,240,255), width=1)
    # 分叉
    if len(pts)>5:
        bx,by=pts[5]; sd.line([(bx,by),(bx+8,by+14),(bx+6,by+24)], fill=(210,214,240), width=1)
bolt(268, 14, [(14,6),(12,-5),(13,7),(11,-4),(12,8),(10,-3),(12,6)])

# 远山（两层）
sd.polygon([(0,150),(40,120),(85,140),(130,108),(180,138),(240,112),(300,142),(360,118),(400,138),(400,SH),(0,SH)], fill=(44,52,60))
sd.polygon([(0,162),(55,140),(110,158),(170,136),(225,160),(290,142),(350,162),(400,150),(400,SH),(0,SH)], fill=(56,64,68))
# 雾带
fog = Image.new("RGBA",(SW,SH),(0,0,0,0)); fd=ImageDraw.Draw(fog)
fd.rectangle([0,150,SW,178], fill=(150,156,164,70))
scene = Image.alpha_composite(scene.convert("RGBA"), fog).convert("RGB"); sd=ImageDraw.Draw(scene)

# 地面（草地台地）
sd.rectangle([0,168,SW,SH], fill=(58,84,52))
sd.rectangle([0,168,SW,172], fill=(92,126,66))
for _ in range(1500):
    x=random.randint(0,SW-1); y=random.randint(173,SH-1)
    c=random.choice([(52,76,46),(64,92,54),(70,98,58),(48,70,44)])
    sd.point((x,y), fill=c)
# 泥土侧面纹理
for _ in range(300):
    x=random.randint(0,SW-1); y=random.randint(200,SH-1)
    sd.point((x,y), fill=random.choice([(86,66,48),(74,56,42),(96,74,54)]))

# 树
def tree(tx, ty, s=1):
    sd.rectangle([tx-2*s, ty-14*s, tx+2*s, ty+4*s], fill=(78,58,42))
    for (dx,dy,dw,dh) in [(-9,-30,7,7),(-2,-36,7,7),(5,-30,7,7),(-5,-24,14,7),(-2,-18,10,6)]:
        sd.rectangle([tx+dx*s, ty+dy*s, tx+(dx+dw)*s, ty+(dy+dh)*s], fill=random.choice([(30,58,34),(36,66,38),(42,72,42)]))
tree(70,176,1); tree(118,180,1); tree(332,174,1)

# 雨丝
rain = Image.new("RGBA",(SW,SH),(0,0,0,0)); rd=ImageDraw.Draw(rain)
for _ in range(420):
    x=random.randint(0,SW); y=random.randint(0,SH)
    rd.line([(x,y),(x-3,y+8)], fill=(190,205,225,random.randint(60,130)), width=1)
scene = Image.alpha_composite(scene.convert("RGBA"), rain).convert("RGB")

# 放大到最终分辨率（像素风）
img = scene.resize((W,H), Image.NEAREST).convert("RGBA")

# ---------- 2. HUD ----------
def shadow_text(d, xy, txt, font, fill=(255,255,255,255), off=2, sc=(0,0,0,200)):
    x,y=xy; d.text((x+off,y+off), txt, font=font, fill=sc); d.text((x,y), txt, font=font, fill=fill)

def panel(base, box, fill=(8,10,16,175), outline=(210,200,160,140), width=2, radius=8):
    lay=Image.new("RGBA", base.size,(0,0,0,0)); d=ImageDraw.Draw(lay)
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)
    return Image.alpha_composite(base, lay)

d = ImageDraw.Draw(img)

# 顶部 logo
shadow_text(d,(28,22),"▮ ATMOS", F(30,True), (255,214,110,255), 2)
shadow_text(d,(238,30),"v0.1.0  ·  星穹旅驿  ·  Minecraft 1.21.11", F(17), (220,220,225,235), 2)

# ===== 顶部 BossBar 红色预警 =====
img = panel(img, (430,54,1170,128), (24,8,10,205), (210,60,60,220), 2)
d = ImageDraw.Draw(img)
d.rounded_rectangle((440,104,1160,120), radius=6, fill=(70,20,20,255))
d.rounded_rectangle((440,104,440+720*0.66,120), radius=6, fill=(214,52,48,255))
d.text((800,62),"⚠  红色预警 · 超级雷暴", font=F(27,True), fill=(255,235,235,255), anchor="mm", stroke_width=2, stroke_fill=(0,0,0,200))
d.text((800,90),"预计 03:20 后抵达  ·  请立即进入室内庇护", font=F(16), fill=(255,200,200,255), anchor="mm")

# ===== 左上 七要素状态栏 =====
img = panel(img, (28,72,338,322), (8,12,18,185), (120,150,170,150), 2)
d = ImageDraw.Draw(img)
shadow_text(d,(44,84),"局地实况 · 主世界 / 平原", F(19,True), (180,220,235,255), 2)
rows = [
    ("温度", "31.2 °C", (255,150,90,255), "▲ 较昨日 +2.4"),
    ("湿度", "84 %",    (120,190,235,255), "体感 闷热"),
    ("风力", "8 级",    (150,225,180,255), "东南风 12 m/s"),
    ("气压", "1004 hPa",(220,190,255,255), "▼ 3h 变压 -6"),
    ("露点", "27.8 °C", (160,210,235,255), "近饱和"),
    ("能见度", "1.2 km",(230,230,210,255), "受降水影响"),
]
yy=118
for k,v,c,note in rows:
    shadow_text(d,(44,yy),k, F(16), (200,205,212,255), 1)
    shadow_text(d,(150,yy-4),v, F(21,True), c, 1)
    d.text((320,yy+2),note, font=F(12), fill=(170,178,188,255), anchor="ra")
    yy+=33
# 温度/风力小条
d.rounded_rectangle((44,310,150,316),radius=3,fill=(50,55,62,255))
d.rounded_rectangle((44,310,44+106*0.78,316),radius=3,fill=(230,120,70,255))

# ===== 右上 7 日预报（书本） =====
img = panel(img, (1160,60,1572,452), (214,200,166,242), (120,96,58,255), 3, 6)
d = ImageDraw.Draw(img)
d.line((1172,110,1560,110), fill=(140,112,70,220), width=2)
d.text((1366,84),"七 日 天 气 预 报", font=F(24,True), fill=(70,48,28,255), anchor="mm")
d.text((1366,104),"星穹旅驿 · 主世界 · 每 MC 日滚动订正", font=F(12), fill=(110,86,58,255), anchor="mm")

def wicon(cx, cy, kind):
    # 云
    d.ellipse((cx-11,cy-6,cx+2,cy+4), fill=(96,104,116,255))
    d.ellipse((cx-2,cy-9,cx+13,cy+4), fill=(112,120,132,255))
    if kind=="storm":
        d.line([(cx+2,cy+4),(cx-3,cy+13),(cx+2,cy+13),(cx-3,cy+22)], fill=(230,180,40,255), width=2)
        for ox in(-8,7): d.line([(cx+ox,cy+6),(cx+ox-2,cy+13)], fill=(70,110,180,255), width=2)
    elif kind=="rain":
        for ox in(-7,0,7): d.line([(cx+ox,cy+5),(cx+ox-2,cy+13)], fill=(70,110,180,255), width=2)
    elif kind=="snow":
        for ox in(-7,0,7): d.ellipse((cx+ox-2,cy+6,cx+ox+2,cy+10), fill=(220,235,245,255))
    elif kind=="cloud":
        pass
    elif kind=="sun":
        d.ellipse((cx-4,cy-16,cx+8,cy-4), fill=(235,190,60,255))
    elif kind=="fog":
        for j in range(3): d.line((cx-10,cy+6+j*4,cx+11,cy+6+j*4), fill=(160,166,172,255), width=2)

fc = [
    ("第1天","雷阵雨","28~33°","风8","红","92%","storm",(196,52,48)),
    ("第2天","暴雨","25~29°","风9","橙","84%","rain",(224,136,40)),
    ("第3天","阴","24~28°","风5","蓝","76%","cloud",(70,110,170)),
    ("第4天","多云","23~30°","风4","—","68%","sun",(110,110,110)),
    ("第5天","小雨","22~27°","风6","黄","57%","rain",(214,178,50)),
    ("第6天","晴","24~32°","风3","—","44%","sun",(110,110,110)),
    ("第7天","雾","20~26°","风2","蓝","31%","fog",(70,110,170)),
]
ry=124
for day,wt,tp,wd,lv,cf,ic,lc in fc:
    d.text((1180,ry),day, font=F(14,True), fill=(80,58,36,255))
    wicon(1250,ry+8,ic)
    d.text((1274,ry),wt, font=F(15,True), fill=(58,44,30,255))
    d.text((1366,ry),tp, font=F(14), fill=(92,66,42,255), anchor="ma")
    d.text((1446,ry),wd, font=F(13), fill=(92,66,42,255), anchor="ma")
    d.rounded_rectangle((1504,ry+2,1520,ry+18),radius=4,fill=lc+(255,))
    d.text((1548,ry),cf, font=F(12), fill=(110,86,58,255), anchor="ra")
    ry+=45

# ===== 左下 雷达 =====
cx0,cy0,rad=150,742,96
img = panel(img, (34,616,286,872), (6,12,14,190), (110,150,150,150), 2)
d = ImageDraw.Draw(img)
d.text((160,632),"◉ 气象雷达 · 气旋追踪", font=F(16,True), fill=(150,220,215,255), anchor="mm")
d.ellipse((cx0-rad,cy0-rad,cx0+rad,cy0+rad), fill=(10,22,22,255), outline=(90,140,130,255), width=2)
for rr in (32,64):
    d.ellipse((cx0-rr,cy0-rr,cx0+rr,cy0+rr), outline=(60,100,96,180), width=1)
d.line((cx0-rad,cy0,cx0+rad,cy0), fill=(60,100,96,180), width=1)
d.line((cx0,cy0-rad,cx0,cy0+rad), fill=(60,100,96,180), width=1)
# 地形回波点
for _ in range(70):
    ang=random.random()*math.tau; rr=random.random()*rad*0.9
    px=cx0+math.cos(ang)*rr; py=cy0+math.sin(ang)*rr
    d.point((px,py), fill=random.choice([(40,110,80,220),(50,130,95,200),(34,90,70,200)]))
# 气旋螺旋
spx,spy=cx0+22,cy0-18
pts=[]
for i in range(120):
    t=i/120*math.tau*2.4; r=4+t/ (math.tau*2.4) * 42
    pts.append((spx+math.cos(t)*r, spy+math.sin(t)*r))
d.line(pts, fill=(224,70,62,235), width=4)
d.ellipse((spx-6,spy-6,spx+6,spy+6), fill=(255,120,90,255))
d.text((160,854),"低压吸引子 996 hPa · 东南 12km", font=F(12), fill=(200,150,145,255), anchor="mm")

# ===== 底部 ActionBar =====
ab="现实 14:32   ☀ 午后   群系: 平原   局地: 雷阵雨 78%   §c室内庇护: 安全"
shadow_text(d,(800,808),ab, F(20), (245,245,235,255), 2)

# ===== 快捷栏 hotbar =====
gx,gy,cs,gap=501,838,62,5
for i in range(9):
    x=gx+i*(cs+gap)
    d.rectangle((x,gy,x+cs,gy+cs), fill=(30,30,34,210), outline=(225,225,225,235), width=3)
    if i==1:
        d.rectangle((x-3,gy-3,x+cs+3,gy+cs+3), outline=(255,235,150,255), width=3)
# 简易物品图标
def item(i, x, y):
    cx=x+31; cy=y+31
    if i==0: # 钻石剑
        d.line((cx-10,cy+12,cx+9,cy-12), fill=(120,220,225,255), width=6)
        d.line((cx-14,cy+8,cx-6,cy+16), fill=(150,110,60,255), width=5)
    elif i==1: # 草方块
        d.rectangle((cx-13,cy-8,cx+13,cy+14), fill=(96,72,50,255))
        d.rectangle((cx-13,cy-13,cx+13,cy-6), fill=(86,132,64,255))
    elif i==2: # 火把
        d.rectangle((cx-2,cy-4,cx+2,cy+15), fill=(150,110,60,255))
        d.ellipse((cx-6,cy-14,cx+6,cy), fill=(255,170,50,255))
    elif i==3: # 木板
        d.rectangle((cx-14,cy-8,cx+14,cy+9), fill=(170,130,80,255))
        d.line((cx-14,cy,cx+14,cy), fill=(130,95,55,255), width=2)
    elif i==4: # 面包
        d.ellipse((cx-12,cy-8,cx+12,cy+12), fill=(205,165,95,255))
    elif i==5: # 水桶
        d.rectangle((cx-10,cy-6,cx+10,cy+12), fill=(150,160,170,255))
        d.rectangle((cx-8,cy-10,cx+8,cy-5), fill=(90,150,210,255))
    elif i==6: # 矿石
        d.rectangle((cx-12,cy-10,cx+12,cy+12), fill=(110,110,116,255))
        for p in[(-6,-4),(4,-2),(0,6)]: d.ellipse((cx+p[0]-3,cy+p[1]-3,cx+p[0]+3,cy+p[1]+3), fill=(90,210,220,255))
    elif i==7: # 弓
        d.arc((cx-12,cy-14,cx+12,cy+14), 60, 300, fill=(180,140,90,255), width=3)
        d.line((cx-8,cy-13,cx-8,cy+13), fill=(230,230,230,255), width=1)
    elif i==8: # 末影珍珠
        d.ellipse((cx-10,cy-10,cx+10,cy+10), fill=(80,60,120,255))
        d.ellipse((cx-6,cy-6,cx+2,cy+2), fill=(140,120,190,255))
for i in range(9): item(i, gx+i*(cs+gap), gy)

# 准星
d.line((796,450,804,450), fill=(255,255,255,235), width=2)
d.line((800,446,800,454), fill=(255,255,255,235), width=2)

# 右下角小水印
d.text((1570,884),"服务端权威 · 单端口 25565 · Fabric 纯客户端", font=F(13), fill=(235,235,235,210), anchor="ra")

out = "/workspace/atmos/dist/atmos-demo.png"
img.convert("RGB").save(out, "PNG")
print("saved:", out, img.size)
