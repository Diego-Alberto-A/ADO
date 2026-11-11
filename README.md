# Crosswalker for iPhone and iPad

Native Swift 5 code, SwiftUI screens, a Core Graphics board, AVFoundation audio, and UserDefaults persistence. iOS/iPadOS 16+, Xcode 16+. No external packages are required. Swift application code is in `Crosswalker/`, with the entry point in `Crosswalker/CrosswalkerApp.swift`. This branch is a standalone iOS project; the Android project is maintained on the `android` and `main` branches.

## Run and test

Open `Crosswalker.xcodeproj`, select the **Crosswalker** scheme, select an installed iPhone or iPad simulator, and Run. Use Product → Test to run unit and UI tests. Select a development team for device signing.

The simulation and repository also build independently of the app:

The root `.github/workflows/swift.yml` workflow runs core tests and app tests on iPhone and iPad simulators after a push. It has not been executed in this session.

```sh
swift test
```

For the full app, list installed simulator destinations, then use an available simulator ID:

```sh
xcodebuild -project Crosswalker.xcodeproj -scheme Crosswalker -showdestinations
xcodebuild -project Crosswalker.xcodeproj -scheme Crosswalker -destination 'platform=iOS Simulator,id=SIMULATOR_ID' test CODE_SIGNING_ALLOWED=NO
```

## Coverage of the original

| Original | Swift implementation |
| --- | --- |
| Simulation.kt | Core/Simulation.swift: Float arithmetic, 50 ms steps, traffic clocks, hazard prediction, all nine actions, daytime/nighttime visibility, emergency stop, collision retry, timeout and stars |
| GameRepository.kt | Core/GameRepository.swift: unchanged campaign JSON, best stars, sequential unlocks, sound/guidance settings |
| MainActivity.kt / Session | Session.swift and CrosswalkerApp.swift: budget enforcement, per-person assignment, selections, run/reset/retry, next level, local progress, lifecycle pause and sounds |
| HomeMenu.kt | UI/Screens.swift: diorama, letter logo, play, settings and achievements |
| WorldMap / Game | UI/Screens.swift: all worlds, locked/unlocked levels, level briefing, walkers, crossing buttons, tool categories, guidance, budget, emergency control |
| Board.kt | UI/Board.swift: same 360 × 460 coordinate system, materials, props, traffic, crosswalks, route previews, sprites, night overlay and stop indicator |
| CraftArt.kt | UI/CraftArt.swift: original textures, cut-paper polygon, font, chips, stars, artwork and button press feedback |
| Transitions.kt | UI/Transitions.swift: frozen outgoing screen, forward/back circular reveal, 420 ms world slide, 700 ms screen transition, input lock |
| PaperDialog | UI/Dialogs.swift: 260 ms entry, 170 ms deferred exit, nested pause/settings, results and plan-preserving retry |

All 21 drawable images are converted from the original WebP files to PNG without changing decoded pixels. The campaign, font and font license are copied byte-for-byte. Sound clips reproduce the Android tone frequencies/cadences, capped to the original call durations; audio output is subject to the platform mixer. The original tone definitions are in [AOSP ToneGenerator](https://android.googlesource.com/platform/frameworks/base/+/4dfbec2/media/libmedia/ToneGenerator.cpp).

The iOS app stores its own progress. Android SharedPreferences are not transferred between devices. iOS system navigation, safe areas, switches, and system accessibility behavior necessarily use Apple equivalents. The in-game design and rules are ported from the original; visual equivalence is not yet certified.

## Artwork packaging

All 21 named images, including `material_cream`, `material_kraft`, `material_felt`, and `home_diorama`, are universal image sets in `Crosswalker/Assets.xcassets`. Xcode compiles the catalog into the application for both SwiftUI and UIKit name lookup. `Resources/` contains campaign data, audio, the font and license, and the privacy manifest; artwork is not copied there as loose PNG files.

`AssetTests` loads every image from the built app bundle, checks the font/audio/campaign, and renders the SwiftUI material backgrounds to catch blank output. The static validator checks the image-set manifests and resource build-phase membership as well as source pixel identity.

Open the root `Crosswalker.xcodeproj` from this branch. After updating from the old layout, close the old project, open this project, clean the build folder, and rebuild.

## Verification status

- Executed the original Kotlin simulation to generate **360 reference trajectories and 4,069 checkpoints** across all levels, crossing positions, skill variants, street protections, and emergency use. `SimulationTests` compares Swift positions, traffic positions, completion flags, phases, elapsed times and scores against those traces.
- Added the original simulation regression cases, affordable campaign solutions, persistence/settings tests, session budget/pause/reset/win tests, and UI navigation/result tests.
- Checked packaged campaign/font/license identity, image pixel equality, asset references, project references, and resource metadata on Windows.
- **Not executed here:** Swift compilation, XCTest, iPhone/iPad simulator runs, physical-device audio or visual comparison. The host has no Swift compiler or Apple SDK. Passing resource validation does not establish that the app builds or that it matches every rendered pixel.

## Reproducibility

From the repository root:

```sh
python tools/package_swift.py
python tools/swift_reference_traces.py
python tools/validate_swift_port.py
```

Packaging requires FFmpeg. Reference generation requires Java and the Kotlin 2.2.10 compiler/dependencies in the existing Gradle cache (or adapt the script's tool paths). `Reference/` contains a frozen copy of the original assets and simulation used for migration verification. All three tools use that local snapshot without an Android checkout. Reference generation compiles that original `Simulation.kt` directly; the snapshot is not included in the iOS app or Swift package build.

Before accepting exact parity, run the full tests on both an iPhone and iPad, then compare home, every world, day/night boards, all tool states, nested dialogs, result flows, emergency use, background/resume, and all transitions with Android. The app retains the original portrait orientation on both device families. Verify small-screen scrolling and the guidance-off layout.
