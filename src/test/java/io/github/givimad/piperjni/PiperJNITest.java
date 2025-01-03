package io.github.givimad.piperjni;

import javax.naming.ConfigurationException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public class PiperJNITest {

    private static PiperJNI piper;

    @BeforeAll
    public static void before() throws IOException {
        piper = new PiperJNI();
    }

    @Test
    public void getPiperVersion() {
        var version = piper.getPiperVersion();
        assertNotNull(version, "version is defined");
        System.out.println("Piper version: " + version);
    }

    @Test
    public void initializePiper() throws IOException {
        try {
            piper.initialize(true, false);
        } finally {
            piper.terminate();
        }
    }

    @Test
    public void createPiperVoice() throws IOException, ConfigurationException, PiperJNI.NotInitialized {
        String voiceModel = System.getenv("VOICE_MODEL");
        String voiceModelConfig = System.getenv("VOICE_MODEL_CONFIG");
        if (voiceModel == null || voiceModel.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL is required");
        }
        if (voiceModelConfig == null || voiceModelConfig.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL_CONFIG is required");
        }
        try {
            piper.initialize();
            try (var voice = piper.loadVoice(Paths.get(voiceModel), Path.of(voiceModelConfig))) {
                assertNotNull(voice);
            }
        } finally {
            piper.terminate();
        }
    }

    @Test
    public void createAudioData() throws IOException, ConfigurationException, PiperJNI.NotInitialized {
        String voiceModel = System.getenv("VOICE_MODEL");
        String voiceModelConfig = System.getenv("VOICE_MODEL_CONFIG");
        String textToSpeak = System.getenv("TEXT_TO_SPEAK");
        if (voiceModel == null || voiceModel.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL is required");
        }
        if (voiceModelConfig == null || voiceModelConfig.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL_CONFIG is required");
        }
        if (textToSpeak == null || textToSpeak.isBlank()) {
            throw new ConfigurationException("env var TEXT_TO_SPEAK is required");
        }
        try {
            piper.initialize(true, true);
            try (var voice = piper.loadVoice(Paths.get(voiceModel), Path.of(voiceModelConfig), 0)) {
                assertNotNull(voice);
                int sampleRate = voice.getSampleRate();
                short[] samples = piper.textToAudio(voice, textToSpeak);
                assertNotEquals(0, samples.length);
                createWAVFile(List.of(samples), sampleRate, Path.of("test.wav"));
            }
        } finally {
            piper.terminate();
        }
    }

    @Test
    public void streamAudioData() throws ConfigurationException, IOException, PiperJNI.NotInitialized {
        String voiceModel = System.getenv("VOICE_MODEL");
        String voiceModelConfig = System.getenv("VOICE_MODEL_CONFIG");
        String textToSpeak = System.getenv("TEXT_TO_SPEAK");
        if (voiceModel == null || voiceModel.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL is required");
        }
        if (voiceModelConfig == null || voiceModelConfig.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL_CONFIG is required");
        }
        if (textToSpeak == null || textToSpeak.isBlank()) {
            throw new ConfigurationException("env var TEXT_TO_SPEAK is required");
        }
        try {
            piper.initialize(true, false);
            try (var voice = piper.loadVoice(Paths.get(voiceModel), Path.of(voiceModelConfig))) {
                assertNotNull(voice);
                int sampleRate = voice.getSampleRate();
                final ArrayList<short[]> audioSamplesChunks = new ArrayList<>();
                piper.textToAudio(voice, textToSpeak, audioSamplesChunks::add);
                assertFalse(audioSamplesChunks.isEmpty());
                assertNotEquals(0, audioSamplesChunks.get(0).length);
                createWAVFile(audioSamplesChunks, sampleRate, Path.of("test-stream.wav"));
            }
        } finally {
            piper.terminate();
        }
    }

    private void createWAVFile(List<short[]> sampleChunks, long sampleRate, Path outFilePath) {
        javax.sound.sampled.AudioFormat jAudioFormat;
        ByteBuffer byteBuffer;
        int numSamples = sampleChunks.stream().map(c -> c.length).reduce(0, Integer::sum);
        jAudioFormat = new javax.sound.sampled.AudioFormat(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                sampleRate, 16, 1, 2, sampleRate, false);
        byteBuffer = ByteBuffer.allocate(numSamples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (var chunk : sampleChunks) {
            for (var sample : chunk) {
                byteBuffer.putShort(sample);
            }
        }
        AudioInputStream audioInputStreamTemp = new AudioInputStream(new ByteArrayInputStream(byteBuffer.array()),
                jAudioFormat, numSamples);
        try {
            FileOutputStream audioFileOutputStream = new FileOutputStream(outFilePath.toFile());
            AudioSystem.write(audioInputStreamTemp, AudioFileFormat.Type.WAVE, audioFileOutputStream);
            audioFileOutputStream.close();
        } catch (IOException e) {
            System.err.println("Unable to store sample: " + e.getMessage());
        }
    }
}
