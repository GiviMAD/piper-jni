package io.github.givimad.piperjni;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PiperConfig extends PiperJNI.JNIRef {

    private final PiperJNI piper;

    /**
     * Creates a new object used to represent a struct pointer on the native library.
     */
    protected PiperConfig(PiperJNI piper) throws IllegalArgumentException {
        super(piper.newConfig());
        this.piper = piper;
    }

    protected void setESpeakDataPath(Path path) throws FileNotFoundException {
        if (path != null && (!Files.exists(path) || !Files.isDirectory(path))) {
            throw new FileNotFoundException("Provided folder path is not correct.");
        }
        piper.setESpeakDataPath(this.ref, path != null ? path.toAbsolutePath().toString() : null);
    }

    protected void setTashkeelModelPath(Path path) throws FileNotFoundException {
        if (path != null && (!Files.exists(path) || Files.isDirectory(path))) {
            throw new FileNotFoundException("Provided model path is not correct.");
        }
        piper.setTashkeelModelPath(this.ref, path != null ? path.toAbsolutePath().toString() : null);
    }

    @Override
    public void close() throws Exception {
        piper.freeConfig(this.ref);
    }
}
