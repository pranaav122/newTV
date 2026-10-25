# newTV

The home screen contains a seven-app dock, header search, character favourites, mood cards, real app-published Continue watching, movie and TV shelves, then More apps. Sections scroll on one Home surface. The selected dock logo changes colour and grows; other dock logos stay white. Idle Home fades to wallpaper after ten minutes.

Mood cards support skipping, a decelerating random reel, automatic opening and manual browsing. Titles whose streaming app is absent are skipped. Continue watching reads Android TV's shared resume entries rather than inventing viewing progress. The launcher has no INTERNET permission, blur, video wallpaper or continuous background render loop.

## Build

Install JDK 17 and Android SDK platform/build-tools 34, then set `JAVA_HOME` and `ANDROID_HOME` for your machine.

Windows:

```powershell
.\gradlew.bat --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

Linux/macOS:

```sh
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

The APK is generated at `app/build/outputs/apk/release/app-release.apk`. APKs and signing keys are not committed. The package remains `com.gathin.tvhome`; updates to an existing installation need the same signing key used for that installation, kept locally.

## Layout and local artwork

- `app/`: complete launcher source, resources and JVM tests.
- `theme/`: the 54-title catalogue snapshot and wallpaper settings. See its README for local artwork filenames.
- `companions/weather/`: an optional weather provider. Choose a location in its settings; no location or coordinates are hardcoded. It is an independent Gradle project and can be built with its own wrapper. Network access belongs to that companion, not the launcher.
- `gradle/`: pinned Gradle wrapper, including its checksum.

Personal wallpaper, character cutouts, scenes, posters and backdrops are supplied locally. They are not included in this source upload. Without those files the app builds, but does not reproduce the artwork installed on the original TV. The catalogue metadata is included so its title IDs, links and moods can be restored alongside local artwork.

Use the upstream project's documented TV backup, theme transfer and Home-helper workflow for deployment. Its stock-launcher-disabling installer copies are not part of this standalone source repository. Keep the stock launcher and essential TV services enabled, verify the real remote, then revoke debugging authorisations, disable debugging and restart the TV after deployment.

## Validation

The launcher has JVM tests and Android lint checks. Performance work reduces drawing preparation and avoids unnecessary row/layout work; a locked 60 fps is not guaranteed. Real-remote checks remain necessary when deploying to a different TV.

This repository contains source and shareable catalogue metadata. TV identifiers, accounts, viewing-history dumps, backups, screenshots, traces, private artwork and debugging/signing keys stay outside version control.

The public weather companion uses the generic ID `com.newtv.weather`. Location choices and cached weather belong to on-device preferences. See [PRIVACY.md](PRIVACY.md) for the data boundaries.

Required source and font licence notices are retained in [LICENSE](LICENSE), [FONT-LICENSE-OFL.txt](FONT-LICENSE-OFL.txt) and [NOTICE.md](NOTICE.md).
