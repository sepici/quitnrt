"""Dev tool: cuts one static weight of Schibsted Grotesk from the variable font, for res/font.

Every bundled weight was cut this way, from SchibstedGrotesk[wght].ttf version 1.100
(google/fonts, ofl/schibstedgrotesk), so a new weight matches the others glyph for glyph.
Needs fontTools.

Usage: python -I cut_font.py SchibstedGrotesk[wght].ttf 700 schibsted_grotesk_bold.ttf
"""
import sys

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

variable_path, weight, out_path = sys.argv[1], int(sys.argv[2]), sys.argv[3]
cut = instancer.instantiateVariableFont(TTFont(variable_path), {'wght': weight}, updateFontNames=True)
cut.save(out_path)
print(out_path, 'weight', TTFont(out_path)['OS/2'].usWeightClass)
