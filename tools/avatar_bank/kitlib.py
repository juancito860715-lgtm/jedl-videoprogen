import re
import math
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.path import Path
from matplotlib.patches import PathPatch

S = 640
INK_W = 0.0105

def S_(d, fill="#ffffff", ink=True, lw=1.0, z=0, alpha=1.0):
    return dict(d=d, fill=fill, ink=ink, lw=lw, z=z, alpha=alpha)

def parse(d):
    toks = re.findall(r"[MLCQZ]|-?\\d*\\.?\\d+(?:e-?\\d+)?", d)
    i = 0
    cmd = None
    verts, codes, start = [], [], None
    def num():
        nonlocal i
        value = float(toks[i])
        i += 1
        return value
    while i < len(toks):
        if toks[i] in ("M", "L", "C", "Q", "Z"):
            cmd = toks[i]
            i += 1
        if cmd == "M":
            p = (num(), num())
            verts.append(p); codes.append(Path.MOVETO); start = p; cmd = "L"
        elif cmd == "L":
            verts.append((num(), num())); codes.append(Path.LINETO)
        elif cmd == "C":
            for _ in range(3):
                verts.append((num(), num())); codes.append(Path.CURVE4)
        elif cmd == "Q":
            for _ in range(2):
                verts.append((num(), num())); codes.append(Path.CURVE3)
        elif cmd == "Z":
            if start is None:
                raise ValueError("Path closes before it has a start point")
            verts.append(start); codes.append(Path.CLOSEPOLY); cmd = None
        else:
            raise ValueError(f"Unsupported SVG path command: {cmd}")
    if not verts:
        raise ValueError("Empty vector path")
    return verts, codes

def mirror_d(d):
    toks = re.findall(r"[MLCQZ]|-?\\d*\\.?\\d+(?:e-?\\d+)?", d)
    result, coordinate_index = [], 0
    for token in toks:
        if token in ("M", "L", "C", "Q", "Z"):
            result.append(token); coordinate_index = 0
        else:
            value = float(token)
            if coordinate_index % 2 == 0:
                value = -value
            result.append(repr(round(value, 5)))
            coordinate_index += 1
    return " ".join(result)

def mirror_shapes(shapes):
    return [dict(shape, d=mirror_d(shape["d"])) for shape in shapes]

def bbox(shapes, margin=0.012):
    xs, ys = [], []
    for shape in shapes:
        vertices, _ = parse(shape["d"])
        xs.extend(point[0] for point in vertices)
        ys.extend(point[1] for point in vertices)
    if not xs or not ys:
        raise ValueError("Cannot calculate bounds for empty shapes")
    return min(xs)-margin, min(ys)-margin, max(xs)+margin, max(ys)+margin

def render_piece(shapes, joint, png_path):
    x0, y0, x1, y1 = bbox(shapes)
    width = max(1, int(math.ceil((x1-x0)*S)))
    height = max(1, int(math.ceil((y1-y0)*S)))
    fig = plt.figure(figsize=(width/100, height/100), dpi=100)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, width); ax.set_ylim(height, 0); ax.axis("off")
    fig.patch.set_alpha(0); ax.patch.set_alpha(0)
    for shape in sorted(shapes, key=lambda item: item["z"]):
        vertices, codes = parse(shape["d"])
        vertices = [((p[0]-x0)*S, (p[1]-y0)*S) for p in vertices]
        patch = PathPatch(
            Path(vertices, codes),
            facecolor=shape["fill"] if shape["fill"] else "none",
            edgecolor="#0b0b0f" if shape["ink"] else "none",
            linewidth=INK_W*S*shape["lw"]*72/100,
            joinstyle="round", capstyle="round",
            alpha=shape["alpha"], antialiased=True
        )
        ax.add_patch(patch)
    fig.savefig(png_path, transparent=True, dpi=100)
    plt.close(fig)
    return {
        "w": width, "h": height,
        "pivot": [round((joint[0]-x0)*S, 1), round((joint[1]-y0)*S, 1)],
        "bounds": [x0, y0, x1, y1]
    }
