import json
import math
import unittest

from tools.skins.integrate_existing import (
    CREEPER_DESIGNS,
    KEPLER_DESIGNS,
    ZOMBIE_DESIGNS,
    _design,
    bones,
    minecraft_rotation,
    motor,
    render_target,
    targets,
)


def _rz_ry_rx(x: float, y: float, z: float) -> list[list[float]]:
    """Minecraft part rotation matrix (ModelPart: Rz * Ry * Rx)."""
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]

    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]

    return mul(rz, mul(ry, rx))


class SkinIntegrationTests(unittest.TestCase):
    def test_every_mod_mob_has_a_design(self):
        self.assertEqual(len(CREEPER_DESIGNS), 9)
        self.assertEqual(len(ZOMBIE_DESIGNS), 16)
        self.assertEqual(len(KEPLER_DESIGNS), 7)
        self.assertEqual(len(targets()), 9 + 1 + 16 + 7)

    def test_rotations_survive_the_change_to_minecraft_axes(self):
        # The motor rotates x, then z, then y with y up and +z front; Minecraft flips y and z.
        flip = (1, -1, -1)
        for rot in ((20, 0, 0), (0, 0, -35), (0, 40, 0), (25, 30, -15), (-90, 10, 6)):
            with self.subTest(rot=rot):
                for axis in ((1, 0, 0), (0, 1, 0), (0, 0, 1)):
                    expected = [a * f for a, f in zip(motor.rotar(tuple(a * f for a, f in zip(axis, flip)), rot), flip)]
                    m = _rz_ry_rx(*minecraft_rotation(rot))
                    got = [sum(m[i][j] * axis[j] for j in range(3)) for i in range(3)]
                    for e, g in zip(expected, got):
                        self.assertAlmostEqual(e, g, places=6)

    def test_pieces_ride_on_their_bones(self):
        _, model, _ = motor.construir(_design("minero_zombie"))
        found, owner = bones(model)
        by_role = {}
        for part, bone in zip(model, owner):
            by_role.setdefault(part["r"], set()).add(bone)
        head = next(b["nombre"] for b in found if b["tipo"] == "cabeza")
        self.assertEqual(by_role["casco"], {head})
        self.assertEqual(by_role["cuerpo"], {None})
        self.assertTrue(all(o and o.startswith("brazo") for o in by_role["pico"]))
        legs = [b for b in found if b["tipo"] == "pierna"]
        self.assertEqual(len(legs), 2)
        self.assertNotEqual(legs[0]["fase"], legs[1]["fase"])

    def test_spider_legs_keep_their_shins(self):
        _, model, _ = motor.construir(_design("acechador"))
        found, owner = bones(model)
        legs = [b for b in found if b["tipo"] == "pierna"]
        self.assertEqual(len(legs), 8)
        shins = [o for part, o in zip(model, owner) if part["r"] == "canilla"]
        self.assertTrue(all(o and o.startswith("pierna") for o in shins))

    def test_every_target_exports_model_texture_relief_and_eyes(self):
        for target in targets():
            with self.subTest(mob=target.design):
                texture, exterior, eyes, model = render_target(target)
                self.assertEqual(texture.size, exterior.size)
                self.assertEqual(list(texture.size), model["textura"])
                self.assertIsNotNone(texture.getchannel("A").getbbox())
                painted = sum(1 for value in exterior.getchannel("A").getdata() if value)
                self.assertGreater(painted, 0)
                bone_names = {b["nombre"] for b in model["huesos"]}
                for piece in model["piezas"]:
                    self.assertTrue(piece["hueso"] is None or piece["hueso"] in bone_names)
                for bone in model["huesos"]:
                    self.assertEqual(model["piezas"][bone["pieza"]]["hueso"], bone["nombre"])
                self.assertGreater(model["alto"], 0)
                json.dumps(model)  # what the mods read must be plain JSON

    def test_sockets_and_wounds_stay_sunken(self):
        texture, _, exterior = motor.construir(_design("zombie_gigante"))
        sunken_colors = {color for ramp in motor.HUNDIDO for color in ramp}
        sunken = [
            (x, y)
            for y in range(texture.size[1])
            for x in range(texture.size[0])
            if texture.getpixel((x, y))[:3] in sunken_colors
        ]
        self.assertTrue(sunken)
        self.assertFalse([point for point in sunken if exterior.getpixel(point)[3]])

    def test_exterior_inflation_follows_part_thickness(self):
        self.assertEqual(motor.inflado((8, 8, 8)), 0.5)
        self.assertEqual(motor.inflado((4, 12, 4)), 0.5)
        self.assertEqual(motor.inflado((2, 12, 2)), 0.25)
        self.assertEqual(motor.inflado((1, 5, 1)), 0.0)


if __name__ == "__main__":
    unittest.main()
