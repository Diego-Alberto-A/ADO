# Crosswalker — the classroom craft box

## Construction rules

Build every silhouette from recognisable simple pieces, then add the material. A bus is a squat rectangle, two window rectangles and two button wheels. A paper child is a circle, tunic, two arms and two legs. Houses use a rectangle and triangle. Trees use overlapping felt circles. Stars have five broad points.

All pieces come from the same imaginary classroom box: 2 mm corrugated cardboard, fibrous construction paper, sage felt, black buttons, a glue stick and masking tape. Imperfections belong to construction: scissor-cut edges, slightly crooked layers, restrained pencil marks and small exposed cardboard borders. Avoid decorative dirt, random damage, glossy plastic, vector outlines, excessive detail and synthetic gradients.

## Palette and lighting

Kraft #CAA574; cream #F5E6C5; sage #637F57; coral #BA5845; mustard #D9AC49; dusty blue #5688A1. All photography uses soft upper-left daylight, low relief and sharp focus. The nighttime world applies its existing lighting overlay to the same materials.

## Production contract

Each runtime sprite is an individual transparent image. No screenshot is used as a playable board. Vehicle sprites face right, and the renderer retains its existing mirroring. UI text, budgets, paths, selections and level numbers stay live and accessible. Texture fills and decorative shapes are presentation only. Simulation, campaign data, progression, prices and controls must not change.

The initial `source/style-guide.png` is the common visual reference for subsequent assets. Original built-in image-generation outputs are kept under `source/`; prompt records are under `prompts/`. Runtime WebP files are sized for mobile use and preserve source alpha.

## Deliverables and verification

21 runtime images live in app/src/main/res/drawable-nodpi; full-resolution originals are in art/source. All artwork was produced with built-in ImageGen from the shared structural reference. Exact generation prompts are in art/prompts/production.json and art/prompts/tool-icons.json.

The updated APK was installed and reviewed in the Pixel 10 Pro emulator: home, world map, level card, planning, selected action, pause, and a completed level. Screenshots are saved in art/screenshots. Build and lint pass with zero errors. Gameplay-file hashes and the Session class match their pre-artwork baselines. The gameplay unit tests also pass.

