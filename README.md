# PiperJNI

A JNI wrapper for [Piper](https://github.com/OHF-Voice/piper1-gpl), a fast, local neural text to speech system.

## Platform Support

This library aims to support the following platforms:

* Windows x86_64
* Linux x86_64/arm64 (built with Ubuntu Focal Fossa, GLIBC version 2.31)
* macOS x86_64/arm64 (built for macOS 14 Sonoma and newer)

The JAR includes the piper-jni native library, which contains the [Piper](https://github.com/OHF-Voice/piper1-gpl) source,
and the shared libraries it depends on:
[espeak](https://espeak.sourceforge.net), [piper-phonemize](https://github.com/OHF-Voice/piper1-gpl) and [onxxruntime](https://onnxruntime.ai).

## Usage

The package is distributed through [Maven Central](https://central.sonatype.com/artifact/io.github.givimad/piper-jni):

### Maven

```xml
<dependency>
    <groupId>io.github.givimad</groupId>
    <artifactId>piper-jni</artifactId>
    <!-- replace $version with a specific version -->
    <version>$version</version>
</dependency>

```

### Gradle

```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.givimad:piper-jni:+' // gets the latest version
}
```

You can also find the package's jar attached to each [release](https://github.com/GiviMAD/piper-jni/releases).

### Examples

```java
PiperJNI piper = new PiperJNI();
piper.initialize(true);
try (var voice = piper.loadVoice(Paths.get("/path/to/en_US-lessac-medium.onnx"), Path.of("/path/to/en_US-lessac-medium.onnx.json"), 0)) {
    int sampleRate = voice.getSampleRate();
    short[] samples = piper.textToAudio(voice, textToSpeak);
    // Do something with the samples...
} finally {
    piper.terminate(config);
}
```

## Development

You need to have Java >= 11 and C++ setup.

After cloning the project, you need to init the piper submodule by running:

```shell
git submodule update --init
```

### Build with Docker (Recommended)

You can build for all supported Linux platforms (amd64, arm64, armv7l) using Docker:

```shell
./build_linux-all.sh
```

This uses `docker buildx` to build the native libraries and places them in `src/main/resources`.

### Native Build (Local)

If you prefer to build locally for your current platform:

```shell
# You need to set the correct resources folder as install prefix.
cmake -Bbuild -DCMAKE_INSTALL_PREFIX=src/main/resources/debian-$(uname -m)
cmake --build build --config Release
cmake --install build
```

Then you need to download a piper voice (`.onnx`) and its configuration (`.onnx.json`).
Piper voices can be downloaded from [HuggingFace](https://huggingface.co/rhasspy/piper-voices/tree/main).

Finally, you can run the project's tests to confirm it works:

```shell
# Download the test models
./download_test_model.sh
# Tests will generate test.wav in the root dir.
mvn test
```

Optionally, you can override the voice model and configuration paths as well as the text to speak:

```shell
# Path to piper voice model
export VOICE_MODEL=/test-data/es_ES-sharvard-medium.onnx
# Path to piper voice model config
export VOICE_MODEL_CONFIG=/test-data/es_ES-sharvard-medium.onnx.json
# Text to speak
export TEXT_TO_SPEAK="Buenos días"
mvn test
```

### Java Build

Finally, you can build the Java project:

```shell
mvn package
```

### Extending the Native API

If you want to add any missing piper functionality, you need to:

* Add the native method description in [`PiperJNI.java`](src/main/java/io/github/givimad/piperjni/PiperJNI.java).
* Run the `gen_header.sh` script to regenerate the [`io_github_givimad_piperjni_PiperJNI.h`](src/main/native/io_github_givimad_piperjni_PiperJNI.h) header file. 
* Add the native method implementation in [`io_github_givimad_piperjni_PiperJNI.cpp`](src/main/native/io_github_givimad_piperjni_PiperJNI.cpp).
* Add a new test for it at [`PiperJNITest.java`](src/test/java/io/github/givimad/piperjni/PiperJNITest.java).
