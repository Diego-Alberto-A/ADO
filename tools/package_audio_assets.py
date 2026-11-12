#!/usr/bin/env python3
"""Copia y renombra el audio y el puntero de latestAssets/pointingHand a res/.

Source: latestAssets/pointingHand (sin procesar, fuera de git).
Output: app/src/main/res/raw, app/src/main/res/drawable-nodpi,
        app/src/main/assets/licenses/audio-credits.txt.

No se recomprime audio (ya viene comprimido); solo se copia y renombra.
`audios/SFX.zip` se ignora a propósito: no corresponde a ninguna mecánica del juego.
"""
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "latestAssets/pointingHand"
RAW = ROOT / "app/src/main/res/raw"
DRAWABLE = ROOT / "app/src/main/res/drawable-nodpi"
LICENSES = ROOT / "app/src/main/assets/licenses"

AUDIO = {
    "audios/menuSong/jump and run - tropics.ogg": "music_menu.ogg",
    "audios/world1Song/Market on the Sea - MP3.mp3": "music_world1.mp3",
    "audios/world2Song/TalkingCuteChiptune.mp3": "music_world2.mp3",
    "audios/world3Sonng/RPG_Town_Ambience_1.mp3": "music_world3.mp3",
    "audios/UISOUNDS/buttonPressing.mp3": "sfx_button.mp3",
    "audios/UISOUNDS/enteringLevel.mp3": "sfx_enter_level.mp3",
    "audios/gameOverEffect/collapsePedestrian.wav": "sfx_collapse.wav",
    "audios/gameOverEffect/songGameOver.mp3": "music_gameover.mp3",
    "audios/winningEffect/win.mp3": "music_win.mp3",
    "audios/characterSpeaking/talkingsynth.mp3": "voice_talking.mp3",
}

CREDITS = """Crédito de audio (pack latestAssets/pointingHand):

- music_menu.ogg: "Jump and Run - Tropics". Requiere enlazar a http://opengameart.org en créditos.
- music_world2.mp3: "Talking Cute Chiptune" de Alex McCulloch. Atribución apreciada, no obligatoria.
- music_world1.mp3, music_world3.mp3, sfx_collapse.wav, music_gameover.mp3, music_win.mp3,
  voice_talking.mp3, sfx_button.mp3, sfx_enter_level.mp3: licencias permisivas, uso libre.
"""


def copy_audio():
    RAW.mkdir(parents=True, exist_ok=True)
    for src_rel, dest_name in AUDIO.items():
        src = SRC / src_rel
        if not src.exists():
            raise FileNotFoundError(f"falta {src}")
        shutil.copyfile(src, RAW / dest_name)
        print(f"  {dest_name}")


def copy_pointer():
    DRAWABLE.mkdir(parents=True, exist_ok=True)
    img = Image.open(SRC / "images/pointer/hand.png").convert("RGBA")
    big = img.resize((img.width * 8, img.height * 8), Image.NEAREST)
    big.save(DRAWABLE / "pointer_hand.webp", "WEBP", lossless=True, quality=100, method=6)
    print("  pointer_hand.webp")


def write_credits():
    LICENSES.mkdir(parents=True, exist_ok=True)
    (LICENSES / "audio-credits.txt").write_text(CREDITS, encoding="utf-8")
    print("  audio-credits.txt")


if __name__ == "__main__":
    print("Audio:")
    copy_audio()
    print("Puntero:")
    copy_pointer()
    print("Licencias:")
    write_credits()
