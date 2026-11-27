# Crosswalker — the classroom craft box

## Construction rules

Build every silhouette from recognisable simple pieces, then add the material. A bus is a squat rectangle, two window rectangles and two button wheels. A paper child is a circle, tunic, two arms and two legs. Houses use a rectangle and triangle. Trees use overlapping felt circles. Stars have five broad points.

All pieces come from the same imaginary classroom box: 2 mm corrugated cardboard, fibrous construction paper, sage felt, black buttons, a glue stick and masking tape. Imperfections belong to construction: scissor-cut edges, slightly crooked layers, restrained pencil marks and small exposed cardboard borders. Avoid decorative dirt, random damage, glossy plastic, vector outlines, excessive detail and synthetic gradients.

## Palette and lighting

Kraft #CAA574; cream #F5E6C5; sage #637F57; coral #BA5845; mustard #D9AC49; dusty blue #5688A1. All photography uses soft upper-left daylight, low relief and sharp focus. The nighttime world applies its existing lighting overlay to the same materials.

## Production contract

Each runtime sprite is an individual transparent image. No screenshot is used as a playable board. Vehicle sprites face right, and the renderer retains its existing mirroring. UI text, budgets, paths, selections and level numbers stay live and accessible. Texture fills and decorative shapes are presentation only. Simulation, campaign data, progression, prices and controls must not change.

Use `source/style-guide.png` as the shared visual reference. Full-resolution artwork is in `source/`. Runtime WebP files are sized for mobile use and preserve source alpha.

## Deliverables and verification

Runtime images live in `assets/game/res/drawable-nodpi/`. To package the craft artwork on Windows, run `tools/package_art.ps1` with FFmpeg and Python with Pillow available on PATH.

The updated APK was installed and reviewed in the Pixel 10 Pro emulator: home, world map, level card, planning, selected action, pause, and a completed level. Screenshots are saved in assets/source/art/screenshots. Build and lint pass with zero errors. Gameplay-file hashes and the Session class match their pre-artwork baselines. The gameplay unit tests also pass.

