# Binding audits

Historical inputs for `tools/bindings/BindingAudit.java`, kept so a later port does not have to
rediscover the same obfuscated members. Nothing in this directory is compiled or shipped: the audit
tool parses the `val` declarations that build a `Gboard…Target(…)` out of a file and resolves each one
against an APK, and `tools/bindings/MethodDump.java` prints the members those declarations came from.

## `frosted-and-flag-store-18.8.0.kt`

The thirteen targets Frosted Glass and the shared flag family need on
**18.8.0.1022711984-beta-arm64-v8a**, sha256
`d1406caa270011aaea2a58bf828a3ffd94a58927a53688dc7bcd26e537c4d34b`.

Ten of them are the values used by `GboardFrostedGlassTargets.kt`. The three `flag*` entries are recorded
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
   which is `Ladfs;`, whose `f` field has type `Ladgf;`. A `MethodDump` run on `Ladfs;->g()Ljava/lang/Object;`
   confirmed the shape the composer's predicate requires: `iget-object v0, v2, Ladfs;->f:Ladgf;`, then
   `iget-object v2, v0, Ladgf;->a:Object`, then `return-object v2` — the holder lands in `v0` (register 0
   = `FLAG_NAME_SCRATCH_REGISTER`), the result in `p0` (register 1 = `RECEIVER_SCRATCH_REGISTER`). Those
   three names are exactly the `18.8.0` row of `GBOARD_FLAG_STOCK_CONTRACTS`.
4. Re-ran the audit against the file in this directory: **13 of 13 resolved** (6 methods, 7 fields).

### Re-checking

```bash
JAVA="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java"
"$JAVA" -cp libs/morphe-cli.jar tools/bindings/BindingAudit.java \
    tools/bindings/audits/frosted-and-flag-store-18.8.0.kt \
    "dist/com.google.android.inputmethod.latin_18.8.0.1022711984-beta-arm64-v8a-176124766_minAPI26(arm64-v8a)(nodpi)_apkmirror.com.apk"
```

Expect `resolved: 13` and `unresolved: 0`. The APK itself is not in the repo, so match it by version
and, when you have the same download, by the sha256 above.

### Looking at a shape

Everything above can be re-derived with the two tools in `tools/bindings/`, without a scratch script.
`MethodDump` lists a class's fields and methods with their descriptors and access flags, or one
method's instructions and register count:

```bash
# The audited flag getter: registers=3, holder into v0, value into v2, a single return-object.
"$JAVA" -cp libs/morphe-cli.jar tools/bindings/MethodDump.java <apk> 'Ladfs;' g

# The navigation model: five fields, all access=0x11 (public final, non-static), plus the constructor
# and the (Window, model) applier that takes it.
"$JAVA" -cp libs/morphe-cli.jar tools/bindings/MethodDump.java <apk> 'Lajle;'
"$JAVA" -cp libs/morphe-cli.jar tools/bindings/MethodDump.java <apk> 'Lajlf;'
```

Dropping the method name is how a member is found again after a rename: list the class, compare
parameter descriptors and register counts, then dump the candidate.

### Status and limits

- **Frosted Glass** was not only resolved: it was applied to this APK for real, and the patch's own
  assertions held — `onConfigureWindow` expanded from 11 to 17 registers, exactly one
  `Window.setLayout` survived, all five model fields are final and non-static, and the nav-bar
  entry/exit and lifecycle delegates landed. Nothing has run on a device, so blur and nav-bar styling
  are still unverified, which is why 18.8 is offered as an experimental target only.
- **Flag store** is resolved and shape-checked only. It was exercised once through a temporary 18.8
  projection that is not in the tree.
- If a later 18.8 build changes these shapes the audit fails loudly: re-derive rather than guess.
