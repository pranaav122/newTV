# Weather companion

Independent Android application used by the launcher's weather header. Based on the upstream cleanyourtv weather add-on; its MIT licence is retained here. It uses Open-Meteo and exposes cached weather through the `com.newtv.weather` provider. The launcher itself has no network permission. Choose a location in this app before using the header widget; the source contains no preset location or coordinates.

Build from this directory with JDK 17 and Android SDK 34:

```sh
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

On Windows use `gradlew.bat`. Build/sign the companion and launcher with the same locally held signing identity. This source does not include keys, cached weather or device data.
