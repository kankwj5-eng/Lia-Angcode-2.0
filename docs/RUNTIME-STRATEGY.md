# Runtime strategy

## Android execution constraint

Android 10 introduced a W^X restriction: apps targeting API 29+ cannot directly execute files from their writable app home directory. A Termux-style package manager installs executables into that writable area, so blindly raising the runtime target SDK breaks the core model.

For the first working architecture, AngCode compiles against the current SDK but deliberately targets API 28 while the embedded runtime is being integrated. This keeps the execution model compatible with the upstream Termux approach.

Longer term we will evaluate two paths:

1. **Single APK / target 28** — simplest and closest to Termux; intended for sideloaded development builds.
2. **Split frontend + runtime companion** — modern-target frontend communicating with a signed runtime companion that owns executable workspaces.

We will not pretend PRoot fixes the W^X restriction: PRoot is a user-space compatibility layer and does not replace Android's package/runtime security model.

## Upstream pieces to adapt

From the supplied Termux source snapshot, the main upstream areas under audit are:

- TermuxInstaller / bootstrap extraction;
- TermuxService / command and session lifecycle;
- RunCommandService behavior and result handling;
- termux-shared shell/environment/path utilities;
- terminal emulator/view only where a visible interactive terminal is required.

The new UI will not be based on TermuxActivity.
