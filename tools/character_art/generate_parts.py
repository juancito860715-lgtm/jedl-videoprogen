#!/usr/bin/env python3
"""Generate original, rig-ready JEDL SVG parts and transparent PNG runtime assets."""
from pathlib import Path
import json
import cairosvg
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "app/src/main/assets/characters/jedl_master_v1/manifest.json"
SVG_DIR = Path(__file__).resolve().parent / "generated"
PNG_DIR = ROOT / "app/src/main/assets/characters/jedl_master_v1/layers"
SIZE = 256
INK = "#17151a"

def svg(body, defs=""):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256">
    <defs>
      <linearGradient id="skin" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#f3ad70"/><stop offset=".48" stop-color="#c97843"/><stop offset="1" stop-color="#8e482e"/></linearGradient>
      <linearGradient id="denim" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#78b9e8"/><stop offset=".55" stop-color="#347eb9"/><stop offset="1" stop-color="#174e83"/></linearGradient>
      <linearGradient id="cloth" x1="0" y1="0" x2="0.8" y2="1"><stop stop-color="#ffffff"/><stop offset=".65" stop-color="#e8e7e5"/><stop offset="1" stop-color="#bfc3c9"/></linearGradient>
      <linearGradient id="black" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#38313a"/><stop offset=".55" stop-color="#17151a"/><stop offset="1" stop-color="#08090d"/></linearGradient>
      <linearGradient id="shoe" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#fff"/><stop offset=".72" stop-color="#e8e9ec"/><stop offset="1" stop-color="#b8bdc5"/></linearGradient>
      <linearGradient id="metal" x1="0" y1="0" x2="1" y2="0"><stop stop-color="#777d85"/><stop offset=".25" stop-color="#f8f5e9"/><stop offset=".55" stop-color="#9ba4ae"/><stop offset=".8" stop-color="#f5f1e5"/><stop offset="1" stop-color="#555d68"/></linearGradient>
      {defs}
    </defs>
    {body}
    </svg>'''

def path(d, fill, stroke=INK, sw=7, extra=""):
    return f'<path d="{d}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round" {extra}/>'

def ellipse(cx,cy,rx,ry,fill,stroke=INK,sw=6,extra=""):
    return f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" {extra}/>'

def line(x1,y1,x2,y2,color,width):
    return f'<path d="M{x1} {y1} L{x2} {y2}" fill="none" stroke="{color}" stroke-width="{width}" stroke-linecap="round"/>'

def artwork(pid):
    # Every component is authored in its own normalized local frame; transparent
    # padding leaves the manifest pivot at the joint rather than at the bitmap edge.
    if pid in ("head_base",):
        return svg(ellipse(128,132,75,101,"url(#skin)",sw=8) +
            path("M72 125 Q82 52 130 45 Q180 49 190 121 Q173 91 151 83 Q117 99 73 94 Z","url(#skin)",sw=4) +
            ellipse(76,137,13,21,"url(#skin)",sw=5) + ellipse(180,137,13,21,"url(#skin)",sw=5) +
            ellipse(76,140,5,9,"#8f4b31",sw=2) + ellipse(180,140,5,9,"#8f4b31",sw=2) +
            path("M82 176 Q128 207 173 175 Q164 229 128 231 Q91 224 82 176Z","#a75c3b",sw=4) +
            path("M91 114 Q108 105 121 114 M143 114 Q158 105 171 113","none",sw=7) +
            ellipse(108,130,9,13,"#fff",sw=2) + ellipse(151,130,9,13,"#fff",sw=2) +
            ellipse(110,132,4,7,"#251a18",sw=1) + ellipse(153,132,4,7,"#251a18",sw=1) +
            path("M128 130 Q116 156 130 160 Q139 161 145 155","none",sw=5) +
            path("M104 184 Q128 197 153 182 Q146 211 128 211 Q110 207 104 184Z","#24171a",sw=5) +
            path("M111 184 Q129 191 148 183","none","#f4d1b5",3))
    if pid in ("torso_base",):
        return svg(path("M83 34 Q128 12 173 34 L207 77 L184 114 L176 231 Q128 246 80 231 L72 114 L49 77 Z","url(#cloth)",sw=9) +
            path("M85 35 Q128 61 171 35","none","#b8b8bd",5) +
            path("M73 91 Q128 111 183 91 L176 222 Q128 238 80 222Z","none","#d0d0d2",3) +
            path("M87 111 L169 180 L151 201 L74 139 Z","#33313a",sw=3) +
            path("M103 120 L151 161 L141 174 L94 132Z","#8b50aa",sw=2) +
            path("M126 116 L171 153 L159 167 L114 129Z","#f2a32d",sw=2) +
            path("M88 142 L157 199","none","#45a6d8",7) +
            path("M112 149 L137 126 L149 138 L124 162Z","#20232a",sw=2) +
            line(98,205,158,205,"#b9b9bc",2))
    if pid in ("pants_base","thigh_l","thigh_r","shin_l","shin_r"):
        if pid=="pants_base":
            d="M63 18 Q128 6 193 18 L184 119 L72 119 Z"
        elif pid.startswith("thigh"):
            d="M79 12 Q128 3 177 12 L169 239 Q128 251 87 239 Z"
        else:
            d="M88 10 Q128 2 168 10 L160 241 Q128 250 96 241 Z"
        extra=path("M105 27 L99 215 M151 28 L158 216","none","#a8d6f1",5)+path("M103 157 L151 171","none","#d8e7ef",3)
        if pid.startswith("shin"): extra += path("M105 176 L148 184 L138 202 L99 195Z","#e9edf1",sw=3)
        if pid.startswith("thigh"): extra += path("M95 147 L121 159 L111 181 L88 170Z","#e8eef2",sw=3)+line(93,164,116,176,"#e8eef2",4)
        return svg(path(d,"url(#denim)",sw=8)+extra)
    if pid.startswith(("upper_arm","forearm")):
        if pid.startswith("upper"):
            d="M89 8 Q128 -1 167 8 L179 225 Q128 250 77 225 Z"
        else:
            d="M91 7 Q128 -2 165 7 L173 237 Q128 254 83 237 Z"
        return svg(path(d,"url(#skin)",sw=8)+path("M105 33 Q128 48 151 31","none","#f5bd8d",7)+path("M106 65 Q128 76 150 63","none","#a45a38",3)+path("M109 105 Q128 117 147 103","none","#f0a875",4))
    if pid.startswith("sleeve"):
        return svg(path("M47 50 Q128 5 209 50 L190 177 Q128 210 66 177 Z","url(#cloth)",sw=8)+path("M65 160 Q128 187 191 160","none","#c2c4c8",4))
    if pid.startswith("hand"):
        return svg(path("M91 34 Q107 16 119 34 L126 75 L137 26 Q150 14 157 32 L151 83 L165 45 Q181 40 183 57 L169 116 Q157 151 128 160 Q96 151 82 119 L72 75 Q76 57 91 67Z","url(#skin)",sw=7)+path("M112 78 Q126 99 143 83 M93 105 Q111 124 127 126","none","#8e482e",4))
    if pid in ("shoe_l","shoe_r"):
        return svg(path("M83 56 Q110 31 141 45 L164 100 Q187 120 216 130 Q232 147 218 174 Q170 198 57 173 Q38 160 59 127Z","url(#shoe)",sw=8)+path("M83 126 Q143 145 220 137 L220 167 Q160 190 58 169","none","#aab0ba",6)+line(108,78,159,124,"#fff",7)+line(102,95,151,138,"#b9bec7",4)+line(88,148,191,157,"#fff",5))
    if pid.startswith("foot"):
        return svg(ellipse(128,128,63,41,"url(#skin)",sw=7))
    if pid in ("cap_front","hat_back"):
        return svg(path("M48 128 Q51 42 126 31 Q202 24 218 105 L202 143 Q130 119 48 128Z","url(#black)",sw=9)+path("M52 126 Q135 107 228 136 Q213 165 144 163 Q83 158 52 126Z","#111116",sw=7)+path("M88 72 Q131 46 177 68","none","#51434b",4)+path("M116 76 Q148 55 181 66 Q151 91 116 76Z","#e33b32",sw=2))
    if pid.startswith("hair") or pid=="beard_short":
        if pid=="hair_back": d="M51 144 Q33 26 128 20 Q224 28 205 151 L181 177 L73 177Z"
        elif pid=="hair_afro": d="M48 147 Q25 55 82 35 Q128 -2 173 34 Q229 65 208 151 L183 120 L72 123Z"
        elif pid=="hair_long": d="M50 112 Q44 21 128 17 Q218 26 204 119 L220 236 L178 244 L165 125 L82 125 L73 244 L34 235Z"
        elif pid=="beard_short": d="M69 86 Q128 124 187 86 L178 174 Q153 220 128 220 Q99 215 78 174Z"
        else: d="M62 130 Q59 35 128 28 Q199 34 194 130 L170 104 Q128 79 84 106Z"
        return svg(path(d,"url(#black)",sw=7)+path("M78 93 Q126 58 177 93","none","#5d4540",5))
    if pid in ("eyes_pair","eyes_closed","brows_pair","nose_small","mouth_smile","mouth_neutral","mouth_open"):
        if pid=="eyes_pair": body=ellipse(91,128,13,20,"#fff",sw=3)+ellipse(164,128,13,20,"#fff",sw=3)+ellipse(94,132,6,10,"#211a19",sw=1)+ellipse(167,132,6,10,"#211a19",sw=1)
        elif pid=="eyes_closed": body=path("M68 128 Q91 145 114 128 M141 128 Q164 145 187 128","none",sw=7)
        elif pid=="brows_pair": body=path("M66 98 Q91 81 116 98 M140 98 Q165 81 190 98","none",sw=9)
        elif pid=="nose_small": body=path("M125 95 Q107 143 128 151 Q143 153 151 143","none","#8c482f",6)+ellipse(137,146,10,5,"#e39a68",sw=2)
        elif pid=="mouth_smile": body=path("M79 108 Q128 169 178 108 Q159 194 128 190 Q96 187 79 108Z","#3a1c1c",sw=5)+path("M91 121 Q128 149 166 121","none","#fff0df",6)
        elif pid=="mouth_neutral": body=path("M91 143 Q128 151 165 143","none","#472020",7)
        else: body=ellipse(128,145,22,30,"#4a1e20",sw=5)+path("M111 130 Q128 139 145 130","none","#fff0df",4)
        return svg(body)
    if pid in ("ear_left","ear_right","neck"):
        return svg(ellipse(128,128,48,78,"url(#skin)",sw=6)+ellipse(133,128,20,43,"none","#8e482e",4))
    if pid=="glasses":
        return svg('<g fill="none" stroke="#29252b" stroke-width="8"><rect x="47" y="88" width="66" height="56" rx="18"/><rect x="143" y="88" width="66" height="56" rx="18"/><path d="M113 109 Q128 98 143 109 M47 105 L27 95 M209 105 L229 95"/></g>')
    if pid=="chain":
        return svg(path("M67 20 Q128 147 189 20","none","#c99a32",7)+path("M77 20 Q128 130 179 20","none","#f7d77a",3))
    if pid=="medal":
        return svg(ellipse(128,128,58,66,"#d4a43c",sw=7)+ellipse(128,128,43,50,"#f5d778",sw=3)+path("M128 92 L139 121 L169 122 L145 141 L153 171 L128 153 L103 171 L111 141 L87 122 L117 121Z","#fff0b1",sw=3))
    if pid=="watch":
        return svg(path("M94 14 L162 14 L162 241 L94 241Z","#303039",sw=5)+path("M87 72 Q128 49 169 72 L169 183 Q128 204 87 183Z","#d6d9dc",sw=6)+ellipse(128,128,31,39,"#20232a",sw=3)+line(128,128,128,102,"#f3cf67",4)+line(128,128,148,139,"#f3cf67",4))
    if pid.startswith("stick"):
        return svg(path("M118 235 L143 21 Q147 9 155 18 L135 240Z","#d99b43",sw=4)+line(145,34,130,214,"#f5ce82",4))
    if pid=="drum_shell":
        return svg(path("M42 69 Q128 38 214 69 L205 211 Q128 244 51 211Z","url(#metal)",sw=8)+path("M42 69 Q128 101 214 69","none","#f8f1df",9)+path("M51 199 Q128 227 205 199","none","#f8f1df",8)+line(61,88,61,195,"#5a626d",6)+line(194,88,194,195,"#5a626d",6))
    if pid=="drum_head":
        return svg(ellipse(128,128,105,36,"#e9e4d8",sw=8)+ellipse(128,128,91,25,"#f9f7ed",sw=3))
    if pid=="drum_stand":
        return svg(line(128,24,128,190,"url(#metal)",15)+line(128,190,49,239,"#555d68",12)+line(128,190,207,239,"#555d68",12)+line(128,190,128,245,"#555d68",12))
    raise ValueError(f"No original SVG artwork mapping for {pid}")


def make_preview():
    # Neutral standing art board assembled from the exact exported rig pieces.
    canvas=Image.new("RGBA",(512,640),(0,0,0,0))
    def paste(pid, center, size, angle=0):
        image=Image.open(PNG_DIR/(pid+".png")).convert("RGBA").resize(size,Image.Resampling.LANCZOS)
        if angle:
            image=image.rotate(angle,resample=Image.Resampling.BICUBIC,expand=True)
        canvas.alpha_composite(image,(int(center[0]-image.width/2),int(center[1]-image.height/2)))
    paste("thigh_l",(211,386),(82,168),-3); paste("thigh_r",(301,386),(82,168),3)
    paste("shin_l",(210,507),(70,148),2); paste("shin_r",(302,507),(70,148),-2)
    paste("shoe_l",(202,589),(116,64)); paste("shoe_r",(310,589),(116,64))
    paste("upper_arm_l",(154,222),(74,142),-7); paste("upper_arm_r",(358,222),(74,142),7)
    paste("forearm_l",(145,323),(66,142),-3); paste("forearm_r",(367,323),(66,142),3)
    paste("sleeve_l",(153,202),(84,82),-7); paste("sleeve_r",(359,202),(84,82),7)
    paste("hand_l",(140,389),(64,78)); paste("hand_r",(372,389),(64,78))
    paste("torso_base",(256,226),(182,224))
    paste("neck",(256,113),(58,62))
    paste("ear_left",(197,95),(27,39)); paste("ear_right",(315,95),(27,39))
    paste("head_base",(256,89),(136,166))
    paste("eyes_pair",(256,82),(91,69)); paste("brows_pair",(256,63),(91,52))
    paste("nose_small",(256,101),(32,48)); paste("mouth_smile",(256,128),(53,39))
    paste("beard_short",(256,126),(83,54))
    paste("hair_back",(256,60),(136,150)); paste("hair_front",(256,65),(136,75))
    paste("cap_front",(256,27),(150,78))
    out=ROOT/"app/build/character-art/avatar-preview.png"
    out.parent.mkdir(parents=True,exist_ok=True)
    canvas.save(out)
    print("Generated rig-art preview:",out)

def main():
    manifest=json.loads(MANIFEST.read_text(encoding="utf-8"))
    SVG_DIR.mkdir(parents=True,exist_ok=True); PNG_DIR.mkdir(parents=True,exist_ok=True)
    seen=set()
    for part in manifest["parts"]:
        pid=part["id"]
        if pid in seen: raise SystemExit(f"duplicate part id: {pid}")
        seen.add(pid)
        source=artwork(pid)
        svg_path=SVG_DIR/f"{pid}.svg"
        png_path=PNG_DIR/f"{pid}.png"
        svg_path.write_text(source,encoding="utf-8")
        cairosvg.svg2png(bytestring=source.encode(),write_to=str(png_path),output_width=SIZE,output_height=SIZE)
        root=ET.parse(svg_path).getroot()
        if root.attrib.get("viewBox")!="0 0 256 256": raise SystemExit(f"bad SVG viewBox: {pid}")
        if not png_path.exists() or png_path.stat().st_size < 100: raise SystemExit(f"missing/empty PNG: {pid}")
        with Image.open(png_path) as image:
            if image.mode != "RGBA" or image.size != (SIZE, SIZE):
                raise SystemExit(f"invalid PNG mode/size for {pid}: {image.mode} {image.size}")
            low, high = image.getchannel("A").getextrema()
            if low == high or high == 0:
                raise SystemExit(f"PNG must contain transparent and visible pixels: {pid}")
    make_preview()
    print(f"Generated {len(seen)} original SVG layers and {len(seen)} transparent PNGs")
if __name__=="__main__": main()
