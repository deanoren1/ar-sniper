"""
מרנדר את האנימציות של דגם Blender לגיליון ספרייטים 6x4 (ראו docs/MONSTER_SPRITES.md).
הרצה:  blender -b model.blend -P blender_render_sheet.py -- <side|front|back> <out.png> [cell=384]

הנחות (לשנות בראש הקובץ אם צריך):
- יש אובייקט ראשי MODEL (ה-Armature או Empty שהדגם מקושר אליו) ומצלמה אורתוגרפית קבועה בסצנה.
- יש Actions בשמות Idle, Walk, Charge, Shoot, Hit, Death.
- רקע שקוף (Film > Transparent), והצללים על הדמות בלבד.
הסקריפט לא נבדק על ידי Claude: ייתכנו תיקונים קטנים.
"""
import sys
import bpy
import numpy as np

MODEL = "Monster"          # שם האובייקט שמסתובב לפי הזווית
# (שם הפעולה, מספר פריימים, שורה, עמודה ראשונה)
SHEET = [
    ("Idle", 6, 0, 0),
    ("Walk", 6, 1, 0),
    ("Charge", 3, 2, 0),
    ("Shoot", 3, 2, 3),
    ("Hit", 2, 3, 0),
    ("Death", 4, 3, 2),
]
# סיבוב סביב Z (במעלות) שמציג את הדמות מהצד, מקדימה ומאחור. לכוונן לפי כיוון הדגם.
ANGLES = {"side": 90.0, "front": 0.0, "back": 180.0}
COLS, ROWS = 6, 4

argv = sys.argv[sys.argv.index("--") + 1:]
angle_name = argv[0]
out_path = argv[1]
cell = int(argv[2]) if len(argv) > 2 else 384

scene = bpy.context.scene
scene.render.resolution_x = cell
scene.render.resolution_y = cell
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"

model = bpy.data.objects[MODEL]
model.rotation_euler[2] = np.radians(ANGLES[angle_name])
if model.animation_data is None:
    model.animation_data_create()

sheet = np.zeros((ROWS * cell, COLS * cell, 4), dtype=np.float32)
tmp = bpy.app.tempdir + "frame.png"

for action_name, count, row, col0 in SHEET:
    action = bpy.data.actions[action_name]
    model.animation_data.action = action
    start, end = action.frame_range
    for i in range(count):
        # מדגם את הפעולה במרווחים שווים (בלולאה: בלי הפריים האחרון שחוזר על הראשון)
        t = start + (end - start) * i / (count if action_name in ("Idle", "Walk") else max(count - 1, 1))
        scene.frame_set(int(round(t)))
        scene.render.filepath = tmp
        bpy.ops.render.render(write_still=True)
        img = bpy.data.images.load(tmp, check_existing=False)
        px = np.array(img.pixels[:], dtype=np.float32).reshape(cell, cell, 4)
        bpy.data.images.remove(img)
        # ל-Blender ציר Y הפוך: השורה הראשונה שלו היא התחתונה
        px = px[::-1]
        r0 = row * cell
        c0 = (col0 + i) * cell
        sheet[r0:r0 + cell, c0:c0 + cell] = px

out = bpy.data.images.new("sheet", COLS * cell, ROWS * cell, alpha=True)
out.pixels = sheet[::-1].reshape(-1).tolist()
out.filepath_raw = out_path
out.file_format = "PNG"
out.save()
print("saved", out_path)
