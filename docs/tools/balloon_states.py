import sys, numpy as np
from PIL import Image
src=sys.argv[1]; base=sys.argv[2]
im=Image.open(src).convert('RGBA'); A=np.array(im)
H,W=A.shape[:2]; cw,ch=W//6,H//9
f=A.astype(float)/255; r,g,b,al=f[...,0],f[...,1],f[...,2],A[...,3]
mx=f[...,:3].max(-1); mn=f[...,:3].min(-1); sat=np.where(mx>0,(mx-mn)/np.maximum(mx,1e-6),0)
blue=(b>r+0.25)&(b>g+0.05)&(sat>0.5)&(mx>0.35)&(al>128)
brown=(r>g)&(g>b)&(r-b>0.18)&(al>128)
out={k:A.copy() for k in (2,1,0)}
report=[]
for row in range(9):
  for col in range(6):
    y0,x0=row*ch,col*cw
    bl=blue[y0:y0+ch,x0:x0+cw]; br=brown[y0:y0+ch,x0:x0+cw]; alc=al[y0:y0+ch,x0:x0+cw]>0
    ys,xs=np.nonzero(bl)
    # goggle/strap band: first row (below balloon top) with wide brown span
    rows=np.nonzero(br.sum(1)>cw*0.18)[0]
    if len(ys)<400 or len(rows)==0: report.append('-'); continue
    top=ys.min()
    gt=rows[rows>top+ch*0.12]
    if len(gt)==0: report.append('-'); continue
    gtop=gt.min()
    zone=bl.copy(); zone[gtop:]=False
    zy,zx=np.nonzero(zone)
    if len(zy)<300: report.append('-'); continue
    # 3 centers via kmeans on x of blue in zone
    xsf=zx.astype(float); c=np.percentile(xsf,[15,50,85])
    for _ in range(30):
        lab=np.argmin(np.abs(xsf[:,None]-c[None,:]),1)
        for j in range(3):
            if (lab==j).any(): c[j]=xsf[lab==j].mean()
    cy=np.array([zy[lab==j].mean() if (lab==j).any() else 0 for j in range(3)])
    order=np.argsort(c)  # left, mid, right
    L,M,R=order
    rad={}
    for j in range(3):
        sel=lab==j
        topj=zy[sel].min() if sel.any() else cy[j]
        rad[j]=max(cy[j]-topj, ch*0.05)
    # הפס הריק בין הבלונים לראש: השורה עם הכי מעט פיקסלים (רק החוטים), מתחת לבלונים ומעל הרצועה
    lo=int(max(cy[j]+rad[j]*0.8 for j in range(3))); hi=int(min(gtop, ch-1))
    if hi<=lo+2: lim=lo
    else:
        cov=alc[lo:hi].sum(1)
        lim=lo+int(np.argmin(cov))
    py,px=np.nonzero(alc[:lim])
    def keepmask(keep):
        k=np.zeros(len(py),bool)
        for j in keep:
            dx=px-c[j]; dy=py-cy[j]
            k|=(dx/(rad[j]*1.12))**2+(dy/(rad[j]*(1.3 if True else 1)))**2<1
            k|=(np.abs(px-c[j])<cw*0.05)&(py>cy[j])
        return k
    sets={2:[L,M],1:[L],0:[]}
    for kk,keep in sets.items():
        rem=~keepmask(keep)
        out[kk][y0+py[rem],x0+px[rem],3]=0
    report.append('ok')
for k in out: Image.fromarray(out[k]).save(f'{base}_b{k}.webp',lossless=False,quality=92,method=6)
print(' '.join(report))
# preview
for k in out:
    v=Image.new('RGBA',(W,H),(60,60,60,255)); v.alpha_composite(Image.fromarray(out[k])); v.convert('RGB').resize((W//2,H//2)).save(f'/tmp/prev_b{k}.jpg')
