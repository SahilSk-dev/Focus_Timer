import math
import os

svg = []
svg.append('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 1000" width="800" height="1000">')
svg.append('<defs>')
svg.append('''
    <linearGradient id="gold1" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="#FFF2B2"/>
        <stop offset="25%" stop-color="#D4AF37"/>
        <stop offset="50%" stop-color="#8A6327"/>
        <stop offset="75%" stop-color="#D4AF37"/>
        <stop offset="100%" stop-color="#FFF2B2"/>
    </linearGradient>
    <linearGradient id="gold2" x1="100%" y1="0%" x2="0%" y2="100%">
        <stop offset="0%" stop-color="#B8860B"/>
        <stop offset="30%" stop-color="#FFD700"/>
        <stop offset="50%" stop-color="#FFF8DC"/>
        <stop offset="70%" stop-color="#FFD700"/>
        <stop offset="100%" stop-color="#B8860B"/>
    </linearGradient>
    <linearGradient id="gold3" x1="0%" y1="50%" x2="100%" y2="50%">
        <stop offset="0%" stop-color="#D4AF37"/>
        <stop offset="50%" stop-color="#FFF8DC"/>
        <stop offset="100%" stop-color="#D4AF37"/>
    </linearGradient>
    <radialGradient id="dial" cx="50%" cy="50%" r="50%">
        <stop offset="70%" stop-color="#1A1D24"/>
        <stop offset="100%" stop-color="#0A0B0E"/>
    </radialGradient>
    <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
        <feGaussianBlur stdDeviation="3" result="blur" />
        <feComposite in="SourceGraphic" in2="blur" operator="over" />
    </filter>
    <filter id="drop-shadow" x="-20%" y="-20%" width="140%" height="140%">
        <feDropShadow dx="2" dy="5" stdDeviation="5" flood-color="#000" flood-opacity="0.7"/>
    </filter>
''')
svg.append('</defs>')

cx, cy = 400, 550

# Top Crown (Center)
svg.append(f'<rect x="{cx-40}" y="50" width="80" height="100" fill="url(#gold3)" rx="5"/>')
svg.append(f'<circle cx="{cx}" cy="80" r="50" fill="none" stroke="url(#gold1)" stroke-width="15"/>') # Top ring
for i in range(7):
    x = cx - 30 + i * 10
    svg.append(f'<line x1="{x}" y1="40" x2="{x}" y2="80" stroke="#8A6327" stroke-width="3"/>')

def add_pusher(angle_deg):
    angle = math.radians(angle_deg - 90)
    px = cx + 330 * math.cos(angle)
    py = cy + 330 * math.sin(angle)
    svg.append(f'<g transform="translate({px}, {py}) rotate({angle_deg})">')
    svg.append(f'<rect x="-25" y="-80" width="50" height="80" fill="url(#gold2)" rx="5"/>')
    svg.append(f'<rect x="-35" y="-100" width="70" height="20" fill="url(#gold1)" rx="3"/>')
    svg.append('</g>')

add_pusher(-35)
add_pusher(35)

# Main case
svg.append(f'<circle cx="{cx}" cy="{cy}" r="380" fill="url(#gold1)" filter="url(#drop-shadow)"/>')
svg.append(f'<circle cx="{cx}" cy="{cy}" r="350" fill="url(#gold2)"/>')
svg.append(f'<circle cx="{cx}" cy="{cy}" r="320" fill="#000000"/>')
svg.append(f'<circle cx="{cx}" cy="{cy}" r="300" fill="url(#dial)"/>')

# Abstract gears
svg.append(f'<circle cx="{cx}" cy="{cy}" r="220" fill="none" stroke="url(#gold3)" stroke-width="2" opacity="0.4"/>')
svg.append(f'<circle cx="{cx}" cy="{cy}" r="150" fill="none" stroke="url(#gold3)" stroke-width="10" stroke-dasharray="20 10" opacity="0.3"/>')

# Tick marks
for i in range(60):
    angle = i * 6
    is_hour = i % 5 == 0
    length = 20 if is_hour else 10
    width = 4 if is_hour else 2
    color = "#D4AF37" if is_hour else "#A9A9A9"
    y1 = cy - 290
    y2 = y1 + length
    svg.append(f'<line x1="{cx}" y1="{y1}" x2="{cx}" y2="{y2}" stroke="{color}" stroke-width="{width}" transform="rotate({angle}, {cx}, {cy})"/>')

# Minute numbers
for i in range(12):
    num = (i * 5)
    if num == 0: num = 60
    angle = i * 30
    rad = math.radians(angle - 90)
    tx = cx + 250 * math.cos(rad)
    ty = cy + 250 * math.sin(rad) + 6
    if i % 3 != 0: 
        svg.append(f'<text x="{tx}" y="{ty}" fill="#D4AF37" font-family="Arial, sans-serif" font-size="16" text-anchor="middle">{num:02d}</text>')

# Roman Numerals
romans = {0: 'XII', 90: 'III', 180: 'VI', 270: 'IX'}
for angle, numeral in romans.items():
    rad = math.radians(angle - 90)
    tx = cx + 250 * math.cos(rad)
    ty = cy + 250 * math.sin(rad) + 12
    svg.append(f'<text x="{tx}" y="{ty}" fill="url(#gold1)" font-family="Times New Roman, serif" font-size="42" font-weight="bold" text-anchor="middle" filter="url(#glow)">{numeral}</text>')

# Subdials
def add_subdial(sdx, sdy, max_val):
    svg.append(f'<circle cx="{sdx}" cy="{sdy}" r="80" fill="#0D1016" stroke="url(#gold2)" stroke-width="3"/>')
    for i in range(12):
        angle = i * 30
        y1 = sdy - 75
        y2 = y1 + 10
        svg.append(f'<line x1="{sdx}" y1="{y1}" x2="{sdx}" y2="{y2}" stroke="#D4AF37" stroke-width="2" transform="rotate({angle}, {sdx}, {sdy})"/>')
    svg.append(f'<text x="{sdx}" y="{sdy-45}" fill="#FFF" font-family="Arial" font-size="14" text-anchor="middle">{max_val}</text>')
    svg.append(f'<text x="{sdx+45}" y="{sdy+5}" fill="#FFF" font-family="Arial" font-size="14" text-anchor="middle">{max_val//4}</text>')
    svg.append(f'<text x="{sdx}" y="{sdy+55}" fill="#FFF" font-family="Arial" font-size="14" text-anchor="middle">{max_val//2}</text>')
    svg.append(f'<text x="{sdx-45}" y="{sdy+5}" fill="#FFF" font-family="Arial" font-size="14" text-anchor="middle">{(max_val*3)//4}</text>')
    svg.append(f'<g transform="rotate({30 if max_val==60 else 120}, {sdx}, {sdy})">')
    svg.append(f'<line x1="{sdx}" y1="{sdy}" x2="{sdx}" y2="{sdy-60}" stroke="url(#gold1)" stroke-width="3" stroke-linecap="round"/>')
    svg.append(f'<circle cx="{sdx}" cy="{sdy}" r="5" fill="url(#gold3)"/>')
    svg.append('</g>')

add_subdial(cx, cy - 110, 60)
add_subdial(cx, cy + 110, 60)

# Digital displays
def add_digital_display(dx, dy, time_str, label, glow=False):
    svg.append(f'<rect x="{dx-85}" y="{dy-35}" width="170" height="70" fill="#0A0B0E" stroke="#333" stroke-width="2" rx="10"/>')
    color = "#FFFFFF" if glow else "#D4AF37"
    fil = 'filter="url(#glow)"' if glow else ''
    svg.append(f'<text x="{dx}" y="{dy+10}" fill="{color}" font-family="monospace" font-size="40" font-weight="bold" text-anchor="middle" {fil}>{time_str}</text>')
    svg.append(f'<text x="{dx}" y="{dy+25}" fill="#AAAAAA" font-family="Arial" font-size="11" text-anchor="middle">{label}</text>')

add_digital_display(cx - 130, cy, "00:04", "Minutes    Seconds", glow=True)
add_digital_display(cx + 130, cy, "38:25", "Seconds Milliseconds", glow=False)

# Hands
svg.append(f'<g transform="rotate(-60, {cx}, {cy})">')
svg.append(f'<path d="M{cx-8} {cy} L{cx} {cy-180} L{cx+8} {cy} Z" fill="url(#gold1)" filter="url(#drop-shadow)"/>')
svg.append(f'<path d="M{cx-4} {cy} L{cx} {cy-170} L{cx+4} {cy} Z" fill="#FFFFFF"/>')
svg.append('</g>')

svg.append(f'<g transform="rotate(60, {cx}, {cy})">')
svg.append(f'<path d="M{cx-6} {cy} L{cx} {cy-260} L{cx+6} {cy} Z" fill="url(#gold2)" filter="url(#drop-shadow)"/>')
svg.append(f'<path d="M{cx-3} {cy} L{cx} {cy-250} L{cx+3} {cy} Z" fill="#FFFFFF"/>')
svg.append('</g>')

svg.append(f'<g transform="rotate(0, {cx}, {cy})">')
svg.append(f'<line x1="{cx}" y1="{cy+40}" x2="{cx}" y2="{cy-280}" stroke="#D4AF37" stroke-width="3" filter="url(#drop-shadow)"/>')
svg.append(f'<circle cx="{cx}" cy="{cy-240}" r="6" fill="#0D1016" stroke="#D4AF37" stroke-width="2"/>')
svg.append('</g>')

svg.append(f'<circle cx="{cx}" cy="{cy}" r="12" fill="url(#gold3)" filter="url(#drop-shadow)"/>')
svg.append(f'<circle cx="{cx}" cy="{cy}" r="6" fill="#8A6327"/>')

svg.append(f'<path id="top-curve" d="M {cx-150} {cy-330} A 330 330 0 0 1 {cx+150} {cy-330}" fill="none"/>')
svg.append(f'<text fill="#A9A9A9" font-family="Arial" font-size="14" letter-spacing="3"><textPath href="#top-curve" startOffset="50%" text-anchor="middle">LEGENDARY TIMEKEEPER</textPath></text>')

svg.append(f'<path id="bottom-curve" d="M {cx-150} {cy+330} A 330 330 0 0 0 {cx+150} {cy+330}" fill="none"/>')
svg.append(f'<text fill="#A9A9A9" font-family="Arial" font-size="14" letter-spacing="3"><textPath href="#bottom-curve" startOffset="50%" text-anchor="middle">PRECISION CHRONOGRAPH</textPath></text>')

svg.append('</svg>')

with open(r"c:\Users\sahil\Documents\10_Sahil_Workspace\python\golden_stopwatch.svg", "w", encoding="utf-8") as f:
    f.write("\\n".join(svg))
print("SVG file created successfully!")
