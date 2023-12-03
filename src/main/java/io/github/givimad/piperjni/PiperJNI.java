package io.github.givimad.piperjni;

import io.github.givimad.piperjni.internal.NativeUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Piper JNI
 */
public class PiperJNI  {

    private static boolean libraryLoaded;
    private boolean initialized = false;

    //region native api
    protected native int newConfig();
    protected native void freeConfig(int configRef);
    protected native void setESpeakDataPath(int configRef, String eSpeakDataPath);
    protected native void setTashkeelModelPath(int configRef, String tashkeelModelPath);
    protected native void initializeConfig(int configRef) throws IOException;
    protected native void terminateConfig(int configRef) throws IOException;
    protected native int loadVoice(int configRef, String model, String modelConfig, long speakerId, boolean useCUDA);
    protected native void freeVoice(int voiceRef);
    protected native boolean voiceUsesESpeakPhonemes(int voiceRef);
    protected native boolean voiceUsesTashkeelModel(int voiceRef);
    protected native int voiceSampleRate(int voiceRef);
    private native short[] textToAudio(int configRef, int voiceRef, String text, AudioCallback audioCallback) throws IOException;
    private native String getVersion();
    //endregion

    public PiperJNI() throws IOException {
        loadLibrary();
    }

    /**
     *  Get piper version.
     *
     * @return piper library version.
     * @throws IOException if library is not loaded
     */
    public String getPiperVersion() throws Exception {
        assertRegistered();
        return getVersion();
    }

    public PiperConfig createConfig() throws Exception {
        assertRegistered();
        return new PiperConfig(this);
    }
    public void initialize(PiperConfig config, PiperVoice voice) throws IOException {
        voice.assertAvailable();
        initialize(config, voice.getUsesESpeakPhonemes(), voice.getUsesTashkeelModel());
    }
    public void initialize(PiperConfig config, boolean useESpeakPhonemes, boolean useTashkeelModel) throws IOException {
        config.assertAvailable();
        if (initialized) {
            throw new IOException("Already initialized");
        }
        if (useESpeakPhonemes) {
            config.setESpeakDataPath(NativeUtils.getESpeakNGData());
        } else {
            config.setESpeakDataPath(null);
        }
        if (useTashkeelModel) {
            config.setTashkeelModelPath(NativeUtils.getTashkeelModel());
        } else {
            config.setTashkeelModelPath(null);
        }
        initializeConfig(config.ref);
        this.initialized = true;
    }
    public void terminate(PiperConfig config) throws IOException {
        config.assertAvailable();
        if (!initialized) {
            throw new IOException("Not initialized");
        }
        terminateConfig(config.ref);
        this.initialized = false;
    }
    public PiperVoice loadVoice(PiperConfig config, Path modelPath, Path modelConfigPath) throws Exception {
        return loadVoice(config, modelPath, modelConfigPath, -1, false);
    }
    public PiperVoice loadVoice(PiperConfig config, Path modelPath, Path modelConfigPath, long speakerId) throws Exception {
        return loadVoice(config, modelPath, modelConfigPath, speakerId, false);
    }
    public PiperVoice loadVoice(PiperConfig config, Path modelPath, Path modelConfigPath, boolean useCUDA) throws Exception {
        return loadVoice(config, modelPath, modelConfigPath, -1, useCUDA);
    }
    public PiperVoice loadVoice(PiperConfig config, Path modelPath, Path modelConfigPath, long speakerId, boolean useCUDA) throws Exception {
        if(modelPath == null || !Files.exists(modelPath) || Files.isDirectory(modelPath)) {
            throw new IllegalArgumentException("Model file is required");
        }
        if(modelConfigPath == null || !Files.exists(modelConfigPath) || Files.isDirectory(modelConfigPath)) {
            throw new IllegalArgumentException("Model config file is required");
        }
        if(config == null) {
            throw new IllegalArgumentException("Config can not be null");
        }
        return new PiperVoice(this, config, modelPath, modelConfigPath, speakerId, useCUDA);
    }

    public short[] textToAudio(PiperConfig config, PiperVoice voice, String text) throws Exception {
        return textToAudioImpl(config, voice, text, null);
    }

    public void textToAudio(PiperConfig config, PiperVoice voice, String text, AudioCallback audioCallback) throws Exception {
        textToAudioImpl(config, voice, text, audioCallback);
    }

    private short[] textToAudioImpl(PiperConfig config, PiperVoice voice, String text, AudioCallback audioCallback) throws Exception {
        if(!isInitialized()) {
            throw new IllegalArgumentException("Piper not initialized");
        }
        if(config == null) {
            throw new IllegalArgumentException("Config can not be null");
        }
        if(voice == null) {
            throw new IllegalArgumentException("Voice can not be null");
        }
        if(text == null) {
            throw new IllegalArgumentException("Text can not be null");
        }
        if(text.isBlank()) {
            // return empty.
            return new short[] {};
        }
        return textToAudio(config.ref, voice.ref, text, audioCallback);
    }

    public boolean isInitialized() {
        return initialized;
    }
    /**
     * Register the native library, should be called at first.
     *
     * @throws IOException when unable to load the native library
     */
    private void loadLibrary() throws IOException {
        if (libraryLoaded) {
            return;
        }
        String bundleLibraryPath = null;
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        if (osName.contains("win")) {
            if (osArch.contains("amd64") || osArch.contains("x86_64")) {
                NativeUtils.loadLibraryFromJar("/win-amd64/onnxruntime.dll");
                NativeUtils.loadLibraryFromJar("/win-amd64/espeak-ng.dll");
                NativeUtils.loadLibraryFromJar("/win-amd64/piper_phonemize.dll");
                bundleLibraryPath = "/win-amd64/piper-jni.dll";
            }
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            if (osArch.contains("amd64") || osArch.contains("x86_64")) {
                NativeUtils.loadLibraryFromJar("/debian-amd64/libonnxruntime.so.1.14.1");
                NativeUtils.loadLibraryFromJar("/debian-amd64/libespeak-ng.so.1");
                NativeUtils.loadLibraryFromJar("/debian-amd64/libpiper_phonemize.so.1");
                bundleLibraryPath = "/debian-amd64/libpiper-jni.so";
            } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
                NativeUtils.loadLibraryFromJar("/debian-arm64/libonnxruntime.so.1.14.1");
                NativeUtils.loadLibraryFromJar("/debian-arm64/libespeak-ng.so.1");
                NativeUtils.loadLibraryFromJar("/debian-arm64/libpiper_phonemize.so.1");
                bundleLibraryPath = "/debian-arm64/libpiper-jni.so";
            } else if (osArch.contains("armv7") || osArch.contains("arm")) {
                NativeUtils.loadLibraryFromJar("/debian-armv7l/libonnxruntime.so.1.14.1");
                NativeUtils.loadLibraryFromJar("/debian-armv7l/libespeak-ng.so.1");
                NativeUtils.loadLibraryFromJar("/debian-armv7l/libpiper_phonemize.so.1");
                bundleLibraryPath = "/debian-armv7l/libpiper-jni.so";
            }
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            if (osArch.contains("amd64") || osArch.contains("x86_64")) {
                NativeUtils.loadLibraryFromJar("/macos-amd64/libonnxruntime.1.14.1.dylib");
                NativeUtils.loadLibraryFromJar("/macos-amd64/libespeak-ng.1.dylib");
                NativeUtils.loadLibraryFromJar("/macos-amd64/libpiper_phonemize.1.dylib");
                bundleLibraryPath = "/macos-amd64/libpiper-jni.dylib";
            } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
                NativeUtils.loadLibraryFromJar("/macos-arm64/libonnxruntime.1.14.1.dylib");
                NativeUtils.loadLibraryFromJar("/macos-arm64/libespeak-ng.1.dylib");
                NativeUtils.loadLibraryFromJar("/macos-arm64/libpiper_phonemize.1.dylib");
                bundleLibraryPath = "/macos-arm64/libpiper-jni.dylib";
            }
        }
        if (bundleLibraryPath == null) {
            throw new java.io.IOException("piper-jni: Unsupported platform " + osName + " - " + osArch + ".");
        }
        NativeUtils.loadLibraryFromJar(bundleLibraryPath);
        libraryLoaded = true;
    }
    private static void assertRegistered() throws IOException {
        if (!libraryLoaded) {
            throw new IOException("Native library is unavailable.");
        }
    }
    public interface AudioCallback {
        void onAudio(short[] audioSamples);
    }

    /**
     * In order to avoid sharing pointers between the c++ and java, we use this
     * util base class which holds a random integer id generated in the whisper.cpp wrapper.
     *
     * @author Miguel Álvarez Díez - Initial contribution
     */
    public static abstract class JNIRef implements AutoCloseable {
        /**
         * Native pointer reference identifier.
         */
        protected final int ref;
        private boolean released;

        /**
         * Asserts the provided pointer is still available.
         *
         */
        protected void assertAvailable() {
            if (this.isReleased()) {
                throw new RuntimeException("Unavailable pointer, object is closed");
            }
        }

        /**
         * Creates a new object used to represent a struct pointer on the native library.
         *
         * @param ref a random integer id generated by the native wrapper
         */
        protected JNIRef(int ref) {
            this.ref = ref;
        }

        /**
         * Return true if native memory is free
         *
         * @return a boolean indicating if the native data was already released
         */
        protected boolean isReleased() {
            return released;
        }

        /**
         * Mark the point as released
         */
        protected void release() {
            released = true;
        }
    }
}
