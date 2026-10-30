"""
התמנון (Octo) בקוד: בונה את הדמות מצורות בסיס ב-Blender, מזיז אותה בקוד ומרנדר את כל הפריימים.
הרצה:  blender -b -P docs/blender/octopus.py -- <outdir> [angles=side,front,back] [cell=384] [samples=32]
הפלט: <outdir>/frames/<angle>_<row>_<col>.png (פריים לכל תא בגיליון 6x4) ועוד hero_*.png לבדיקה.
את הגיליון עצמו מרכיב docs/blender/make_sheet.py (Pillow).
"""
import math
import os
import random
import sys

import bmesh
import bpy
from mathutils import Euler, Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else "out"
ANGLES = (argv[1] if len(argv) > 1 else "side").split(",")
CELL = int(argv[2]) if len(argv) > 2 else 384
SAMPLES = int(argv[3]) if len(argv) > 3 else 32
FRAMES_DIR = os.path.join(OUT, "frames")
os.makedirs(FRAMES_DIR, exist_ok=True)

# סיבוב סביב Z: "side" = שלושת-רבעי פונה ימינה (כך רואים את הפנים בהליכה), מקדימה ומאחור
ANGLE_DEG = {"side": 60.0, "front": 0.0, "back": 180.0}

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
rng = random.Random(7)

# ------------------------------------------------------------------ חומרים


def mat(name, color, rough=0.45, metal=0.0, emit=None, strength=0.0, coat=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes["Principled BSDF"]

    def s(key, value):
        try:
            bsdf.inputs[key].default_value = value
        except Exception:
            pass

    s("Base Color", (*color, 1.0))
    s("Roughness", rough)
    s("Metallic", metal)
    if coat:
        s("Coat Weight", coat)
        s("Coat Roughness", 0.12)
    if emit is not None:
        s("Emission Color", (*emit, 1.0))
        s("Emission Strength", strength)
    m["bsdf"] = bsdf.name
    return m


def bsdf_of(m):
    return m.node_tree.nodes[m["bsdf"]]


def add_skin(m, scale=14.0, strength=0.6, vein=None, vein_scale=5.0, vein_width=0.07):
    """מרקם עור: רעש ל-bump (גבשושיות) ורשת ורידים כהה/אדומה מעל צבע הבסיס"""
    nt = m.node_tree
    bsdf = nt.nodes[m["bsdf"]]
    tc = nt.nodes.new("ShaderNodeTexCoord")
    noise = nt.nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = scale
    noise.inputs["Detail"].default_value = 8.0
    noise.inputs["Roughness"].default_value = 0.65
    nt.links.new(tc.outputs["Object"], noise.inputs["Vector"])
    bump = nt.nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = strength
    bump.inputs["Distance"].default_value = 0.05
    nt.links.new(noise.outputs["Fac"], bump.inputs["Height"])
    nt.links.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
    if vein is not None:
        vor = nt.nodes.new("ShaderNodeTexVoronoi")
        vor.feature = "DISTANCE_TO_EDGE"
        vor.inputs["Scale"].default_value = vein_scale
        nt.links.new(tc.outputs["Object"], vor.inputs["Vector"])
        ramp = nt.nodes.new("ShaderNodeValToRGB")
        ramp.color_ramp.elements[0].position = 0.0
        ramp.color_ramp.elements[0].color = (1, 1, 1, 1)
        ramp.color_ramp.elements[1].position = vein_width
        ramp.color_ramp.elements[1].color = (0, 0, 0, 1)
        nt.links.new(vor.outputs["Distance"], ramp.inputs["Fac"])
        mix = nt.nodes.new("ShaderNodeMixRGB")
        mix.blend_type = "MIX"
        mix.inputs["Color1"].default_value = bsdf.inputs["Base Color"].default_value
        mix.inputs["Color2"].default_value = (*vein, 1.0)
        nt.links.new(ramp.outputs["Color"], mix.inputs["Fac"])
        nt.links.new(mix.outputs["Color"], bsdf.inputs["Base Color"])


def glass_mat():
    m = bpy.data.materials.new("Glass")
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    mix = nt.nodes.new("ShaderNodeMixShader")
    clear = nt.nodes.new("ShaderNodeBsdfTransparent")
    glossy = nt.nodes.new("ShaderNodeBsdfPrincipled")
    lw = nt.nodes.new("ShaderNodeLayerWeight")
    lw.inputs["Blend"].default_value = 0.5
    mad = nt.nodes.new("ShaderNodeMath")
    mad.operation = "MULTIPLY_ADD"
    mad.inputs[1].default_value = 0.34
    mad.inputs[2].default_value = 0.025
    for key, value in (("Base Color", (0.35, 0.6, 0.5, 1.0)), ("Roughness", 0.06), ("Metallic", 0.0),
                       ("Emission Color", (0.2, 0.5, 0.4, 1.0)), ("Emission Strength", 0.1)):
        try:
            glossy.inputs[key].default_value = value
        except Exception:
            pass
    nt.links.new(lw.outputs["Fresnel"], mad.inputs[0])
    nt.links.new(mad.outputs[0], mix.inputs[0])
    nt.links.new(clear.outputs[0], mix.inputs[1])
    nt.links.new(glossy.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs[0])
    return m


PURPLE = mat("Purple", (0.56, 0.022, 0.70), rough=0.34, coat=0.32)
add_skin(PURPLE, scale=18.0, strength=0.025)  # smoother stylized skin; avoid the dense web-like vein pattern
PURPLE_DARK = mat("PurpleDark", (0.16, 0.008, 0.28), rough=0.28, coat=0.55)
add_skin(PURPLE_DARK, scale=18.0, strength=0.08)
SPOT = mat("Spot", (1.0, 0.075, 0.62), rough=0.34, coat=0.30)
SPOT_DARK = mat("SpotDark", (0.62, 0.025, 0.72), rough=0.40, coat=0.20)
EYE_WHITE = mat("EyeWhite", (1.0, 0.91, 0.72), rough=0.2, coat=0.25)
# Smooth cream sclera; no visible vein pattern in the toy style.
IRIS = mat("Iris", (1.0, 0.19, 0.008), rough=0.28, coat=0.25, emit=(1.0, 0.12, 0.005), strength=0.08)
PUPIL = mat("Pupil", (0.0, 0.0, 0.0), rough=0.9)
SHINE = mat("Shine", (1.0, 1.0, 1.0), emit=(1.0, 1.0, 1.0), strength=6.0)
MOUTH = mat("Mouth", (0.10, 0.0, 0.02), rough=0.9)
TEETH = mat("Teeth", (0.95, 0.89, 0.72), rough=0.32)
TONGUE = mat("Tongue", (1.0, 0.08, 0.34), rough=0.28, coat=0.35)
METAL = mat("Metal", (0.10, 0.10, 0.12), rough=0.5, metal=0.9)
add_skin(METAL, scale=25.0, strength=0.08)
RING = mat("Ring", (0.98, 0.16, 0.52), rough=0.32, metal=0.18, coat=0.35)
add_skin(RING, scale=30.0, strength=0.08)
COPPER = mat("Copper", (0.72, 0.39, 0.13), rough=0.32, metal=0.88, coat=0.18)
BADGE_YELLOW = mat("BadgeYellow", (1.0, 0.62, 0.025), rough=0.34, metal=0.12, coat=0.25)
FLAME_BLACK = mat("FlameBlack", (0.012, 0.008, 0.018), rough=0.65)
FLAME = mat("Flame", (1.0, 0.25, 0.02), emit=(1.0, 0.22, 0.02), strength=2.2)
ORB = mat("Orb", (0.6, 0.15, 1.0), emit=(0.6, 0.15, 1.0), strength=1.3)
ORB_CORE = mat("OrbCore", (1, 1, 1), emit=(0.9, 0.7, 1.0), strength=3.5)
STAR = mat("Star", (1.0, 0.85, 0.1), emit=(1.0, 0.8, 0.1), strength=5.0)
XMARK = mat("XMark", (0.02, 0.0, 0.02), rough=0.5)
SUCKER = mat("Sucker", (1.0, 0.10, 0.58), rough=0.30, coat=0.38, emit=(1.0, 0.08, 0.40), strength=0.24)
BONE = mat("Bone", (0.98, 0.24, 0.78), rough=0.3, coat=0.45)
TANK = mat("Tank", (0.72, 0.025, 0.46), rough=0.4, metal=0.05, coat=0.22)
# Smooth enamel tanks, matching the bright magenta body.
GLASS = glass_mat()

# ------------------------------------------------------------------ עזרים

main_parts = []   # כל מה שמצויר בדרך כלל (בהיעלמות בסוף המוות מסתירים אותם)


def smooth(o):
    me = o.data
    me.polygons.foreach_set("use_smooth", [True] * len(me.polygons))
    me.update()


def fin(o, m, parent, name, main=True):
    o.name = name
    o.data.materials.append(m)
    smooth(o)
    if parent is not None:
        o.parent = parent
    if main:
        main_parts.append(o)
    return o


def sphere(name, r, loc, m, parent=None, scale=(1, 1, 1), rot=(0, 0, 0), seg=32, main=True):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=seg // 2, radius=r, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.scale = scale
    return fin(o, m, parent, name, main)


def cylinder(name, r, depth, loc, m, parent=None, rot=(0, 0, 0), main=True):
    bpy.ops.mesh.primitive_cylinder_add(vertices=32, radius=r, depth=depth, location=loc, rotation=rot)
    return fin(bpy.context.active_object, m, parent, name, main)


def cone(name, r_bottom, r_top, depth, loc, m, parent=None, rot=(0, 0, 0), scale=(1, 1, 1), main=True):
    bpy.ops.mesh.primitive_cone_add(vertices=28, radius1=r_bottom, radius2=r_top, depth=depth, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.scale = scale
    return fin(o, m, parent, name, main)


def torus(name, major, minor, loc, m, parent=None, rot=(0, 0, 0), main=True):
    bpy.ops.mesh.primitive_torus_add(major_radius=major, minor_radius=minor, major_segments=56, minor_segments=16,
                                     location=loc, rotation=rot)
    return fin(bpy.context.active_object, m, parent, name, main)


def pipe_curve(name, points, material, parent, bevel=0.045):
    curve = bpy.data.curves.new(name + "Curve", "CURVE")
    curve.dimensions = "3D"
    curve.resolution_u = 16
    curve.bevel_depth = bevel
    curve.bevel_resolution = 5
    spline = curve.splines.new("BEZIER")
    spline.bezier_points.add(len(points) - 1)
    for bp, point in zip(spline.bezier_points, points):
        bp.co = point
        bp.handle_left_type = "AUTO"
        bp.handle_right_type = "AUTO"
    obj = bpy.data.objects.new(name, curve)
    bpy.context.collection.objects.link(obj)
    obj.parent = parent
    obj.data.materials.append(material)
    main_parts.append(obj)
    return obj


def flame_icon(name, x, y, z, parent):
    outline = [
        (0.000, -0.105), (-0.068, -0.025), (-0.060, 0.040),
        (-0.025, 0.012), (0.000, 0.115), (0.038, 0.052),
        (0.075, 0.010), (0.058, -0.052), (0.025, -0.085)
    ]
    inner = [
        (0.000, -0.050), (-0.030, -0.010), (-0.025, 0.024),
        (-0.010, 0.008), (0.000, 0.064), (0.018, 0.028),
        (0.036, 0.006), (0.027, -0.025), (0.010, -0.043)
    ]

    def add_flame_layer(layer_name, points, layer_y, material):
        verts = [(x + px, layer_y, z + pz) for px, pz in points]
        mesh = bpy.data.meshes.new(layer_name + "Mesh")
        mesh.from_pydata(verts, [], [tuple(range(len(verts)))])
        mesh.update()
        obj = bpy.data.objects.new(layer_name, mesh)
        bpy.context.collection.objects.link(obj)
        obj.parent = parent
        obj.data.materials.append(material)
        main_parts.append(obj)
        return obj

    add_flame_layer(name + "Outline", outline, y, FLAME_BLACK)
    return add_flame_layer(name, inner, y - 0.004, FLAME)


def empty(name, parent=None, loc=(0, 0, 0), rot=(0, 0, 0)):
    e = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(e)
    e.location = loc
    e.rotation_euler = rot
    if parent is not None:
        e.parent = parent
    return e


def normal_rot(n):
    return n.to_track_quat("Z", "Y").to_euler()


# ------------------------------------------------------------------ הדמות
# מערכת צירים: הדמות פונה אל -Y (אל המצלמה בזווית "front"). Z = למעלה.

TORSO_Z = 1.55
monster = empty("Monster")
torso = empty("Torso", monster, loc=(0, 0, TORSO_Z))
pivot = empty("BodyPivot", torso)   # נושם: מתכווץ ומתנפח, ובתוכו הגוף, העין והפה

# גוף: סילואטה עגולה וחלקה, עם כתמים ורודים גדולים בסגנון צעצוע
BODY_R = 0.9
BODY_SCALE = (1.05, 0.95, 0.85)

# Shared profile keeps the shell and raised spots aligned to the same surface.
BODY_PROFILE = [
    (-0.76, 0.64), (-0.70, 0.78), (-0.54, 0.91), (-0.30, 1.00),
    (-0.02, 1.035), (0.25, 1.00), (0.48, 0.88), (0.67, 0.67),
    (0.82, 0.40), (0.89, 0.12), (0.90, 0.015)
]

def shell_radius_at(z):
    if z <= BODY_PROFILE[0][0]:
        return BODY_PROFILE[0][1]
    for (z0, r0), (z1, r1) in zip(BODY_PROFILE, BODY_PROFILE[1:]):
        if z0 <= z <= z1:
            t = (z - z0) / (z1 - z0)
            return r0 + (r1 - r0) * t
    return BODY_PROFILE[-1][1]

def make_shell(name, parent, material):
    profile = BODY_PROFILE
    sides = 64
    verts, faces = [], []
    for z, radius in profile:
        for j in range(sides):
            a = 2 * math.pi * j / sides
            verts.append((radius * math.cos(a), radius * 0.88 * math.sin(a), z))
    for k in range(len(profile) - 1):
        for j in range(sides):
            a = k * sides + j
            b = k * sides + (j + 1) % sides
            faces.append((a, b, b + sides, a + sides))
    faces.append(tuple(range(sides - 1, -1, -1)))
    faces.append(tuple((len(profile) - 1) * sides + j for j in range(sides)))
    mesh = bpy.data.meshes.new(name + "Mesh")
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.parent = parent
    obj.data.materials.append(material)
    smooth(obj)
    sub = obj.modifiers.new("SilhouetteSmooth", "SUBSURF")
    sub.levels = 2
    sub.render_levels = 2
    main_parts.append(obj)
    return obj

make_shell("Body", pivot, PURPLE)

# כתמים בולטים על המצח והלחיים; הם שטוחים על פני הגוף ולא יוצרים מרקם של רשת
for i, (sx, sz, rx, rz, angle) in enumerate((
    (-0.62, 0.48, 0.12, 0.08, -0.35), (-0.34, 0.68, 0.10, 0.07, -0.55),
    (0.08, 0.78, 0.08, 0.055, 0.0), (0.46, 0.62, 0.12, 0.075, 0.45),
    (0.68, 0.34, 0.11, 0.08, 0.55), (-0.74, 0.08, 0.10, 0.11, -0.2),
    (0.56, -0.12, 0.11, 0.08, -0.4), (-0.48, -0.28, 0.10, 0.08, 0.4),
    (0.10, -0.32, 0.08, 0.055, 0.0),
)):
    x, z = sx, sz
    rx *= 0.82
    rz *= 0.80
    radius = shell_radius_at(z)
    surface = -0.88 * radius * math.sqrt(max(0.025, 1.0 - (x / radius)**2)) - 0.006
    spot_material = SPOT_DARK if i in (1, 5, 7) else SPOT
    sphere(f"BodySpot{i}", 1.0, (x, surface, z), spot_material, pivot,
           scale=(rx, 0.010, rz), rot=(0, angle, 0), seg=24)

# Small, irregularly spaced flat spots; avoid the obvious symmetrical ring around the face.
for i, (sx, sz, rx, rz) in enumerate((
    (-0.25, 0.82, 0.060, 0.042), (0.31, 0.79, 0.060, 0.042),
    (-0.78, 0.27, 0.065, 0.045), (0.76, 0.17, 0.060, 0.044),
    (-0.60, -0.04, 0.055, 0.040), (0.34, -0.38, 0.060, 0.040),
    (-0.18, -0.41, 0.050, 0.035),
)):
    rx *= 0.84
    rz *= 0.82
    radius = shell_radius_at(sz)
    surface = -0.88 * radius * math.sqrt(max(0.025, 1.0 - (sx / radius)**2)) - 0.004
    sphere(f"SmallBodySpot{i}", 1.0, (sx, surface, sz), SPOT, pivot,
           scale=(rx, 0.009, rz), seg=20)

# A visible crown of rounded pink spikes, including the front silhouette.
for i, (az_deg, pol_deg, height) in enumerate((
    (218, 42, 0.22), (242, 34, 0.25), (270, 29, 0.27),
    (298, 34, 0.25), (322, 42, 0.22)
)):
    az = math.radians(az_deg)
    pol = math.radians(pol_deg)
    n = Vector((math.sin(pol) * math.cos(az), math.sin(pol) * math.sin(az), math.cos(pol)))
    p = Vector((n.x * BODY_R * BODY_SCALE[0], n.y * BODY_R * BODY_SCALE[1], n.z * BODY_R * BODY_SCALE[2])) * 0.96
    cone(f"Horn{i}", 0.078, 0.0, height, p + n * (height / 2), SPOT, pivot, rot=normal_rot(n))

for i in range(14):
    az = rng.uniform(0, 2 * math.pi)
    pol = math.acos(rng.uniform(-0.5, 0.95))
    n = Vector((math.sin(pol) * math.cos(az), math.sin(pol) * math.sin(az), math.cos(pol)))
    if n.y < -0.35 and abs(n.x) < 0.8 and -0.65 < n.z < 0.85:
        continue  # שם העין והפה
    p = Vector((n.x * BODY_R * BODY_SCALE[0], n.y * BODY_R * BODY_SCALE[1], n.z * BODY_R * BODY_SCALE[2])) * 0.97
    h = rng.uniform(0.045, 0.09)
    cone(f"Thorn{i}", 0.04, 0.0, h, p + n * (h / 2), BONE, pivot, rot=normal_rot(n))

# Large exposed cyclopean eye seated in a raised purple socket.
EYE_Y, EYE_Z = -0.75, 0.26
eye = empty("Eye", pivot, loc=(0, EYE_Y, EYE_Z))
eye.scale = (1.04, 1.04, 1.04)
eye_parts = [
    sphere("EyeWhite", 0.56, (0, 0, 0), EYE_WHITE, eye, scale=(1, 0.50, 1), seg=56),
    sphere("Iris", 0.365, (0, -0.225, 0), IRIS, eye, scale=(1, 0.34, 1), seg=56),
    sphere("Pupil", 0.225, (0, -0.315, 0), PUPIL, eye, scale=(1.0, 0.30, 1.0), seg=48),
    sphere("Shine", 0.060, (0.080, -0.375, 0.125), SHINE, eye, scale=(1, 0.35, 1), seg=24),
]
look_parts = eye_parts[1:]  # iris, pupil and highlight move together
# Thick purple skin lip around the exposed eye; no glass dome and no upper eyelid.
torus("EyeSocket", 0.605, 0.065, (0, -0.025, 0), PURPLE, eye, rot=(math.pi / 2, 0, 0))
torus("EyeRim", 0.565, 0.018, (0, -0.045, 0), PURPLE_DARK, eye, rot=(math.pi / 2, 0, 0))

# Wider open grin with four readable ivory teeth and a small pink tongue.
mouth = empty("Mouth", pivot, loc=(0, -0.82, -0.43))
mouth_parts = [sphere("MouthCavity", 0.27, (0, 0, 0), MOUTH, mouth, scale=(1.45, 0.36, 0.48), seg=40)]
for i, x in enumerate((-0.20, 0.20)):
    h = 0.078
    mouth_parts.append(cone(f"ToothU{i}", 0.054, 0.0, h, (x, -0.072, 0.060 - h / 2), TEETH, mouth, rot=(math.pi, 0, 0)))
for i, x in enumerate((-0.13, 0.13)):
    h = 0.064
    mouth_parts.append(cone(f"ToothL{i}", 0.046, 0.0, h, (x, -0.074, -0.065 + h / 2), TEETH, mouth))
mouth_parts.append(sphere("Tongue", 0.12, (0, -0.094, -0.102), TONGUE, mouth,
                          scale=(1.10, 0.22, 0.34), seg=28))

# Broad pink collar with visible warm-metal rivets, like the reference harness.
torus("LowerPinkBand", 0.73, 0.045, (0, 0, -0.66), RING, torso)
torus("BandEdge", 0.73, 0.010, (0, 0, -0.708), PURPLE_DARK, torso)
for i in range(12):
    a = i * (2 * math.pi / 12)
    sphere(f"BandRivet{i}", 0.016, (0.73 * math.cos(a), 0.73 * math.sin(a), -0.66),
           COPPER, torso, seg=16)

# Twin magenta tanks with gunmetal straps, yellow/black flame badges and copper elbows.
for side, sx in (("L", -1), ("R", 1)):
    tx, ty = 1.14 * sx, 0.10
    cylinder(f"Tank{side}", 0.205, 0.80, (tx, ty, 0.015), TANK, torso)
    cylinder(f"TankCapTop{side}", 0.226, 0.085, (tx, ty, 0.462), METAL, torso)
    cylinder(f"TankCapBot{side}", 0.226, 0.085, (tx, ty, -0.432), METAL, torso)
    torus(f"TankBand1{side}", 0.206, 0.022, (tx, ty, 0.220), METAL, torso)
    torus(f"TankBand2{side}", 0.206, 0.022, (tx, ty, -0.205), METAL, torso)
    torus(f"TankGoldRing{side}", 0.206, 0.021, (tx, ty, 0.400), COPPER, torso)
    cylinder(f"TankNozzle{side}", 0.055, 0.095, (tx, ty, 0.535), METAL, torso)
    sphere(f"Valve{side}", 0.038, (tx, ty, 0.600), COPPER, torso, seg=20)
    cylinder(f"Connector{side}", 0.050, 0.21, (0.985 * sx, ty - 0.005, 0.205),
             COPPER, torso, rot=(0, math.pi / 2, 0))
    cylinder(f"Badge{side}", 0.142, 0.024, (tx, ty - 0.212, 0.005),
             BADGE_YELLOW, torso, rot=(math.pi / 2, 0, 0))
    flame_icon(f"FlameIcon{side}", tx, ty - 0.229, 0.005, torso)
    for j, (bx, bz) in enumerate(((-0.165, 0.15), (0.165, 0.15), (-0.165, -0.15), (0.165, -0.15))):
        sphere(f"TankBolt{side}{j}", 0.018, (tx + bx, ty - 0.215, bz),
               COPPER, torso, scale=(1, 0.35, 1), seg=16)
    pipe_curve(f"TankPipe{side}", (
        (tx, ty, 0.50), (tx, ty, 0.62), (tx - 0.09 * sx, ty, 0.73),
        (tx - 0.25 * sx, ty, 0.73), (tx - 0.32 * sx, ty, 0.63)
    ), COPPER, torso, bevel=0.040)

# Eight organic arms: one continuous tapered tube per arm instead of visible sausage segments.
ARM_LEN = [0.32, 0.28, 0.23, 0.18]
ARM_RAD = [0.22, 0.19, 0.16, 0.125, 0.065]
ARM_CURLS = [0.22, 0.35, -0.72]   # retained for the pose controls
ARM_TOTAL = sum(ARM_LEN)


def arm_curve_x(t):
    # Sweep outward, then turn back earlier so the hook reads clearly in the front view.
    u = max(0.0, min(1.0, (t - 0.46) / 0.54))
    hook = u * u * (3.0 - 2.0 * u)
    return 0.95 * math.sin(math.pi * 0.58 * t) - 0.88 * hook


def arm_curve_y(t):
    # A little depth variation prevents all eight curls from stacking into one plane.
    return 0.055 * math.sin(2.0 * math.pi * t)


def arm_curve_z(t):
    u = max(0.0, min(1.0, (t - 0.46) / 0.54))
    hook = u * u * (3.0 - 2.0 * u)
    # The arm drops into a compact loop and lifts its rounded tip back toward the body.
    return -0.86 * t + 1.05 * hook


def arm_radius(t):
    # Keep the arms plush and give each curled tip enough volume to stay rounded.
    if t < 0.68:
        return 0.25 - 0.025 * (t / 0.68)
    u = min(1.0, max(0.0, (t - 0.68) / 0.32))
    return 0.225 * (0.68 + 0.32 * math.cos(u * math.pi * 0.5))


def arm_center(t):
    return Vector((arm_curve_x(t), arm_curve_y(t), arm_curve_z(t)))


def make_arm_tube(name, parent):
    rings, sides = 56, 24
    verts, faces = [], []
    # Build each cross-section perpendicular to the actual curved centerline.
    # This prevents the tube from looking like a stack of rotated sausage segments.
    for ri in range(rings):
        t = ri / (rings - 1)
        center = arm_center(t)
        before = arm_center(max(0.0, t - 0.002))
        after = arm_center(min(1.0, t + 0.002))
        tangent = (after - before).normalized()
        reference = Vector((0.0, 1.0, 0.0))
        normal = reference - tangent * reference.dot(tangent)
        if normal.length < 1e-5:
            reference = Vector((1.0, 0.0, 0.0))
            normal = reference - tangent * reference.dot(tangent)
        normal.normalize()
        binormal = tangent.cross(normal).normalized()
        radius = arm_radius(t)
        for j in range(sides):
            a = 2.0 * math.pi * j / sides
            p = center + radius * (normal * math.cos(a) + binormal * math.sin(a))
            verts.append(tuple(p))
    for ri in range(rings - 1):
        for j in range(sides):
            a = ri * sides + j
            b = ri * sides + (j + 1) % sides
            faces.append((a, b, b + sides, a + sides))
    last_ring = (rings - 1) * sides
    # Closed ends let subdivision round the tip instead of pinching it into a point.
    faces.append(tuple(last_ring + j for j in range(sides)))
    faces.append(tuple(reversed(range(sides))))
    mesh = bpy.data.meshes.new(name + "Mesh")
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.parent = parent
    obj.data.materials.append(PURPLE)
    smooth(obj)
    sub = obj.modifiers.new("OrganicArmSmooth", "SUBSURF")
    sub.levels = 2
    sub.render_levels = 2
    main_parts.append(obj)
    return obj


arms = []   # לכל זרוע: (שורש, [מפרקים 0..3], הטיית שורש)
tips = []
for i in range(8):
    theta = math.radians(i * 45 + 22.5)
    tilt = 0.62 + rng.uniform(-0.06, 0.06)
    root = empty(f"ArmRoot{i}", torso, loc=(0.52 * math.cos(theta), 0.52 * math.sin(theta), -0.68), rot=(0, tilt, theta))
    joints = [root]
    for k in range(1, 4):
        joints.append(empty(f"Joint{i}_{k}", joints[k - 1], loc=(0, 0, -ARM_LEN[k - 1]), rot=(0, ARM_CURLS[k - 1], 0)))
    make_arm_tube(f"ArmTube{i}", root)
    # Suckers face the camera-facing side of each radial arm, rather than all pointing
    # in one local direction. Their shallow inset keeps them from reading as beads.
    root_rotation = root.rotation_euler.to_matrix()
    front_local = root_rotation.inverted() @ Vector((0.0, -1.0, 0.0))
    for q, t in enumerate((0.22, 0.42, 0.62, 0.80)):
        center = arm_center(t)
        before = arm_center(max(0.0, t - 0.002))
        after = arm_center(min(1.0, t + 0.002))
        tangent = (after - before).normalized()
        cup_normal = front_local - tangent * front_local.dot(tangent)
        if cup_normal.length < 1e-5:
            cup_normal = Vector((0.0, 1.0, 0.0))
        cup_normal.normalize()
        rr = arm_radius(t)
        cup_pos = center + cup_normal * rr * 0.93
        torus(f"SuckerRim{i}_{q}", rr * 0.40, rr * 0.115, tuple(cup_pos), SUCKER, root,
              rot=normal_rot(cup_normal))
        inset_pos = cup_pos + cup_normal * rr * 0.025
        sphere(f"SuckerCup{i}_{q}", 1.0, tuple(inset_pos), PURPLE_DARK, root,
               scale=(rr * 0.20, rr * 0.20, rr * 0.050), rot=normal_rot(cup_normal), seg=18)
    # Two soft pink skin patches break up the otherwise solid-purple tentacle surface.
    for q, t in enumerate((0.34, 0.66)):
        center = arm_center(t)
        before = arm_center(max(0.0, t - 0.002))
        after = arm_center(min(1.0, t + 0.002))
        tangent = (after - before).normalized()
        patch_normal = front_local - tangent * front_local.dot(tangent)
        if patch_normal.length < 1e-5:
            patch_normal = Vector((0.0, 1.0, 0.0))
        patch_normal.normalize()
        rr = arm_radius(t)
        patch_pos = center + patch_normal * rr * 0.94
        sphere(f"ArmSpot{i}_{q}", 1.0, tuple(patch_pos), SPOT, root,
               scale=(rr * 0.30, rr * 0.045, rr * 0.22),
               rot=normal_rot(patch_normal), seg=20)
    tip_loc = tuple(arm_center(1.0))
    tips.append(empty(f"Tip{i}", root, loc=tip_loc))
    arms.append((root, joints, tilt))

# ------------------------------------------------------------------ חלקים נסתרים רוב הזמן (אנרגיה, כוכבים, X, שברים)

orb_halo = sphere("OrbHalo", 1.0, (0, -1.3, -0.4), ORB, torso, seg=24, main=False)
orb_core = sphere("OrbCore", 0.55, (0, -1.3, -0.4), ORB_CORE, torso, seg=24, main=False)
orb_parts = [orb_halo, orb_core]

stars = []
for i, (sx, sz, sc) in enumerate(((-0.55, 1.45, 0.1), (0.0, 1.75, 0.13), (0.55, 1.45, 0.1))):
    stars.append(cone(f"Star{i}", sc, 0.0, sc * 1.6, (sx, 0, sz), STAR, torso, main=False))
    stars.append(cone(f"StarB{i}", sc, 0.0, sc * 1.6, (sx, 0, sz - sc * 1.6), STAR, torso, rot=(math.pi, 0, 0), main=False))

x_marks = []
for i, ang in enumerate((math.radians(45), math.radians(-45))):
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0, EYE_Y - 0.31, EYE_Z))
    o = bpy.context.active_object
    o.scale = (0.12, 0.05, 0.78)
    o.rotation_euler = (0, ang, 0)
    x_marks.append(fin(o, XMARK, pivot, f"X{i}", main=False))

frags = []
for i in range(16):
    a = rng.uniform(0, 2 * math.pi)
    el = rng.uniform(-0.2, 1.0)
    d = Vector((math.cos(a) * math.cos(el), math.sin(a) * math.cos(el) * 0.6, math.sin(el)))
    r = rng.uniform(0.07, 0.17)
    f = sphere(f"Frag{i}", r, (0, 0, 0), PURPLE if i % 3 else SPOT, torso, seg=12, main=False)
    frags.append((f, d, rng.uniform(1.0, 1.7)))

puddle = []
for i in range(9):
    a = rng.uniform(0, 2 * math.pi)
    d = rng.uniform(0.0, 0.85)
    r = rng.uniform(0.34, 0.6)
    puddle.append(sphere(f"Puddle{i}", r, (d * math.cos(a), d * math.sin(a) * 0.6, 0.04), PURPLE if i % 2 else PURPLE_DARK,
                         monster, scale=(1, 1, 0.3), seg=16, main=False))
shards = []
for i in range(7):
    a = rng.uniform(0, 2 * math.pi)
    d = rng.uniform(0.3, 1.1)
    shards.append(cone(f"Shard{i}", 0.1, 0.0, 0.28, (d * math.cos(a), d * math.sin(a) * 0.6, 0.12), GLASS, monster,
                       rot=(rng.uniform(1.0, 2.2), rng.uniform(-0.8, 0.8), rng.uniform(0, 6)), main=False))
for o in shards:
    try:
        o.visible_shadow = False
    except Exception:
        pass

# ------------------------------------------------------------------ קרקע, מצלמה, אורות

bpy.context.view_layer.update()
min_z = min(t.matrix_world.translation.z - ARM_RAD[4] for t in tips)
monster.location.z = -min_z
bpy.context.view_layer.update()
top_z = max((o.matrix_world @ Vector(c)).z for o in main_parts for c in o.bound_box)
HEIGHT = top_z
print("monster height", HEIGHT, "ground shift", -min_z)

bpy.ops.mesh.primitive_plane_add(size=30, location=(0, 0, 0))
ground = bpy.context.active_object
ground.name = "Ground"
ground.is_shadow_catcher = True

cam_data = bpy.data.cameras.new("Cam")
cam_data.type = "ORTHO"
cam_data.ortho_scale = max(4.2, HEIGHT * 1.2)
cam = bpy.data.objects.new("Cam", cam_data)
bpy.context.collection.objects.link(cam)
cam.location = (0, -14, HEIGHT * 0.5)
cam.rotation_euler = (math.radians(90), 0, 0)
scene.camera = cam
cam_data.clip_end = 100


def add_light(name, kind, energy, loc, rot=None, target=None, size=None, color=(1, 1, 1)):
    ld = bpy.data.lights.new(name, kind)
    ld.energy = energy
    ld.color = color
    if size is not None:
        ld.size = size
    lo = bpy.data.objects.new(name, ld)
    bpy.context.collection.objects.link(lo)
    lo.location = loc
    if target is not None:
        lo.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    elif rot is not None:
        lo.rotation_euler = rot
    return lo


sun = add_light("Sun", "SUN", 2.8, (0, 0, 8), rot=(math.radians(50), 0, math.radians(-35)), color=(1.0, 0.92, 0.82))
sun.data.angle = 0.18
add_light("Fill", "AREA", 150, (3.5, -6, 3.5), target=(0, 0, HEIGHT * 0.5), size=6, color=(0.7, 0.8, 1.0))
add_light("Rim", "AREA", 420, (-3.5, 5, 4.5), target=(0, 0, HEIGHT * 0.5), size=5, color=(0.95, 0.3, 1.0))

world = bpy.data.worlds.new("World")
scene.world = world
world.use_nodes = True
bg = world.node_tree.nodes["Background"]
bg.inputs[0].default_value = (0.25, 0.3, 0.5, 1.0)
bg.inputs[1].default_value = 0.4

# Eevee avoids a reproducible native Cycles BVH crash in the headless GitHub runner.
scene.render.engine = "BLENDER_EEVEE_NEXT"
scene.eevee.taa_render_samples = max(16, SAMPLES)
scene.render.image_settings.file_format = "PNG"
scene.render.film_transparent = True
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.render.image_settings.color_depth = "8"
scene.view_settings.view_transform = "Standard"
scene.view_settings.look = "None"

# ------------------------------------------------------------------ אנימציה בקוד

# מצב מנוחה של כל מה שזז
animated = [torso, pivot, eye, mouth] + look_parts + [orb_halo, orb_core]
for root, joints, _ in arms:
    animated += joints
rest = {o: (o.location.copy(), Euler(o.rotation_euler), o.scale.copy()) for o in animated}
body_bsdf = bsdf_of(PURPLE)
BASE_EMIT = (body_bsdf.inputs["Emission Strength"].default_value if "Emission Strength" in body_bsdf.inputs else 0.0)
flame_bsdf = bsdf_of(FLAME)
SIN, COS = math.sin, math.cos


def show(objs, flag):
    for o in objs:
        o.hide_render = not flag


def tint(amount, color=(1.0, 0.25, 0.2)):
    for m in (PURPLE, PURPLE_DARK):
        b = bsdf_of(m)
        try:
            b.inputs["Emission Color"].default_value = (*color, 1.0)
            b.inputs["Emission Strength"].default_value = amount
        except Exception:
            pass


def restore():
    for o, (loc, rot, scale) in rest.items():
        o.location = loc.copy()
        o.rotation_euler = Euler(rot)
        o.scale = scale.copy()
    show(main_parts, True)
    show(orb_parts + stars + x_marks + puddle + shards + [f for f, _, _ in frags], False)
    show(eye_parts, True)
    tint(0.0)
    flame_bsdf.inputs["Emission Strength"].default_value = 1.6
    for f, _, _ in frags:
        f.location = (0, 0, 0)
        f.scale = (1, 1, 1)


def arms_wave(t, amp_y, amp_x, lag=0.6, spread=0.785, extra_y=0.0):
    for i, (root, joints, tilt) in enumerate(arms):
        ph0 = t - i * spread
        root.rotation_euler.y = tilt + 0.5 * amp_y * SIN(ph0) - extra_y * 0.5
        for k in range(1, 4):
            ph = ph0 - k * lag
            joints[k].rotation_euler.y = ARM_CURLS[k - 1] + amp_y * (0.55 + 0.3 * k) * SIN(ph) + extra_y
            joints[k].rotation_euler.x = amp_x * COS(ph)


def look(dx, dz=0.0):
    for p in look_parts:
        p.location.x += dx
        p.location.z += dz


def pose(clip, i, n):
    restore()
    if clip in ("Idle", "Walk"):
        u = i / n
    else:
        u = i / max(n - 1, 1)
    t = 2 * math.pi * u
    if clip == "Idle":
        torso.location.z += 0.03 * SIN(t)
        pivot.scale = (1 - 0.014 * SIN(t), 1 - 0.014 * SIN(t), 1 + 0.03 * SIN(t))
        arms_wave(t, 0.09, 0.05)
        look(0.05 * SIN(t))
        if i == 3:
            eye.scale.z = 0.12
    elif clip == "Walk":
        torso.location.z += 0.06 * SIN(2 * t)
        torso.rotation_euler.x = 0.05 * SIN(t)
        torso.rotation_euler.y = 0.05 * COS(t)
        pivot.scale = (1 + 0.02 * SIN(2 * t), 1 + 0.02 * SIN(2 * t), 1 - 0.03 * SIN(2 * t))
        arms_wave(t, 0.5, 0.18, lag=0.7)
        look(0.08 * SIN(t))
    elif clip == "Charge":
        k = u  # 0 .. 1
        torso.rotation_euler.x = -0.18 * k
        torso.location.z += 0.05 * k
        pivot.scale = (1 + 0.04 * k, 1 + 0.04 * k, 1 - 0.03 * k)
        arms_wave(0.0, 0.0, 0.0, extra_y=-0.18 * k)
        mouth.scale = (1.0, 1.0, 1.0 + 0.9 * k)
        flame_bsdf.inputs["Emission Strength"].default_value = 1.6 + 6.0 * k
        show(orb_parts, True)
        r = 0.12 + 0.2 * k
        orb_halo.scale = (r, r, r)
        orb_core.scale = (r * 0.45, r * 0.45, r * 0.45)
        for o in orb_parts:
            o.location = (0, -1.25 - 0.05 * k, -0.42)
    elif clip == "Shoot":
        recoil = (1.0, 0.55, 0.0)[i]
        torso.rotation_euler.x = -0.22 * recoil + 0.1
        torso.location.y += 0.1 * recoil
        mouth.scale = (1.0, 1.0, 1.9 - 0.4 * i)
        flame_bsdf.inputs["Emission Strength"].default_value = 7.0 - 2.0 * i
        arms_wave(0.0, 0.0, 0.0, extra_y=-0.1)
        show(orb_parts, True)
        r = (0.42, 0.34, 0.24)[i]
        orb_halo.scale = (r, r, r)
        orb_core.scale = (r * 0.45, r * 0.45, r * 0.45)
        oy = (-1.5, -1.8, -2.05)[i]
        for o in orb_parts:
            o.location = (0, oy, -0.42)
    elif clip == "Hit":
        if i == 0:
            torso.rotation_euler.x = -0.3
            torso.location.z -= 0.08
            pivot.scale = (1.12, 1.1, 0.86)
            eye.scale.z = 0.3
            mouth.scale = (1.0, 1.0, 1.6)
            tint(0.4, (0.9, 0.05, 0.02))
            arms_wave(0.0, 0.0, 0.0, extra_y=-0.25)
        else:
            torso.rotation_euler.x = -0.12
            pivot.scale = (1.04, 1.04, 0.97)
            eye.scale.z = 0.7
            tint(0.15, (0.9, 0.05, 0.02))
            arms_wave(0.0, 0.0, 0.0, extra_y=0.05)
        show(stars, True)
    elif clip == "Death":
        show(eye_parts, False)
        show(x_marks, True)
        mouth.scale = (1.0, 1.0, 1.4)
        if i == 0:
            torso.rotation_euler.x = -0.4
            torso.location.z -= 0.1
            tint(0.3, (0.9, 0.05, 0.02))
            arms_wave(0.0, 0.0, 0.0, extra_y=-0.45)
        elif i == 1:
            torso.rotation_euler.x = -1.15
            torso.location.z -= 0.55
            torso.location.y += 0.35
            tint(0.2, (0.9, 0.05, 0.02))
            arms_wave(0.0, 0.0, 0.0, extra_y=-0.85)
        elif i == 2:
            torso.rotation_euler.x = -1.15
            torso.location.z -= 0.55
            torso.location.y += 0.35
            pivot.scale = (1.3, 1.3, 1.3)
            tint(0.45, (0.9, 0.35, 0.9))
            arms_wave(0.0, 0.0, 0.0, extra_y=-0.85)
            show([o for o in main_parts if o.name.startswith(("LowerPinkBand", "BandRivet"))], False)
            for f, d, dist in frags:
                f.hide_render = False
                f.location = d * dist
            show(shards, True)
        else:
            show(main_parts + x_marks, False)
            show(puddle + shards, True)
            for f, d, dist in frags[:10]:
                f.hide_render = False
                f.location = Vector((d.x * 1.0, d.y * 0.8, 0.0)) * dist * 0.7
                f.location.z = -TORSO_Z + 0.1
                f.scale = (1, 1, 0.4)


SHEET = [("Idle", 6, 0, 0), ("Walk", 6, 1, 0), ("Charge", 3, 2, 0), ("Shoot", 3, 2, 3), ("Hit", 2, 3, 0), ("Death", 4, 3, 2)]


def render(path, res):
    scene.render.resolution_x = res
    scene.render.resolution_y = res
    scene.render.resolution_percentage = 100
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


# מבט בדיקה גדול (3/4 ומקדימה) לפני הגיליון
pose("Idle", 0, 6)
for name, deg in (("hero_34", 35.0), ("hero_front", 0.0)):
    monster.rotation_euler[2] = math.radians(deg)
    render(os.path.join(OUT, name + ".png"), 768)

for angle in ANGLES:
    monster.rotation_euler[2] = math.radians(ANGLE_DEG[angle])
    for clip, count, row, col0 in SHEET:
        for i in range(count):
            pose(clip, i, count)
            render(os.path.join(FRAMES_DIR, f"{angle}_{row}_{col0 + i}.png"), CELL)
    print("angle done", angle)
