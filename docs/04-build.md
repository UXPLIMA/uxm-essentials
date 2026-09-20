# 4. The build

Ten Gradle modules, one version catalogue, and the checks that run before a jar is allowed to
exist. It is written from the build files that cite it.

## 4.1 The shape

Gradle with the Kotlin DSL, a convention plugin in `buildSrc` that every module applies, and
`gradle/libs.versions.toml` as the one place a version is written. Groovy is not used
anywhere.

The toolchain is Java 25 from Adoptium and `options.release` is 25.

## 4.2 No version number is printed in prose

The catalogue is the source, and no document copies it. This is not a preference: a guard
used to compare a fenced copy of `gradle/libs.versions.toml` printed inside this very
document against the real catalogue, and because `docs/` was not in the repository at the
time, it aborted on every checkout. JUnit records an abort as a skip, the suite reported
green, and five tests guarded nothing for as long as they existed.

What is guarded now is the part a reader acts on. `README.md` ships, and it names the server,
the server build and the Java version somebody must be running. `ShippedVersionsDriftTest`
holds each of those against the catalogue and against the constant in the convention plugin,
and every file it reads is in the repository, so it runs offline on every build.

The trade is written down rather than hidden: a pin no document mentions is unguarded. Adding
a table of pins here would put that guard back in the state that made it useless.

## 4.3 What is shaded and what is not

`paper-api` is `compileOnly` and never shaded. Adventure and Kyori are never shaded either:
Paper bundles them, and relocating them breaks the server.

What is shaded is relocated into this plugin's own namespace, because two plugins shading the
same library must not clash: bStats and uxmLib both move under
`com.uxplima.uxmessentials.libs`.

The Redis client is in no jar but the Redis companion, where Lettuce and its Netty and
Reactor transitives are relocated. The main jar therefore carries no Redis client at all and
has nothing to relocate.

## 4.4 The local library checkout

When uxmLib is checked out beside this repository, `settings.gradle.kts` includes it as a
composite build, so a change in the library is picked up without publishing. Three directory
names are accepted, because the GitHub repository was renamed and because the workspace keeps
the library two levels up from a plugin. Without a sibling checkout the published artifacts
resolve from `mavenLocal` as usual.

Matching only one of those names would drop the composite silently and fall back to whatever
`mavenLocal` happens to hold, which reads as a stale library rather than as a missing one.

## 4.5 What `check` runs

`build` is the gate, and these hang off it:

- Error Prone and NullAway, under `-Werror`. A warning is a failure.
- Spotless with Palantir Java Format. Run `./gradlew spotlessApply` before anything else; a
  format failure is the usual cause of a build that was green locally.
- The tests, in parallel, with the heap pinned at 2 GB.
- `verifyNoSkippedTests`, because a skipped test reports as green while protecting nothing.
- The locale parity gate, §4.8.1.

## 4.6 Jars

`shadowJar` produces the five deployable jars named in `09-deployment.md` §9.1, and
`assemble` depends on it, so the jar is never stale against the classes.

## 4.7 Dependencies that are fetched rather than shipped

### 4.7.5 The Discord bridge downloads JDA, and drops opus

The bridge jar is thin. JDA is `compileOnly` and the loader directive in `paper-plugin.yml`
downloads it from Maven Central at boot, rather than shading a large library into a plugin
most servers will not install.

`opus-java` is excluded at the loader level. The bridge only posts text and never touches
voice, so the voice codec is a native library nobody would load, and a native library that is
fetched and never used is a failure mode with no upside.

## 4.8 The gates that are not tests

### 4.8.1 Locale parity

Every shipped `messages_<lang>.conf` must declare exactly the key set `messages_en.conf`
declares, which in turn must match the `MessageKey` constants. English is the authority: a
hole left by an English change and a stale key left by a rename both fail.

It runs as its own task off `check`, ahead of the test phase, and prints the offending keys
and exits non-zero. The point of it being first is speed: a missing key is found in seconds
rather than after the whole suite.
