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
        orb_core.scale = (r * 0.38, r * 0.38, r * 0.38)
        for o in orb_parts:
            o.location = (0, -1.25 - 0.05 * k, -0.42)
    elif clip == "Shoot":
        recoil = (1.0, 0.55, 0.0)[i]
