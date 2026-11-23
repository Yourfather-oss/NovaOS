NOVA CLASSIC LAYOUT FIX

Changes:
1. Frequently used apps are no longer pinned as a bottom dock-like strip. They remain a normal home widget in the widget flow.
2. Removed the frequent-app pin/unpin control from the home widget edit row and removed its setting from Design Studio.
3. Classic mode retains the selected grid column count (including 5 columns) but uses compact, fixed-height rows with a top-aligned grid occupying approximately 78% of the available page height.
4. Removed the bottom page range text (such as HOME / C-L / L-O) and the tiny page indicator dots.
5. Removed the classic app long-press menu's Add to dock / Remove from dock action.

Apply:
Replace the matching files in:
app/src/main/java/com/nova/os/ui/
- Classic.kt
- HomeWidgets.kt
- Studio.kt

The rest of the Phase 13 files are included unchanged for reference. This archive does not include the Gradle project wrapper/build configuration, so it is a source update and not a verified APK.
