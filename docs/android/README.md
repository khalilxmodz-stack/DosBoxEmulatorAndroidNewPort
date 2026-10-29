# DOSBox Staging for Android (armeabi-v7a) — status: UNBUILT, UNTESTED

This directory documents the Android port of the real DOSBox Staging 0.84.0-alpha core.
**As of this commit nothing here has been compiled or run**: the work was prepared in an
environment with no Android NDK, no CMake and no network. First CI run will likely expose
compile or dependency errors. Do not treat any claim below as verified.

## What exists
| Piece | Location |
|---|---|
| CMake: Android platform, `armeabi-v7a` CPU branch (interpreter only), OpenGL off, `libmain.so` target | `CMakeLists.txt` |
| JNI glue (HOME/XDG env, working dir) | `src/platform/android/android_glue.cpp` |
| Gradle app (ABI filter `armeabi-v7a` only, minSdk 24) | `android/` |
| SDL3 activity subclass, first-run setup, game-folder import | `android/app/src/main/java/io/dosboxstaging/android/` |
| Multi-touch DOS keys (arrows, Ctrl, Alt, Shift, Space, Esc, Enter, Tab, Y, N) | `TouchControls.java` |
| CI | `.github/workflows/android.yml` |

## Design decisions (from the actual source)
* **No ARMv7 dynamic recompiler.** `src/cpu/core_dynrec/` only has `risc_x64.h` and
  `risc_armv8le.h` (AArch64). The 32-bit build uses the interpreter (`core = normal`), which is
  slower; whether DOOM is playable at full speed on a given phone is unmeasured.
* **No desktop OpenGL.** The GL renderer requests GL 3.3/4.3 core through glad; Android has GLES.
  The port builds with `OPT_OPENGL=OFF`, i.e. the existing SDL texture renderer.
* **Shared library, not executable.** SDL's Java activity `dlopen`s `libmain.so`.
* **Storage.** Games are imported (copied) from a folder chosen with the system picker into
  app-private `files/games`, mounted as `C:`. No storage permission and no INTERNET permission.
  Config: `files/config/dosbox/dosbox-staging.conf`, created once.
* **SDL Java glue** is cloned at CI time from the tag matching the packaged `libSDL3.so`
  (read from vcpkg), so Java and native SDL cannot drift apart.

## Using your own DOOM files
No game data is included. Copy your legally obtained DOOM folder (`DOOM.EXE`, `DOOM.WAD`, ...) to the
phone, tap the folder button (top-right of the touch overlay), pick it, then in DOSBox:
`RESCAN`, `CD DOOM`, `DOOM.EXE`. Unverified end-to-end.

## Build (CI)
Run the **Android ARMv7 (armeabi-v7a)** workflow; download artifact `DOSBox-Staging-Android-ARMv7`.
The workflow fails if libmain.so/libSDL3.so are not ELF32 ARM, if any dependency is neither packaged
nor an Android system library, if other ABIs or game data are in the APK, or if `aapt2` does not report
`native-code: 'armeabi-v7a'`.

## Known risks / not done
* vcpkg ports `fluidsynth`, `libmt32emu`, `opusfile`, `sdl3` (shared) for `arm-neon-android` are unverified.
  `sdl3` must resolve to a shared `libSDL3.so`; the workflow stops with an error otherwise.
* C++23 on NDK r29's libc++ is unverified for this code base.
* POSIX/Linux-only sources (e.g. `cdrom_ioctl_linux.cpp`, `fs_utils_posix.cpp`) are assumed to build on Bionic.
* Pause/resume, audio, orientation, touch behaviour and DOOM gameplay: no test hardware, not tested.
* Unit tests are disabled for the cross build (`OPT_TESTS=OFF`); the upstream Linux workflow still runs them on the host.
* A debug (debug-signed) APK only; no release signing.
