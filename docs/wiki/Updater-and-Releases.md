# Updater and Releases

## In the app (`data.update`)
`UpdateManager` queries `api.github.com/repos/27Salex/usos-launcher/releases/latest` at most every 24 h, ignores draft/prerelease, compares semver, downloads to `cacheDir/updates`, verifies size, package name, **same signing certificate** and a **higher versionCode**, then installs via `PackageInstaller`. Settings > SYSTEM can disable it or force a check. The configuration is never reset.

## Publishing a release
1. In `app/build.gradle.kts` bump `versionCode` (integer, always increasing) and `versionName` (`X.Y.Z`, semver).
2. `./gradlew test assembleRelease` with your keystore configured ([Build and Run](Build-and-Run)); the key never goes into the repo or CI.
3. Name the signed APK `usos-X.Y.Z.apk`.
4. Create a GitHub release (tag `vX.Y.Z`, not draft or prerelease) and attach the APK as an asset.
5. Write the notes in Spanish and English (shown in the app via `ReleaseNotes.plain`).
6. Check on a device with the previous version that the update installs.
