"""
Minimalist Focus Timer Logo Generator (Python + SVG + Sharp/Pillow)
Generates a clean monochrome stopwatch logo across Web (SVG, PNG, ICO)
and Android (adaptive foreground PNG, mipmap WebP icons).
"""

import os
import subprocess
import tempfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent

ICON_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="512" height="512">
  <rect width="64" height="64" rx="16" fill="#09090b"/>
  <rect x="2" y="2" width="60" height="60" rx="14" fill="none" stroke="rgba(255,255,255,0.16)" stroke-width="2"/>
  <line x1="26" y1="12" x2="38" y2="12" stroke="#ffffff" stroke-width="3.5" stroke-linecap="round"/>
  <line x1="32" y1="12" x2="32" y2="17" stroke="#ffffff" stroke-width="3" stroke-linecap="round"/>
  <circle cx="32" cy="35" r="17" fill="none" stroke="#ffffff" stroke-width="3.5"/>
  <path d="M32 25v10l7 4" fill="none" stroke="#ffffff" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"/>
</svg>
"""

ROUND_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="512" height="512">
  <circle cx="32" cy="32" r="32" fill="#09090b"/>
  <circle cx="32" cy="32" r="30" fill="none" stroke="rgba(255,255,255,0.16)" stroke-width="2"/>
  <line x1="26" y1="12" x2="38" y2="12" stroke="#ffffff" stroke-width="3.5" stroke-linecap="round"/>
  <line x1="32" y1="12" x2="32" y2="17" stroke="#ffffff" stroke-width="3" stroke-linecap="round"/>
  <circle cx="32" cy="35" r="17" fill="none" stroke="#ffffff" stroke-width="3.5"/>
  <path d="M32 25v10l7 4" fill="none" stroke="#ffffff" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"/>
</svg>
"""

ANDROID_FG_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="512" height="512">
  <line x1="26" y1="12" x2="38" y2="12" stroke="#ffffff" stroke-width="3.5" stroke-linecap="round"/>
  <line x1="32" y1="12" x2="32" y2="17" stroke="#ffffff" stroke-width="3" stroke-linecap="round"/>
  <circle cx="32" cy="35" r="17" fill="none" stroke="#ffffff" stroke-width="3.5"/>
  <path d="M32 25v10l7 4" fill="none" stroke="#ffffff" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"/>
</svg>
"""


def main():
    public_dir = ROOT / "public"
    android_res = ROOT / "Focus_Timer_Android" / "app" / "src" / "main" / "res"

    icon_svg_path = public_dir / "icon.svg"
    icon_svg_path.write_text(ICON_SVG, encoding="utf-8")

    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = Path(tmp)
        round_svg_path = tmp_dir / "icon_round.svg"
        fg_svg_path = tmp_dir / "icon_fg.svg"
        round_svg_path.write_text(ROUND_SVG, encoding="utf-8")
        fg_svg_path.write_text(ANDROID_FG_SVG, encoding="utf-8")

        node_script = f"""
const sharp = require('sharp');
const path = require('path');

async function run() {{
  const iconSvg = {str(icon_svg_path)!r};
  const roundSvg = {str(round_svg_path)!r};
  const fgSvg = {str(fg_svg_path)!r};
  const publicDir = {str(public_dir)!r};
  const androidRes = {str(android_res)!r};

  await sharp(iconSvg).resize(512, 512).png().toFile(path.join(publicDir, 'icon.png'));
  await sharp(iconSvg).resize(192, 192).png().toFile(path.join(publicDir, '168_192_icon.png'));
  await sharp(fgSvg).resize(512, 512).png().toFile(path.join(androidRes, 'drawable', 'app_icon_fg.png'));

  const mipmaps = [
    ['mipmap-mdpi', 48],
    ['mipmap-hdpi', 72],
    ['mipmap-xhdpi', 96],
    ['mipmap-xxhdpi', 144],
    ['mipmap-xxxhdpi', 192],
  ];

  for (const [folder, size] of mipmaps) {{
    await sharp(iconSvg).resize(size, size).webp({{ quality: 95 }}).toFile(path.join(androidRes, folder, 'ic_launcher.webp'));
    await sharp(roundSvg).resize(size, size).webp({{ quality: 95 }}).toFile(path.join(androidRes, folder, 'ic_launcher_round.webp'));
  }}
  console.log('Rasterized PNG and WebP icons successfully.');
}}

run().catch(err => {{ console.error(err); process.exit(1); }});
"""
        subprocess.run(["node", "-e", node_script], cwd=ROOT, check=True)

    # Generate multi-resolution favicon.ico using Pillow
    img = Image.open(public_dir / "icon.png").convert("RGBA")
    img.save(
        public_dir / "favicon.ico",
        format="ICO",
        sizes=[(16, 16), (32, 32), (48, 48)],
    )
    print("Generated favicon.ico and all Web + Android logo assets.")


if __name__ == "__main__":
    main()
