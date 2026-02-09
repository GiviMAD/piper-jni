package io.github.givimad.piperjni;

import java.nio.file.Path;

/** The class {@link PiperVoice} represents a loaded voice model. */
public class PiperVoice extends PiperJNI.JNIRef {

    private final PiperJNI piper;

    protected PiperVoice(
            PiperJNI piper,
            PiperConfig config,
            Path modelPath,
            Path modelConfigPath,
            long speakerId,
            boolean useCUDA)
            throws IllegalArgumentException {
        super(
                piper.loadVoice(
                        config.ref,
                        modelPath.toAbsolutePath().toString(),
                        modelConfigPath.toAbsolutePath().toString(),
                        speakerId,
                        useCUDA));
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

    /**
     * Get the generated audio sample rate.
     *
     * @return the audio sample rate for these voice
     */
    public int getSampleRate() {
        assertAvailable();
        return piper.voiceSampleRate(this.ref);
    }

    @Override
    public void close() {
        if (!isReleased()) {
            piper.freeVoice(this.ref);
            release();
        }
    }
}
