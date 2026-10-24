# ruff: noqa: F403, F405
"""Zombie gigante mutado: encorvado como gorila, brazo enorme con nudillos de hueso, brazo atrofiado,
mandibula colgando y una segunda cabeza brotando del hombro."""
from motor import *

MOB = dict(clave="zombie_gigante", grupo="Conceptos extra", nombre="Zombie gigante mutado", escala=2.0, seed=505)

PIEZAS = dict(cabeza=(8, 8, 8), mandibula=(8, 3, 6), cabeza2=(5, 5, 5), pua=(2, 5, 2), torso=(12, 14, 7),
              hombro=(6, 13, 6), antebrazo=(8, 16, 8), brazo_flaco=(4, 10, 4), antebrazo_flaco=(4, 12, 4),
              muslo=(5, 8, 5), canilla=(5, 8, 5))

def partes():
    S = []
    # piernas cortas y flexionadas
    for sx in (-1, 1):
        cad, r_m = (sx * 3.5, 16, 0), (-35, 0, sx * 8)
        S.append(dict(r="muslo", caja=(-2.5, -8, -2.5, 2.5, 0, 2.5), piv=cad, rot=r_m))
        S.append(dict(r="canilla", caja=(-2.5, -8, -2.5, 2.5, 0, 2.5), piv=en(cad, r_m, (0, -8, 0)), rot=(20, 0, sx * 4)))
    # torso encorvado hacia adelante
    piv_t, r_t = (0, 15, 0), (42, 0, 0)
    S.append(dict(r="torso", caja=(-6, 0, -3.5, 6, 14, 3.5), piv=piv_t, rot=r_t))
    # cabeza hundida entre los hombros, mandibula colgando
    p_cab, r_c = en(piv_t, r_t, (0, 11, 3.5)), (8, 0, 0)
    S.append(dict(r="cabeza", caja=(-4, -2, -2, 4, 6, 6), piv=p_cab, rot=r_c))
    S.append(dict(r="mandibula", caja=(-4, -3, 0, 4, 0, 6), piv=en(p_cab, r_c, (0, -2, -1)), rot=(45, 0, 0)))
    # columna de puas
    for y in (2, 5, 8, 11):
        S.append(dict(r="pua", caja=(-1, 0, -1, 1, 5, 1), piv=en(piv_t, r_t, (0, y, -3.3)), rot=(r_t[0] - 70, 0, 0)))
    # brazo enorme (derecha del mob)
    p_hom, r_h = en(piv_t, r_t, (-8.5, 12, 0)), (-30, 0, -14)
    S.append(dict(r="hombro", caja=(-3, -12, -3, 3, 1, 3), piv=p_hom, rot=r_h))
    p_codo, r_a = en(p_hom, r_h, (0, -11, 0)), (8, 0, -4)
    S.append(dict(r="antebrazo", caja=(-4, -16, -4, 4, 0, 4), piv=p_codo, rot=r_a))
    for k, ang in enumerate((-55, -80)):
        S.append(dict(r="pua", caja=(-1, 0, -1, 1, 5, 1), piv=en(p_codo, r_a, (-3.5, -2 - 4 * k, -3)), rot=(ang, 0, 35)))
    # brazo atrofiado, doblado como garra
    p_hom2, r_h2 = en(piv_t, r_t, (7.5, 11.5, 0)), (-5, 0, 18)
    S.append(dict(r="brazo_flaco", caja=(-2, -10, -2, 2, 0, 2), piv=p_hom2, rot=r_h2))
    S.append(dict(r="antebrazo_flaco", caja=(-2, -12, -2, 2, 0, 2), piv=en(p_hom2, r_h2, (0, -9.5, 0)), rot=(-65, 0, -25)))
    # segunda cabeza brotando del hombro
    S.append(dict(r="cabeza2", caja=(-2.5, 0, -2.5, 2.5, 5, 2.5), piv=en(piv_t, r_t, (4, 13.5, 0.5)), rot=(10, 0, -28)))
    return S

def pintar(t, uv, pz):
    cab = t.pintar(uv("cabeza"), pz["cabeza"], PIEL, base=0.5)
    t.dibujo(cab["front"], [
        ". . . . . . . .",
        ". k k . . k k .",
        "K K K . . K K K",
        "K R K . . K W K",
        ". K . . . . K .",
        ". . . s s . . .",
        ". . c c c c . .",
        ". W . W W . W .",
    ], {"K": (NEGRO, 0), "k": -2, "s": -1, "R": (OJO, 2), "W": (HUESO, 3), "c": (CARNE, 1)})
    t.dibujo(cab["izq"], [". . . . . . . .", ". . H H H . . .", ". H H K H H . .", ". . H H H . . ."],
             {"H": (HUESO, 3), "K": (NEGRO, 1)})
    t.rellenar(cab["bottom"], CARNE, 1)

    man = t.pintar(uv("mandibula"), pz["mandibula"], PIEL, base=0.45)
    t.dibujo(man["front"], ["W c W c c W c W", "c c c c c c c c"], {"W": (HUESO, 3), "c": (CARNE, 1)})
    t.rellenar(man["top"], CARNE, 2)
    t.dibujo(man["top"], ["W . . . . . . W", "W . . . . . . W"], {"W": (HUESO, 3)})

    c2 = t.pintar(uv("cabeza2"), pz["cabeza2"], CARNE, base=0.65, grad=0.4)
    t.dibujo(c2["front"], [". . . . .", ". W W W .", ". W R W .", ". W W W .", ". c K c ."],
             {"W": (HUESO, 4), "R": (OJO, 2), "K": (NEGRO, 0), "c": (CARNE, 0)})
    t.hueso(regiones(*uv("pua"), *pz["pua"]))

    tor = t.pintar(uv("torso"), pz["torso"], PIEL, base=0.5)
    t.mancha(tor["front"], 6, 5.5, 5, CARNE, 0.35)
    t.costillas(tor["front"], 2, 2, 3, 4)
    t.costillas(tor["front"], 7, 2, 3, 4)
    for cara in ("front", "der", "izq", "back"):  # restos del pantalon
        x0, y0, rw, rh = tor[cara]
        for i in range(rw):
            for j in range(rh - 4 + (i * 7 % 3), rh):
                t.set(x0 + i, y0 + j, DENIM, 2 - (j == rh - 1) + (j == rh - 4))
    t.columna(tor["back"], 5, hasta=tor["back"][3] - 4)
    t.sombra(tor["front"], 1, "top", 1)

    hom = t.pintar(uv("hombro"), pz["hombro"], PIEL, base=0.55)
    for cara in ("front", "izq", "der"):
        t.mancha(hom[cara], 3, 5, 1.8, CARNE, 0.4)
    ant = t.pintar(uv("antebrazo"), pz["antebrazo"], PIEL, base=0.55, grad=0.4)
    for cara in ("front", "der", "izq", "back"):
        t.sombra(ant[cara], 2, "top", 2)
        t.garras(ant[cara], 2)
    t.mancha(ant["der"], 4, 6, 2.2, CARNE, 0.4)
    x0, y0, rw, rh = ant["bottom"]  # nudillos vistos desde abajo
    for j in range(rh):
        for i in range(rw):
            t.set(x0 + i, y0 + j, HUESO, 2 + (i % 3 != 0) - (j % 3 == 0))

    for n in ("brazo_flaco", "antebrazo_flaco"):
        br = t.pintar(uv(n), pz[n], PIEL, base=0.45)
        for cara in ("front", "der", "izq", "back"):
            x0, y0, rw, rh = br[cara]
            if n == "brazo_flaco":  # manga rota
                for i in range(rw):
                    for j in range(3 - (i % 2)):
                        t.set(x0 + i, y0 + j, DENIM, 2 + (j == 0))
            else:
                t.garras(br[cara])

    mus = t.pintar(uv("muslo"), pz["muslo"], DENIM, base=0.45)
    for cara in ("front", "der", "izq", "back"):
        x0, y0, rw, rh = mus[cara]
        t.sombra(mus[cara], 2, "top", 2)
        for i in range(rw):
            for j in range(rh - 1 - (i % 3), rh):
                t.set(x0 + i, y0 + j, PIEL, 2)
    can = t.pintar(uv("canilla"), pz["canilla"], PIEL, base=0.5, grad=0.5)
    for cara in ("front", "der", "izq", "back"):
        t.garras(can[cara])
