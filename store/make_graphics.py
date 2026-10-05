"""Erzeugt die Grafiken für den Play Store im Stil der Screenshots (store/1.png bis 6.png).

- icon-512.png            App-Symbol wie in der App: weißer Tacho auf Blau
- icon-512-hell.png       Variante im Screenshot-Stil: schwarzer Tacho auf Hellgrau
- feature-graphic-1024x500.png  Vorstellungsgrafik im Screenshot-Stil

Stil der Screenshots: Hintergrund #EDEDED, Titel in einer Garamond, ein Wort kursiv,
schwarzer Strich darunter, leichte Unterzeile, Großbuchstaben kursiv und unterstrichen,
handgezeichneter Pfeil (wird aus store/1.png übernommen).

Gezeichnet wird in vierfacher Größe und danach verkleinert, damit die Kanten glatt werden.
Aufruf: python store/make_graphics.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = r"C:\Windows\Fonts"
SCALE = 4

PAPER = (237, 237, 237)   # Hintergrund der Screenshots
INK = (0, 0, 0)
SOFT_INK = (40, 40, 40)   # Unterzeile, wie in den Screenshots etwas weicher als Schwarz
BLUE = (31, 98, 180)      # #1F62B4, wie ic_launcher_background
WHITE = (255, 255, 255)


def font(name, px):
    return ImageFont.truetype(os.path.join(FONTS, name), round(px * SCALE))


def draw_gauge(draw, cx, cy, unit, color):
    """Tacho im Koordinatensystem des 108er-Vektors (ic_launcher_foreground); [unit] = Pixel pro Einheit."""
    r = 22 * unit
    w = round(6 * unit)
    draw.arc([cx - r, cy - r, cx + r, cy + r], start=180, end=360, fill=color, width=w)
    for angle in (180, 360):  # runde Enden des Bogens
        ex = cx + (r - w / 2) * math.cos(math.radians(angle))
        ey = cy + (r - w / 2) * math.sin(math.radians(angle))
        draw.ellipse([ex - w / 2, ey - w / 2, ex + w / 2, ey + w / 2], fill=color)
    nx, ny = cx + 13 * unit, cy - 16 * unit  # Nadel nach rechts oben
    nw = round(5 * unit)
    draw.line([cx, cy, nx, ny], fill=color, width=nw)
    draw.ellipse([nx - nw / 2, ny - nw / 2, nx + nw / 2, ny + nw / 2], fill=color)
    hr = 5 * unit
    draw.ellipse([cx - hr, cy - hr, cx + hr, cy + hr], fill=color)


def icon(background, foreground, filename):
    size = 512 * SCALE
    img = Image.new("RGB", (size, size), background)
    unit = size / 78
    # Der Tacho reicht 25 Einheiten über und 5 unter seinen Mittelpunkt: so verschoben wirkt er mittig.
    draw_gauge(ImageDraw.Draw(img), size / 2, size / 2 + 10 * unit, unit, foreground)
    img.resize((512, 512), Image.LANCZOS).save(os.path.join(HERE, filename))


def arrow_from_screenshot():
    """Schneidet den handgezeichneten Pfeil aus store/1.png aus, als schwarze Form mit Transparenz."""
    shot = Image.open(os.path.join(HERE, "1.png")).convert("L")
    crop = shot.crop((834, 478, 922, 701))
    # Je dunkler das Pixel, desto deckender; der helle Hintergrund wird durchsichtig.
    alpha = crop.point(lambda v: max(0, min(255, round((PAPER[0] - v) * 255 / (PAPER[0] - 20)))))
    arrow = Image.new("RGBA", crop.size, INK + (0,))
    arrow.putalpha(alpha)
    return arrow


def centered_text(draw, cx, y, parts):
    """Zeichnet Textstücke [(text, font, farbe)] nebeneinander, als Ganzes um cx zentriert."""
    widths = [draw.textlength(t, font=f) for t, f, _ in parts]
    x = cx - sum(widths) / 2
    for (t, f, c), w in zip(parts, widths):
        draw.text((x, y), t, font=f, fill=c)
        x += w
    return cx - sum(widths) / 2, cx + sum(widths) / 2


def feature_graphic():
    w, h = 1024 * SCALE, 500 * SCALE
    img = Image.new("RGB", (w, h), PAPER)
    s = SCALE

    # App-Symbol links als abgerundetes Quadrat mit weichem Schatten, wie ein Symbol auf dem Handy.
    box = 210 * s
    ix, iy = 118 * s, (h - box) // 2
    shadow = Image.new("L", (w, h), 0)
    ImageDraw.Draw(shadow).rounded_rectangle([ix, iy + 10 * s, ix + box, iy + box + 10 * s], radius=48 * s, fill=70)
    shadow = shadow.filter(ImageFilter.GaussianBlur(18 * s))
    img.paste((150, 150, 150), (0, 0), shadow)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([ix, iy, ix + box, iy + box], radius=48 * s, fill=BLUE)
    unit = box / 78
    draw_gauge(d, ix + box / 2, iy + box / 2 + 10 * unit, unit, WHITE)

    # Textblock rechts, mittig um cx gesetzt wie in den Screenshots.
    cx = 680 * s
    roman = font("GARA.TTF", 104)
    italic = font("GARAIT.TTF", 104)
    centered_text(d, cx, 52 * s, [("Kilometer", roman, INK), ("buch", italic, INK)])

    # Strich unter dem Titel, Proportionen wie in den Screenshots
    d.rectangle([cx - 90 * s, 206 * s, cx + 90 * s, 209 * s], fill=INK)

    light = font("segoeuil.ttf", 30)
    centered_text(d, cx, 262 * s, [("Fahrten, Tanken und Termine", light, SOFT_INK)])
    caps = font("segoeuii.ttf", 30)
    left, right = centered_text(d, cx, 312 * s, [("EINFACH IM BLICK", caps, SOFT_INK)])
    d.rectangle([left, 352 * s, right, 354 * s], fill=SOFT_INK)

    # Pfeil vom Text zum Symbol: der Pfeil aus den Screenshots, gespiegelt und gedreht, zeigt nach links unten.
    arrow = arrow_from_screenshot().transpose(Image.FLIP_LEFT_RIGHT).rotate(-62, expand=True, resample=Image.BICUBIC)
    arrow = arrow.resize((arrow.width * s * 3 // 4, arrow.height * s * 3 // 4), Image.LANCZOS)
    # Die Spitze liegt im gedrehten Bild etwa 18/95 Punkte vom Ursprung; sie soll auf die rechte Symbolkante zeigen.
    img.paste(arrow, (344 * s, 160 * s), arrow)

    img.resize((1024, 500), Image.LANCZOS).save(os.path.join(HERE, "feature-graphic-1024x500.png"))


if __name__ == "__main__":
    icon(BLUE, WHITE, "icon-512.png")
    feature_graphic()
    print("Erzeugt: icon-512.png, feature-graphic-1024x500.png")
