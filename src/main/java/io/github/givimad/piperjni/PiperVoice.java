/*
 * #%L
 * piper-jni
 * %%
 * Copyright (C) 2023 - 2026 Contributors to whisper-jni
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package io.github.givimad.piperjni;

import java.nio.file.Path;

/** The class {@link PiperVoice} represents a loaded voice model. */
public class PiperVoice extends PiperJNI.JNIRef {

    private final PiperJNI piper;

    protected PiperVoice(
            PiperJNI piper,
            String espeakDataPath,
            Path modelPath,
            Path modelConfigPath,
            long speakerId)
            throws IllegalArgumentException {
        super(
                piper.loadVoice(
                        espeakDataPath,
                        modelPath.toAbsolutePath().toString(),
                        modelConfigPath.toAbsolutePath().toString(),
                        speakerId));
        this.piper = piper;
    }

    public boolean getUsesESpeakPhonemes() {
        assertAvailable();
        return piper.voiceUsesESpeakPhonemes(this.ref);
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
