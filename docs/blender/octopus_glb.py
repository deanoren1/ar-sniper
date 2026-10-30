"""
התמנון מ-Meshy (GLB עם שלד): טוען, מוסיף כיפת זכוכית, מזיז את העצמות בקוד ומרנדר את כל הפריימים.
הרצה (מהשורש של הריפו):  blender -b -P docs/blender/octopus_glb.py -- <outdir> [angles=side,front,back] [cell=384] [samples=32]
הפלט: <outdir>/frames/<angle>_<row>_<col>.png ועוד hero_*.png לבדיקה. הגיליון: docs/blender/make_sheet.py.

מערכת הצירים אחרי הייבוא (Blender, Z למעלה): הדמות פונה אל -Y, הרצפה ב-Z=0 אחרי הזזה.
כל תנועת עצם נעשית בצירי העולם (rot), כדי שלא יהיה תלוי בכיוון הצירים הפנימי של העצמות.
"""
import math
import os
import sys

import bmesh
import bpy
from mathutils import Euler, Matrix, Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else "out"
ANGLES = (argv[1] if len(argv) > 1 else "side").split(",")
CELL = int(argv[2]) if len(argv) > 2 else 384
SAMPLES = int(argv[3]) if len(argv) > 3 else 32
FRAMES_DIR = os.path.join(OUT, "frames")
os.makedirs(FRAMES_DIR, exist_ok=True)
GLB = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models", "octopus_rigged.glb")

ANGLE_DEG = {"side": 60.0, "front": 0.0, "back": 180.0}
Z = Vector((0, 0, 1))
X = Vector((1, 0, 0))

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
bpy.ops.import_scene.gltf(filepath=GLB)
arm = next(o for o in bpy.data.objects if o.type == "ARMATURE")
mesh = max((o for o in bpy.data.objects if o.type == "MESH"), key=lambda o: len(o.data.vertices))
for o in list(bpy.data.objects):
    if o.type == "MESH" and o is not mesh:
        bpy.data.objects.remove(o)  # צורות העצמות שהייבוא מוסיף
print("bones", len(arm.pose.bones), "mesh", mesh.name, "verts", len(mesh.data.vertices))

monster = bpy.data.objects.new("Monster", None)
bpy.context.collection.objects.link(monster)
arm.parent = monster
bpy.context.view_layer.update()

# הרצפה: הנקודה הכי נמוכה של הדגם במנוחה
min_z = min((mesh.matrix_world @ Vector(c)).z for c in mesh.bound_box)
monster.location.z = -min_z
bpy.context.view_layer.update()
top_z = max((mesh.matrix_world @ Vector(c)).z for c in mesh.bound_box)
print("ground shift", -min_z, "top", top_z)

# ------------------------------------------------------------------ חומר: צבע פגיעה, זוהר, דהייה
mat = mesh.data.materials[0]
nt = mat.node_tree
bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
out_node = next(n for n in nt.nodes if n.type == "OUTPUT_MATERIAL")
tint_mix = nt.nodes.new("ShaderNodeMixRGB")
tint_mix.blend_type = "MIX"
links = bsdf.inputs["Base Color"].links
if links:
    nt.links.new(links[0].from_socket, tint_mix.inputs[1])
else:
    tint_mix.inputs[1].default_value = bsdf.inputs["Base Color"].default_value
hue_sat = nt.nodes.new("ShaderNodeHueSaturation")
hue_sat.inputs["Saturation"].default_value = 1.25  # צבעים עמוקים יותר: סגול וורוד כמו בדגם המקורי
hue_sat.inputs["Value"].default_value = 0.86
nt.links.new(tint_mix.outputs[0], hue_sat.inputs["Color"])
# העין (קרם/כתום/צהוב) נשארת בצבע המקורי: מסכה לפי גוון (hue) של הטקסטורה, בלי הגברת הרוויה
sep = nt.nodes.new("ShaderNodeSeparateColor")
sep.mode = "HSV"
nt.links.new(tint_mix.outputs[0], sep.inputs[0])
gt = nt.nodes.new("ShaderNodeMath")
gt.operation = "GREATER_THAN"
gt.inputs[1].default_value = 0.02
nt.links.new(sep.outputs[0], gt.inputs[0])
lt = nt.nodes.new("ShaderNodeMath")
lt.operation = "LESS_THAN"
lt.inputs[1].default_value = 0.2
nt.links.new(sep.outputs[0], lt.inputs[0])
eye_mask = nt.nodes.new("ShaderNodeMath")
eye_mask.operation = "MULTIPLY"
nt.links.new(gt.outputs[0], eye_mask.inputs[0])
nt.links.new(lt.outputs[0], eye_mask.inputs[1])
eye_mix = nt.nodes.new("ShaderNodeMixRGB")
eye_mix.blend_type = "MIX"
nt.links.new(eye_mask.outputs[0], eye_mix.inputs[0])
nt.links.new(hue_sat.outputs[0], eye_mix.inputs[1])
nt.links.new(tint_mix.outputs[0], eye_mix.inputs[2])
nt.links.new(eye_mix.outputs[0], bsdf.inputs["Base Color"])
for key, value in (("Specular IOR Level", 0.3), ("Roughness", 0.42)):
    try:
        if not bsdf.inputs[key].links:
            bsdf.inputs[key].default_value = value
    except Exception:
        pass
tint_mix.inputs[0].default_value = 0.0
fade_tr = nt.nodes.new("ShaderNodeBsdfTransparent")
fade_mix = nt.nodes.new("ShaderNodeMixShader")
nt.links.new(fade_tr.outputs[0], fade_mix.inputs[1])
nt.links.new(bsdf.outputs[0], fade_mix.inputs[2])
nt.links.new(fade_mix.outputs[0], out_node.inputs["Surface"])
fade_mix.inputs[0].default_value = 1.0


def set_tint(amount, color=(1.0, 0.12, 0.08)):
    tint_mix.inputs[0].default_value = amount
    tint_mix.inputs[2].default_value = (*color, 1.0)
    bsdf.inputs["Emission Color"].default_value = (*color, 1.0)
    bsdf.inputs["Emission Strength"].default_value = amount * 1.3


def set_fade(f):
    fade_mix.inputs[0].default_value = 1.0 - f


# ------------------------------------------------------------------ כיפת זכוכית
def glass_mat():
    m = bpy.data.materials.new("Glass")
    m.use_nodes = True
    t = m.node_tree
    t.nodes.clear()
    o = t.nodes.new("ShaderNodeOutputMaterial")
    mix = t.nodes.new("ShaderNodeMixShader")
    clear = t.nodes.new("ShaderNodeBsdfTransparent")
    glossy = t.nodes.new("ShaderNodeBsdfPrincipled")
    lw = t.nodes.new("ShaderNodeLayerWeight")
    lw.inputs["Blend"].default_value = 0.3
    mad = t.nodes.new("ShaderNodeMath")
    mad.operation = "MULTIPLY_ADD"
    mad.inputs[1].default_value = 0.95
    mad.inputs[2].default_value = 0.004
    for key, value in (("Base Color", (0.45, 0.7, 1.0, 1.0)), ("Roughness", 0.02), ("Metallic", 0.0),
                       ("Emission Color", (0.5, 0.7, 1.0, 1.0)), ("Emission Strength", 0.0)):
        try:
            glossy.inputs[key].default_value = value
        except Exception:
            pass
    t.links.new(lw.outputs["Fresnel"], mad.inputs[0])
    t.links.new(mad.outputs[0], mix.inputs[0])
    t.links.new(clear.outputs[0], mix.inputs[1])
    t.links.new(glossy.outputs[0], mix.inputs[2])
    t.links.new(mix.outputs[0], o.inputs[0])
    return m


def metal_mat(name, color, rough=0.4, metal=0.9):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1.0)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    return m


DOME_CENTER = Vector((0.0, 0.04, 1.114))
DOME_SCALE = Vector((0.72, 0.78, 0.765))
DOME_CUT = -0.41  # חיתוך תחתון ביחידות כדור (-1..1)
DOME_TILT = 0.427  # הטיה קדימה (רדיאנים): הטבעת יושבת על האבזם המשופע

bm = bmesh.new()
bmesh.ops.create_uvsphere(bm, u_segments=56, v_segments=28, radius=1.0)
def bulb(z):
    # החצי התחתון מצטמצם לקראת הטבעת, כך שהכיפה רחבה סביב הראש והצינורות וצרה בבסיס
    return 1.0 - 0.10 * ((-z) ** 1.5) if z < 0 else 1.0


for v in bm.verts:
    f = bulb(v.co.z)
    v.co.x *= f
    v.co.y *= f
bmesh.ops.delete(bm, geom=[v for v in bm.verts if v.co.z < DOME_CUT], context="VERTS")
dome_mesh = bpy.data.meshes.new("Dome")
bm.to_mesh(dome_mesh)
bm.free()
dome = bpy.data.objects.new("Dome", dome_mesh)
bpy.context.collection.objects.link(dome)
ring_c = DOME_CENTER + Vector((0, 0, DOME_CUT * DOME_SCALE.z))
dome.location = ring_c + Matrix.Rotation(DOME_TILT, 3, "X") @ (DOME_CENTER - ring_c)
dome.rotation_euler = (DOME_TILT, 0, 0)
dome.scale = DOME_SCALE
dome.data.materials.append(glass_mat())
for p in dome.data.polygons:
    p.use_smooth = True
dome.visible_shadow = False
sol = dome.modifiers.new('Shell', 'SOLIDIFY')
sol.thickness = 0.03
sol.offset = 1.0

ring_r = math.sqrt(1 - DOME_CUT ** 2) * bulb(DOME_CUT)
bpy.ops.mesh.primitive_torus_add(major_radius=1.0, minor_radius=0.034, major_segments=64, minor_segments=12)
ring = bpy.context.active_object
ring.name = "DomeRing"
ring.location = ring_c
ring.rotation_euler = (DOME_TILT, 0, 0)
ring.scale = Vector((DOME_SCALE.x * ring_r, DOME_SCALE.y * ring_r, 1.0))
ring.data.materials.append(metal_mat("RingMetal", (0.42, 0.22, 0.10), rough=0.45))
ring.visible_shadow = False

# הכיפה והטבעת זזות עם הראש (עצם הגוף)
bpy.context.view_layer.update()
head_pb = arm.pose.bones["Bone_001"]
parent_m = arm.matrix_world @ head_pb.matrix @ Matrix.Translation((0, head_pb.bone.length, 0))
bpy.data.objects.remove(ring)  # בלי טבעת: היא הסתירה את הפה
for o in (dome,):
    world = o.matrix_world.copy()
    o.parent = arm
    o.parent_type = "BONE"
    o.parent_bone = "Bone_001"
    o.matrix_parent_inverse = parent_m.inverted()
    o.matrix_world = world

# ------------------------------------------------------------------ סצנה: מצלמה, אורות, ג'ין
bpy.ops.mesh.primitive_plane_add(size=30, location=(0, 0, 0))
ground = bpy.context.active_object
ground.name = "Ground"
ground.is_shadow_catcher = True

HEIGHT = top_z + 0.3
cam_data = bpy.data.cameras.new("Cam")
cam_data.type = "ORTHO"
cam_data.ortho_scale = max(3.2, HEIGHT * 1.55)
cam = bpy.data.objects.new("Cam", cam_data)
bpy.context.collection.objects.link(cam)
cam.location = (0, -14, HEIGHT * 0.56)
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


sun = add_light("Sun", "SUN", 2.6, (0, 0, 8), rot=(math.radians(58), 0, math.radians(-40)), color=(1.0, 0.94, 0.86))
sun.data.angle = 0.12
add_light("Key", "AREA", 190, (-3.0, -5.0, 5.5), target=(0, 0, HEIGHT * 0.5), size=2.5, color=(1.0, 0.96, 0.9))
add_light("Fill", "AREA", 90, (4.5, -6, 2.0), target=(0, 0, HEIGHT * 0.5), size=6, color=(0.7, 0.8, 1.0))
add_light("Rim", "AREA", 520, (-3.5, 5, 4.5), target=(0, 0, HEIGHT * 0.5), size=4, color=(0.95, 0.45, 1.0))
add_light("Top", "AREA", 90, (2.0, -2.0, 7.0), target=(0, 0, HEIGHT * 0.5), size=2.0, color=(0.85, 0.9, 1.0))

world = bpy.data.worlds.new("World")
scene.world = world
world.use_nodes = True
bg = world.node_tree.nodes["Background"]
bg.inputs[0].default_value = (0.35, 0.38, 0.55, 1.0)
bg.inputs[1].default_value = 0.45

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

# ------------------------------------------------------------------ שלד: תנועה בצירי העולם
pbones = arm.pose.bones
rest_head = {b.name: arm.matrix_world @ b.head_local for b in arm.data.bones}
BODY_C = rest_head["Bone_001"]
ROOT = "Bone_000"
ARMS = {  # שרשראות העצמות של הזרועות, מהבסיס לקצה
    "BR": [10, 9, 8, 7, 6],
    "BL": [15, 14, 13, 12, 11],
    "FR": [20, 19, 18, 17, 16],
    "FL": [25, 24, 23, 22, 21],
    "FC": [29, 28, 27, 26],
}
ARM_ORDER = ["FC", "FR", "BR", "BL", "FL"]
ARM_NAMES = {k: [f"Bone_{n:03d}" for n in v] for k, v in ARMS.items()}
RADIAL = {}
for key, names in ARM_NAMES.items():
    h = rest_head[names[-1]] - BODY_C
    h.z = 0
    RADIAL[key] = h.normalized() if h.length > 1e-4 else Vector((0, -1, 0))
GROUND = Vector((0, 0, 0))
BASE_LOC = monster.location.copy()


def lowest():
    return min((arm.matrix_world @ pb.tail).z for pb in pbones)


REST_LOW = lowest()


def reset():
    monster.location = BASE_LOC.copy()
    monster.rotation_euler = (0, 0, 0)
    monster.scale = (1, 1, 1)
    for pb in pbones:
        pb.matrix_basis = Matrix()
    bpy.context.view_layer.update()
    set_tint(0.0)
    set_fade(0.0)


def rot(name, axis, ang, pivot=None):
    pb = pbones[name]
    M = arm.matrix_world @ pb.matrix
    p = pivot.copy() if pivot is not None else M.translation.copy()
    R = Matrix.Translation(p) @ Matrix.Rotation(ang, 4, axis) @ Matrix.Translation(-p)
    pb.matrix = arm.matrix_world.inverted() @ (R @ M)
    bpy.context.view_layer.update()


def move(name, vec):
    pb = pbones[name]
    M = arm.matrix_world @ pb.matrix
    pb.matrix = arm.matrix_world.inverted() @ (Matrix.Translation(Vector(vec)) @ M)
    bpy.context.view_layer.update()


def lean(angle, pivot=GROUND):
    """הטיה קדימה (חיובי) או אחורה, סביב הרצפה"""
    rot(ROOT, X, angle, pivot)


def arms_pose(t, bend, sweep, lag=0.8, droop=0.0, phase_mul=1.0):
    """t: שלב במחזור (0..1); bend/sweep: משרעת בראדיאנים; droop: כיפוף קבוע (חיובי = למטה)"""
    for idx, key in enumerate(ARM_ORDER):
        r = RADIAL[key]
        s = Z.cross(r)
        phase = idx * 2.0 * math.pi / 5.0 * phase_mul
        for j, name in enumerate(ARM_NAMES[key]):
            w = math.sin(2 * math.pi * t - phase - j * lag)
            scale = 0.5 + 0.5 * j / max(1, len(ARM_NAMES[key]) - 1)
            a_sweep = sweep * w * scale
            a_bend = droop / len(ARM_NAMES[key]) + bend * math.cos(2 * math.pi * t - phase - j * lag) * scale
            if abs(a_sweep) > 1e-5:
                rot(name, Z, a_sweep)
            if abs(a_bend) > 1e-5:
                rot(name, s, a_bend)


def tanks(t, amp):
    for name, ph in (("Bone_031", 0.0), ("Bone_033", 1.7)):
        rot(name, X, amp * math.sin(4 * math.pi * t + ph))


def pose(clip, i, n):
    reset()
    _pose(clip, i, n)
    # בלי חדירה לרצפה: אם משהו ירד מתחת לגובה המנוחה, מרימים את הדמות
    low = lowest()
    if low < REST_LOW:
        monster.location.z += REST_LOW - low
    if clip == "Death" and i >= 1:
        monster.location.x += 0.75


def _pose(clip, i, n):
    if clip == "Idle":
        t = i / n
        move(ROOT, Vector((0, 0, 0.025 * math.sin(2 * math.pi * t))))
        lean(0.025 * math.sin(2 * math.pi * t + 0.6))
        arms_pose(t, 0.22, 0.16, lag=0.7)
        tanks(t, 0.02)
    elif clip == "Walk":
        t = i / n
        move(ROOT, Vector((0, 0, 0.07 * abs(math.sin(2 * math.pi * t)))))
        lean(0.10 + 0.03 * math.sin(4 * math.pi * t))
        arms_pose(t, 0.7, 0.4, lag=0.9)
        tanks(t, 0.05)
    elif clip == "Charge":
        k = (i + 1) / n
        move(ROOT, Vector((0, 0, -0.09 * k)))
        lean(-0.10 * k)
        arms_pose(0.0, 0.0, 0.0, droop=-1.3 * k)
        set_tint(0.30 * k, (1.0, 0.5, 0.1))
    elif clip == "Shoot":
        move(ROOT, Vector((0, 0.07 * (1.0 - i / n), 0)))
        lean([-0.20, 0.10, 0.0][i])
        arms_pose(0.0, 0.0, 0.0, droop=[-2.0, -1.2, -0.5][i])
        set_tint([0.4, 0.15, 0.0][i], (1.0, 0.7, 0.3))
    elif clip == "Hit":
        move(ROOT, Vector((0, [0.10, 0.04][i], 0)))
        lean([-0.30, -0.12][i])
        arms_pose(0.0, 0.0, 0.0, droop=[-1.0, -0.4][i])
        set_tint([0.60, 0.28][i])
    elif clip == "Death":
        if i == 0:
            lean(-0.45)
            arms_pose(0.0, 0.0, 0.0, droop=-1.6)
            set_tint(0.30)
        elif i == 1:
            lean(-1.35)
            arms_pose(0.0, 0.0, 0.0, droop=0.7)
            set_tint(0.40, (0.55, 0.05, 0.1))
        elif i == 2:
            lean(-1.35)
            arms_pose(0.0, 0.0, 0.0, droop=0.5)
            monster.scale = (1.08, 1.08, 1.08)
            set_tint(0.30, (1.0, 0.6, 0.3))
        else:
            lean(-1.35)
            arms_pose(0.0, 0.0, 0.0, droop=0.3)
            monster.scale = (0.5, 0.5, 0.5)
            set_tint(0.5, (1.0, 0.7, 0.4))
            set_fade(0.7)


SHEET = [("Idle", 6, 0, 0), ("Walk", 6, 1, 0), ("Charge", 3, 2, 0), ("Shoot", 3, 2, 3), ("Hit", 2, 3, 0), ("Death", 4, 3, 2)]


def render(path, res):
    scene.render.resolution_x = res
    scene.render.resolution_y = res
    scene.render.resolution_percentage = 100
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


pose("Idle", 0, 6)
for name, deg in (("hero_34", 60.0), ("hero_front", 0.0), ("hero_back", 180.0)):
    monster.rotation_euler[2] = math.radians(deg)
    render(os.path.join(OUT, name + ".png"), 768)

# בדיקת צירים: הליכה בפריים 1 ומוות בפריים 1, מבט צד
for name, clip, i, n in (("test_walk", "Walk", 1, 6), ("test_death", "Death", 1, 4), ("test_charge", "Charge", 2, 3)):
    pose(clip, i, n)
    monster.rotation_euler[2] = math.radians(60.0)
    render(os.path.join(OUT, name + ".png"), 512)

for angle in ANGLES:
    for clip, count, row, col0 in SHEET:
        for i in range(count):
            pose(clip, i, count)
            monster.rotation_euler[2] = math.radians(ANGLE_DEG[angle])
            render(os.path.join(FRAMES_DIR, f"{angle}_{row}_{col0 + i}.png"), CELL)
    print("angle done", angle)
