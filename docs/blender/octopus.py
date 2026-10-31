"""
התמנון (Octo) בקוד: בונה את הדמות מצורות בסיס ב-Blender, מזיז אותה בקוד ומרנדר את כל הפריימים.
הרצה:  blender -b -P docs/blender/octopus.py -- <outdir> [angles=side,front,back] [cell=384] [samples=32]
הפלט: <outdir>/frames/<angle>_<row>_<col>.png (פריים לכל תא בגיליון 6x4) ועוד hero_*.png לבדיקה.
את הגיליון עצמו מרכיב docs/blender/make_sheet.py (Pillow).
"""
import math
import os
import random
import shutil
import sys

import bmesh
import bpy
from mathutils import Euler, Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else "out"
ANGLES = (argv[1] if len(argv) > 1 else "side").split(",")
# Keep the full 24-frame CI render comfortably inside the 40-minute job limit.
CELL = min(int(argv[2]) if len(argv) > 2 else 256, 256)
SAMPLES = min(int(argv[3]) if len(argv) > 3 else 6, 6)
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
    """A genuinely see-through EEVEE shell with a restrained blue glossy component."""
    m = bpy.data.materials.new("Glass")
    m.use_nodes = True
    try:
        m.surface_render_method = "BLENDED"
    except Exception:
        pass
    try:
        m.use_transparency_overlap = False
    except Exception:
        pass
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    transparent = nt.nodes.new("ShaderNodeBsdfTransparent")
    shell = nt.nodes.new("ShaderNodeBsdfPrincipled")
    shell.inputs["Base Color"].default_value = (0.24, 0.52, 0.94, 1.0)
    shell.inputs["Roughness"].default_value = 0.075
    shell.inputs["Metallic"].default_value = 0.0
    shell.inputs["Coat Weight"].default_value = 0.55
    shell.inputs["Coat Roughness"].default_value = 0.025
    mix = nt.nodes.new("ShaderNodeMixShader")
    mix.inputs[0].default_value = 0.035
    nt.links.new(transparent.outputs["BSDF"], mix.inputs[1])
    nt.links.new(shell.outputs["BSDF"], mix.inputs[2])
    nt.links.new(mix.outputs["Shader"], out.inputs["Surface"])
    return m


PURPLE = mat("Purple", (0.53, 0.012, 0.78), rough=0.23, coat=0.72)
add_skin(PURPLE, scale=18.0, strength=0.018)  # rich glossy violet, with restrained surface texture
PURPLE_DARK = mat("PurpleDark", (0.16, 0.008, 0.28), rough=0.28, coat=0.55)
LID = mat("Lid", (0.29, 0.008, 0.57), rough=0.20, coat=0.66)
add_skin(PURPLE_DARK, scale=18.0, strength=0.08)
SPOT = mat("Spot", (0.98, 0.075, 0.42), rough=0.30, coat=0.42)
SPOT_DARK = mat("SpotDark", (0.22, 0.006, 0.38), rough=0.34, coat=0.30)
EYE_WHITE = mat("EyeWhite", (1.0, 0.58, 0.16), rough=0.22, coat=0.42,
                emit=(0.22, 0.085, 0.012), strength=0.035)
# Warm amber sclera and deep orange iris, matching the reference's saturated eye.
IRIS = mat("Iris", (1.0, 0.28, 0.006), rough=0.17, coat=0.52, emit=(0.65, 0.065, 0.0), strength=0.06)
PUPIL = mat("Pupil", (0.0001, 0.00005, 0.0001), rough=0.25, coat=0.24)
SHINE = mat("Shine", (1.0, 1.0, 1.0), emit=(1.0, 1.0, 1.0), strength=6.0)
MOUTH = mat("Mouth", (0.001, 0.0001, 0.001), rough=0.72)
TEETH = mat("Teeth", (0.95, 0.89, 0.72), rough=0.32)
TONGUE = mat("Tongue", (1.0, 0.08, 0.34), rough=0.28, coat=0.35)
METAL = mat("Metal", (0.46, 0.50, 0.58), rough=0.31, metal=0.78, coat=0.18)
add_skin(METAL, scale=25.0, strength=0.08)
RING = mat("Ring", (0.92, 0.10, 0.44), rough=0.28, metal=0.12, coat=0.46)
add_skin(RING, scale=30.0, strength=0.055)
COLLAR = mat("Collar", (0.95, 0.12, 0.46), rough=0.25, metal=0.18, coat=0.55)
COPPER = mat("Copper", (0.90, 0.48, 0.15), rough=0.30, metal=0.78, coat=0.22)
BADGE_YELLOW = mat("BadgeYellow", (1.0, 0.46, 0.018), rough=0.34, metal=0.22, coat=0.32)
FLAME_BLACK = mat("FlameBlack", (0.012, 0.008, 0.018), rough=0.65)
FLAME = mat("Flame", (1.0, 0.25, 0.02), emit=(1.0, 0.22, 0.02), strength=2.2)
ORB = mat("Orb", (0.6, 0.15, 1.0), emit=(0.6, 0.15, 1.0), strength=1.3)
ORB_CORE = mat("OrbCore", (1, 1, 1), emit=(0.9, 0.7, 1.0), strength=3.5)
STAR = mat("Star", (1.0, 0.85, 0.1), emit=(1.0, 0.8, 0.1), strength=5.0)
XMARK = mat("XMark", (0.02, 0.0, 0.02), rough=0.5)
SUCKER = mat("Sucker", (0.035, 0.001, 0.065), rough=0.42, coat=0.12)
BONE = mat("Bone", (0.98, 0.24, 0.78), rough=0.3, coat=0.45)
TENTACLE = mat("Tentacle", (0.61, 0.018, 0.84), rough=0.23, coat=0.66,
               emit=(0.12, 0.004, 0.20), strength=0.10)
TANK = mat("Tank", (0.68, 0.018, 0.46), rough=0.38, metal=0.20, coat=0.42)
add_skin(TANK, scale=34.0, strength=0.045)  # subtle worn enamel, not a flat plastic cylinder
# Smooth enamel tanks, matching the bright magenta body.
GLASS = glass_mat()
DOME_RIM = mat("DomeRim", (0.06, 0.34, 0.96), rough=0.18, metal=0.08,
               emit=(0.02, 0.16, 0.88), strength=0.35, coat=0.42)
DOME_HIGHLIGHT = mat("DomeHighlight", (0.92, 0.97, 1.0), rough=0.08,
                     emit=(0.62, 0.78, 1.0), strength=0.45, coat=0.58)

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
        verts = [(x + px * 2.4, layer_y, z + pz * 2.4) for px, pz in points]
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
pivot.scale = (1.0, 1.0, 1.20)  # taller, rounder head like the reference

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


def body_band(name, parent, z_bottom, z_top, material):
    """Even-width circular collar; avoid the oversized triangular flare of the old band."""
    sides = 128
    verts, faces = [], []
    radius = shell_radius_at((z_bottom + z_top) * 0.5) + 0.13
    for z_base in (z_bottom, z_top):
        for j in range(sides):
            a = 2.0 * math.pi * j / sides
            z = z_base + 0.045 * math.cos(a)
            verts.append((radius * math.cos(a), radius * 0.90 * math.sin(a), z))
    for j in range(sides):
        nxt = (j + 1) % sides
        faces.append((j, nxt, sides + nxt, sides + j))
    mesh = bpy.data.meshes.new(name + "Mesh")
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.parent = parent
    obj.data.materials.append(material)
    smooth(obj)
    solid = obj.modifiers.new("BandThickness", "SOLIDIFY")
    solid.thickness = 0.045
    solid.offset = 0.0
    bevel = obj.modifiers.new("BandSoftEdges", "BEVEL")
    bevel.width = 0.018
    bevel.segments = 3
    main_parts.append(obj)
    return obj

# כתמים בולטים על המצח והלחיים; הם שטוחים על פני הגוף ולא יוצרים מרקם של רשת
for i, (sx, sz, rx, rz, angle) in enumerate((
    (-0.62, 0.48, 0.12, 0.08, -0.35), (-0.34, 0.68, 0.10, 0.07, -0.55),
    (0.08, 0.78, 0.08, 0.055, 0.0), (0.46, 0.62, 0.12, 0.075, 0.45),
    (0.68, 0.34, 0.11, 0.08, 0.55), (-0.74, 0.08, 0.10, 0.11, -0.2),
    (0.56, -0.12, 0.11, 0.08, -0.4), (-0.48, -0.28, 0.10, 0.08, 0.4),
    (0.10, -0.32, 0.08, 0.055, 0.0),
    (-0.64, -0.48, 0.085, 0.060, -0.3), (0.68, -0.48, 0.085, 0.060, 0.35),
    (0.02, 0.84, 0.075, 0.050, 0.0),
)):
    x, z = sx, sz
    rx *= 1.82
    rz *= 1.62
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
    (-0.18, -0.41, 0.050, 0.035), (0.52, 0.46, 0.060, 0.045),
    (-0.52, 0.38, 0.060, 0.045),
)):
    rx *= 1.48
    rz *= 1.38
    radius = shell_radius_at(sz)
    surface = -0.88 * radius * math.sqrt(max(0.025, 1.0 - (sx / radius)**2)) - 0.004
    sphere(f"SmallBodySpot{i}", 1.0, (sx, surface, sz), SPOT, pivot,
           scale=(rx, 0.009, rz), seg=20)

# A visible crown of rounded pink spikes, including the front silhouette.
for i, (az_deg, pol_deg, height) in enumerate((
    (218, 42, 0.38), (242, 34, 0.42), (270, 29, 0.45),
    (298, 34, 0.42), (322, 42, 0.38)
)):
    az = math.radians(az_deg)
    pol = math.radians(pol_deg)
    n = Vector((math.sin(pol) * math.cos(az), math.sin(pol) * math.sin(az), math.cos(pol)))
    p = Vector((n.x * BODY_R * BODY_SCALE[0], n.y * BODY_R * BODY_SCALE[1], n.z * BODY_R * BODY_SCALE[2])) * 1.0
    cone(f"Horn{i}", 0.115, 0.0, height * 1.14, p + n * (height * 1.14 / 2), SPOT, pivot, rot=normal_rot(n))

for i in range(14):
    az = rng.uniform(0, 2 * math.pi)
    pol = math.acos(rng.uniform(-0.5, 0.95))
    n = Vector((math.sin(pol) * math.cos(az), math.sin(pol) * math.sin(az), math.cos(pol)))
    if n.y < -0.35 and abs(n.x) < 0.8 and -0.65 < n.z < 0.85:
        continue  # שם העין והפה
    p = Vector((n.x * BODY_R * BODY_SCALE[0], n.y * BODY_R * BODY_SCALE[1], n.z * BODY_R * BODY_SCALE[2])) * 0.97
    h = rng.uniform(0.045, 0.09)
    cone(f"Thorn{i}", 0.04, 0.0, h, p + n * (h / 2), BONE, pivot, rot=normal_rot(n))

# Large horizontal cyclopean eye with a warm almond-shaped opening and a heavy slanted lid.
# The iris remains exposed below the lid, as in the supplied reference.
EYE_Y, EYE_Z = -0.73, 0.20
eye = empty("Eye", pivot, loc=(0, EYE_Y, EYE_Z))
eye.scale = (1.0, 1.0, 1.0)
eye_parts = [
    # Fully open, nearly circular cyclopean eye: cream sclera, orange iris, glossy black pupil.
    sphere("EyeWhite", 0.72, (0, 0, 0), EYE_WHITE, eye, scale=(0.88, 0.56, 0.90), seg=64),
    sphere("Iris", 0.40, (0, -0.345, -0.015), IRIS, eye, scale=(1.00, 0.46, 1.00), seg=64),
    sphere("Pupil", 0.25, (0, -0.465, -0.015), PUPIL, eye, scale=(1.00, 0.38, 1.00), seg=56),
    sphere("Shine", 0.075, (0.10, -0.555, 0.14), SHINE, eye, scale=(1.0, 0.30, 0.90), seg=28),
]
look_parts = eye_parts[1:4]
eye_socket = torus("EyeSocket", 0.61, 0.046, (0, -0.025, 0), PURPLE_DARK, eye, rot=(math.pi / 2, 0, 0))
eye_socket.scale = (1.12, 1.0, 1.02)

# A shaped skin lid, not a floating capsule: its lower edge slopes into an angry squint.
def make_upper_eyelid(parent):
    rx, ry, rz = 0.63 * 1.38, 0.63 * 0.56, 0.63 * 0.72
    cols, rows = 40, 10
    verts, faces, lower_edge = [], [], []
    for i in range(cols + 1):
        x = -rx * 0.84 + (2.0 * rx * 0.84 * i / cols)
        xn = x / (rx * 0.84)
        z_top = rz * math.sqrt(max(0.0, 1.0 - (x / rx) ** 2))
        # The outer-right corner drops lower, creating a clear angry squint.
        z_bottom = 0.12 + 0.15 * (1.0 - xn * xn) - 0.12 * xn
        z_bottom = min(z_bottom, z_top - 0.018)
        for j in range(rows + 1):
            v = j / rows
            z = z_bottom + (z_top - z_bottom) * v
            inside = max(0.015, 1.0 - (x / rx) ** 2 - (z / rz) ** 2)
            y = -ry * math.sqrt(inside) - 0.10 - 0.035 * (1.0 - v)
            verts.append((x, y, z))
        edge_inside = max(0.015, 1.0 - (x / rx) ** 2 - (z_bottom / rz) ** 2)
        lower_edge.append((x, -ry * math.sqrt(edge_inside) - 0.16, z_bottom))
    for i in range(cols):
        for j in range(rows):
            a = i * (rows + 1) + j
            b = (i + 1) * (rows + 1) + j
            faces.append((a, b, b + 1, a + 1))
    mesh = bpy.data.meshes.new("UpperEyelidMesh")
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new("UpperEyelid", mesh)
    bpy.context.collection.objects.link(obj)
    obj.parent = parent
    obj.data.materials.append(LID)
    smooth(obj)
    solid = obj.modifiers.new("LidThickness", "SOLIDIFY")
    solid.thickness = 0.045
    solid.offset = 0.0
    bevel = obj.modifiers.new("LidSoftEdge", "BEVEL")
    bevel.width = 0.018
    bevel.segments = 4
    main_parts.append(obj)
    edge = pipe_curve("UpperLidEdge", lower_edge, LID, parent, bevel=0.034)
    return obj, edge

# The supplied reference has a fully open eye with no eyebrow/eyelid crossing it.
eye_parts.append(eye_socket)

# Jagged grin with uneven fangs, a deeper dark cavity and a visible pink tongue.
mouth = empty("Mouth", pivot, loc=(0, -1.00, -0.42))
mouth_parts = [sphere("MouthCavity", 0.45, (0, 0, 0), MOUTH, mouth,
                      scale=(1.65, 0.22, 0.62), seg=64)]
mouth_lip = torus("MouthLip", 0.45, 0.035, (0, -1.075, -0.42), PURPLE_DARK, pivot,
                  rot=(math.pi / 2, 0, 0))
mouth_lip.scale = (1.58, 0.68, 0.88)
# Four prominent fangs, matching the sparse, large teeth in the reference.
for i, (x, h) in enumerate(((-0.27, 0.24), (0.27, 0.24), (-0.50, 0.16), (0.50, 0.16))):
    mouth_parts.append(cone(f"ToothU{i}", 0.10, 0.0, h,
                            (x, -0.10, (0.18 if abs(x) < 0.4 else 0.13) - h / 2), TEETH, mouth,
                            rot=(math.pi, 0, 0)))
for i, (x, h) in enumerate(((-0.27, 0.18), (0.27, 0.18))):
    mouth_parts.append(cone(f"ToothL{i}", 0.085, 0.0, h,
                            (x, -0.10, -0.18 + h / 2), TEETH, mouth))
mouth_parts.append(sphere("Tongue", 0.17, (0, -0.14, -0.16), TONGUE,
                          scale=(1.90, 0.38, 0.42), seg=40))

# Metallic pink collar with steel edge rails and larger brass fasteners.
body_band("LowerPinkBand", pivot, -1.00, -0.58, COLLAR)
collar_radius = shell_radius_at(-0.81) + 0.13
torus("CollarUpperSteelTrim", collar_radius, 0.032, (0, 0, -0.59), METAL, pivot)
torus("CollarLowerSteelTrim", collar_radius, 0.032, (0, 0, -0.99), METAL, pivot)
for i in range(14):
    a = i * (2 * math.pi / 14)
    z = -0.79 + 0.055 * math.cos(a)
    r = shell_radius_at(-0.81) + 0.185
    sphere(f"BandRivet{i}", 0.054, (r * math.cos(a), r * 0.92 * math.sin(a), z),
           COPPER, pivot, scale=(1.0, 0.72, 1.0), seg=24)

# Heavier industrial side canisters: broader gunmetal collars, larger flame plates and brass fittings.
PIPE_DARK = mat("PipeDark", (0.16, 0.075, 0.032), rough=0.27, metal=0.78, coat=0.28)
for side, sx in (("L", -1), ("R", 1)):
    tx, ty = 1.40 * sx, 0.08
    cylinder(f"Tank{side}", 0.375, 1.16, (tx, ty, 0.015), TANK, torso)
    # Rounded enamel shoulders soften the hard, box-like ends of the canisters.
    sphere(f"TankShoulderTop{side}", 0.37, (tx, ty, 0.52), TANK, torso, scale=(1.0, 1.0, 0.45))
    sphere(f"TankShoulderBot{side}", 0.37, (tx, ty, -0.52), TANK, torso, scale=(1.0, 1.0, 0.45))
    cylinder(f"TankCapTop{side}", 0.430, 0.18, (tx, ty, 0.625), METAL, torso)
    cylinder(f"TankCapBot{side}", 0.430, 0.18, (tx, ty, -0.595), METAL, torso)
    torus(f"TankBand1{side}", 0.397, 0.058, (tx, ty, 0.275), METAL, torso)
    torus(f"TankBand2{side}", 0.397, 0.058, (tx, ty, -0.245), METAL, torso)
    torus(f"TankGoldRing{side}", 0.385, 0.036, (tx, ty, 0.515), COPPER, torso)
    torus(f"TankGoldRingLow{side}", 0.385, 0.030, (tx, ty, -0.485), COPPER, torso)
    cylinder(f"TankNozzle{side}", 0.090, 0.14, (tx, ty, 0.765), METAL, torso)
    sphere(f"Valve{side}", 0.060, (tx, ty, 0.875), COPPER, torso, seg=20)
    cylinder(f"Connector{side}", 0.085, 0.29, (1.12 * sx, ty - 0.005, 0.185),
             COPPER, torso, rot=(0, math.pi / 2, 0))
    cylinder(f"Badge{side}", 0.285, 0.050, (tx, ty - 0.385, 0.015),
             BADGE_YELLOW, torso, rot=(math.pi / 2, 0, 0))
    torus(f"BadgeRim{side}", 0.285, 0.026, (tx, ty - 0.435, 0.015), METAL, torso,
          rot=(math.pi / 2, 0, 0))
    flame_icon(f"FlameIcon{side}", tx, ty - 0.425, 0.015, torso)
    for j, (bx, bz) in enumerate(((-0.285, 0.235), (0.285, 0.235),
                                  (-0.285, -0.235), (0.285, -0.235))):
        sphere(f"TankBolt{side}{j}", 0.038, (tx + bx, ty - 0.420, bz),
               COPPER, torso, scale=(1, 0.35, 1), seg=16)
    pipe_curve(f"TankPipe{side}", (
        (tx, ty, 0.74), (tx, ty, 0.91), (tx - 0.14 * sx, ty, 1.05),
        (tx - 0.31 * sx, ty, 1.05), (tx - 0.40 * sx, ty, 0.85)
    ), PIPE_DARK, torso, bevel=0.067)
    torus(f"PipeCollar{side}", 0.092, 0.027, (tx, ty, 0.86), COPPER, torso,
          rot=(math.pi / 2, 0, 0))
    torus(f"PipeGoldBand{side}", 0.092, 0.025, (tx - 0.28 * sx, ty, 1.045), BADGE_YELLOW, torso,
          rot=(math.pi / 2, 0, 0))

# Open-bottom transparent glass hemisphere: the head stays crisp and visible inside the bubble.
def make_dome_hemisphere(name, parent, center_z, radii, material):
    rings, sides = 28, 96
    verts = [(0.0, 0.0, center_z + radii[2])]
    faces = []
    for ri in range(1, rings + 1):
        theta = (math.pi * 0.5) * ri / rings
        radial = math.sin(theta)
        z = center_z + radii[2] * math.cos(theta)
        for j in range(sides):
            phi = 2.0 * math.pi * j / sides
            verts.append((radii[0] * radial * math.cos(phi),
                          radii[1] * radial * math.sin(phi), z))
    for j in range(sides):
        faces.append((0, 1 + j, 1 + (j + 1) % sides))
    for ri in range(rings - 1):
        start = 1 + ri * sides
        nxt = start + sides
        for j in range(sides):
            a = start + j
            b = start + (j + 1) % sides
            faces.append((a, b, nxt + (j + 1) % sides, nxt + j))
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
    obj.data.materials.append(material)
    smooth(obj)
    shell = obj.modifiers.new("GlassShellThickness", "SOLIDIFY")
    shell.thickness = 0.012
    shell.offset = 0.0
    main_parts.append(obj)
    return obj

DOME_CENTER_Z = -0.78
DOME_RADII = (1.62, 1.48, 2.48)
make_dome_hemisphere("GlassDome", torso, DOME_CENTER_Z, DOME_RADII, GLASS)
# A smooth blue arch traces only the upper glass silhouette.
dome_arch = []
for i in range(33):
    x = -DOME_RADII[0] + (2.0 * DOME_RADII[0] * i / 32.0)
    z = DOME_CENTER_Z + DOME_RADII[2] * math.sqrt(max(0.0, 1.0 - (x / DOME_RADII[0]) ** 2))
    dome_arch.append((x, -0.02, z))
pipe_curve("DomeBlueArch", dome_arch, DOME_RIM, torso, bevel=0.014)
# Reflections are placed mathematically on the ellipsoid surface; this prevents the old floating white ovals.
def add_dome_reflection(name, x, z, width, height):
    nx = x / DOME_RADII[0]
    nz = (z - DOME_CENTER_Z) / DOME_RADII[2]
    y = -DOME_RADII[1] * math.sqrt(max(0.02, 1.0 - nx * nx - nz * nz))
    normal = Vector((x / (DOME_RADII[0] ** 2),
                     y / (DOME_RADII[1] ** 2),
                     (z - DOME_CENTER_Z) / (DOME_RADII[2] ** 2))).normalized()
    return sphere(name, 1.0, (x, y, z), DOME_HIGHLIGHT, torso,
                  scale=(width, height, 0.014), rot=normal_rot(normal), seg=48)

add_dome_reflection("DomeReflectionL", -0.86, 1.18, 0.16, 0.36)
add_dome_reflection("DomeReflectionR", 0.84, 1.22, 0.15, 0.34)

# Eight short, organic arms: compact curled tubes with broad rounded ends.
ARM_LEN = [0.32, 0.28, 0.23, 0.18]
ARM_RAD = [0.19, 0.17, 0.15, 0.14, 0.16]
ARM_CURLS = [0.22, 0.35, -0.72]   # retained for the pose controls
ARM_TOTAL = sum(ARM_LEN)


def arm_curve_x(t):
    # Push outward first, then return slightly while the rounded tip curls upward.
    u = max(0.0, min(1.0, (t - 0.28) / 0.72))
    hook = u * u * (3.0 - 2.0 * u)
    return 1.50 * math.sin(math.pi * 0.50 * t) - 1.15 * hook


def arm_curve_y(t):
    u = max(0.0, min(1.0, (t - 0.28) / 0.72))
    hook = u * u * (3.0 - 2.0 * u)
    return 0.10 * hook


def arm_curve_z(t):
    u = max(0.0, min(1.0, (t - 0.28) / 0.72))
    hook = u * u * (3.0 - 2.0 * u)
    # A deeper drop followed by a clear upward return forms a broad C-hook.
    return -1.45 * t + 1.70 * hook


def arm_radius(t):
    # Thick plush arms with a blunt rounded end rather than a narrow tube tip.
    if t < 0.72:
        return 0.205 - 0.028 * (t / 0.72)
    u = min(1.0, max(0.0, (t - 0.72) / 0.28))
    return 0.177 * (0.92 + 0.08 * math.cos(u * math.pi * 0.5))


def arm_center(t, style="outer"):
    u = max(0.0, min(1.0, (t - 0.28) / 0.72))
    hook = u * u * (3.0 - 2.0 * u)
    if style == "outer":
        x = 1.72 * math.sin(math.pi * 0.50 * t) - 1.00 * hook
        z = -1.48 * t + 1.68 * hook
    elif style == "front":
        # Front arms swing outward before curling back toward the body.
        x = 1.12 * math.sin(math.pi * 0.50 * t) - 0.18 * hook
        z = -1.42 * t + 1.28 * hook
    else:
        x = 1.02 * math.sin(math.pi * 0.50 * t) - 0.46 * hook
        z = -1.36 * t + 1.18 * hook
    return 0.85 * Vector((x, arm_curve_y(t), z))


def make_arm_tube(name, parent, style):
    rings, sides = 56, 24
    verts, faces = [], []
    # Build each cross-section perpendicular to the actual curved centerline.
    # This prevents the tube from looking like a stack of rotated sausage segments.
    for ri in range(rings):
        t = ri / (rings - 1)
        center = arm_center(t, style)
        before = arm_center(max(0.0, t - 0.002), style)
        after = arm_center(min(1.0, t + 0.002), style)
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
    obj.data.materials.append(TENTACLE)
    smooth(obj)
    sub = obj.modifiers.new("OrganicArmSmooth", "SUBSURF")
    sub.levels = 2
    sub.render_levels = 2
    main_parts.append(obj)
    return obj


arms = []   # לכל זרוע: (שורש, [מפרקים 0..3], הטיית שורש)
tips = []
ARM_ANGLES = (205, 245, 295, 335, 155, 25, 75, 105)  # four forward arms plus four rear/side supports
for i in range(8):
    theta = math.radians(ARM_ANGLES[i])
    style = "outer" if i in (0, 3) else "front" if i in (1, 2) else "rear"
    tilt = 0.18 + rng.uniform(-0.06, 0.06)
    # Keep the local curl plane aligned with the hero camera: left arms curl left, right arms curl right.
    curl_heading = math.pi if math.cos(theta) < 0.0 else 0.0
    root = empty(f"ArmRoot{i}", torso, loc=(0.58 * math.cos(theta), 0.58 * math.sin(theta), -1.02), rot=(0, tilt, curl_heading))
    joints = [root]
    for k in range(1, 4):
        joints.append(empty(f"Joint{i}_{k}", joints[k - 1], loc=(0, 0, -ARM_LEN[k - 1]), rot=(0, ARM_CURLS[k - 1], 0)))
    make_arm_tube(f"ArmTube{i}", root, style)
    # Suckers face the camera-facing side of each radial arm, rather than all pointing
    # in one local direction. Their shallow inset keeps them from reading as beads.
    root_rotation = root.rotation_euler.to_matrix()
    front_local = root_rotation.inverted() @ Vector((0.0, -1.0, 0.0))
    for q, t in enumerate((0.50, 0.66, 0.81, 0.94)):
        center = arm_center(t, style)
        before = arm_center(max(0.0, t - 0.002), style)
        after = arm_center(min(1.0, t + 0.002), style)
        tangent = (after - before).normalized()
        cup_normal = front_local - tangent * front_local.dot(tangent)
        if cup_normal.length < 1e-5:
            cup_normal = Vector((0.0, 1.0, 0.0))
        cup_normal.normalize()
        rr = arm_radius(t)
        cup_pos = center + cup_normal * rr * 1.025
        inset_pos = cup_pos + cup_normal * rr * 0.018
        sphere(f"SuckerCup{i}_{q}", 1.0, tuple(inset_pos), SUCKER, root,
               scale=(rr * 0.56, rr * 0.56, rr * 0.085), rot=normal_rot(cup_normal), seg=24)
        torus(f"SuckerLip{i}_{q}", rr * 0.40, rr * 0.085, tuple(inset_pos), SPOT_DARK, root,
              rot=normal_rot(cup_normal))
        hole_pos = inset_pos - cup_normal * rr * 0.020
        sphere(f"SuckerHole{i}_{q}", 1.0, tuple(hole_pos), SUCKER, root,
               scale=(rr * 0.36, rr * 0.36, rr * 0.045), rot=normal_rot(cup_normal), seg=16)
    for q, t in enumerate((0.30, 0.52, 0.74)):
        center = arm_center(t, style)
        before = arm_center(max(0.0, t - 0.002), style)
        after = arm_center(min(1.0, t + 0.002), style)
        tangent = (after - before).normalized()
        spot_normal = front_local - tangent * front_local.dot(tangent)
        if spot_normal.length < 1e-5:
            spot_normal = Vector((0.0, 1.0, 0.0))
        spot_normal.normalize()
        rr = arm_radius(t)
        spot_pos = center + spot_normal * rr * 1.018
        sphere(f"ArmSpot{i}_{q}", 1.0, tuple(spot_pos), SPOT, root,
               scale=(rr * 0.58, rr * 0.42, rr * 0.060), rot=normal_rot(spot_normal), seg=20)
    tip_loc = tuple(arm_center(1.0, style))
    sphere(f"ArmRoundedTip{i}", 0.16, tip_loc, TENTACLE, root, seg=32)
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
cam_data.ortho_scale = max(3.0, HEIGHT * 1.15)
cam = bpy.data.objects.new("Cam", cam_data)
bpy.context.collection.objects.link(cam)
cam.location = (0, -14, HEIGHT * 0.5)
cam.rotation_euler = (math.radians(90), 0, 0)
scene.camera = cam
cam_data.clip_end = 100


def fit_character_camera():
    """Fit the complete tanks, bubble and curled arms in all hero angles."""
    original_angle = monster.rotation_euler[2]
    widths, heights, centers_x, centers_z = [], [], [], []
    for degrees in (0.0, 35.0, 60.0):
        monster.rotation_euler[2] = math.radians(degrees)
        bpy.context.view_layer.update()
        corners = [o.matrix_world @ Vector(c) for o in main_parts for c in o.bound_box]
        min_x = min(p.x for p in corners)
        max_x = max(p.x for p in corners)
        min_z = min(p.z for p in corners)
        max_z = max(p.z for p in corners)
        widths.append(max_x - min_x)
        heights.append(max_z - min_z)
        centers_x.append((min_x + max_x) * 0.5)
        centers_z.append((min_z + max_z) * 0.5)
    monster.rotation_euler[2] = original_angle
    bpy.context.view_layer.update()
    # Leave breathing room: the previous 0.92 scale clipped both the dome and arm tips.
    cam_data.ortho_scale = max(max(widths), max(heights)) * 1.04
    cam.location.x = sum(centers_x) / len(centers_x)
    cam.location.z = sum(centers_z) / len(centers_z)
    bpy.context.view_layer.update()


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
add_light("TentacleFill", "AREA", 210, (0, -4.5, 0.55), target=(0, -0.1, 0.35), size=4.5, color=(0.78, 0.48, 1.0))
add_light("Rim", "AREA", 420, (-3.5, 5, 4.5), target=(0, 0, HEIGHT * 0.5), size=5, color=(0.95, 0.3, 1.0))

world = bpy.data.worlds.new("World")
scene.world = world
world.use_nodes = True
bg = world.node_tree.nodes["Background"]
bg.inputs[0].default_value = (0.25, 0.3, 0.5, 1.0)
bg.inputs[1].default_value = 0.4

# Eevee avoids a reproducible native Cycles BVH crash in the headless GitHub runner.
scene.render.engine = "BLENDER_EEVEE_NEXT"
scene.eevee.taa_render_samples = max(4, SAMPLES)
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
fit_character_camera()
for name, deg in (("hero_34", 35.0), ("hero_front", 0.0)):
    monster.rotation_euler[2] = math.radians(deg)
    render(os.path.join(OUT, name + ".png"), 768)

# Keep run-specific hero copies on the renders branch so a later main-branch
# render cannot overwrite the images needed to inspect this work-branch result.
RUN_TAG = os.environ.get("GITHUB_SHA", "local")[:8]
for name in ("hero_front", "hero_34"):
    source = os.path.join(OUT, name + ".png")
    if os.path.exists(source):
        shutil.copy2(source, os.path.join(OUT, f"{name}_{RUN_TAG}.png"))

for angle in ANGLES:
    monster.rotation_euler[2] = math.radians(ANGLE_DEG[angle])
    for clip, count, row, col0 in SHEET:
        for i in range(count):
            pose(clip, i, count)
            render(os.path.join(FRAMES_DIR, f"{angle}_{row}_{col0 + i}.png"), CELL)
    print("angle done", angle)