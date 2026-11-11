# Crosswalker

An offline Android puzzle game about helping pedestrians cross the street safely. Choose crossings, assign safety actions, and place street protections within a shared token budget. Run your plan, avoid traffic, and earn stars to unlock more levels.

The game has 12 levels across three worlds and Spanish text.

## Artwork

This branch re-skins the street scene with 16×16 pixel art from the
[Kenney RPG Urban pack](https://kenney.nl) (CC0). Pedestrians have a three-frame
walk cycle; cars, trees, buildings, crossings and the tool icons all come from
the same tileset, so the board reads as one city.

The paper-and-cardboard originals are still under `art/source`. Runtime sprites
are generated from the tilemap, never edited by hand:

```
python3 tools/package_kenney_assets.py
```

## Run

Open the project in Android Studio and run the `app` module on an emulator or a device running Android 7.0 or later.

Built with Kotlin and Jetpack Compose.
