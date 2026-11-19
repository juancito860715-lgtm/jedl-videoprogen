from kitlib import S_, mirror_shapes

SH = "#dedede"
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
