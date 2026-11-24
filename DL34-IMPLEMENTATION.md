# DL34 implementation notes (module `classes9.dex` + host smali contract)

Design: "Quiet Premium / Paradox Ultra" — Soft Red x Warm White x Charcoal (DL34-DESIGN-SPEC.md).
Base: YT Music 8.30.54 host as represented by `host-jvm.jar` in this repository. Every obfuscated name
below (`lac`, `brso`, `bbxl`, `bmlj`, `lac.s`, `bmlj.er`, `bmlj.o`) was read from that jar; check that your
smali of the DL33 base shows the same names before applying.

## 1. The call your host smali must make (Home pivot)

**Method (module, static, public):**

```
Lultra/downloads/NativeIntegration;->fromTab(Ljava/lang/Object;Ljava/lang/String;)Z
```

| Argument | Value |
|---|---|
| `Object controller` | the `lac` instance (`p0` inside `lac.t`) — the same object DL33 already passes to `fromTab(Ljava/lang/Object;)V` |
| `String pivot` | the selected pivot's browse id, `brso.e`. Home = **`"FEmusic_ultra_home"`** (`NativeIntegration.HOME_PIVOT`) |
| returns `boolean` | `true` = module-owned pivot handled (Home or Downloads): return immediately. `false` = a host tab: continue the original code |

What it does:
- `"FEmusic_ultra_home"` → closes Downloads if open, mounts Home in `fragment_container` as its own tab (`openHome(true)`), remembers the controller for Back. Back from the Home tab returns to Library through the same native pivot path as Back from Downloads (`UltraNavigationBridge.library`).
- `"FEmusic_ultra_downloads"` → identical to the DL33 call pair `fromTab(this)` + `Hooks.open()`.
- anything else → `false`, nothing changes. Leaving Home for a host tab still goes through your existing `NativeIntegration.leave()` call in `lac.t`, which closes Home.

The DL33 entry `fromTab(Ljava/lang/Object;)V` is unchanged and still works, so an unpatched DL33 host keeps working with this dex (Home is then reachable from the Home pill in the Downloads header).

### 1a. `lac.t(Lbbxl;)V` — insert after the selected row is cast

The method starts by loading the selected pivot row: `iget-object …, p0, Llac;->i:Ljava/util/List;` → `iget …, p1, Lbbxl;->c:I` → `invoke-interface … List;->get(I)` → `move-result-object vR` → **`check-cast vR, Lbrso;`**. Insert directly after that `check-cast` (vR = the register your smali uses there; vT = any register not live at that point, raise `.locals` by 1 if none is free):

```smali
    iget-object vT, vR, Lbrso;->e:Ljava/lang/String;
    invoke-static {p0, vT}, Lultra/downloads/NativeIntegration;->fromTab(Ljava/lang/Object;Ljava/lang/String;)Z
    move-result vT
    if-eqz vT, :ultra_host_pivot
    return-void
    :ultra_host_pivot
```

Keep everything after it. The existing `FEmusic_home` branch (restore content tab, `NativeIntegration.leave()`, `ExtendedUtils.openSearch()`) stays as the **Search** tab. The existing `FEmusic_ultra_downloads` branch becomes unreachable (the new call handles Downloads first); leaving it or deleting it are both fine. Like Downloads, Home returns before `ultraContentTab` is written.

### 1b. `lac.ultraDownloadPivots(Ljava/util/List;)Ljava/util/List;` — replace the body

The order is computed in the module (`UltraPivots.order`, unit-tested). The host only builds the two module pivots with its own private `lac.s(id, label, icon)`:

```smali
.method public static ultraDownloadPivots(Ljava/util/List;)Ljava/util/List;
    .locals 4

    if-nez p0, :ultra_build
    return-object p0

    :ultra_build
    const-string v0, "FEmusic_ultra_home"
    const-string v1, "Home"
    sget-object v2, Lbmlj;->er:Lbmlj;
    invoke-static {v0, v1, v2}, Llac;->s(Ljava/lang/String;Ljava/lang/String;Lbmlj;)Lbrso;
    move-result-object v0

    const-string v1, "FEmusic_ultra_downloads"
    const-string v2, "Downloads"
    sget-object v3, Lbmlj;->o:Lbmlj;
    invoke-static {v1, v2, v3}, Llac;->s(Ljava/lang/String;Ljava/lang/String;Lbmlj;)Lbrso;
    move-result-object v1

    invoke-static {p0, v0, v1}, LUltraPivots;->order(Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;
    move-result-object v0
    return-object v0
.end method
```

- Resulting bar: **Home (`FEmusic_ultra_home`), Explore (`FEmusic_explore`), Search (`FEmusic_home`), Library (`FEmusic_library_landing`), Downloads (`FEmusic_ultra_downloads`)**, then any other host pivot in its original order. Existing module rows are replaced, so a second call is harmless; a `null` list is returned unchanged (same as today).
- Icons: `bmlj.er` = enum constant `HOME` (outlined house). `bmlj.o` = `OFFLINE_DOWNLOAD`, the icon Downloads already uses. Alternatives seen in the jar: `bmlj.bs` = `TAB_HOME`, `bmlj.es` = `HOME_FILLED` (filled, do not use). The selected-state tint (soft red) comes from the host's own TabLayout styling, not from the module.
- `NavigationPatch` already calls `lac.ultraDownloadPivots` on the pivot list; no change there.

### 1c. Which approach for `FEmusic_home`

The spec mentions both "FEmusic_ultra_home first" and "FEmusic_home … point at new home". This build implements the first: a **new** `FEmusic_ultra_home` pivot for Home, and `FEmusic_home` **stays the Search tab** (its existing redirect). That is what gives the five-tab order with both Home and Search. Repointing `FEmusic_home` to Home instead would remove the Search tab; if you want that, call `fromTab(p0, "FEmusic_ultra_home")` in the `FEmusic_home` branch instead of `openSearch()` and drop the new pivot from 1b.

### 1d. Default landing tab

Unchanged (stability first, as in DL33). Making Home the landing tab only works if the host's start-page selection also goes through `lac.t` (which now calls `fromTab`). That was not verifiable here; if the app opens on Home with an empty content area, keep Library as the start page.

## 2. What the module needs from the host (unchanged from DL33)

- `fragment_container` resolved by name (`NativeIntegration.id`), mounts added on top of it. No new resources, ids, permissions, assets or manifest entries.
- Activity hooks already present: `NativeIntegration.attach/paused/destroyed/save/restore/back/permissions/result`. `save/restore` now also keep the Home tab across Activity recreation (`ultra_home_tab` in the saved bundle).
- Back: the existing `UltraBackBridge` callback. It stays enabled while Downloads **or** Home is visible.

Shipped diff stays: AndroidManifest version bump + `classes9.dex` + (new for DL34, approved) the two `lac` methods above. Update `audit.py`'s changed-file set to include the `lac` dex if your host smali lives in a different dex than before.

## 3. Theme

All module screens resolve the theme from the **host Activity's live theme** (`android:colorBackground` luminance; system night mode as fallback). Dark → DL34 dark tokens, light → DL34 light tokens. Stock YT Music 8.30 ships a dark theme, so light appears only when the host theme is light. No host resource is required for colours; text appearances, icons and the chip/menu layouts are still resolved by name with framework fallbacks.

## 4. What is restyled, and the limits

Module-owned (full DL34 system, both themes): Downloads (brand line "YouTube Music **Ultra**", big title, coral chips, "Recently downloaded ⌄" sort row, gradient hero card with round coral Play, rounded list cards with outlined icons and 3-dot menus, coral "+ New" FAB on Playlists), Home (new), track/download sheets, all module dialogs (sort, settings, delete, new playlist, diagnostics), the Add to playlist picker, the selection bar shared with the Library page, default covers.

Not restyled (host-owned, not safely reachable from the module): the stock **mini player**, the **bottom navigation bar** (its floating-pill shape and colours), the stock **Library, Search and Explore pages**, the account **avatar**. The module has no account avatar source without new network calls, so the header shows search/settings instead of an avatar.

Icons: outlined everywhere (`yt_outline_*` by name, outlined drawn fallbacks; the DL33 filled "downloaded" mark is now an outlined check-circle). The only filled marks are the media glyphs inside the coral play buttons, as in the reference.

## 5. Build

- Java 17, `colab-QA.py`: **192 tests, 0 failures** (181 existing — `DL33Test` and `DL28PolishTest` colour assertions updated to DL34 tokens — plus `DL34Test` ×9 and `DL34PivotOrderTest` ×2).
- `target/classes` (fresh `mvn compile`) zipped to `module.jar`, then Android Build Tools 35.0.0 `d8` (D8 8.6.2-dev): `d8 --release --min-api 26 --lib android.jar --classpath host-jvm.jar --output out module.jar` → `classes.dex` renamed `classes9.dex`.
- Module-side `audit.py` checks pass on that dex (302 classes, no duplicates, all integration references resolve, contract methods public static). The MusicActivity override check and the base-APK byte compare need the APK and were **not** run here.
