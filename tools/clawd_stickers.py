"""
Generates the bundled "Claw'd Stickers" pack: a chunky, shaded orange critter (top light band, bottom shade,
a glint on the forehead, straight block arms and legs) with expressive eyes, animated with
iOS 26-style springs (overshoot and settle, squash and stretch).
Output: app/src/main/assets/stickers/clawd/*.tgs (gzipped Lottie, Telegram's animated sticker format:
512x512, 60 fps, <= 3 s, <= 64 KB) plus index.txt ("file emoji" per line). Run: python tools/clawd_stickers.py
"""
import gzip, json, pathlib

OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/stickers/clawd"
FPS, W = 60, 512

BODY = (0.871, 0.486, 0.353)      # #DE7C5A
LIGHT = (0.914, 0.600, 0.486)     # top band
SHADE = (0.749, 0.384, 0.259)     # bottom band / leg tops
GLINT = (0.965, 0.780, 0.706)     # forehead highlight
EYE = (0.165, 0.102, 0.078)       # #2A1A14
WHITE = (1.0, 1.0, 1.0)
PINK = (1.0, 0.36, 0.48)
BLUE = (0.42, 0.72, 1.0)
GOLD = (1.0, 0.82, 0.32)
GREEN = (0.55, 0.88, 0.55)
CONFETTI = [GOLD, BLUE, PINK, GREEN, (0.75, 0.55, 1.0)]

# Layout (canvas px), proportions from the reference art: body 240x154, eyes at 15% / 71% of the width and a
# third of the height, arms on the lower half of the sides, four short legs.
BX0, BX1, BY0, BY1 = 140, 380, 175, 329
FEET = (260, 362)
CENTER = (260, 252)
EYE_L, EYE_R = (176, 226), (310, 226)       # eye centres
EX = (EYE_L[0] + EYE_R[0]) / 2
SHOULDER_L, SHOULDER_R = (BX0, 272), (BX1, 272)


# ------------------------------------------------------------------ keyframe helpers

def _v(v):
    return v if isinstance(v, list) else [v]


def anim(keys, o=(0.33, 0.0), i=(0.67, 1.0), hold=False):
    """keys: [(frame, value)], eased in-out between them."""
    ks = []
    for t, v in keys[:-1]:
        k = {"t": t, "s": _v(v), "o": {"x": [o[0]], "y": [o[1]]}, "i": {"x": [i[0]], "y": [i[1]]}}
        if hold:
            k["h"] = 1
        ks.append(k)
    t, v = keys[-1]
    ks.append({"t": t, "s": _v(v)})
    return {"a": 1, "k": ks}


def spring_keys(t0, a, b, dur=24, amount=0.22):
    """a -> b like an iOS spring: overshoot, small rebound, settle."""
    def mix(x, y, f):
        return [xa + (ya - xa) * f for xa, ya in zip(x, y)] if isinstance(x, list) else x + (y - x) * f
    return [(t0, a), (t0 + int(dur * 0.4), mix(a, b, 1 + amount)), (t0 + int(dur * 0.7), mix(a, b, 1 - amount * 0.3)), (t0 + dur, b)]


def springs(segments, end):
    """Chain of spring moves: segments = [(t0, from, to, dur)], holding the last value until `end`."""
    keys = []
    for t0, a, b, dur in segments:
        keys += spring_keys(t0, a, b, dur)
    keys.append((end, segments[-1][2]))
    keys.sort(key=lambda k: k[0])
    out = []
    for k in keys:   # drop frames that collide (later segment wins)
        if out and out[-1][0] >= k[0]:
            out[-1] = k
        else:
            out.append(k)
    return anim(out)


def static(v):
    return {"a": 0, "k": v}


def sc(x, y=None):
    return [x, x if y is None else y, 100]


# ------------------------------------------------------------------ shapes

def rect(x0, y0, x1, y1, r=0):
    return {"ty": "rc", "d": 1, "p": static([(x0 + x1) / 2, (y0 + y1) / 2]), "s": static([x1 - x0, y1 - y0]), "r": static(r)}


def poly(points, closed=True):
    n = len(points)
    return {"ty": "sh", "d": 1, "ks": static({"i": [[0, 0]] * n, "o": [[0, 0]] * n, "v": [list(p) for p in points], "c": closed})}


def fill(color, opacity=100):
    return {"ty": "fl", "c": static(list(color) + [1]), "o": static(opacity), "r": 1}


def stroke(color, width, cap=1):
    return {"ty": "st", "c": static(list(color) + [1]), "o": static(100), "w": static(width), "lc": cap, "lj": 1, "ml": 4}


TR = {"ty": "tr", "p": static([0, 0]), "a": static([0, 0]), "s": static([100, 100]), "r": static(0), "o": static(100), "sk": static(0), "sa": static(0)}


def group(*items):
    return {"ty": "gr", "it": list(items) + [TR]}


class Sticker:
    def __init__(self, name, frames):
        self.name, self.op, self.layers = name, frames, []

    def layer(self, name, shapes, pivot, parent=None, pos=None, scale=None, rot=None, opacity=None, null=False):
        ind = len(self.layers) + 1
        ks = {"o": opacity or static(100), "r": rot or static(0),
              "p": pos or static([pivot[0], pivot[1], 0]), "a": static([pivot[0], pivot[1], 0]),
              "s": scale or static([100, 100, 100])}
        l = {"ddd": 0, "ind": ind, "ty": 3 if null else 4, "nm": name, "sr": 1, "ks": ks, "ao": 0,
             "ip": 0, "op": self.op, "st": 0, "bm": 0}
        if not null:
            l["shapes"] = shapes
        if parent:
            l["parent"] = parent
        self.layers.append(l)
        return ind

    def save(self):
        doc = {"tgs": 1, "v": "5.5.2", "fr": FPS, "ip": 0, "op": self.op, "w": W, "h": W, "nm": self.name, "ddd": 0,
               "assets": [], "layers": list(reversed(self.layers))}   # Lottie draws the first layer on top
        data = gzip.compress(json.dumps(doc, separators=(",", ":")).encode(), 9)
        assert len(data) <= 64 * 1024, (self.name, len(data))
        (OUT / f"{self.name}.tgs").write_bytes(data)
        return len(data)


# ------------------------------------------------------------------ the critter

def body_shapes():
    # Lottie paints the first item on top, so: glint, shade, light band, then the body under them.
    return [
        group(rect(166, 195, 205, 204, 1), fill(GLINT)),                                 # glint above the left eye
        group(rect(BX0, BY1 - 20, BX1, BY1, 3), fill(SHADE)),                             # bottom shade
        group(rect(BX0, BY0, BX1, BY0 + 22, 3), fill(LIGHT)),                             # top light band
        group(rect(BX0, BY0, BX1, BY1, 4), fill(BODY)),
    ]


def legs_shapes():
    out = []
    for x0 in (148, 210, 282, 344):   # 0.03 / 0.29 / 0.59 / 0.85 of the body width, 28 px wide
        out.append(group(rect(x0, BY1 - 2, x0 + 28, BY1 + 10), fill(SHADE)))          # shaded top, in the body's shadow
        out.append(group(rect(x0, BY1 - 2, x0 + 28, FEET[1]), fill(BODY)))
    return out


def arm_l_shapes():
    return [group(rect(BX0 - 38, 282, BX0 + 4, 292, 3), fill(SHADE)),
            group(rect(BX0 - 38, 252, BX0 + 4, 292, 3), fill(BODY)),
            group(rect(BX0 - 38, 252, BX0 + 4, 258, 2), fill(LIGHT))]


def arm_r_shapes():
    return [group(rect(BX1 - 4, 282, BX1 + 38, 292, 3), fill(SHADE)),
            group(rect(BX1 - 4, 252, BX1 + 38, 292, 3), fill(BODY)),
            group(rect(BX1 - 4, 252, BX1 + 38, 258, 2), fill(LIGHT))]


# Eyes: every expression is its own layer, cross-faded with hold keys so the face can change mid-animation.
def eyes_open(big=1.0):
    w, h = 20 * big, 40 * big
    out = []
    for cx, cy in (EYE_L, EYE_R):
        out.append(group(rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, 2), fill(EYE)))
        out.append(group(rect(cx - w / 2 + 3, cy - h / 2 + 4, cx - w / 2 + 9, cy - h / 2 + 12, 1), fill(WHITE, 85)))   # glint
    return out


def eyes_happy():   # ^ ^
    return [group(poly([(cx - 14, cy + 8), (cx, cy - 8), (cx + 14, cy + 8)], closed=False), stroke(EYE, 9)) for cx, cy in (EYE_L, EYE_R)]


def eyes_closed():  # gentle downward arcs
    return [group(poly([(cx - 14, cy + 2), (cx - 5, cy + 8), (cx + 5, cy + 8), (cx + 14, cy + 2)], closed=False), stroke(EYE, 7)) for cx, cy in (EYE_L, EYE_R)]


def eyes_sad():     # smaller eyes + slanted brows
    out = []
    for (cx, cy), side in ((EYE_L, -1), (EYE_R, 1)):
        out.append(group(rect(cx - 9, cy - 10, cx + 9, cy + 18, 2), fill(EYE)))
        out.append(group(poly([(cx - 16, cy - 16 + 8 * side), (cx + 16, cy - 16 - 8 * side)], closed=False), stroke(EYE, 6)))
    return out


def eyes_wink():
    cx, cy = EYE_L
    return ([group(rect(cx - 10, cy - 20, cx + 10, cy + 20, 2), fill(EYE)), group(rect(cx - 7, cy - 16, cx - 1, cy - 8, 1), fill(WHITE, 85))]
            + [group(poly([(EYE_R[0] - 14, EYE_R[1] + 8), (EYE_R[0], EYE_R[1] - 8), (EYE_R[0] + 14, EYE_R[1] + 8)], closed=False), stroke(EYE, 9))])


HEART = [".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."]


def pix(rows, color, size, cx, cy):
    h, w = len(rows), len(rows[0])
    shapes = []
    for y, r in enumerate(rows):
        x = 0
        while x < w:
            if r[x] == "X":
                s = x
                while x < w and r[x] == "X":
                    x += 1
                shapes.append(rect(cx - w * size / 2 + s * size, cy - h * size / 2 + y * size, cx - w * size / 2 + x * size + 0.5, cy - h * size / 2 + (y + 1) * size + 0.5))
            else:
                x += 1
    return group(*shapes, fill(color))


def eyes_hearts():
    return [pix(HEART, PINK, 6, cx, cy) for cx, cy in (EYE_L, EYE_R)]


def show(frames_on, op):
    """Opacity with hold keys: visible in the given [(start, end)] ranges."""
    keys, t = [(0, 0)], 0
    for a, b in frames_on:
        keys += [(a, 100), (b, 0)]
    keys.append((op, keys[-1][1]))
    keys.sort(key=lambda k: k[0])
    dedup = []
    for k in keys:
        if dedup and dedup[-1][0] == k[0]:
            dedup[-1] = k
        else:
            dedup.append(k)
    return anim(dedup, hold=True)


def critter(s, root_pos=None, root_scale=None, root_rot=None, arm_l=None, claw=None, legs_scale=None,
            faces=None, eye_pos=None, eye_scale=None):
    """faces: [(shapes, visibility-opacity-anim or None)]. Returns the root index (effects can parent to it)."""
    root = s.layer("root", [], FEET, pos=root_pos, scale=root_scale, rot=root_rot, null=True)
    s.layer("legs", legs_shapes(), (FEET[0], BY1), parent=root, scale=legs_scale)
    s.layer("body", body_shapes(), CENTER, parent=root)
    s.layer("armL", arm_l_shapes(), SHOULDER_L, parent=root, rot=arm_l)
    s.layer("armR", arm_r_shapes(), SHOULDER_R, parent=root, rot=claw)
    eyes_pivot = ((EYE_L[0] + EYE_R[0]) / 2, EYE_L[1])
    for n, (shapes, vis) in enumerate(faces or [(eyes_open(), None)]):
        s.layer(f"eyes{n}", shapes, eyes_pivot, parent=root, opacity=vis, pos=eye_pos, scale=eye_scale)
    return root


def blink(op, *at):
    keys = [(0, sc(100))]
    for t in at:
        keys += [(t, sc(100)), (t + 4, sc(100, 8)), (t + 9, sc(100, 108)), (t + 13, sc(100))]
    keys.append((op, sc(100)))
    return anim(keys)


def looks(op, moves):
    """Eye position glances: moves = [(frame, dx, dy)], springy."""
    base = [eyes_x := (EYE_L[0] + EYE_R[0]) / 2, EYE_L[1], 0]
    segs, cur = [], base
    for t, dx, dy in moves:
        nxt = [base[0] + dx, base[1] + dy, 0]
        segs.append((t, cur, nxt, 18))
        cur = nxt
    return springs(segs, op)


# ------------------------------------------------------------------ stickers

ZED = ["XXXX", "..X.", ".X..", "XXXX"]
DOT = ["XX", "XX"]
DROP = [".X.", "XXX", "XXX", ".X."]
CODE = ["..X......X..", ".X..X.....X.", "X...X......X", ".X...X...X..", "..X..X..X..."]
SPARK = ["..X..", "..X..", "XXXXX", "..X..", "..X.."]
BANG = ["X", "X", "X", ".", "X"]


def make():
    OUT.mkdir(parents=True, exist_ok=True)
    made = []
    fx, fy = FEET

    # 1. hello: claw waves on springs, body sways, eyes go happy while waving
    op = 150
    s = Sticker("01_hello", op)
    wave = springs([(0, 0, -38, 16), (16, -38, 6, 16), (32, 6, -38, 16), (48, -38, 6, 16), (64, 6, -38, 16), (80, -38, 0, 22)], op)
    sway = springs([(0, 0, -3, 30), (40, -3, 3, 30), (80, 3, 0, 30)], op)
    critter(s, root_rot=sway, claw=wave,
            faces=[(eyes_open(), show([(0, 12), (100, op)], op)), (eyes_happy(), show([(12, 100)], op))],
            eye_scale=blink(op, 118))
    made.append((s, "👋"))

    # 2. love: heart eyes pulse, springy squash, hearts float up
    op = 150
    s = Sticker("02_love", op)
    pulse = springs([(0, sc(100), sc(110, 92), 16), (16, sc(110, 92), sc(100), 22), (60, sc(100), sc(110, 92), 16), (76, sc(110, 92), sc(100), 22)], op)
    hb = springs([(0, sc(100), sc(125), 14), (14, sc(125), sc(100), 18), (60, sc(100), sc(125), 14), (74, sc(100 + 25), sc(100), 18)], op)
    critter(s, root_scale=pulse, faces=[(eyes_hearts(), None)], eye_scale=hb)
    for k, (x, d) in enumerate([(170, 10), (360, 40), (262, 72)]):
        y0 = BY0 - 10
        s.layer(f"heart{k}", [pix(HEART, PINK, 9, x, y0)], (x, y0),
                pos=anim([(0, [x, y0, 0]), (d, [x, y0, 0]), (d + 66, [x + (k - 1) * 22, y0 - 130, 0]), (op, [x + (k - 1) * 22, y0 - 130, 0])]),
                scale=springs([(d, sc(0), sc(100), 22)], op),
                opacity=anim([(0, 0), (d, 0), (d + 8, 100), (d + 46, 100), (d + 66, 0), (op, 0)]))
    made.append((s, "❤️"))

    # 3. jump: anticipation squash, stretch up, springy landing; wide eyes in the air, happy on landing
    op = 100
    s = Sticker("03_jump", op)
    pos = anim([(0, [fx, fy, 0]), (14, [fx, fy, 0]), (34, [fx, fy - 130, 0]), (52, [fx, fy, 0]), (op, [fx, fy, 0])], o=(0.3, 0), i=(0.6, 1))
    sq = anim([(0, sc(100)), (12, sc(118, 82)), (20, sc(86, 118)), (34, sc(100)), (50, sc(100)), (54, sc(122, 80)), (64, sc(94, 106)), (74, sc(103, 98)), (84, sc(100)), (op, sc(100))])
    arms = springs([(14, 0, 55, 18), (52, 55, 0, 22)], op)
    claw = springs([(14, 0, -60, 18), (52, -60, 0, 22)], op)
    critter(s, root_pos=pos, root_scale=sq, arm_l=arms, claw=claw,
            faces=[(eyes_open(), show([(0, 18)], op)), (eyes_open(1.25), show([(18, 54)], op)), (eyes_happy(), show([(54, op)], op))])
    made.append((s, "😄"))

    # 4. sleep: closed eyes, slow breathing, Z's drifting up
    op = 180
    s = Sticker("04_sleep", op)
    breathe = anim([(0, sc(100)), (90, sc(103, 95)), (op, sc(100))])
    critter(s, root_scale=breathe, faces=[(eyes_closed(), None)], eye_pos=anim([(0, [EX, EYE_L[1], 0]), (90, [EX, EYE_L[1] + 3, 0]), (op, [EX, EYE_L[1], 0])]))
    for k, d in enumerate([0, 60, 120]):
        x, y = 372, BY0 - 14
        s.layer(f"z{k}", [pix(ZED, WHITE, 8 + 2 * k, x, y)], (x, y),
                pos=anim([(0, [x, y, 0]), (d, [x, y, 0]), (min(d + 90, op - 1), [x + 48, y - 110, 0]), (op, [x + 48, y - 110, 0])]),
                scale=springs([(d, sc(0), sc(100), 20)], op),
                opacity=anim([(0, 0), (d, 0), (d + 12, 90), (min(d + 70, op - 10), 90), (min(d + 90, op - 1), 0), (op, 0)]))
    made.append((s, "😴"))

    # 5. think: tilt, claw to the chin, eyes glance up-right then around, dots pop in
    op = 160
    s = Sticker("05_think", op)
    critter(s, root_rot=springs([(0, 0, -6, 26), (130, -6, 0, 26)], op), claw=springs([(0, 0, -78, 24), (130, -78, 0, 24)], op),
            eye_pos=looks(op, [(10, 8, -8), (60, -6, -8), (95, 8, -8), (135, 0, 0)]), eye_scale=blink(op, 80))
    for k in range(3):
        x, y, d = 330 + k * 40, BY0 - 36 - k * 12, 28 + k * 16
        s.layer(f"dot{k}", [pix(DOT, WHITE, 10, x, y)], (x, y),
                scale=springs([(d, sc(0), sc(100), 18), (124, sc(100), sc(0), 14)], op))
    made.append((s, "🤔"))

    # 6. party: springy bounces, arms up, happy eyes, confetti rain
    op = 120
    s = Sticker("06_party", op)
    bounce = anim([(t, [fx, fy - (36 if (t // 15) % 2 else 0), 0]) for t in range(0, op + 1, 15)], o=(0.3, 0), i=(0.5, 1))
    squash = anim([(t, sc(112, 88) if (t // 15) % 2 == 0 else sc(96, 106)) for t in range(0, op + 1, 15)])
    up = anim([(t, -40 if (t // 15) % 2 else -70) for t in range(0, op + 1, 15)])
    down = anim([(t, 40 if (t // 15) % 2 else 70) for t in range(0, op + 1, 15)])
    critter(s, root_pos=bounce, root_scale=squash, claw=up, arm_l=down, faces=[(eyes_happy(), None)])
    for k in range(14):
        x = 50 + (k * 37) % 420
        d = (k * 23) % 60
        col = CONFETTI[k % len(CONFETTI)]
        s.layer(f"c{k}", [group(rect(x - 6, -6, x + 6, 6), fill(col))], (x, 0),
                pos=anim([(0, [x, -20, 0]), (d, [x, -20, 0]), (d + 60, [x + (k % 3 - 1) * 30, 530, 0]), (op, [x, 530, 0])]),
                rot=anim([(0, 0), (d, 0), (d + 60, 360 * (1 if k % 2 else -1)), (op, 0)]))
    made.append((s, "🎉"))

    # 7. sad: slump, droopy eyes with brows, tears dripping
    op = 160
    s = Sticker("07_sad", op)
    slump = springs([(0, sc(100), sc(104, 93), 30)], op)
    critter(s, root_scale=slump, arm_l=springs([(0, 0, 22, 30)], op), claw=springs([(0, 0, 18, 30)], op),
            faces=[(eyes_sad(), None)], eye_pos=springs([(0, [EX, EYE_L[1], 0], [EX, EYE_L[1] + 6, 0], 30)], op))
    for k, (ex, d) in enumerate([(EYE_L[0], 24), (EYE_R[0], 64), (EYE_L[0], 104)]):
        y = EYE_L[1] + 22
        s.layer(f"tear{k}", [pix(DROP, BLUE, 7, ex, y)], (ex, y),
                pos=anim([(0, [ex, y, 0]), (d, [ex, y, 0]), (min(d + 44, op - 1), [ex, y + 120, 0]), (op, [ex, y + 120, 0])], o=(0.5, 0), i=(0.9, 1)),
                scale=springs([(d, sc(0), sc(100), 14)], op),
                opacity=anim([(0, 0), (d, 0), (d + 4, 100), (min(d + 36, op - 6), 100), (min(d + 44, op - 1), 0), (op, 0)]))
    made.append((s, "😢"))

    # 8. coding: arms typing, eyes reading left to right, code glyph pulsing
    op = 120
    s = Sticker("08_coding", op)
    tap_l = anim([(t, 0 if (t // 8) % 2 else 20) for t in range(0, op + 1, 8)])
    tap_r = anim([(t, -18 if (t // 8) % 2 else 0) for t in range(0, op + 1, 8)])
    read = looks(op, [(0, -8, 4), (30, 8, 4), (50, -8, 8), (80, 8, 8), (100, 0, 4)])
    critter(s, arm_l=tap_l, claw=tap_r, eye_pos=read, eye_scale=blink(op, 64))
    cx, cy = 262, BY0 - 50
    s.layer("code", [pix(CODE, GREEN, 10, cx, cy)], (cx, cy),
            scale=springs([(0, sc(90), sc(108), 20), (40, sc(108), sc(96), 20), (80, sc(96), sc(100), 20)], op),
            opacity=anim([(0, 70), (30, 100), (60, 75), (90, 100), (op, 70)]))
    made.append((s, "💻"))

    # 9. sparkle: wink, proud nod, twinkling stars
    op = 130
    s = Sticker("09_sparkle", op)
    nod = springs([(0, sc(100), sc(104, 96), 16), (16, sc(104, 96), sc(100), 20), (50, sc(100), sc(104, 96), 16), (66, sc(104, 96), sc(100), 20)], op)
    critter(s, root_scale=nod, claw=springs([(0, 0, -100, 22), (104, -100, 0, 22)], op),
            faces=[(eyes_open(), show([(0, 20), (100, op)], op)), (eyes_wink(), show([(20, 100)], op))])
    for k, (x, y, d) in enumerate([(108, 150, 0), (410, 128, 26), (432, 330, 56), (86, 330, 86)]):
        s.layer(f"star{k}", [pix(SPARK, GOLD, 8, x, y)], (x, y),
                scale=springs([(d, sc(0), sc(120), 14), (min(d + 26, op - 18), sc(120), sc(0), 16)], op),
                rot=anim([(0, 0), (d, 0), (min(d + 40, op - 1), 90), (op, 90)]))
    made.append((s, "✨"))

    # 10. shocked: jolt back, huge eyes, "!" pops
    op = 120
    s = Sticker("10_shocked", op)
    jolt = anim([(0, [fx, fy, 0]), (8, [fx + 18, fy - 40, 0]), (20, [fx + 10, fy, 0]), (30, [fx + 14, fy - 6, 0]), (40, [fx + 10, fy, 0]), (100, [fx + 10, fy, 0]), (op, [fx, fy, 0])])
    shake = anim([(t, (-3 if (t // 4) % 2 else 3) if 40 <= t <= 90 else 0) for t in range(0, op + 1, 4)])
    critter(s, root_pos=jolt, root_rot=shake, arm_l=springs([(4, 0, 70, 16)], op), claw=springs([(4, 0, -80, 16)], op),
            faces=[(eyes_open(), show([(0, 6), (104, op)], op)), (eyes_open(1.45), show([(6, 104)], op))],
            eye_scale=springs([(6, sc(70), sc(100), 18)], op))
    bx, by = 262, BY0 - 56
    s.layer("bang", [pix(BANG, GOLD, 12, bx, by)], (bx, by + 30),
            scale=springs([(8, sc(0), sc(110), 18), (96, sc(110), sc(0), 14)], op),
            rot=springs([(8, -20, 0, 20)], op))
    made.append((s, "😱"))

    for old in OUT.glob("*.tgs"):
        old.unlink()
    index = []
    for st, emoji in made:
        size = st.save()
        index.append(f"{st.name}.tgs {emoji}")
        print(st.name, size, "bytes")
    (OUT / "index.txt").write_text("\n".join(index) + "\n", encoding="utf-8")


if __name__ == "__main__":
    make()
