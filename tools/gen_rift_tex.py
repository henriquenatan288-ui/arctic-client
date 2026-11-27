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
img=np.zeros((N,N,3))
inner_bg=True
cx,cy=.5,.5
n=70;pts=[]
for i in range(n+1):
    t=i/n*2-1
    px=cx+t*.36*math.cos(math.radians(-87))*-1
    py=cy+t*.44*math.sin(math.radians(-87))*-1
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
    ang=math.radians(-87)+math.pi/2+random.uniform(-.8,.8)+(math.pi if random.random()<.5 else 0)
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

mx=out.max(axis=2)
al=np.clip(mx/170.0,0,1)
al=np.where(inside,np.maximum(al,0.93),al)
rgb=np.clip(out/np.maximum(al[...,None],1e-3),0,255)
rgba=np.dstack([rgb,al*255]).astype('uint8')
im=Image.fromarray(rgba,'RGBA').convert('RGBa')
import os
os.makedirs('/tmp/rift_out',exist_ok=True)
for t in (96,128,192,256,384,512):
    im.resize((t,t),Image.LANCZOS).convert('RGBA').save(f'/tmp/rift_out/rift_{t}.png')
# preview sobre azul escuro
pv=Image.new('RGBA',(512,512),(10,20,50,255));pv.alpha_composite(Image.open('/tmp/rift_out/rift_512.png'));pv.convert('RGB').save('/tmp/rift_prev.png')
