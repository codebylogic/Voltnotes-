# VaultNotes — Production-Ready PRD & Modern UI/UX Specification (v2.2)

| Field | Specification |
|---|---|
| **Product Name** | VaultNotes (Offline Native Android Notes & File Manager) |
| **Version** | 2.2 (Modern Editorial UI/UX + Full File Manager + UI-Hardened) |
| **Platform** | Native Android (Kotlin 2.0+, Jetpack Compose, Material 3) |
| **Target SDK** | minSdk 26 (Android 8.0) / targetSdk 35 (Android 15) |
| **Owner** | Shivam Kumar |
| **Distribution** | Personal Sideloaded APK (Single User, 100% Offline, Zero Network Permissions) |

---

## 1. Modern Visual Design System (Craft / Bear / Notion Aesthetic)

To eliminate generic, dated Android layouts, the app strictly enforces a contemporary editorial design system with generous whitespace (`16.dp`–`20.dp` margins), soft squircle geometry, spring physics, and zero legacy Material 2 components:

* **Custom Editorial Color Palette (Zero Generic Material Purple/Blue):**
  * **Light Mode ("Warm Alabaster Paper"):**
    * Background Canvas: `#FBFBF9`
    * Elevated Card Surface: `#FFFFFF`
    * Soft Tinted Surface / Floating Bar: `#F3F2EE`
    * Primary Brand Accent: `#1E293B` (Deep Slate Charcoal) with `#4F46E5` (Crisp Indigo) for active tool states
    * Folder Badge Tint: `#FEF3C7` (Icon `#D97706`)
    * Note Badge Tint: `#EEF2FF` (Icon `#4F46E5`)
    * Danger / Delete: `#DC2626`
    * Text Primary: `#0F172A` | Text Secondary: `#64748B`
    * Hairline Border: `0.8.dp` `#E2E8F0`
  * **Dark Mode ("Obsidian OLED Charcoal"):**
    * Background Canvas: `#0D0E11`
    * Elevated Card Surface: `#16181D`
    * Soft Tinted Surface / Floating Bar: `#1E2028`
    * Primary Brand Accent: `#F8FAFC` with `#818CF8` (Soft Electric Indigo) for active tool states
    * Folder Badge Tint: `#292112` (Icon `#FBBF24`)
    * Note Badge Tint: `#1E2235` (Icon `#818CF8`)
    * Danger / Delete: `#F87171`
    * Text Primary: `#F8FAFC` | Text Secondary: `#94A3B8`
    * Hairline Border: `0.8.dp` `#262932`
* **Typography & Optical Spacing:**
  * Clean sans-serif (`FontFamily.SansSerif`) with tight display tracking and airy body leading:
    * Collapsing Folder Display Title: `26.sp`, `FontWeight.Bold`, `letterSpacing = (-0.4).sp`
    * Compact Top Bar Title: `17.sp`, `FontWeight.SemiBold`
    * Breadcrumb Chip Text: `12.sp`, `FontWeight.Medium`
    * File/Folder Card Title: `15.sp`, `FontWeight.SemiBold`, max 1 line (`TextOverflow.Ellipsis`)
    * File/Folder Card Metadata: `12.sp`, `TextSecondary`
    * Editor Note Title: `26.sp`, `FontWeight.Bold`, `lineHeight = 32.sp`, `letterSpacing = (-0.3).sp`
    * Editor Body Text: `16.sp` base (user-adjustable `10.sp`–`30.sp`), `lineHeight = 25.sp`
* **Modern Shapes, Motion & Haptics:**
  * Cards & Floating Bars: `RoundedCornerShape(14.dp)` to `RoundedCornerShape(16.dp)`, `0.8.dp` `Hairline Border`, subtle `2.dp`–`6.dp` ambient elevation.
  * **No Legacy Dialogs:** All confirmations, rename inputs, table row/col pickers, and overflow actions use modern `ModalBottomSheet` with drag handles (`32.dp × 4.dp` pill).
  * **Spring Physics & Haptics:** Use `spring(dampingRatio = 0.8f, stiffness = 400f)` for toggles, popovers, and drag-sort elevation (`1.03x` scale + `8.dp` shadow on active drag) paired with subtle `HapticFeedbackType.LongPress` / `TextHandleMove`.

---

## 2. Vault Storage & Lossless HTML Architecture

### 2.1 Vault Structure
All notes and folders live directly inside the user-chosen SAF directory (`ACTION_OPEN_DOCUMENT_TREE`). App metadata (Vault URI, per-directory custom sort order) lives in Room DB.

```text
<Selected Vault>/
├── Project Ideas.html
├── Untitled 1.html
├── Study/
│   └── Physics Notes.html
└── assets/
    └── img_20261116_113000.jpg
```

### 2.2 Lossless HTML Schema (Single Source of Truth)
Every note is a standalone, browser-readable HTML5 file parsed and serialized by `NoteHtmlCodec.kt` (`org.jsoup:jsoup`):

```html
<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="note-id" content="550e8400-e29b-41d4-a716-446655440000">
  <title>Physics Notes</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; max-width: 720px; margin: 32px auto; padding: 0 20px; line-height: 1.65; color: #0F172A; }
    table { border-collapse: collapse; margin: 16px 0; }
    td, th { border: 1px solid #CBD5E1; padding: 10px; vertical-align: top; }
    details { border: 1px solid #E2E8F0; border-radius: 12px; padding: 12px 16px; margin: 12px 0; background: #F8FAFC; }
    summary { font-weight: 600; cursor: pointer; }
  </style>
</head>
<body>
  <h1 class="note-title">Physics Notes</h1>
  <div class="note-body">
    <p>Kinetic energy: <span class="math" data-tex="K = \frac{1}{2}mv^2">K = ½mv²</span></p>
    <p><span style="color:#DC2626;font-weight:bold;font-size:18px;">Important</span> <span style="background-color:rgba(255,235,59,0.45);">highlight</span>.</p>
    <img src="assets/img_20261116_113000.jpg" style="max-width:100%;border-radius:12px;">
    <table>
      <colgroup><col style="width:140px"><col style="width:180px"></colgroup>
      <tbody><tr><td>Mass</td><td>5 kg</td></tr></tbody>
    </table>
    <details open><summary>Derivation</summary><p>Work $W = F \cdot d$</p></details>
  </div>
</body>
</html>
```

---

## 3. Screen 1: Modern File-Manager Home Screen

### 3.1 Collapsing Editorial Top Bar & Breadcrumb Strip
* **Normal Mode:**
  * Large title header (`26.sp` Bold showing current directory name, e.g., `"Study"` or `"Vault"`) that smoothly collapses into a `56.dp` top bar on scroll.
  * Left: Back chevron (`‹`) when inside a subfolder.
  * Right: Icon buttons (`40.dp` circular ghost buttons) for **Search (`🔍`)** (expands a sleek pill search bar), **Custom Drag-Sort (`⇅`)** (highlights with active Indigo pill), and **Vault Switcher (`📁`)**.
  * **Horizontal Breadcrumb Pill Strip:** Below the title, a scrollable row of subtle chips (`Vault › Study › Physics`) allowing one-tap jump to any ancestor folder.
* **Multi-Select Contextual Top Bar (Triggered on Long-Press):**
  * Displays close (`×`), `"3 selected"` pill badge, and `"Select All"` (`☑`) button.

### 3.2 Modern File & Folder Cards (`LazyColumn`, `16.dp` horizontal padding, `8.dp` vertical gap)
* **Card Anatomy (`64.dp` height, `RoundedCornerShape(14.dp)`, `Card Surface` fill, `0.8.dp` border):**
  * **Leading Squircle Badge (`40.dp × 40.dp`, `RoundedCornerShape(10.dp)`):**
    * Folder: Soft amber background with `22.dp` filled/duotone folder icon.
    * Note: Soft indigo background with `22.dp` document icon.
    * In Multi-Select Mode: Smoothly morphs/shows a modern rounded selection checkbox.
  * **Center Content:**
    * Primary Name (`15.sp` SemiBold; shows `"Untitled"` in muted italic style when title is empty, backed by `Untitled 1.html`).
    * Secondary Subtitle (`12.sp` `TextSecondary`, e.g., `"4 items • 16 Nov"` for folders, `"Edited 16 Nov 2026"` for notes).
  * **Trailing Action:**
    * Normal Mode: `⋮` button (`44.dp` touch target) opening a sleek **`ModalBottomSheet`** with **Rename**, **Move to…**, **Copy to…**, and **Delete** (in `Danger` red).
    * Arrange / Drag-Sort Mode: `≡` Drag Handle (`22.dp` icon inside `48.dp` hitbox). Active dragged card scales to `1.03x` with `8.dp` shadow elevation and persists per-directory custom order in Room DB.
* **Custom Empty State:**
  * Centered minimal vector icon inside a `64.dp` soft circle, `"Nothing here yet"` (`16.sp` SemiBold), and `"Tap + New Note below to start writing"` (`13.sp` `TextSecondary`).

### 3.3 Full File-Manager Operations (Move, Copy, Delete, Rename)
1. **Single & Batch Multi-Select:**
   * Trigger **Move**, **Copy**, or **Delete** from a single card's `ModalBottomSheet` OR by long-pressing multiple files/folders (which docks a floating **Multi-Select Action Pill** at the bottom with `Move`, `Copy`, and `Delete`).
2. **Floating Clipboard Paste Dock (Move & Copy Destination Flow):**
   * Initiating **Move** or **Copy** docks a sleek floating pill banner at the bottom (`"Moving 2 items"` / `"Copying 2 items"`) with **[+ Folder]**, **[Paste Here]** (solid primary button), and **[× Cancel]**.
   * The user navigates freely across folders and subfolders, then taps **Paste Here**.
3. **Storage Safety Guarantees:**
   * **Asset Path Preservation:** Because all images reside in `<vault>/assets/`, moving or copying notes into nested subfolders preserves `assets/...` links cleanly without broken images.
   * **UUID & Collision Safety:** Copying a note assigns a fresh `<meta name="note-id">` UUID and appends ` - Copy` (or `(2)`) if pasted into the same directory.
   * **Recursive Directory Engine:** Moving, copying, or deleting folders recursively handles all nested subfolders and notes while updating Room DB sort orders.
   * **Self-Containment Guard:** Blocks moving a folder into itself or any of its own subfolders.
   * **Delete Confirmation Sheet:** Clean `ModalBottomSheet` asking confirmation before permanent SAF deletion.

### 3.4 Navigation & Back Button Rule
* System Back inside any nested folder steps up **strictly one parent directory per press**.
* If Multi-Select Mode is active, Back clears selection first.
* If Clipboard Paste Mode is active, Back cancels clipboard mode first.
* System Back at the vault root exits the app (`finish()`).

### 3.5 Floating Bottom Action Dock (Normal Mode, Bottom-Right, `16.dp` margin)
* Instead of dated round FABs, display a modern dual-pill dock:
  * Secondary Icon Pill (`48.dp × 48.dp`, `RoundedCornerShape(14.dp)`, `Card Surface` + `0.8.dp` border): **New Folder (`📁+`)**.
  * Primary Action Pill (`48.dp` height, `RoundedCornerShape(14.dp)`, `Primary Brand Accent` fill): **`+ New Note`**.

---

## 4. Screen 2: Distraction-Free Floating Studio Editor

* **System Insets:** `Modifier.statusBarsPadding()`.
* **Row 1 — Minimalist Studio Header (`44.dp` height, `12.dp` horizontal padding):**
  * Left: Back button (`←`) returning to Home (immediately flushes auto-save).
  * Center: Subtle live status pill (`✓ Saved` in muted `12.sp`, pulsing softly to `Saving…` while dirty).
  * Right: Segmented pill (`RoundedCornerShape(10.dp)`, `Soft Tinted Surface`) containing **Undo (`↶`)** and **Redo (`↷`)** (`20.dp` icons, `40.dp` hitboxes, `0.35f` alpha when disabled; up to 100 session-only states).
* **Row 2 — Editorial Title Input (`20.dp` horizontal padding, `12.dp` top / `8.dp` bottom):**
  * Borderless `BasicTextField` (`26.sp` Bold, `-0.3.sp` tracking), placeholder `"Note title"` at `0.35f` alpha, separated from the body by a subtle `0.8.dp` hairline divider. Editing updates `<h1 class="note-title">` and safely renames the `.html` file on disk.
* **Row 3 — Scrollable Block-Based Rich Body (`Modifier.weight(1f)`, `20.dp` horizontal padding):**
  * `LazyColumn` rendering `ParagraphBlock`, `ImageBlock`, `TableBlock`, and `ToggleBlock`:
    * **ParagraphBlock:** `BasicTextField` powered by `AnnotatedString` (`SpanStyle` for text color, translucent background highlight, font size `10.sp`–`30.sp`, bold, italic, underline) + `InlineTextContent` for inline LaTeX math (`JLaTeXMath` cached bitmaps scaled to `1.2x` line height).
    * **ImageBlock:** Displays cropped image from `vault/assets/` (`RoundedCornerShape(12.dp)`, max width `100%`, `0.8.dp` border) with a clean floating remove (`×`) chip on tap.
    * **TableBlock:** Wrapped in `Modifier.horizontalScroll(rememberScrollState())` so wide tables scroll sideways independently of the vertical note body. Focused tables show a floating mini-pill above them (`+ Row`, `- Row`, `+ Col`, `- Col`) and draggable column resize handles (`60.dp`–`320.dp`). Every cell supports full rich text and math.
    * **ToggleBlock:** Soft tinted container (`RoundedCornerShape(12.dp)`, `0.8.dp` border) with clickable header (`▶`/`▼` spring-rotated chevron + editable summary) and `AnimatedVisibility` body container beneath it.
* **Row 4 — Floating Pill Formatting Toolbar (`46.dp` height, `12.dp` horizontal margin, `8.dp` bottom margin above keyboard via `Modifier.imePadding().navigationBarsPadding()`):**
  * A sleek floating pill (`RoundedCornerShape(16.dp)`, `Soft Tinted Surface`, `0.8.dp` border, `4.dp` shadow) with a subtle right-edge gradient fade indicating horizontal scrollability.
  * Active formatting buttons (e.g., Bold, Italic, Underline) display a soft Indigo pill highlight (`alpha = 0.15f`).
  * Contains **11 tools in exact order**:
    1. **Text (`Tt`)**: Focuses active text field & opens system keyboard.
    2. **Sum (`Σ`)**: Toggles the custom **Mathematical Keyboard Panel** (`220.dp` height) with visual keys (`√`, `x²`, `a/b`, `π`, `θ`, `∑`, `∫`, `≤`, `≥`, `≠`, `∞`, `→`, Greek letters, brackets) + **Insert** button rendering LaTeX inline at the cursor anywhere (body, table cell, toggle).
    3. **Import (`🖼️`)**: Launches Android Photo Picker → full-screen **In-App Image Cropper** (draggable corner handles + aspect chips `Free`, `1:1`, `4:3`, `16:9` + top-right **Next** pill button) → saves JPEG to `vault/assets/` and inserts at cursor.
    4. **Table (`⊞`)**: Opens modern `ModalBottomSheet` asking `Rows` & `Columns` → inserts `TableBlock` at cursor.
    5. **Toggle (`▸`)**: Inserts collapsible `ToggleBlock` at cursor.
    6. **Colour (`A`)**: Opens a floating `48.dp` pill strip `8.dp` above the toolbar (**10 preset swatches** with active selection ring + `+` custom HSV/Hex picker).
    7. **Bold (`B`)**: Toggles `FontWeight.Bold` on selection or next typed text.
    8. **Italic (`I`)**: Toggles `FontStyle.Italic`.
    9. **Underline (`U`)**: Toggles `TextDecoration.Underline`.
    10. **Highlighter (`🖍️`)**: Opens a floating `48.dp` pill strip `8.dp` above the toolbar (**10 translucent highlighters** `alpha = 0.40f` + `+` custom picker).
    11. **Font Size (`16px ▾`)**: Opens a floating stepper/slider pill (`10px` to `30px`, step `2px`) `8.dp` above the toolbar.

---

## 5. Auto-Save & Atomic Write Engine

* **1-Second Change-Driven Auto-Save:** Increments `documentRevision` on every change; `LaunchedEffect(documentRevision)` waits `1000ms` and writes atomically only when dirty.
* **Immediate Lifecycle Flush:** `ON_PAUSE`, `ON_STOP`, and Back press flush pending changes immediately.
* **Atomic SAF Write:** Writes to `.tmp_<uuid>.html` in the current directory, flushes stream, and replaces target `.html` so sudden app kills never corrupt files.

---

## 6. Complete Project Structure (Single-Pass Deliverable Blueprint)

Generate the complete, compilable Android Studio project under package `com.shivam.vaultnotes`:

```text
app/src/main/java/com/shivam/vaultnotes/
├── MainActivity.kt                  # Edge-to-edge setup, back-press dispatcher, Compose root
├── data/
│   ├── model/NoteDocument.kt        # Immutable Block AST (Paragraph, Image, Table, Toggle, MathSpan)
│   ├── codec/NoteHtmlCodec.kt       # Lossless Jsoup HTML <-> NoteDocument parser & serializer
│   ├── storage/VaultRepository.kt   # SAF DocumentFile ops (Create, Read, Atomic Write, Move, Copy, Delete, Rename, Assets)
│   └── local/AppDatabase.kt         # Room DB storing Vault URI & per-directory custom sort order
├── ui/
│   ├── theme/Theme.kt               # Warm Alabaster & Obsidian OLED Material 3 palette & typography
│   ├── home/
│   │   ├── HomeScreen.kt            # Collapsing header, breadcrumb chips, drag-sort cards, dual action pill
│   │   └── FileManagerSheets.kt     # Multi-select bar, Clipboard Paste dock, ModalBottomSheets (Rename/Delete)
│   ├── editor/
│   │   ├── EditorScreen.kt          # Studio top bar, Title, Block LazyColumn, 46dp Floating Pill Toolbar
│   │   ├── RichTextBlockEditor.kt   # AnnotatedString span engine + JLaTeXMath inline rendering
│   │   ├── TableBlockView.kt        # Horizontally scrollable table + resizable columns + row/col controls
│   │   ├── ToggleBlockView.kt       # Collapsible summary + spring-animated body container
│   │   ├── MathKeyboardPanel.kt     # Visual symbol/fraction/exponent builder -> LaTeX inserter
│   │   └── ToolbarPopovers.kt       # Floating Color swatches, Highlighter swatches, 10px–30px Font Size pill
│   └── crop/ImageCropScreen.kt      # Interactive drag-handle cropper with top-right "Next" pill
```

### Code Generation Rules
1. Output **all Kotlin files and Gradle build files in full** with zero `// TODO` stubs or omitted functions.
2. Dependencies: Jetpack Compose BOM, Material 3, Room, Jsoup (`org.jsoup:jsoup:1.18.1`), JLaTeXMath (`ru.noties:jlatexmath-android:0.2.0`), Coil (`io.coil-kt:coil-compose:2.7.0`), Reorderable (`sh.calvin.reorderable:reorderable:2.4.0`).
3. Configure `AndroidManifest.xml` with **zero internet permissions** and `android:windowSoftInputMode="adjustResize"`.