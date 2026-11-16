"""
Generate a stylized radial ice-crack overlay as a transparent PNG.
Run:
 blender -b --python create_ice_crack_overlay.py -- --output //Exports/Ice_Cracks/ice_cracks.png --size 1024
The overlay is intentionally generic: position/scale it in Compose over the
player photo rather than baking it into a particular arena image.
"""
import bpy, math, os, sys, argparse, random
from mathutils import Vector

def get_args():
    av=sys.argv[sys.argv.index("--")+1:] if "--" in sys.argv else []
    p=argparse.ArgumentParser(); p.add_argument("--output",default="//Exports/Ice_Cracks/ice_cracks.png")
    p.add_argument("--size",type=int,default=1024); return p.parse_args(av)

def main():
    a=get_args(); random.seed(73)
    bpy.ops.object.select_all(action="SELECT"); bpy.ops.object.delete(use_global=False)
    scene=bpy.context.scene
    scene.render.engine="BLENDER_EEVEE_NEXT" if bpy.app.version>=(4,2,0) else "BLENDER_EEVEE"
    scene.render.resolution_x=a.size; scene.render.resolution_y=a.size; scene.render.resolution_percentage=100
    scene.render.image_settings.file_format="PNG"; scene.render.image_settings.color_mode="RGBA"
    scene.render.film_transparent=True; scene.view_settings.view_transform="Standard"
    mat=bpy.data.materials.new("Ice_Crack_Emission"); mat.use_nodes=True
    n=mat.node_tree.nodes; n.clear(); out=n.new("ShaderNodeOutputMaterial"); em=n.new("ShaderNodeEmission")
    em.inputs["Color"].default_value=(0.22,0.78,1.0,1); em.inputs["Strength"].default_value=3.2
    mat.node_tree.links.new(em.outputs[0],out.inputs["Surface"])
    # Multiple jagged radial branches, varying length and width.
    for branch in range(22):
        angle=random.random()*math.tau
        length=random.uniform(1.3,4.0); steps=random.randint(8,15)
        pts=[]; lateral=0
        for j in range(steps+1):
            t=j/steps; lateral += random.uniform(-0.12,0.12) if j else 0
            r=0.12+length*t
            ang=angle+lateral
            pts.append((r*math.cos(ang),r*math.sin(ang),0))
        c=bpy.data.curves.new("Crack_%02d"%branch,"CURVE"); c.dimensions="3D"
        c.bevel_depth=random.uniform(.006,.014); c.bevel_resolution=2
        sp=c.splines.new("POLY"); sp.points.add(len(pts)-1)
        for p,co in zip(sp.points,pts): p.co=(*co,1)
        ob=bpy.data.objects.new("Ice_Crack_%02d"%branch,c); bpy.context.collection.objects.link(ob); ob.data.materials.append(mat)
        # short side branches at a few points
        for k in (3,6,9):
            if k>=len(pts)-1: continue
            base=Vector(pts[k]); direction=Vector((math.cos(angle+1.2),math.sin(angle+1.2),0))
            end=base+direction*random.uniform(.25,.65)
            cc=bpy.data.curves.new("Branch_%02d_%02d"%(branch,k),"CURVE"); cc.dimensions="3D"
            cc.bevel_depth=.005; cc.bevel_resolution=2; ss=cc.splines.new("POLY"); ss.points.add(1)
            ss.points[0].co=(*base,1); ss.points[1].co=(*end,1)
            oo=bpy.data.objects.new("Crack_Branch",cc); bpy.context.collection.objects.link(oo); oo.data.materials.append(mat)
    bpy.ops.object.camera_add(location=(0,0,14)); cam=bpy.context.object
    cam.data.type="ORTHO"; cam.data.ortho_scale=9; scene.camera=cam
    scene.render.filepath=os.path.abspath(bpy.path.abspath(a.output))
    os.makedirs(os.path.dirname(scene.render.filepath),exist_ok=True)
    bpy.ops.render.render(write_still=True)
    print("DONE:",scene.render.filepath)
if __name__=="__main__": main()
