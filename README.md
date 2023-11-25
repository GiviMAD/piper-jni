# PiperJNI

A JNI wrapper for [piper](https://github.com/rhasspy/piper) a local neural text to speech system, so it can be used on Java projects.

## Platform support

This library aims to support the following platforms:

* Windows x86_64
* Linux GLIBC x86_64/arm64 (built with debian focal, GLIBC version 2.31)
* macOS x86_64/arm64 (built targeting v11.0)

The jar includes the piper-jni native library, which includes the piper source, and the shared libraries it depends on: [espeak](https://espeak.sourceforge.net), [piper-phonetize](https://github.com/rhasspy/piper-phonemize) and [onxxruntime](https://onnxruntime.ai).

## Installation

The package is distributed through [Maven Central](https://central.sonatype.com/artifact/io.github.givimad/piper-jni).

You can also find the package's jar attached to each [release](https://github.com/GiviMAD/piper-jni/releases).

## Basic Example

```java
        ...
        String voiceModel = System.getenv("VOICE_MODEL");
        String voiceModelConfig = System.getenv("VOICE_MODEL_CONFIG");
        String textToSpeak = System.getenv("TEXT_TO_SPEAK");
        if(voiceModel == null || voiceModel.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL is required");
        }
        if(voiceModelConfig == null || voiceModelConfig.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL_CONFIG is required");
        }
        if(textToSpeak == null || textToSpeak.isBlank()) {
            throw new ConfigurationException("env var TEXT_TO_SPEAK is required");
        }
        try (var config = piper.createConfig()) {
            try (var voice = piper.loadVoice(config, Paths.get(voiceModel), Path.of(voiceModelConfig), 0)) {
                int sampleRate = voice.getSampleRate();
                config.initialize(voice);
                short[] samples = piper.textToAudio(config, voice, textToSpeak);
                config.terminate();
                createWAVFile(samples, sampleRate, Path.of("test.wav"));
            }
        }
        ...

        private void createWAVFile(short[] samples, long sampleRate, Path outFilePath) {
                AudioFormat jAudioFormat;
                ByteBuffer byteBuffer;
                int numSamples = samples.length;
                jAudioFormat = new javax.sound.sampled.AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, 1, 2, sampleRate, false
                );
                byteBuffer = ByteBuffer.allocate(numSamples * 2).order(ByteOrder.LITTLE_ENDIAN);
                for(var sample: samples) {
                        byteBuffer.putShort(sample);
                }
                AudioInputStream audioInputStreamTemp = new AudioInputStream(new ByteArrayInputStream(byteBuffer.array()), jAudioFormat, numSamples);
                try {
                        FileOutputStream audioFileOutputStream = new FileOutputStream(outFilePath.toFile());
                        AudioSystem.write(audioInputStreamTemp, AudioFileFormat.Type.WAVE, audioFileOutputStream);
                        audioFileOutputStream.close();
                } catch (IOException e) {
                        System.err.println("Unable to store sample: " + e.getMessage());
                }
        }
```

## Building the project from source.

You need to have Java and Cpp setup.

After cloning the project you need to init the piper submodule by running:

```sh
git submodule update --init
```

Build the piper-jni library:

```sh
# You need to set the correct resources folder as install prefix.
cmake -Bbuild -DCMAKE_INSTALL_PREFIX=src\main\resources\debian-amd64
cmake --build build --config Release
cmake --install build
```

Then you need to download a piper voice and its config.

Finally, you can run the project tests to confirm it works:

```sh
# Path to piper voice model
export VOICE_MODEL=/test-data/es_ES-sharvard-medium.onnx
# Path to piper voice model config
export VOICE_MODEL_CONFIG=/test-data/es_ES-sharvard-medium.onnx.json
# Text to speak
export TEXT_TO_SPEAK="Buenos días"
# tests will generate test.wav in the root dir.
mvn test
```

## Extending the native api

If you want to add any missing piper functionality you need to:

* Add the native method description in src/main/java/io/github/givimad/piperjni/PiperJNI.java.
* Run the gen_header.sh script to regenerate the src/main/native/io_github_givimad_piperjni_PiperJNI.h header file. 
* Add the native method implementation in src/main/native/io_github_givimad_piperjni_PiperJNI.cpp.
* Add a new test for it at src/test/java/io/github/givimad/piperjni/PiperJNITest.java.

BR
