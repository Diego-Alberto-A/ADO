# Crosswalker

An offline Android puzzle game about helping pedestrians cross the street safely. Choose crossings, assign safety actions, and place street protections within a shared token budget. Run your plan, avoid traffic, and earn stars to unlock more levels.

The game has 12 levels across three worlds and Spanish text.

## Artwork

This branch re-skins the street scene with 16×16 pixel art from the
[Kenney RPG Urban pack](https://kenney.nl) (CC0). Pedestrians have a three-frame
walk cycle; cars, trees, buildings, crossings and the tool icons all come from
the same tileset, so the board reads as one city.

The interface is drawn to the same rules: one-pixel ink outlines, solid drop
shadows, light from above, 2x2 dithering instead of soft alpha, and a palette
sampled from the tiles. Dialogue boxes type themselves out with a tilted name
plate, in the Animal Crossing / classic Pokemon manner.

Type is Pixelify Sans (body), Jersey 10 (titles) and Silkscreen (HUD), all OFL;
their licences are in `app/src/main/assets/licenses`.

Mateo's wheelchair comes from "[LPC Revised] Elders!" by Eliza Wyatt et al.
(OGA-BY 3.0, credit in `app/src/main/assets/licenses`), halved to the
tileset's 16 px scale and combined with a Kenney character. Tomás carries a
phone marker made from `art/phones/nokia.png`. The full source packs live in
`NewAssets/` (ignored by git); only the files the pipeline reads are copied
into `art/`.

The paper-and-cardboard originals are still under `art/source`. Runtime sprites
are generated from the tilemap, never edited by hand:

```
python3 tools/package_kenney_assets.py
```

## Run

Open the project in Android Studio and run the `app` module on an emulator or a device running Android 7.0 or later.

Built with Kotlin and Jetpack Compose.
