"""Confere a identidade Mist Teal e a área segura do ícone adaptativo."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re, math
root = Path(__file__).resolve().parents[1]
a = '{http://schemas.android.com/apk/res/android}'
svg = ET.parse(root / 'docs/design/rendly/rendly-mist-teal.svg').getroot()
paths = list(svg)[1:]
for variant in ('foreground', 'monochrome'):
    vector = ET.parse(root / f'app/src/main/res/drawable/ic_launcher_{variant}.xml').getroot()
    group = vector[0]
    assert len(group) == len(paths) == 4
    for node, source in zip(group, paths):
        assert node.get(a+'pathData') == source.get('d')
        assert node.get(a+'fillColor') == ('#000000' if variant == 'monochrome' else source.get('fill'))
        # All paths use absolute commands. Their control points bound each Bézier curve.
        assert not re.search('[a-z]', source.get('d'))
        coords = list(map(float, re.findall(r'-?\d+(?:\.\d+)?', source.get('d'))))
        for x,y in zip(coords[::2], coords[1::2]):
            x=x*float(group.get(a+'scaleX'))+float(group.get(a+'translateX'))
            y=y*float(group.get(a+'scaleY'))+float(group.get(a+'translateY'))
            assert math.hypot(x-54,y-54) <= 33, 'Fora da área segura circular'
assert svg[0].get('fill') == '#DDF5F0'
print('Mist Teal: vetor, monocromático e área segura circular aprovados.')
