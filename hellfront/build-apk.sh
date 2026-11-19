#!/usr/bin/env bash
# Compila el APK firmado. Requiere JDK 17+ y Android SDK (ANDROID_HOME) con platforms;android-34 y build-tools;34.0.0
set -e
cd "$(dirname "$0")/android"
echo "sdk.dir=${ANDROID_HOME:?define ANDROID_HOME}" > local.properties
./gradlew assembleRelease
mkdir -p ../dist && cp app/build/outputs/apk/release/app-release.apk ../dist/hellfront-latest.apk
echo "OK -> hellfront/dist/hellfront-latest.apk"
