import numpy as np, math, random, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
N=1024
rng=np.random.default_rng(5); random.seed(5)
yy,xx=np.mgrid[0:N,0:N]/N
def noise(sc,seed):
    r=np.random.default_rng(seed); g=r.random((int(sc)+2,int(sc)+2))
    return np.asarray(Image.fromarray((g*255).astype('uint8')).resize((N,N),Image.BICUBIC))/255.0
def fbm(sc,seed,o=5):
    t=0;a=.5
    for i in range(o): t=t+a*noise(sc*2**i,seed+i);a*=.5
    return t/(1-.5**o)
top=np.array([5,8,20.]);bot=np.array([14,34,84.])
bg=top+(bot-top)*yy[...,None]
def blob(cx,cy,r,col,k):
    d=np.sqrt((xx-cx)**2+(yy-cy)**2)/r
    return (np.exp(-d*d*2.2)*k)[...,None]*np.array(col)
bg=bg+blob(.5,.45,.5,(70,110,230),.55)+blob(.12,.88,.4,(120,80,255),.3)
img=bg.copy()
cx,cy=.5,.38
n=70;pts=[]
for i in range(n+1):
    t=i/n*2-1
    px=cx+t*.40*math.cos(math.radians(-62))*-1
    py=cy+t*.42*math.sin(math.radians(-62))*-1
    j=(rng.random()-.5)*.045*(1-abs(t)*.3)
    pts.append((px+j*0.8,py+j))
pts=np.array(pts)
dist=np.full((N,N),9.0); tpar=np.zeros((N,N))
for i in range(n):
    a=pts[i];b=pts[i+1];ab=b-a;L=(ab**2).sum()
    px=xx-a[0];py=yy-a[1]
    t=np.clip((px*ab[0]+py*ab[1])/L,0,1)
    dx=px-t*ab[0];dy=py-t*ab[1]
    dd=np.sqrt(dx*dx+dy*dy);m=dd<dist
    dist=np.where(m,dd,dist);tpar=np.where(m,(i+t)/n*2-1,tpar)
wid=.052*(1-np.abs(tpar)**1.4)+.003
sd=(dist+(fbm(16,40)-.5)*.026)-wid
inside=sd<0
w1=fbm(5,10);w2=fbm(5,20)
hue=(fbm(6,30)*1.6+fbm(4,60)*1.2)%1.0
cols=np.array([(30,8,120),(200,25,160),(255,130,30),(25,190,225),(30,8,120)],dtype=float)
s=hue*4;i0=np.clip(np.floor(s).astype(int),0,3);f=(s-i0)[...,None]
inner=cols[i0]*(1-f)+cols[i0+1]*f
inner=inner*(0.25+0.95*fbm(9,70)**1.6)[...,None]
inner=inner+((rng.random((N,N))>.9965).astype(float))[...,None]*255
depth=np.clip(-sd/wid,0,1)[...,None]
inner=inner*(0.30+0.85*depth)
img=np.where(inside[...,None],inner,img)
glow=np.exp(-(np.maximum(sd,0)/.035))[...,None]*np.array([140,90,255])*.30+np.exp(-(np.abs(sd)/.004)**2)[...,None]*np.array([235,250,255])*.95
cim=Image.new('L',(N,N),0);cd=ImageDraw.Draw(cim)
for k in range(10):
    i=random.randint(8,n-8);bx,by=pts[i]*N
    ang=math.radians(-62)+math.pi/2+random.uniform(-.8,.8)+(math.pi if random.random()<.5 else 0)
    x0,y0=bx,by;L=random.uniform(60,170)
    for s_ in range(7):
        ang+=random.uniform(-.5,.5);x1=x0+math.cos(ang)*L/7;y1=y0+math.sin(ang)*L/7
        cd.line([x0,y0,x1,y1],fill=255,width=3 if s_<4 else 2);x0,y0=x1,y1
crack=np.asarray(cim.filter(ImageFilter.GaussianBlur(.8)))/255.0
img=np.clip(img+glow+crack[...,None]*np.array([190,225,255])*.9,0,255)
pil=Image.fromarray(img.astype('uint8'));bl=np.zeros_like(img)
for r,wt in ((5,.25),(16,.22),(44,.18)):
    b=np.asarray(pil.filter(ImageFilter.GaussianBlur(r))).astype(float)
    bl+=np.clip(b-165,0,255)*wt
img=np.clip(img+bl,0,255)
out=img.copy();sh=int(N*.005)
out[...,0]=np.roll(img[...,0],sh,axis=1);out[...,2]=np.roll(img[...,2],-sh,axis=1)
im=Image.fromarray(out.astype('uint8')).convert('RGBA');d=ImageDraw.Draw(im)
for _ in range(80):
    sx,sy=random.randint(0,N-1),random.randint(0,N-1)
    if inside[sy,sx]: continue
    r=random.choice([1,2,2,3]);d.ellipse([sx-r,sy-r,sx+r,sy+r],fill=(220,232,255,random.randint(90,220)))
vg=np.clip(1-((xx-.5)**2+(yy-.5)**2)*1.1,0,1)[...,None]
im=Image.fromarray((np.asarray(im.convert('RGB')).astype(float)*(0.6+0.4*vg)).astype('uint8')).convert('RGBA');d=ImageDraw.Draw(im)
fp='/usr/share/fonts/opentype/inter/Inter-Medium.otf'
f=ImageFont.truetype(fp if os.path.exists(fp) else '/usr/share/fonts/opentype/inter/Inter-Thin.otf',124)
t="ARCTIC";sp=44
ws=[d.textlength(c,font=f) for c in t];tw=sum(ws)+sp*(len(t)-1);tx=(N-tw)/2;ty=int(N*.80)
for c,w_ in zip(t,ws):
    d.text((tx,ty),c,font=f,fill=(255,255,255,255));tx+=w_+sp
d.line([N*.3,ty+176,N*.7,ty+176],fill=(150,190,255,130),width=4)
im.convert('RGB').resize((512,512),Image.LANCZOS).save('/tmp/arctic_placa_rift.png')
