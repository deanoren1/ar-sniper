"""
SnapBoo Frozen Tundra — procedural portal generator
Blender 3.6+ / 4.x. Run:
  blender -b --python create_frozen_portal.py -- --output //Exports/Portal --frames 48 --size 1024

Creates a stylized icy energy portal on a transparent background and renders
an RGBA PNG sequence. This is a procedural starting asset, not a pre-rendered
or device-tested final game asset.
"""
import bpy, math, os, sys, argparse, random
from mathutils import Vector

def args_after_dashes():
    argv=sys.argv
    argv=argv[argv.index("--")+1:] if "--" in argv else []
    p=argparse.ArgumentParser()
    p.add_argument("--output",default="//Exports/Portal")
    p.add_argument("--frames",type=int,default=48)
    p.add_argument("--size",type=int,default=1024)
    p.add_argument("--fps",type=int,default=24)
    return p.parse_args(argv)

def clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for datablocks in (bpy.data.curves,bpy.data.meshes,bpy.data.materials):
        pass

def emission(name,color,strength=3.0,alpha=1.0):
    m=bpy.data.materials.new(name); m.diffuse_color=(*color,alpha); m.use_nodes=True
    n=m.node_tree.nodes; n.clear()
    out=n.new("ShaderNodeOutputMaterial"); e=n.new("ShaderNodeEmission")
    e.inputs["Color"].default_value=(*color,alpha)
    e.inputs["Strength"].default_value=strength
    m.node_tree.links.new(e.outputs[0],out.inputs["Surface"])
    return m

def make_torus(name,major,minor,mat,rot=0):
    bpy.ops.mesh.primitive_torus_add(major_radius=major,minor_radius=minor,
        major_segments=96,minor_segments=12,location=(0,0,0),rotation=(0,0,rot))
    o=bpy.context.object; o.name=name; o.data.materials.append(mat)
    return o

def animate_scale(o,start,end,first,last):
    o.scale=(start,start,start); o.keyframe_insert("scale",frame=first)
    o.scale=(end,end,end); o.keyframe_insert("scale",frame=last)
    if o.animation_data and o.animation_data.action:
        for fc in o.animation_data.action.fcurves:
            for kp in fc.keyframe_points: kp.interpolation="BEZIER"

def make_curve(name,points,bevel,mat):
    c=bpy.data.curves.new(name,"CURVE"); c.dimensions="3D"; c.resolution_u=16
    c.bevel_depth=bevel; c.bevel_resolution=3
    s=c.splines.new("POLY"); s.points.add(len(points)-1)
    for p,co in zip(s.points,points): p.co=(co[0],co[1],co[2],1)
    o=bpy.data.objects.new(name,c); bpy.context.collection.objects.link(o); o.data.materials.append(mat)
    return o

def main():
    a=args_after_dashes(); random.seed(24); clear_scene()
    scene=bpy.context.scene
    scene.render.engine="BLENDER_EEVEE_NEXT" if bpy.app.version>=(4,2,0) else "BLENDER_EEVEE"
    scene.render.resolution_x=a.size; scene.render.resolution_y=a.size
    scene.render.resolution_percentage=100; scene.render.fps=a.fps
    scene.render.image_settings.file_format="PNG"; scene.render.image_settings.color_mode="RGBA"
    scene.render.film_transparent=True
    scene.view_settings.view_transform="Standard"
    scene.frame_start=1; scene.frame_end=a.frames
    scene.render.image_settings.color_depth="8"
    scene.world.color=(0,0,0)
    cyan=emission("VFX_Ice_Cyan",(0.08,0.72,1.0),4.0)
    blue=emission("VFX_Deep_Blue",(0.015,0.18,0.85),2.6)
    white=emission("VFX_Frost_White",(0.72,0.94,1.0),5.0)
    violet=emission("VFX_Cold_Violet",(0.22,0.18,0.95),2.2)
    # Layered concentric rings: scale grows into the final portal.
    rings=[
        ("Outer_Ice_Rim",2.55,0.075,cyan,0.0),
        ("Inner_Energy_Ring",2.18,0.045,white,0.12),
        ("Blue_Orbit",1.92,0.065,blue,-0.08),
        ("Fine_Halo",2.82,0.025,violet,0.0),
    ]
    for name,r,t,mat,rot in rings:
        o=make_torus(name,r,t,mat,rot)
        animate_scale(o,0.10,1.0,1,a.frames)
        o.rotation_euler[2]=rot; o.keyframe_insert("rotation_euler",frame=1)
        o.rotation_euler[2]=rot+(1 if "Orbit" in name else -0.45)
        o.keyframe_insert("rotation_euler",frame=a.frames)
    # Spiral energy strands.
    for idx in range(3):
        pts=[]
        phase=idx*2*math.pi/3
        for j in range(180):
            t=j/179; ang=phase+t*math.pi*3.2
            rad=0.18+2.25*t
            pts.append((rad*math.cos(ang),rad*math.sin(ang),0.08+idx*0.025))
        o=make_curve("Energy_Spiral_%02d"%idx,pts,0.018 if idx else 0.026,[cyan,white,violet][idx])
        o.scale=(0.06,0.06,0.06); o.keyframe_insert("scale",frame=1)
        o.scale=(1,1,1); o.keyframe_insert("scale",frame=a.frames)
        o.rotation_euler[2]=phase; o.keyframe_insert("rotation_euler",frame=1)
        o.rotation_euler[2]=phase+0.6; o.keyframe_insert("rotation_euler",frame=a.frames)
    # Central dark-looking void is intentionally transparent: the player's photo remains visible.
    # A sparse swirl of frost motes, each animated toward the portal.
    for i in range(64):
        ang=random.random()*math.tau; radius=random.uniform(2.8,4.3)
        z=random.uniform(-0.10,0.15)
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=random.uniform(0.018,0.055),
             location=(radius*math.cos(ang),radius*math.sin(ang),z))
        p=bpy.context.object; p.name="Frost_Mote_%03d"%i
        p.data.materials.append(white if i%4==0 else cyan)
        start=Vector((radius*math.cos(ang),radius*math.sin(ang),z))
        end=Vector((random.uniform(-0.3,0.3),random.uniform(-0.3,0.3),z))
        p.location=start; p.keyframe_insert("location",frame=1)
        p.location=end; p.keyframe_insert("location",frame=a.frames)
        p.scale=(0.25,0.25,0.25); p.keyframe_insert("scale",frame=1)
        p.scale=(1.0,1.0,1.0); p.keyframe_insert("scale",frame=max(2,a.frames//2))
        p.scale=(0.05,0.05,0.05); p.keyframe_insert("scale",frame=a.frames)
    # Floating crystal shards around the perimeter.
    for i in range(18):
        ang=random.random()*math.tau; r=random.uniform(2.7,3.7)
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=1,
            location=(r*math.cos(ang),r*math.sin(ang),0.12))
        s=bpy.context.object; s.name="Ice_Shard_%02d"%i
        s.scale=(random.uniform(.035,.075),random.uniform(.10,.22),.035)
        s.rotation_euler=(random.random(),random.random(),ang)
        s.data.materials.append(white if i%3==0 else cyan)
        s.keyframe_insert("location",frame=1)
        s.location.x*=0.88; s.location.y*=0.88; s.keyframe_insert("location",frame=a.frames)
        s.rotation_euler.z+=random.uniform(-1.2,1.2); s.keyframe_insert("rotation_euler",frame=a.frames)
    # Camera faces the XY portal head-on.
    bpy.ops.object.camera_add(location=(0,0,14))
    cam=bpy.context.object; cam.name="VFX_Camera"; cam.data.type="ORTHO"; cam.data.ortho_scale=9.2
    cam.rotation_euler=(0,0,0); scene.camera=cam
    # Transparent render, no ground plane and no fixed arena background.
    scene.render.filepath=os.path.abspath(bpy.path.abspath(a.output)) + os.sep
    os.makedirs(scene.render.filepath,exist_ok=True)
    # Set linear interpolation on all generated actions for predictable motion.
    for obj in scene.objects:
        if obj.animation_data and obj.animation_data.action:
            for fc in obj.animation_data.action.fcurves:
                for kp in fc.keyframe_points: kp.interpolation="LINEAR"
    scene.frame_set(1)
    # Render all frames; files are frame_0001.png etc.
    bpy.ops.render.render(animation=True)
    print("DONE: transparent portal PNG sequence at",scene.render.filepath)

if __name__=="__main__": main()
