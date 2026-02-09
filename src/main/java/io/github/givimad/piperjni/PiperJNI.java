package io.github.givimad.piperjni;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.givimad.piperjni.internal.NativeUtils;

/** Piper JNI */
public class PiperJNI implements AutoCloseable {

    private static boolean libraryLoaded;
    private PiperConfig currentConfig;

    // region native api
    protected native int newConfig();

    protected native void freeConfig(int configRef);

    protected native void setESpeakDataPath(int configRef, String eSpeakDataPath);

    protected native void setTashkeelModelPath(int configRef, String tashkeelModelPath);

    protected native void initializeConfig(int configRef) throws IOException;

    protected native void terminateConfig(int configRef);

    protected native int loadVoice(
            int configRef, String model, String modelConfig, long speakerId, boolean useCUDA);

    protected native void freeVoice(int voiceRef);

    protected native boolean voiceUsesESpeakPhonemes(int voiceRef);

    protected native boolean voiceUsesTashkeelModel(int voiceRef);

    protected native int voiceSampleRate(int voiceRef);

    private native short[] textToAudio(
            int configRef, int voiceRef, String text, AudioCallback audioCallback)
            throws IOException;

    private native String getVersion();

    // endregion

    /**
     * Creates a new Piper instance.
     *
     * @throws IOException if library registration fails
     */
    public PiperJNI() throws IOException {
        loadLibrary();
    }

    /**
     * Get piper version.
     *
     * @return piper library version.
     */
    public String getPiperVersion() {
        assertRegistered();
        return getVersion();
    }

    /**
     * Initializes the piper instance configuration. Should be called before using the instance.
     *
     * @throws IOException if initialization fails.
     */
    public void initialize() throws IOException {
        initialize(true, false);
    }

    /**
     * Initializes the piper instance configuration. Should be called before using the instance.
     *
     * @param useESpeakPhonemes Support voices with ESpeak phonemes.
     * @param useTashkeelModel Support voices using the Tashkeel model.
     * @throws IOException if initialization fails.
     */
    public void initialize(boolean useESpeakPhonemes, boolean useTashkeelModel) throws IOException {
        assertRegistered();
        terminate();
        PiperConfig config = new PiperConfig(this);
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
        this.currentConfig = config;
    }

    /** Unload current configuration if any. */
    public void terminate() {
        if (isInitialized()) {
            terminateConfig(currentConfig.ref);
            this.currentConfig = null;
        }
    }

    /**
     * Loads piper voice model and config.
     *
     * @param modelPath model file path
     * @param modelConfigPath model config file path
     * @return a {@link PiperVoice} instance
     * @throws FileNotFoundException if models or config doesn't exist
     * @throws NotInitialized if piper was not initialized
     */
    public PiperVoice loadVoice(Path modelPath, Path modelConfigPath)
            throws IOException, NotInitialized {
        return loadVoice(modelPath, modelConfigPath, -1, false);
    }

    /**
     * Loads piper voice model and config.
     *
     * @param modelPath model file path
     * @param modelConfigPath model config file path
     * @param speakerId Speaker id or -1.
     * @return a {@link PiperVoice} instance
     * @throws FileNotFoundException if models or config doesn't exist
     * @throws NotInitialized if piper was not initialized
     */
    public PiperVoice loadVoice(Path modelPath, Path modelConfigPath, long speakerId)
            throws IOException, NotInitialized {
        return loadVoice(modelPath, modelConfigPath, speakerId, false);
    }

    /**
     * Loads piper voice model and config.
     *
     * @param modelPath model file path
     * @param modelConfigPath model config file path
     * @param useCUDA Use CUDA
     * @return a {@link PiperVoice} instance
     * @throws FileNotFoundException if models or config doesn't exist
     * @throws NotInitialized if piper was not initialized
     */
    public PiperVoice loadVoice(Path modelPath, Path modelConfigPath, boolean useCUDA)
            throws IOException, NotInitialized {
        return loadVoice(modelPath, modelConfigPath, -1, useCUDA);
    }

    /**
     * Loads piper voice model and config.
     *
     * @param modelPath model file path
     * @param modelConfigPath model config file path
     * @param speakerId Speaker id or -1.
     * @param useCUDA Use CUDA
     * @return a {@link PiperVoice} instance
     * @throws FileNotFoundException if models or config doesn't exist
     * @throws NotInitialized if piper was not initialized
     */
    public PiperVoice loadVoice(
            Path modelPath, Path modelConfigPath, long speakerId, boolean useCUDA)
            throws FileNotFoundException, NotInitialized {
        assertRegistered();
        assertInitialized();
        if (modelPath == null || !Files.exists(modelPath) || Files.isDirectory(modelPath)) {
            throw new FileNotFoundException("Model file is required");
        }
        if (modelConfigPath == null
                || !Files.exists(modelConfigPath)
                || Files.isDirectory(modelConfigPath)) {
            throw new FileNotFoundException("Model config file is required");
        }
        return new PiperVoice(this, currentConfig, modelPath, modelConfigPath, speakerId, useCUDA);
    }

    /**
     * Convert text to audio using the provided voice.
     *
     * @param voice {@link PiperVoice} instance to use.
     * @param text Text to speak.
     * @return The audio samples
     * @throws IOException If generation fails.
     * @throws NotInitialized If piper not initialized.
     */
    public short[] textToAudio(PiperVoice voice, String text) throws IOException, NotInitialized {
        return textToAudioImpl(voice, text, null);
    }

    /**
     * Convert text to audio using the provided voice and emit segments asynchronously.
     *
     * @param voice {@link PiperVoice} instance to use.
     * @param text Text to speak.
     * @param audioCallback Callback for each audio segment.
     * @throws IOException If generation fails.
     * @throws NotInitialized If piper not initialized.
     */
    public void textToAudio(PiperVoice voice, String text, AudioCallback audioCallback)
            throws IOException, NotInitialized {
        textToAudioImpl(voice, text, audioCallback);
    }

    private short[] textToAudioImpl(PiperVoice voice, String text, AudioCallback audioCallback)
            throws IOException, NotInitialized {
        assertRegistered();
        assertInitialized();
        if (voice == null) {
            throw new NullPointerException("Voice can not be null");
        }
        if (text == null) {
            throw new NullPointerException("Text can not be null");
        }
        if (text.isBlank()) {
            // return empty.
            return new short[] {};
        }
        return textToAudio(currentConfig.ref, voice.ref, text, audioCallback);
    }

    /**
     * Checks if piper was initialized
     *
     * @return true if piper was initialized
     */
    public boolean isInitialized() {
        return currentConfig != null && !currentConfig.isReleased();
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
                NativeUtils.loadLibraryResource("/win-amd64/onnxruntime.dll");
                NativeUtils.loadLibraryResource("/win-amd64/espeak-ng.dll");
                NativeUtils.loadLibraryResource("/win-amd64/piper_phonemize.dll");
                bundleLibraryPath = "/win-amd64/piper-jni.dll";
            }
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            if (osArch.contains("amd64") || osArch.contains("x86_64")) {
                NativeUtils.loadLibraryResource("/debian-amd64/libonnxruntime.so.1.14.1");
                NativeUtils.loadLibraryResource("/debian-amd64/libespeak-ng.so.1");
                NativeUtils.loadLibraryResource("/debian-amd64/libpiper_phonemize.so.1");
                bundleLibraryPath = "/debian-amd64/libpiper-jni.so";
            } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
                NativeUtils.loadLibraryResource("/debian-arm64/libonnxruntime.so.1.14.1");
                NativeUtils.loadLibraryResource("/debian-arm64/libespeak-ng.so.1");
                NativeUtils.loadLibraryResource("/debian-arm64/libpiper_phonemize.so.1");
                bundleLibraryPath = "/debian-arm64/libpiper-jni.so";
            } else if (osArch.contains("armv7") || osArch.contains("arm")) {
                NativeUtils.loadLibraryResource("/debian-armv7l/libonnxruntime.so.1.14.1");
                NativeUtils.loadLibraryResource("/debian-armv7l/libespeak-ng.so.1");
                NativeUtils.loadLibraryResource("/debian-armv7l/libpiper_phonemize.so.1");
                bundleLibraryPath = "/debian-armv7l/libpiper-jni.so";
            }
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            if (osArch.contains("amd64") || osArch.contains("x86_64")) {
                NativeUtils.loadLibraryResource("/macos-amd64/libonnxruntime.1.14.1.dylib");
                NativeUtils.loadLibraryResource("/macos-amd64/libespeak-ng.1.dylib");
                NativeUtils.loadLibraryResource("/macos-amd64/libpiper_phonemize.1.dylib");
                bundleLibraryPath = "/macos-amd64/libpiper-jni.dylib";
            } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
                NativeUtils.loadLibraryResource("/macos-arm64/libonnxruntime.1.14.1.dylib");
                NativeUtils.loadLibraryResource("/macos-arm64/libespeak-ng.1.dylib");
                NativeUtils.loadLibraryResource("/macos-arm64/libpiper_phonemize.1.dylib");
                bundleLibraryPath = "/macos-arm64/libpiper-jni.dylib";
            }
        }
        if (bundleLibraryPath == null) {
            throw new FileNotFoundException(
                    "piper-jni: Unsupported platform " + osName + " - " + osArch + ".");
        }
        NativeUtils.loadLibraryResource(bundleLibraryPath);
        libraryLoaded = true;
    }

    private static void assertRegistered() {
        if (!libraryLoaded) {
            throw new RuntimeException("Native library is unavailable.");
        }
    }

    private void assertInitialized() throws NotInitialized {
        if (!isInitialized()) {
            throw new NotInitialized();
        }
    }

    @Override
    public void close() {
        terminate();
    }

    /** Callback for streamed audio. */
    public interface AudioCallback {
        /**
         * Called once on each generated voice segment.
         *
         * @param audioSamples The segment samples.
         */
        void onAudio(short[] audioSamples);
    }

    /** Emitted if Piper instance was not initialized by calling the {@link #initialize} method. */
    public static class NotInitialized extends Exception {
        private NotInitialized() {
            super("Piper not initialized");
        }
    }

    /**
     * In order to avoid sharing pointers between the c++ and java, we use this util base class
     * which holds a random integer id generated in the whisper.cpp wrapper.
     *
     * @author Miguel Álvarez Díez - Initial contribution
     */
    public abstract static class JNIRef implements AutoCloseable {
        /** Native pointer reference identifier. */
        protected final int ref;

        private boolean released;

        /** Asserts the provided pointer is still available. */
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

        /** Mark the point as released */
        protected void release() {
            released = true;
        }
    }
}
