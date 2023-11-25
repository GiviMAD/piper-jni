package io.github.givimad.piperjni;

import io.github.givimad.piperjni.internal.JNIRef;

import java.nio.file.Path;

public class PiperVoice extends JNIRef {

    private final PiperJNI piper;

    /**
     * Creates a new object used to represent a struct pointer on the native library.
     *
     */
    protected PiperVoice(PiperJNI piper, PiperConfig config, Path modelPath, Path modelConfigPath, long speakerId) throws IllegalArgumentException {
        super(piper.loadVoice(config.ref, modelPath.toAbsolutePath().toString(), modelConfigPath.toAbsolutePath().toString(), speakerId));
        this.piper = piper;
    }
    public boolean getUsesESpeakPhonemes() {
        assertAvailable();
        return piper.voiceUsesESpeakPhonemes(this.ref);
    }
    public boolean getUsesTashkeelModel() {
        assertAvailable();
        return piper.voiceUsesTashkeelModel(this.ref);
    }
    public int getSampleRate() {
        assertAvailable();
        return piper.voiceSampleRate(this.ref);
    }
    @Override
    public void close() throws Exception {
        assertAvailable();
        piper.freeVoice(this.ref);
        release();
    }
}
