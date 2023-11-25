package io.github.givimad.piperjni;

import io.github.givimad.piperjni.internal.JNIRef;
import io.github.givimad.piperjni.internal.NativeUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PiperConfig extends JNIRef {

    private final PiperJNI piper;
    private boolean initialized = false;

    /**
     * Creates a new object used to represent a struct pointer on the native library.
     */
    protected PiperConfig(PiperJNI piper) throws IllegalArgumentException {
        super(piper.newConfig());
        this.piper = piper;
    }

    private void setESpeakDataPath(Path path) throws FileNotFoundException {
        if (path != null && (!Files.exists(path) || !Files.isDirectory(path))) {
            throw new FileNotFoundException("Provided folder path is not correct.");
        }
        piper.setESpeakDataPath(this.ref, path != null ? path.toAbsolutePath().toString() : null);
    }

    private void setTashkeelModelPath(Path path) throws FileNotFoundException {
        if (path != null && (!Files.exists(path) || Files.isDirectory(path))) {
            throw new FileNotFoundException("Provided model path is not correct.");
        }
        piper.setTashkeelModelPath(this.ref, path != null ? path.toAbsolutePath().toString() : null);
    }

    public void initialize(PiperVoice voice) throws IOException {
        assertAvailable();
        if (isInitialized()) {
            throw new IOException("Already initialized");
        }
        if (voice.getUsesESpeakPhonemes()) {
            this.setESpeakDataPath(NativeUtils.getESpeakNGData());
        } else {
            this.setESpeakDataPath(null);
        }
        if (voice.getUsesTashkeelModel()) {
            this.setTashkeelModelPath(NativeUtils.getTashkeelModel());
        } else {
            this.setTashkeelModelPath(null);
        }
        piper.initializeConfig(this.ref);
        this.initialized = true;
    }

    public void terminate() throws IOException {
        assertAvailable();
        if (!isInitialized()) {
            throw new IOException("Not initialized");
        }
        piper.terminateConfig(this.ref);
        this.initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void close() throws Exception {
        if (initialized) {
            terminate();
        }
        piper.freeConfig(this.ref);
    }
}
