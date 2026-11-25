# Binding audits

Historical inputs for `tools/bindings/BindingAudit.java`, kept so a later port does not have to
rediscover the same obfuscated members. Nothing in this directory is compiled or shipped: the audit
tool parses the `val` declarations that build a `Gboard…Target(…)` out of a file and resolves each one
against an APK.

## `frosted-and-flag-store-18.8.0.kt`

The twelve targets Frosted Glass and the shared flag family need on
**18.8.0.1022711984-beta-arm64-v8a**, sha256
`d1406caa270011aaea2a58bf828a3ffd94a58927a53688dc7bcd26e537c4d34b`.

Ten of them are the values used by `GboardFrostedGlassTargets.kt`. The two `flag*` entries are recorded
here because no 18.8 bindings projection exists yet: the flag composer reasons about the store by
shape, and its 18.8 contract lives in `GboardFlagFamilyComposer.kt`.

### How the values were derived

1. Ran the audit with the committed 18.6.3 values against the 18.8 APK: **0 of 12 resolved**. The
   classes still exist but nothing on them matches — `Lpbk;` is a `Runnable` on 18.8 and `Lodo;` is a
   function wrapper — so every member was re-located by shape instead of by name.
2. Method shapes, with `DexQuery <apk> sig V '<params>'` and `DexQuery <apk> paramsig '<params>'`:
   - `onConfigureWindow(Window;ZZ)V`, `onWindowShown()V` and `onWindowHidden()V` all sit on the one
     `InputMethodService` subclass → `Lafae;`.
   - `(I I Lj$/util/OptionalInt; Lj$/util/OptionalInt; Z)V` had a single hit → `Lajle;-><init>`.
   - `(Window;Lajle;)V` had a single hit → `Lajlf;->g`.
   - the five model fields were read straight off `Lajle;`: `a:I`, `b:I`, `c:OptionalInt`,
     `d:OptionalInt`, `e:Z`, the same names, types and order as the 18.6.3 projection, all
     `public final` (`0x11`) and none static.
3. Flag store: indexed the APK (`DexQuery <apk> index`) for the class carrying the flag triangle —
   `String` name, `Object` value behind a holder, `[Object` keys, `[Z` defaults, `Map` parameters —
   which is `Ladfs;` with holder `Ladgf;`. A dexlib2 dump of `Ladfs;->g()Ljava/lang/Object;` confirmed
   the shape the composer's predicate requires: holder into `v0`, `iget-object v2, v0,
   Ladgf;->a:Object`, `return-object v2` — value to `p0` (register 0 = `FLAG_NAME_SCRATCH_REGISTER`),
   holder in register 1 = `RECEIVER_SCRATCH_REGISTER`.
4. Re-ran the audit against the file in this directory: **12 of 12 resolved** (6 methods, 6 fields).

### Re-checking

```bash
JAVA="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java"
"$JAVA" -cp libs/morphe-cli.jar tools/bindings/BindingAudit.java \
    tools/bindings/audits/frosted-and-flag-store-18.8.0.kt \
    "dist/com.google.android.inputmethod.latin_18.8.0.1022711984-beta-arm64-v8a-176124766_minAPI26(arm64-v8a)(nodpi)_apkmirror.com.apk"
```

Expect `resolved: 12` and `unresolved: 0`. The APK itself is not in the repo, so match it by version
and, when you have the same download, by the sha256 above.

The register-level probes behind those shapes were throwaway `dexlib2` dumps; the equivalent
inspection command is `"$JAVA" -cp libs/morphe-cli.jar tmp/RegDump.java <apk> 'Lajlf;' g`.

### Status and limits

- **Frosted Glass** was not only resolved: it was applied to this APK for real, and the patch's own
  assertions held — `onConfigureWindow` expanded from 11 to 17 registers, exactly one
  `Window.setLayout` survived, all five model fields are final and non-static, and the nav-bar
  entry/exit and lifecycle delegates landed. Nothing has run on a device, so blur and nav-bar styling
  are still unverified, which is why 18.8 is offered as an experimental target only.
- **Flag store** is resolved and shape-checked only. It was exercised once through a temporary 18.8
  projection that is not in the tree.
- If a later 18.8 build changes these shapes the audit fails loudly: re-derive rather than guess.
