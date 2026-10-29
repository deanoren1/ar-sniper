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
    mad.inputs[1].default_value = 0.65
    mad.inputs[2].default_value = 0.035
    for key, value in (("Base Color", (0.55, 0.78, 1.0, 1.0)), ("Roughness", 0.04), ("Metallic", 0.0),
                       ("Emission Color", (0.35, 0.65, 1.0, 1.0)), ("Emission Strength", 0.12)):
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


PURPLE = mat("Purple", (0.40, 0.05, 0.72), rough=0.38, coat=0.5)
PURPLE_DARK = mat("PurpleDark", (0.30, 0.04, 0.55), rough=0.3, coat=0.6)
SPOT = mat("Spot", (0.95, 0.22, 0.65), rough=0.5)
EYE_WHITE = mat("EyeWhite", (1.0, 0.86, 0.58), rough=0.25, emit=(1.0, 0.8, 0.45), strength=0.25)
IRIS = mat("Iris", (1.0, 0.18, 0.0), rough=0.25, emit=(1.0, 0.2, 0.02), strength=0.35)
PUPIL = mat("Pupil", (0.0, 0.0, 0.0), rough=0.9)
SHINE = mat("Shine", (1.0, 1.0, 1.0), emit=(1.0, 1.0, 1.0), strength=6.0)
MOUTH = mat("Mouth", (0.06, 0.0, 0.08), rough=0.9)
TEETH = mat("Teeth", (1.0, 0.97, 0.88), rough=0.3)
METAL = mat("Metal", (0.10, 0.10, 0.13), rough=0.35, metal=0.9)
RING = mat("Ring", (0.9, 0.4, 0.65), rough=0.3, metal=0.8)
COPPER = mat("Copper", (0.75, 0.42, 0.14), rough=0.3, metal=1.0)
FLAME = mat("Flame", (1.0, 0.4, 0.03), emit=(1.0, 0.35, 0.03), strength=1.6)
ORB = mat("Orb", (0.6, 0.15, 1.0), emit=(0.6, 0.15, 1.0), strength=1.3)
ORB_CORE = mat("OrbCore", (1, 1, 1), emit=(0.9, 0.7, 1.0), strength=3.5)
STAR = mat("Star", (1.0, 0.85, 0.1), emit=(1.0, 0.8, 0.1), strength=5.0)
XMARK = mat("XMark", (0.15, 0.02, 0.1), rough=0.5)
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

# גוף
BODY_R = 0.9
BODY_SCALE = (1.0, 0.95, 0.92)
sphere("Body", BODY_R, (0, 0, 0), PURPLE, pivot, scale=BODY_SCALE, seg=48)

# קוצים וכתמים על הגוף
for i in range(11):
    az = math.radians(i * (360 / 11) + 8)
    pol = math.radians(rng.uniform(22, 58))
    n = Vector((math.sin(pol) * math.cos(az), math.sin(pol) * math.sin(az), math.cos(pol)))
    p = Vector((n.x * BODY_R * BODY_SCALE[0], n.y * BODY_R * BODY_SCALE[1], n.z * BODY_R * BODY_SCALE[2])) * 0.97
    h = rng.uniform(0.16, 0.24)
    cone(f"Spike{i}", 0.085, 0.0, h, p + n * (h / 2), PURPLE_DARK, pivot, rot=normal_rot(n))

for i in range(30):
    az = rng.uniform(0, 2 * math.pi)
    pol = math.acos(rng.uniform(-0.6, 0.95))
    n = Vector((math.sin(pol) * math.cos(az), math.sin(pol) * math.sin(az), math.cos(pol)))
    if n.y < -0.45 and abs(n.x) < 0.75 and -0.45 < n.z < 0.7:
        continue  # שם העין והפה
    p = Vector((n.x * BODY_R * BODY_SCALE[0], n.y * BODY_R * BODY_SCALE[1], n.z * BODY_R * BODY_SCALE[2])) * 0.985
    r = rng.uniform(0.06, 0.12)
    sphere(f"Spot{i}", r, p, SPOT, pivot, scale=(1, 1, 0.3), rot=normal_rot(n), seg=16)

# עין
EYE_Y, EYE_Z = -0.62, 0.22
eye = empty("Eye", pivot, loc=(0, EYE_Y, EYE_Z))
eye_parts = [
    sphere("EyeWhite", 0.46, (0, 0, 0), EYE_WHITE, eye, scale=(1, 0.55, 1), seg=40),
    sphere("Iris", 0.34, (0, -0.23, 0), IRIS, eye, scale=(1, 0.3, 1), seg=40),
    sphere("Pupil", 0.23, (0, -0.29, 0), PUPIL, eye, scale=(1, 0.3, 1), seg=28),
    sphere("Shine", 0.065, (0.1, -0.325, 0.11), SHINE, eye, scale=(1, 0.4, 1), seg=16),
]
look_parts = eye_parts[1:]  # הקשתית, האישון והברק זזים יחד כשמסתכלים

# פה עם שיניים
mouth = empty("Mouth", pivot, loc=(0, -0.70, -0.40))
mouth_parts = [sphere("MouthCavity", 0.3, (0, 0, 0), MOUTH, mouth, scale=(1.25, 0.3, 0.5), seg=28)]
for i, x in enumerate((-0.17, -0.06, 0.06, 0.17)):
    mouth_parts.append(cone(f"Tooth{i}", 0.04, 0.0, 0.1, (x, -0.09, 0.07), TEETH, mouth, rot=(math.pi, 0, 0)))

# כיפת זכוכית עם טבעת נחושת-ורודה
DOME_R, DOME_Z, DOME_CUT = 1.05, 0.10, -0.75
bpy.ops.mesh.primitive_uv_sphere_add(segments=56, ring_count=28, radius=DOME_R, location=(0, 0, DOME_Z))
dome = bpy.context.active_object
bm = bmesh.new()
bm.from_mesh(dome.data)
bmesh.ops.delete(bm, geom=[v for v in bm.verts if v.co.z < DOME_CUT], context="VERTS")
bm.to_mesh(dome.data)
bm.free()
fin(dome, GLASS, torso, "Dome")
try:
    dome.visible_shadow = False
except Exception:
    pass
rim_r = math.sqrt(DOME_R ** 2 - DOME_CUT ** 2)
rim_z = DOME_Z + DOME_CUT
torus("DomeRing", rim_r, 0.09, (0, 0, rim_z), RING, torso)
for i in range(22):
    a = i * (2 * math.pi / 22)
    sphere(f"Rivet{i}", 0.032, (rim_r * math.cos(a), rim_r * math.sin(a), rim_z + 0.09), COPPER, torso, seg=12)

# שני מיכלי אש בצדדים ומעט מאחור (כדי שמהצד עדיין רואים את הפנים), עם צינור לכיפה
for side, sx in (("L", -1), ("R", 1)):
    tx, ty = 1.2 * sx, 0.45
    cylinder(f"Tank{side}", 0.3, 1.1, (tx, ty, 0.1), PURPLE_DARK, torso)
    cylinder(f"TankCapTop{side}", 0.335, 0.12, (tx, ty, 0.69), METAL, torso)
    cylinder(f"TankCapBot{side}", 0.335, 0.12, (tx, ty, -0.49), METAL, torso)
    torus(f"TankBand1{side}", 0.305, 0.035, (tx, ty, 0.35), METAL, torso)
    torus(f"TankBand2{side}", 0.305, 0.035, (tx, ty, -0.15), METAL, torso)
    cylinder(f"TankNozzle{side}", 0.08, 0.16, (tx, ty, 0.82), METAL, torso)
    sphere(f"Valve{side}", 0.07, (tx, ty, 0.94), COPPER, torso, seg=16)
    cylinder(f"Connector{side}", 0.07, 0.3, (0.95 * sx, ty - 0.02, 0.4), COPPER, torso, rot=(0, math.pi / 2, 0))
    cylinder(f"Badge{side}", 0.17, 0.03, (tx, ty - 0.3, 0.1), METAL, torso, rot=(math.pi / 2, 0, 0))
    sphere(f"FlameBase{side}", 0.075, (tx, ty - 0.33, 0.04), FLAME, torso, scale=(1, 0.3, 1), seg=16)
    cone(f"FlameTip{side}", 0.075, 0.0, 0.2, (tx, ty - 0.33, 0.17), FLAME, torso, scale=(1, 0.3, 1))

# שמונה זרועות: שרשרת של 4 מקטעים, כל מפרק מסתובב בקוד
ARM_LEN = [0.36, 0.32, 0.28, 0.24]
ARM_RAD = [0.24, 0.20, 0.17, 0.14, 0.11]
ARM_CURLS = [0.5, 0.5, 0.3]   # כמה כל מפרק מיישר את הזרוע כלפי מטה במנוחה
arms = []   # לכל זרוע: (שורש, [מפרקים 0..3], הטיית שורש)
tips = []
for i in range(8):
    theta = math.radians(i * 45 + 22.5)
    tilt = 1.0 + rng.uniform(-0.1, 0.1)
    root = empty(f"ArmRoot{i}", torso, loc=(0.5 * math.cos(theta), 0.5 * math.sin(theta), -0.72), rot=(0, -tilt, theta))
    joints = [root]
    for k in range(1, 4):
        joints.append(empty(f"Joint{i}_{k}", joints[k - 1], loc=(0, 0, -ARM_LEN[k - 1]), rot=(0, ARM_CURLS[k - 1], 0)))
    for k in range(4):
        cone(f"Seg{i}_{k}", ARM_RAD[k + 1], ARM_RAD[k], ARM_LEN[k], (0, 0, -ARM_LEN[k] / 2), PURPLE, joints[k])
        sphere(f"Knee{i}_{k}", ARM_RAD[k], (0, 0, 0), PURPLE, joints[k], seg=20)
    tips.append(sphere(f"Tip{i}", ARM_RAD[4], (0, 0, -ARM_LEN[3]), PURPLE, joints[3], seg=20))
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
    r = rng.uniform(0.28, 0.48)
    puddle.append(sphere(f"Puddle{i}", r, (d * math.cos(a), d * math.sin(a) * 0.6, 0.04), PURPLE if i % 2 else PURPLE_DARK,
                         monster, scale=(1, 1, 0.2), seg=16, main=False))
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


sun = add_light("Sun", "SUN", 2.4, (0, 0, 8), rot=(math.radians(50), 0, math.radians(-35)))
sun.data.angle = 0.18
add_light("Fill", "AREA", 280, (3.5, -6, 3.5), target=(0, 0, HEIGHT * 0.5), size=6, color=(1.0, 0.95, 0.9))
add_light("Rim", "AREA", 250, (-3.5, 5, 4.5), target=(0, 0, HEIGHT * 0.5), size=5, color=(0.75, 0.6, 1.0))

world = bpy.data.worlds.new("World")
scene.world = world
world.use_nodes = True
bg = world.node_tree.nodes["Background"]
bg.inputs[0].default_value = (0.55, 0.6, 0.78, 1.0)
bg.inputs[1].default_value = 0.5

scene.render.engine = "CYCLES"
scene.cycles.device = "CPU"
scene.cycles.samples = SAMPLES
scene.cycles.use_denoising = True
try:
    scene.cycles.denoiser = "OPENIMAGEDENOISE"
except Exception:
    pass
scene.cycles.filter_width = 1.4
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
        root.rotation_euler.y = -tilt + 0.5 * amp_y * SIN(ph0) + extra_y * 0.5
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
            tint(0.3)
            arms_wave(0.0, 0.0, 0.0, extra_y=-0.25)
        else:
            torso.rotation_euler.x = -0.12
            pivot.scale = (1.04, 1.04, 0.97)
            eye.scale.z = 0.7
            tint(0.12)
            arms_wave(0.0, 0.0, 0.0, extra_y=0.05)
        show(stars, True)
    elif clip == "Death":
        show(eye_parts, False)
        show(x_marks, True)
        mouth.scale = (1.0, 1.0, 1.4)
        if i == 0:
            torso.rotation_euler.x = -0.4
            torso.location.z -= 0.1
            tint(0.25)
            arms_wave(0.0, 0.0, 0.0, extra_y=-0.45)
        elif i == 1:
            torso.rotation_euler.x = -1.15
            torso.location.z -= 0.55
            torso.location.y += 0.35
            tint(0.3)
            arms_wave(0.0, 0.0, 0.0, extra_y=-0.85)
        elif i == 2:
            torso.rotation_euler.x = -1.15
            torso.location.z -= 0.55
            torso.location.y += 0.35
            pivot.scale = (1.3, 1.3, 1.3)
            tint(0.9, (1.0, 0.75, 1.0))
            arms_wave(0.0, 0.0, 0.0, extra_y=-0.85)
            show([o for o in main_parts if o.name.startswith(("Dome", "Rivet"))], False)
            for f, d, dist in frags:
                f.hide_render = False
                f.location = d * dist
            show(shards, True)
        else:
            show(main_parts, False)
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
