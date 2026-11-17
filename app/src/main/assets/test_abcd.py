import sys,re,base64,cv2,numpy as np; sys.path.insert(0,'/home/claude/work')
from gl_eval import *; from render import render2
html=open('/mnt/user-data/outputs/bloodstream_scene.html').read(); open('/tmp/emb.glb','wb').write(base64.b64decode(re.search(r'const B64="([^"]+)"',html).group(1)))
g=G('/tmp/emb.glb'); SC=.72; I=g.idx
def bone(W,n): return W[I[n]][:3,3]
def fwd_axis(W): # same rule as the scene: left-right axes of shoulders and eye bones; f = left x up
    out=0
    for a,b in (('Shoulder_L','Shoulder_R'),('Eye_L','Eye_R')):
        l=bone(W,a)-bone(W,b); v=np.array([-l[2],l[0]]); out=out+v/np.linalg.norm(v)
    return out
T,Q,S=g.rest(); W0=g.fk(T,Q,S); fl=fwd_axis(W0); FWD=np.arctan2(fl[0],fl[1])
def rotY(th,v): c,s=np.cos(th),np.sin(th); return np.array([v[0]*c+v[2]*s,v[2]*c-v[0]*s])
# --- controller chain for key W, camera yaw 0 (identical to scene code) ---
mv=np.array([np.sin(0),np.cos(0)]); head=np.arctan2(mv[0],mv[1]); rot_y=head-FWD
def world_pose(clip,t,V,tm):
    ai=g.anim_names().index(clip); d=g.anim_dur(ai); T,Q,S=g.sample(ai,(t*tm)%d); W=g.fk(T,Q,S)
    return W
def skin_world(clip,t,V,tm,z0=0.0):
    ai=g.anim_names().index(clip); d=g.anim_dur(ai); T,Q,S=g.sample(ai,(t*tm)%d); sk=g.skin(T,Q,S); th=rot_y; c,s=np.cos(th),np.sin(th); out=[]
    for v,n in sk:
        w=v*SC; x=w[:,0]*c+w[:,2]*s; z=-w[:,0]*s+w[:,2]*c; vw=np.c_[x,w[:,1],z+z0+V*t]
        nx=n[:,0]*c+n[:,2]*s; nz=-n[:,0]*s+n[:,2]*c; out.append((vw,np.c_[nx,n[:,1],nz]))
    return out
rows=[('A  Idle + movement (W)','Idle',1.98,1.0,[0,.5,1.0,1.5]),('B  Walk only (hero fixed in world)','Walk',0.0,1.0,[0,.14,.28,.42]),
      ('C  Walk + movement (W)','Walk',1.98,1.0,[0,.14,.28,.42]),('D  Run + movement (W)','Run',3.77,1.0,[0,.1,.2,.3])]
W_,H_=330,300; sheet=[]
for title,clip,V,tm,ts in rows:
    cells=[]; zc=0.5*V*max(ts)+0.0
    for t in ts:
        sk=skin_world(clip,t,V,tm); img=render2(g,sk,yaw=90,pitch=0,size=(W_,H_),ymin=-.15,ymax=3.0,cx=zc)
        sc=H_/3.15; gy=int((3.0-0)*sc)
        cv2.line(img,(0,gy),(W_,gy),(120,120,120),1)
        for k in range(-6,8):      # fixed world ticks every 0.25 m
            x=int(W_/2+(k*.25-zc)*sc); cv2.line(img,(x,gy-4),(x,gy+5),(60,60,60),1)
        # arrows: face direction (from the real bones) and movement direction
        Wp=g.fk(*g.sample(g.anim_names().index(clip),(t*tm)%g.anim_dur(g.anim_names().index(clip)))); 
        f=fwd_axis(Wp); fw=rotY(rot_y,[f[0],0,f[1]]); fw/=np.linalg.norm(fw)
        hz=0.35*H_/1.0; px0=int(W_/2+(V*t-zc)*sc); py0=int((3.0-2.9)*sc)
        cv2.arrowedLine(img,(px0-40,py0),(int(px0-40+fw[1]*60),py0),(60,190,60),3,tipLength=.3)       # green = face
        if V>0: cv2.arrowedLine(img,(px0-40,py0+14),(px0-40+60,py0+14),(0,190,240),3,tipLength=.3)  # yellow = movement
        cv2.putText(img,f't={t:.2f}s',(6,H_-8),cv2.FONT_HERSHEY_SIMPLEX,.45,(40,40,40),1,cv2.LINE_AA); cells.append(img)
    row=np.hstack(cells); cv2.putText(row,title+'   (right = +Z = movement direction;  green = face dir, yellow = movement)',(6,16),cv2.FONT_HERSHEY_SIMPLEX,.5,(10,10,160),1,cv2.LINE_AA); sheet.append(row)
cv2.imwrite('/mnt/user-data/outputs/tests_ABCD.png',np.vstack(sheet))
# --------------------------- numeric verdicts ---------------------------
print(f'chain: key W, camera yaw 0 -> movement (+0,+1) -> head {np.degrees(head):.1f} deg; FWD {np.degrees(FWD):+.2f} deg -> hero.rotation.y {np.degrees(rot_y):+.2f} deg')
def face_dirs(clip,t):
    ai=g.anim_names().index(clip); W=g.fk(*g.sample(ai,t%g.anim_dur(ai)))
    mouth=bone(W,'Mouth')-bone(W,'Hips'); eye=((bone(W,'Pupil_L')+bone(W,'Pupil_R'))/2-bone(W,'Hips')); sh=fwd_axis(W)
    r=lambda v: rotY(rot_y,[v[0],0,v[-1] if len(v)==2 else v[2]])
    return [r(np.array([mouth[0],mouth[2]])),r(np.array([eye[0],eye[2]])),rotY(rot_y,[sh[0],0,sh[1]])]
def unit(v): return v/np.linalg.norm(v)
# A
fd=face_dirs('Idle',0.7); dots=[float(np.dot(unit(v),mv)) for v in fd]; print(f'A  Idle+movement : dot(face/chest dir, movement) mouth {dots[0]:+.2f}, eyes {dots[1]:+.2f}, shoulder-axis {dots[2]:+.2f}; Idle has no leg cycle, body translates along its facing axis -> {"CORRECT" if min(dots)>.9 else "INVERTED"}')
# B and C/D: gait loop orientation + planted-foot world drift
def gait(clip):
    ai=g.anim_names().index(clip); d=g.anim_dur(ai); n=int(round(d*120)); res={}
    for s in 'LR':
        P=[];
        for k in range(n):
            W=g.fk(*g.sample(ai,k/120)); h=bone(W,'Hips'); a=bone(W,'Ankle_'+s); P.append([(a[2]-h[2])*SC,a[1]*SC])
        P=np.array(P); area=.5*np.sum(P[:-1,0]*P[1:,1]-P[1:,0]*P[:-1,1]); vz=np.gradient(P[:,0]); res[s]=(area,np.corrcoef(vz,P[:,1]-P[:,1].min())[0,1])
    return res
for clip in ('Walk','Run'):
    r=gait(clip); ok=all(v[0]<0 and v[1]>0 for v in r.values())
    print(f'B  {clip} only    : chest/face heading +Z (right); ankle loops clockwise (area L {r["L"][0]:+.3f} R {r["R"][0]:+.3f}), foot raised while moving forward (corr L {r["L"][1]:+.2f} R {r["R"][1]:+.2f}) -> {"CORRECT (foot advances toward the face direction)" if ok else "INVERTED"}')
cfg={'Walk':(1.98,17/30,.62),'Run':(3.77,14/30,.40)}
for lab,clip in (('C','Walk'),('D','Run')):
    V,T_,beta=cfg[clip]; ai=g.anim_names().index(clip); d=g.anim_dur(ai); out=[]
    for s,off in (('L',0),('R',.5)):
        t0=((-off)%1)*T_; ts=np.arange(t0+.08*beta*T_,t0+.92*beta*T_,1/120); zs=[]
        for t in ts:
            W=g.fk(*g.sample(ai,t%d)); M=W@g.ibm; v=g.prims[0]['pos']; jt=g.prims[0]['jt']; wt=g.prims[0]['wt']; b=I['Ankle_'+s]; m=((jt==b)*wt).sum(1)>.8
            vv=np.c_[v[m],np.ones(m.sum())]; p=(M[b][:3,:]@vv.T).T; q=p[p[:,1].argmin()]; zs.append(V*t+q[2]*SC)
        out.append((zs[-1]-zs[0],(ts[-1]-ts[0])*V))
    print(f'{lab}  {clip}+movement: hero moves +Z at {V} m/s, face +Z (dot {float(np.dot(unit(face_dirs(clip,0.2)[1]),mv)):+.2f}); stance foot body-frame moves BACK, world drift of planted foot L {out[0][0]:+.3f} m R {out[1][0]:+.3f} m over a {out[0][1]:.2f} m stride -> {"CORRECT (planted, no skating, no backward walk)" if max(abs(out[0][0]),abs(out[1][0]))<.08*out[0][1] else "CHECK"}')
