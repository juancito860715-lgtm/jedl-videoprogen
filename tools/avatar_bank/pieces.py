from kitlib import S_, mirror_shapes

SH = "#dedede"
INK = "#0b0b0f"
K = 0.5523
J = dict(
    torso=(0,0), head=(0,-0.28), armUL=(-0.10,-0.26), foreArmL=(-0.10,-0.115),
    armUR=(0.10,-0.26), foreArmR=(0.10,-0.115), thighL=(-0.07,-0.02),
    shinL=(-0.07,0.17), thighR=(0.07,-0.02), shinR=(0.07,0.17)
)

def tube(cx,y0,y1,w0,w1):
    a=w0/2; b=w1/2
    return f"M {cx-a:.4f} {y0:.4f} L {cx+a:.4f} {y0:.4f} L {cx+b:.4f} {y1:.4f} L {cx-b:.4f} {y1:.4f} Z"

def ell(cx,cy,rx,ry):
    return (
        f"M {cx-rx:.4f} {cy:.4f} C {cx-rx:.4f} {cy-ry*K:.4f} {cx-rx*K:.4f} {cy-ry:.4f} {cx:.4f} {cy-ry:.4f} "
        f"C {cx+rx*K:.4f} {cy-ry:.4f} {cx+rx:.4f} {cy-ry*K:.4f} {cx+rx:.4f} {cy:.4f} "
        f"C {cx+rx:.4f} {cy+ry*K:.4f} {cx+rx*K:.4f} {cy+ry:.4f} {cx:.4f} {cy+ry:.4f} "
        f"C {cx-rx*K:.4f} {cy+ry:.4f} {cx-rx:.4f} {cy+ry*K:.4f} {cx-rx:.4f} {cy:.4f} Z"
    )

def hand(kind, cx):
    # Compact palm/finger silhouette anchored at the forearm endpoint.
    if kind == "fist":
        return [S_(ell(cx, 0.060, 0.027, 0.030), z=0),
                S_(f"M {cx-0.020:.4f} 0.045 L {cx+0.020:.4f} 0.045 L {cx+0.018:.4f} 0.068 L {cx-0.018:.4f} 0.068 Z", z=1)]
    if kind == "open":
        return [S_(f"M {cx-0.018:.4f} 0.040 L {cx-0.022:.4f} 0.010 L {cx-0.014:.4f} 0.006 L {cx-0.008:.4f} 0.034 L {cx-0.004:.4f} -0.002 L {cx+0.004:.4f} -0.002 L {cx+0.006:.4f} 0.034 L {cx+0.012:.4f} 0.004 L {cx+0.020:.4f} 0.008 L {cx+0.016:.4f} 0.040 C {cx+0.025:.4f} 0.046 {cx+0.024:.4f} 0.070 {cx+0.010:.4f} 0.078 L {cx-0.012:.4f} 0.078 C {cx-0.026:.4f} 0.068 {cx-0.027:.4f} 0.050 {cx-0.018:.4f} 0.040 Z", z=0)]
    raise ValueError(f"Unknown hand kind: {kind}")

P = {}
def add(pid, slot, bone, tint, z, shapes, variant=None):
    P[pid] = dict(slot=slot, bone=bone, tint=tint, z=z, shapes=shapes, variant=variant)

add("head_skin","head","head","skin",10,[S_(ell(0,-0.4,0.09,0.1), z=0)],variant="boy")
add("face_eyes_dots","eyes","head",None,20,[S_(ell(0,-0.4,0.01,0.01),fill="black",ink=False,z=0)],variant="dots")
add("face_mouth_smile","mouth","head",None,22,[S_("M -0.02 -0.35 Q 0 -0.33 0.02 -0.35",fill=None,lw=1.0,z=0)],variant="smile")
add("hair_short_back","hair_back","head","hair",5,[S_(ell(0,-0.42,0.095,0.09),z=0)],variant="short")
add("hair_short_front","hair_front","head","hair",30,[S_("M -0.1 -0.4 Q 0 -0.52 0.1 -0.4 Z",z=0)],variant="short")
add("torso_skin","torso","torso","skin",10,[S_(tube(0,-0.3,0,0.2,0.2),z=0)],variant="boy")
add("shirt_tee_torso","shirt","torso","shirt",20,[S_("M -0.1 -0.28 L 0.1 -0.28 L 0.1 0 L -0.1 0 Z",z=0)],variant="tee")
add("pants_jeans_waist","pants","torso","pants",15,[S_("M -0.1 0 L 0.1 0 L 0.1 0.05 L -0.1 0.05 Z",z=0)],variant="jeans")
for side, sign in (("L",-1),("R",1)):
    add(f"armU_skin_{side}","arm_skin",f"armU{side}","skin",12 if side=="R" else 52,[S_(tube(sign*0.1,-0.26,-0.1,0.05,0.05),z=0)],variant="boy")
    add(f"shirt_tee_sleeve_{side}","shirt",f"armU{side}","shirt",13 if side=="R" else 53,[S_(tube(sign*0.1,-0.26,-0.2,0.06,0.06),z=0)],variant="tee")
    add(f"forearm_skin_open_{side}","hand",f"foreArm{side}","skin",11 if side=="R" else 51,[S_(tube(sign*0.1,-0.1,0.05,0.04,0.04),z=0)],variant="open")
    add(f"thigh_jeans_{side}","pants",f"thigh{side}","pants",14 if side=="R" else 16,[S_(tube(sign*0.07,0,0.2,0.07,0.06),z=0)],variant="jeans")
    add(f"shin_jeans_{side}","pants",f"shin{side}","pants",14 if side=="R" else 16,[S_(tube(sign*0.07,0.2,0.35,0.06,0.06),z=0)],variant="jeans")
    add(f"shoe_sneaker_upper_{side}","shoes",f"shin{side}","shoe",17 if side=="R" else 18,[S_(tube(sign*0.07,0.35,0.4,0.07,0.08),z=0)],variant="sneaker")

# --- PIEZAS FALTANTES (22; total del banco: 40) ---
def eyes_extra(kind):
    if kind=="happy":
        return [S_("M -0.004 -0.390 C 0.004 -0.408 0.022 -0.408 0.030 -0.390", fill=None, lw=1.25, z=1),
                S_("M 0.048 -0.390 C 0.055 -0.405 0.070 -0.405 0.077 -0.390", fill=None, lw=1.15, z=1)]
    if kind=="wide":
        return [S_(ell(0.012,-0.394,0.0155,0.0205), z=0), S_(ell(0.017,-0.394,0.0075,0.0110), fill=INK, ink=False, z=1),
                S_(ell(0.062,-0.394,0.0135,0.0195), z=0), S_(ell(0.066,-0.394,0.0068,0.0105), fill=INK, ink=False, z=1)]
for k in ("happy","wide"): add(f"face_eyes_{k}","eyes","head",None,20,eyes_extra(k),variant=k)

def brows(kind):
    if kind=="normal": return [S_("M -0.006 -0.420 C 0.006 -0.427 0.022 -0.427 0.032 -0.421", fill=None, lw=0.9, z=1), S_("M 0.048 -0.421 C 0.057 -0.426 0.068 -0.425 0.076 -0.420", fill=None, lw=0.9, z=1)]
    if kind=="raised": return [S_("M -0.006 -0.428 C 0.006 -0.438 0.022 -0.438 0.032 -0.430", fill=None, lw=0.9, z=1), S_("M 0.048 -0.430 C 0.057 -0.437 0.068 -0.436 0.076 -0.428", fill=None, lw=0.9, z=1)]
    if kind=="angry":  return [S_("M -0.006 -0.428 C 0.008 -0.424 0.022 -0.419 0.032 -0.414", fill=None, lw=1.1, z=1), S_("M 0.048 -0.414 C 0.058 -0.419 0.068 -0.423 0.076 -0.428", fill=None, lw=1.1, z=1)]
for k in ("normal","raised","angry"): add(f"face_brows_{k}","brows","head",None,21,brows(k),variant=k)

def mouth_extra(kind):
    if kind=="open":    return [S_("M 0.030 -0.348 C 0.036 -0.325 0.064 -0.325 0.070 -0.348 C 0.058 -0.352 0.042 -0.352 0.030 -0.348 Z", fill="#3a1418", z=1),
                                S_("M 0.040 -0.334 C 0.048 -0.328 0.058 -0.328 0.062 -0.334 C 0.056 -0.338 0.046 -0.338 0.040 -0.334 Z", fill="#e86a7a", ink=False, z=2)]
    if kind=="neutral": return [S_("M 0.034 -0.340 L 0.066 -0.341", fill=None, lw=0.95, z=1)]
    if kind=="shout":   return [S_("M 0.028 -0.352 C 0.030 -0.318 0.068 -0.318 0.072 -0.352 C 0.058 -0.358 0.042 -0.358 0.028 -0.352 Z", fill="#3a1418", z=1),
                                S_("M 0.038 -0.330 C 0.048 -0.322 0.060 -0.322 0.064 -0.330 C 0.056 -0.335 0.046 -0.335 0.038 -0.330 Z", fill="#e86a7a", ink=False, z=2)]
for k in ("open","neutral","shout"): add(f"face_mouth_{k}","mouth","head",None,22,mouth_extra(k),variant=k)

add("face_blush","blush","head",None,19,[S_(ell(0.006,-0.352,0.017,0.0105), fill="#ff7f8c", ink=False, alpha=0.55, z=1), S_(ell(0.080,-0.356,0.013,0.0095), fill="#ff7f8c", ink=False, alpha=0.55, z=1)])

add("hair_spiky_back","hair_back","head","hair",5,[S_("M -0.094 -0.412 C -0.110 -0.384 -0.100 -0.344 -0.078 -0.330 C -0.080 -0.362 -0.084 -0.392 -0.090 -0.412 Z", z=0)],variant="spiky")
add("hair_spiky_front","hair_front","head","hair",30,[S_("M -0.098 -0.404 L -0.112 -0.462 L -0.082 -0.452 L -0.084 -0.520 L -0.052 -0.488 L -0.036 -0.552 L -0.010 -0.500 L 0.018 -0.560 L 0.034 -0.498 L 0.066 -0.548 L 0.070 -0.480 L 0.108 -0.500 L 0.102 -0.414 C 0.092 -0.436 0.076 -0.444 0.060 -0.438 C 0.050 -0.452 0.030 -0.456 0.014 -0.446 C 0.000 -0.460 -0.036 -0.455 -0.050 -0.436 C -0.070 -0.442 -0.088 -0.430 -0.098 -0.404 Z", z=0)],variant="spiky")

add("hair_curly_back","hair_back","head","hair",5,[S_(ell(-0.098,-0.372,0.026,0.036), z=0)],variant="curly")
cl=[]
for i,(cx,cy,r) in enumerate([(-0.078,-0.462,0.030),(-0.040,-0.495,0.032),(0.006,-0.508,0.034),(0.052,-0.495,0.032),(0.086,-0.458,0.028),(-0.090,-0.425,0.024)]): cl.append(S_(ell(cx,cy,r,r), z=i))
cl.append(S_("M -0.088 -0.420 C -0.060 -0.452 0.060 -0.452 0.098 -0.414 C 0.098 -0.440 0.060 -0.470 0.004 -0.470 C -0.056 -0.470 -0.092 -0.448 -0.088 -0.420 Z", ink=False, z=10))
add("hair_curly_front","hair_front","head","hair",30,cl,variant="curly")

add("hat_cap_crown","hat_crown","head","hat_crown",40,[S_("M -0.102 -0.432 C -0.108 -0.502 -0.058 -0.548 0.006 -0.548 C 0.020 -0.548 0.030 -0.547 0.036 -0.545 C 0.044 -0.500 0.044 -0.462 0.042 -0.432 Z", z=0)],variant="cap")
add("hat_cap_panel","hat_panel","head","hat_panel",41,[S_("M 0.034 -0.546 C 0.078 -0.540 0.108 -0.508 0.106 -0.432 L 0.040 -0.432 C 0.044 -0.470 0.042 -0.512 0.034 -0.546 Z", z=0)],variant="cap")
add("hat_cap_brim","hat_brim","head","hat_brim",42,[S_("M 0.040 -0.430 C 0.072 -0.442 0.128 -0.446 0.168 -0.424 C 0.172 -0.412 0.156 -0.405 0.134 -0.408 C 0.104 -0.413 0.070 -0.415 0.040 -0.416 Z", z=0)],variant="cap")

for side,sg in (("L",-1),("R",1)):
    for kind in ("open","fist"):
        add(f"hand_{kind}_{side}","hand",f"foreArm{side}","skin",11 if side=="R" else 51,hand(kind,sg*0.10),variant=kind)

for side,sg in (("L",-1),("R",1)):
    ax_=sg*0.07
    add(f"shoe_sneaker_sole_{side}","shoes",f"shin{side}","sole",17 if side=="R" else 18,[S_(f"M {ax_-0.048:.4f} 0.390 L {ax_+0.092:.4f} 0.390 C {ax_+0.100:.4f} 0.392 {ax_+0.102:.4f} 0.404 {ax_+0.094:.4f} 0.412 C {ax_+0.086:.4f} 0.418 {ax_+0.070:.4f} 0.420 {ax_+0.060:.4f} 0.420 L {ax_-0.040:.4f} 0.420 C {ax_-0.052:.4f} 0.420 {ax_-0.056:.4f} 0.404 {ax_-0.048:.4f} 0.390 Z", z=0)],variant="sneaker")
