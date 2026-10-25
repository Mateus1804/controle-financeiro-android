"""Confere pares explícitos de texto/fundo; não substitui a inspeção de acessibilidade."""
import re
from pathlib import Path

theme = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/mateuswilliam/financas/ui/Theme.kt").read_text()
named = dict(re.findall(r"val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)", theme))


def luminance(color):
    channels = [int(color[index:index + 2], 16) / 255 for index in (0, 2, 4)]
    linear = [value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4 for value in channels]
    return sum(value * weight for value, weight in zip(linear, (0.2126, 0.7152, 0.0722)))


checks = 0
for name in ("Light", "Dark"):
    body = re.search(rf"private val {name} = \w+ColorScheme\((.*?)\n\)", theme, re.S).group(1)
    colors = {}
    for key, value in re.findall(r"(\w+)\s*=\s*(Color\(0xFF[0-9A-Fa-f]{6}\)|Color.White|\w+)", body):
        colors[key] = "FFFFFF" if value == "Color.White" else value[10:-1] if value.startswith("Color(0xFF") else named[value]
    for foreground, background in (("onBackground", "background"), ("onSurface", "surface"), ("onSurfaceVariant", "surfaceVariant"),
                                   ("onPrimaryContainer", "primaryContainer"), ("onSecondaryContainer", "secondaryContainer"),
                                   ("onPrimary", "primary"), ("tertiary", "surface"), ("error", "surface")):
        low, high = sorted((luminance(colors[foreground]), luminance(colors[background])))
        ratio = (high + 0.05) / (low + 0.05)
        if ratio < 4.5:
            raise SystemExit(f"{name}: {foreground}/{background} com contraste {ratio:.2f}:1")
        print(f"{name}: {foreground}/{background} = {ratio:.2f}:1")
        checks += 1
print(f"{checks} pares explícitos com contraste mínimo de 4,5:1.")
