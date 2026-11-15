#!/bin/bash
# Builds TDLib (Java interface) for Android with the official td/example/android scripts. Run inside WSL Ubuntu.
set -e
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq git curl wget unzip zip cmake gperf php-cli default-jdk-headless make perl build-essential python3 >/dev/null
cd /root
[ -d td ] || git clone -q https://github.com/tdlib/td.git
cd td && git checkout -q -- . && git pull -q && git log -1 --format='td %h %cd' && grep -m1 "TDLib VERSION" CMakeLists.txt
cd example/android
# Only the ABIs we ship: real phones (arm64) and the emulator (x86_64).
sed -i 's/for ABI in arm64-v8a armeabi-v7a x86_64 x86 ; do/for ABI in arm64-v8a x86_64 ; do/' build-tdlib.sh build-openssl.sh
sed -i 's/ABIS=${7:-"arm64-v8a armeabi-v7a x86_64 x86"}/ABIS=${7:-"arm64-v8a x86_64"}/' build-tdlib.sh
./check-environment.sh
[ -d SDK ] || ./fetch-sdk.sh
[ -d third-party/openssl ] || ./build-openssl.sh
./build-tdlib.sh
ls -R tdlib | head -40
