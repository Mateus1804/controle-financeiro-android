"""Validate Rendly's original geometry and Android launcher resources.

Python dependencies: scripts/requirements-rendly.txt (CairoSVG only for --render).
All PNGs are rendered deterministically from the supplied SVG, without image generation.
"""

import argparse
import hashlib
import io
import math
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFont
from svgpathtools import parse_path


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = ROOT / "docs/design/rendly/rendly-icone-curva.svg"
PREVIEWS = ROOT / "docs/imagens/rendly"
ANDROID = "{http://schemas.android.com/apk/res/android}"
SVG = "{http://www.w3.org/2000/svg}"
SOURCE_SHA256 = "a883a48aab7e09965f13cfcfb4bad7da3950a494de9358b41a56e5b96a88d2e0"
PALETTE = {"background": "#F7FBFC", "forest": "#014030", "mint": "#20CD91"}
PATH_IDS = ("curva-e-perna", "barra-1", "barra-2", "barra-3-haste")
SCALE = 0.70
TRANSLATE = 19.0875
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
MASKS = ("circle", "squircle", "rounded-square", "square")


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def source_paths():
    root = ET.parse(SOURCE).getroot()
    nodes = {node.get("id"): node for node in root.iter(SVG + "path")}
    require(tuple(nodes) == ("fundo-squircle",) + PATH_IDS, "SVG: caminhos alterados.")
    return nodes


def verify_vectors(nodes):
    all_bounds = []
    for variant in ("foreground", "monochrome"):
        path = RES / f"drawable/ic_launcher_{variant}.xml"
        vector = ET.parse(path).getroot()
        require(vector.tag == "vector", f"{path}: esperado VectorDrawable.")
        for key in ("width", "height"):
            require(vector.get(ANDROID + key) == "108dp", f"{path}: dimensão incorreta.")
        for key in ("viewportWidth", "viewportHeight"):
            require(vector.get(ANDROID + key) == "108", f"{path}: viewport incorreto.")
        require(len(vector) == 1 and vector[0].tag == "group", f"{path}: camadas extras.")
        group = vector[0]
        expected_transform = {"scaleX": SCALE, "scaleY": SCALE, "translateX": TRANSLATE, "translateY": TRANSLATE}
        require(set(group.attrib) == {ANDROID + key for key in expected_transform}, f"{path}: transformação extra.")
        for key, expected in expected_transform.items():
            require(float(group.get(ANDROID + key, "nan")) == expected, f"{path}: transformação {key} alterada.")
        require(len(group) == len(PATH_IDS), f"{path}: squircle/caminho extra no foreground.")
        for node, path_id in zip(group, PATH_IDS):
            require(node.tag == "path", f"{path}: elemento inesperado.")
            require(node.get(ANDROID + "name") == path_id, f"{path}: ordem dos caminhos alterada.")
            require(node.get(ANDROID + "pathData") == nodes[path_id].get("d"), f"{path}: geometria original alterada em {path_id}.")
            color = "#000000" if variant == "monochrome" else PALETTE["forest" if path_id == "curva-e-perna" else "mint"]
            require(node.get(ANDROID + "fillColor") == color, f"{path}: cor alterada em {path_id}.")
            require(set(node.attrib) == {ANDROID + key for key in ("name", "fillColor", "pathData")}, f"{path}: efeitos não autorizados.")
            if variant == "foreground":
                all_bounds.append(parse_path(nodes[path_id].get("d")).bbox())

    xmin = min(bounds[0] for bounds in all_bounds)
    xmax = max(bounds[1] for bounds in all_bounds)
    ymin = min(bounds[2] for bounds in all_bounds)
    ymax = max(bounds[3] for bounds in all_bounds)
    corners = [(SCALE * x + TRANSLATE, SCALE * y + TRANSLATE) for x in (xmin, xmax) for y in (ymin, ymax)]
    radius = max(math.hypot(x - 54, y - 54) for x, y in corners)
    require(radius <= 33, f"Monograma fora da área circular segura de 66dp: raio {radius:.4f}dp.")
    print(f"Monograma original: x={xmin:.2f}..{xmax:.2f}, y={ymin:.2f}..{ymax:.2f}; raio conservador {radius:.4f}dp < 33dp.")


def verify_resources():
    background = ET.parse(RES / "drawable/ic_launcher_background.xml").getroot()
    require(background.tag == "shape" and len(background) == 1, "Background deve ser uma camada independente e sem máscara.")
    require(background[0].tag == "solid" and background[0].get(ANDROID + "color") == PALETTE["background"], "Background deve usar #F7FBFC.")
    for version in (26, 33):
        for suffix in ("", "_round"):
            resource = RES / f"mipmap-anydpi-v{version}/ic_launcher{suffix}.xml"
            icon = ET.parse(resource).getroot()
            expected = {"background": "@drawable/ic_launcher_background", "foreground": "@drawable/ic_launcher_foreground"}
            if version == 33:
                expected["monochrome"] = "@drawable/ic_launcher_monochrome"
            require(icon.tag == "adaptive-icon", f"{resource}: esperado adaptive-icon.")
            require({node.tag: node.get(ANDROID + "drawable") for node in icon} == expected and len(icon) == len(expected), f"{resource}: camadas incorretas.")
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot().find("application")
    require(manifest.get(ANDROID + "icon") == "@mipmap/ic_launcher", "Manifesto: ícone padrão incorreto.")
    require(manifest.get(ANDROID + "roundIcon") == "@mipmap/ic_launcher_round", "Manifesto: ícone redondo incorreto.")
    require(manifest.get(ANDROID + "label") == "@string/app_name", "Nome do aplicativo alterado.")


def raster(svg, size):
    import cairosvg

    root = ET.fromstring(svg)
    root.set("width", str(size * 8))
    root.set("height", str(size * 8))
    rendered = cairosvg.svg2png(bytestring=ET.tostring(root))
    return Image.open(io.BytesIO(rendered)).convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)


def monogram_svg(nodes, monochrome=None):
    root = ET.Element("svg", {"xmlns": "http://www.w3.org/2000/svg", "viewBox": "18 18 72 72"})
    group = ET.SubElement(root, "g", {"transform": f"translate({TRANSLATE} {TRANSLATE}) scale({SCALE})"})
    for path_id in PATH_IDS:
        color = monochrome or PALETTE["forest" if path_id == "curva-e-perna" else "mint"]
        ET.SubElement(group, "path", {"fill": color, "d": nodes[path_id].get("d")})
    return ET.tostring(root)


def mask(shape, size):
    high = size * 8
    result = Image.new("L", (high, high), 0)
    draw = ImageDraw.Draw(result)
    extent = (0, 0, high - 1, high - 1)
    if shape == "circle":
        draw.ellipse(extent, fill=255)
    elif shape == "rounded-square":
        draw.rounded_rectangle(extent, radius=high / 5, fill=255)
    elif shape == "squircle":
        points = []
        for index in range(4096):
            angle = math.tau * index / 4096
            cosine, sine = math.cos(angle), math.sin(angle)
            points.append(((high - 1) / 2 * (1 + math.copysign(math.sqrt(abs(cosine)), cosine)), (high - 1) / 2 * (1 + math.copysign(math.sqrt(abs(sine)), sine))))
        draw.polygon(points, fill=255)
    else:
        require(shape == "square", f"Máscara desconhecida: {shape}")
        draw.rectangle(extent, fill=255)
    return result.resize((size, size), Image.Resampling.LANCZOS)


def composite(mark, shape, background=PALETTE["background"]):
    icon = Image.new("RGBA", mark.size, background)
    icon.alpha_composite(mark)
    icon.putalpha(mask(shape, mark.width))
    return icon


def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=False, compress_level=9)


def render(nodes):
    original = SOURCE.read_bytes()
    round_svg = ET.fromstring(original)
    # Keep title/description and the monogram exactly; replace only the background.
    background = next(node for node in round_svg if node.get("id") == "fundo-squircle")
    round_svg.remove(background)
    round_svg.insert(list(round_svg).index(next(node for node in round_svg if node.get("id") == "monograma")), ET.Element(SVG + "circle", {"cx": "50", "cy": "50", "r": "50", "fill": PALETTE["background"]}))
    for density, size in DENSITIES.items():
        for suffix, svg in (("", original), ("_round", ET.tostring(round_svg))):
            save(raster(svg, size), RES / f"mipmap-{density}/ic_launcher{suffix}.png")

    previews = {"original-32": raster(original, 32)}
    mark = raster(monogram_svg(nodes), 32)
    for shape in MASKS:
        previews[f"adaptive-{shape}-32"] = composite(mark, shape)
    for label, foreground, background in (("light", "#014030", "#CCE9DE"), ("dark", "#AAEFD0", "#073D2D")):
        previews[f"monochrome-{label}-32"] = composite(raster(monogram_svg(nodes, foreground), 32), "circle", background)
    for name, icon in previews.items():
        save(icon, PREVIEWS / f"{name}.png")

    sheet = Image.new("RGB", (1008, 514), "#E8ECEE")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=16)
    for index, (name, icon) in enumerate(previews.items()):
        x, y = 16 + index % 4 * 248, 20 + index // 4 * 248
        draw.text((x, y), name.replace("-32", ""), fill="#153C31", font=font)
        sheet.paste(icon.resize((128, 128), Image.Resampling.NEAREST), (x + 48, y + 30), icon.resize((128, 128), Image.Resampling.NEAREST))
        sheet.paste(icon, (x + 96, y + 178), icon)
        draw.text((x + 60, y + 216), "128px / 32px", fill="#153C31", font=font)
    save(sheet, PREVIEWS / "launcher-masks.png")

    # Show the complete 108dp layer, the launcher viewport, and the 66dp safe circle.
    safe_svg = ET.fromstring(monogram_svg(nodes))
    safe_svg.set("viewBox", "0 0 108 108")
    safe = Image.new("RGBA", (648, 648), PALETTE["background"])
    safe.alpha_composite(raster(ET.tostring(safe_svg), 648))
    guide = ImageDraw.Draw(safe)
    guide.rectangle((108, 108, 540, 540), outline="#9B7188", width=2)
    guide.ellipse((126, 126, 522, 522), outline="#9B7188", width=2)
    save(safe, PREVIEWS / "adaptive-safe-area.png")
    print("PNGs legados e prévias regenerados a partir do SVG original.")


def verify_pngs():
    for density, size in DENSITIES.items():
        for suffix in ("", "_round"):
            path = RES / f"mipmap-{density}/ic_launcher{suffix}.png"
            with Image.open(path) as icon:
                require(icon.size == (size, size), f"{path}: dimensão incorreta.")
                require(icon.convert("RGBA").getpixel((0, 0))[3] == 0, f"{path}: máscara sem transparência.")
                require(icon.convert("RGBA").getpixel((size // 2, size // 8))[:3] == (247, 251, 252), f"{path}: fundo alterado.")
    for name in ("original", *(f"adaptive-{shape}" for shape in MASKS), "monochrome-light", "monochrome-dark"):
        with Image.open(PREVIEWS / f"{name}-32.png") as icon:
            require(icon.size == (32, 32), f"{name}: prévia precisa ter 32px.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--render", action="store_true", help="Regenerar PNGs legados e prévias locais antes de validar.")
    args = parser.parse_args()
    require(hashlib.sha256(SOURCE.read_bytes()).hexdigest() == SOURCE_SHA256, "O SVG original deve permanecer byte a byte igual ao arquivo fornecido.")
    nodes = source_paths()
    verify_vectors(nodes)
    verify_resources()
    if args.render:
        render(nodes)
    verify_pngs()
    print("Rendly conferido: SVG, 4 caminhos/cores originais, camadas, área segura, adaptive API26/API33, monochrome, 10 PNGs legados e prévias de 32px.")


if __name__ == "__main__":
    main()
