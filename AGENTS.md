# Local build and installation workflow

User preference (2026-10-07): after changes to Orbis, build and validate the affected artifacts, then install the resulting build directly into the Minecraft 26.3 profile without asking again.

- Target profile: `%APPDATA%\ModrinthApp\profiles\Fabric 26.3`.
- Install into its `mods` directory. For atlas/minimap work, install the debug JAR (`-PorbisDebug=true`), which includes Orbis itself; never install normal and debug Orbis together.
- Run the appropriate tests and `tools/verify_standalone.py` (with `--debug` for debug artifacts). Keep normal/debug packaging separate. Do not install a failed build or a sources JAR.
- Preserve the build output in the repository and copy the verified JAR into the profile. Verify the installed file hash. Move superseded Orbis JARs to a backup directory outside `mods`, leaving unrelated mods, settings and saves untouched.
- Do not terminate a running Minecraft instance automatically. If a running instance locks the JAR, retain the validated build and explain that installation needs Minecraft to close.
- Report installation status and distinguish automated checks from actual in-game validation.
