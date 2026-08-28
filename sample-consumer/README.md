# uxmEssentials sample consumer

A complete, compiling example of a plugin that hooks into uxmEssentials: one veto listener, two notification
listeners, the front door, the query surface read the way it is meant to be read (no `join()` on the tick thread,
a feature check per module, independent reads started together), and the action surface written the way it is meant
to be written (taken with the calling plugin so every write is attributed, branched on a failure code rather than
on a message, chained rather than joined). Copy it, rename it, delete what you do not need.

```bash
./gradlew build
```

It resolves the API from the published repository, which is all a real consumer needs:

```kotlin
repositories {
    maven("https://raw.githubusercontent.com/UXPLIMA/uxmEssentials/maven")
}

dependencies {
    compileOnly("com.uxplima.uxmessentials:uxmessentials-bukkit-api:0.8.2")
}
```

One coordinate is enough. The pure view types (`UxmLocation`, `UxmMoney` and the rest) come with it through the POM.

Two properties change what is resolved, which is how CI points this build at the artifacts the current source tree
produces rather than at the released ones:

```bash
./gradlew build -PuxmRepo=file:///path/to/uxmEssentials/build/maven-repo -PuxmVersion=0.5.2
./gradlew showResolvedApi
```

The full documentation lives at <https://docs.uxplima.com/minecraft/plugins/uxmessentials/developer/overview/>.
