#include "mutex"
#include "condition_variable"
#include "atomic"
#include "thread"
#include "io_github_jvoice_piperjni_PiperJNI.h"
#include "piper.h"
#include "piper_impl.hpp"

// Custom deleter for piper_synthesizer to use with smart pointers
struct PiperDeleter {
    void operator()(piper_synthesizer* p) const {
        if (p) {
            piper_free(p);
        }
    }
};

using PiperVoicePtr = std::shared_ptr<piper_synthesizer>;

std::map<int, PiperVoicePtr> voiceMap;
std::mutex voiceMapMutex;

// Exception helper
/// From https://stackoverflow.com/a/12014833/6189530
struct NewJavaException {
    NewJavaException(JNIEnv * env, const char* type="", const char* message="")
    {
        jclass newExcCls = env->FindClass(type);
        if (newExcCls != nullptr)
            env->ThrowNew(newExcCls, message);
        // if it is null, a NoClassDefFoundError was already thrown
    }
};

void swallow_cpp_exception_and_throw_java(JNIEnv * env) {
    try {
        throw;
    } catch(const std::bad_alloc& rhs) {
        // translate OOM C++ exception to Java exception
        NewJavaException(env, "java/lang/OutOfMemoryError", rhs.what());
    } catch(const std::exception& e) {
        // translate IO C++ exception to Java exception
        NewJavaException(env, "java/lang/RuntimeException", e.what());
    } catch(...) {
        // translate unknown C++ exception to Java error
        NewJavaException(env, "java/lang/Error", "Unknown native exception type");
    }
}
///

// RAII Wrapper for JNI String
class JNIString {
    JNIEnv* env;
    jstring jstr;
    const char* cstr;

public:
    JNIString(JNIEnv* env, jstring jstr) : env(env), jstr(jstr), cstr(nullptr) {
        if (jstr) cstr = env->GetStringUTFChars(jstr, nullptr);
    }
    ~JNIString() {
        if (env && jstr && cstr) env->ReleaseStringUTFChars(jstr, cstr);
    }
    const char* get() const { return cstr; }
    operator const char*() const { return cstr; }
};

std::atomic<int> nextVoiceId{0};

// Helper function to get a unique voice id
int getVoiceId() {
    return nextVoiceId.fetch_add(1);
}

// Background consumer thread callback function
void jCallbackOutputProc(JavaVM *jvm, const jobject &jAudioCallback, std::vector<int16_t> &sharedAudioBuffer, std::mutex &mutAudio,
                   std::condition_variable &cvAudio, bool &audioReady, const bool &audioFinished) {
    JNIEnv *env;
    bool jvmAttached = false;

    // Attach this new background thread to the JVM
    if (jvm->AttachCurrentThread(reinterpret_cast<void **>(&env), nullptr) == JNI_OK) {
        jvmAttached = true;
    } else {
        NewJavaException(env, "java/lang/RuntimeException", "Failed to attach callback thread to JVM");
        return;
    }

    std::vector<int16_t> internalAudioBuffer;
    // Consumer Loop
    while (true) {
        {
            // Wait for the Producer to signal that audio data is ready
            std::unique_lock lockAudio{mutAudio};
            cvAudio.wait(lockAudio, [&]{ return audioReady || audioFinished; });

            if (sharedAudioBuffer.empty() && audioFinished) {
                break; // Exit when completely done
            }

            // Move audio data from shared buffer to internal buffer to quickly release the lock
            internalAudioBuffer.insert(internalAudioBuffer.end(), sharedAudioBuffer.begin(), sharedAudioBuffer.end());
            sharedAudioBuffer.clear();
            if (!audioFinished) {
                audioReady = false;
            }
        }

        // Send audio data to the JVM
        if (jvmAttached && env->ExceptionCheck() != JNI_TRUE) {
            jclass cbClass = env->GetObjectClass(jAudioCallback);
            jmethodID cbMethodId = env->GetMethodID(cbClass, "onAudio", "([S)V");
            jshortArray jAudioBuffer = env->NewShortArray(internalAudioBuffer.size());
            jshort *jSamples = env->GetShortArrayElements(jAudioBuffer, nullptr);

            for (size_t index = 0; index < internalAudioBuffer.size(); ++index) {
                jSamples[index]= (jshort) internalAudioBuffer[index];
            }

            env->ReleaseShortArrayElements(jAudioBuffer, jSamples, 0);
            env->CallVoidMethod(jAudioCallback, cbMethodId, jAudioBuffer);
            env->DeleteLocalRef(jAudioBuffer);
        }
        internalAudioBuffer.clear();
    }
    if (jvmAttached) {
        // Detach this background thread from the JVM
        jvm->DetachCurrentThread();
    }
}

// JNI Implementations

JNIEXPORT jint JNICALL Java_io_github_jvoice_piperjni_PiperJNI_loadVoice(JNIEnv *env, jobject /*thisObject*/, jstring espeakDataPath, jstring modelPath, jstring modelConfigPath, jlong jSpeakerId) {
    try {
        JNIString cEspeakDataPath(env, espeakDataPath);
        JNIString cModelPath(env, modelPath);
        JNIString cModelConfigPath(env, modelConfigPath);

        PiperVoicePtr voice(piper_create(cModelPath, cModelConfigPath, cEspeakDataPath), PiperDeleter());

        if (!voice) {
             NewJavaException(env, "java/lang/RuntimeException", "Failed to load voice");
             return -1;
        }

        // Set speaker id if provided
        if (jSpeakerId > -1) {
             voice->speaker_id = (SpeakerId)jSpeakerId;
        }

        int ref = getVoiceId();
        std::lock_guard<std::mutex> lock(voiceMapMutex);
        voiceMap.insert({ref, voice});
        return ref;
    } catch (const std::exception&) {
        swallow_cpp_exception_and_throw_java(env);
        return -1;
    }
}

JNIEXPORT jboolean JNICALL Java_io_github_jvoice_piperjni_PiperJNI_voiceUsesESpeakPhonemes(JNIEnv *env, jobject /*thisObject*/, jint voiceRef) {
    try {
        std::lock_guard<std::mutex> lock(voiceMapMutex);
        PiperVoicePtr voice = voiceMap.at(voiceRef);
        return (jboolean) (voice->espeak_voice.empty() && !voice->phoneme_id_map.empty());
    } catch (const std::exception&) {
        swallow_cpp_exception_and_throw_java(env);
        return false;
    }
}

JNIEXPORT jint JNICALL Java_io_github_jvoice_piperjni_PiperJNI_voiceSampleRate(JNIEnv *env, jobject /*thisObject*/, jint voiceRef) {
    try {
        std::lock_guard<std::mutex> lock(voiceMapMutex);
        return (jint) voiceMap.at(voiceRef)->sample_rate;
    } catch (const std::exception&) {
        swallow_cpp_exception_and_throw_java(env);
        return 0;
    }
}

JNIEXPORT void JNICALL Java_io_github_jvoice_piperjni_PiperJNI_freeVoice(JNIEnv */*env*/, jobject /*thisObject*/, jint voiceRef) {
    std::lock_guard<std::mutex> lock(voiceMapMutex);
    voiceMap.erase(voiceRef);
    // PiperDeleter will automatically call piper_free when the shared_ptr is destroyed
    // and no other references exist (e.g. from running textToAudio calls).
}

JNIEXPORT jshortArray JNICALL Java_io_github_jvoice_piperjni_PiperJNI_textToAudio(JNIEnv *env, jobject /*thisObject*/, jint voiceRef, jstring jText, jobject jAudioCallback) {
    try {
        PiperVoicePtr voice;
        {
            std::lock_guard<std::mutex> lock(voiceMapMutex);
            voice = voiceMap.at(voiceRef);
        }

        JNIString cText(env, jText);
        piper_synthesize_options options = piper_default_synthesize_options(voice.get());
        if (piper_synthesize_start(voice.get(), cText.get(), &options) != PIPER_OK) {
             NewJavaException(env, "java/lang/RuntimeException", "Failed to start synthesis");
             return nullptr;
        }

        piper_audio_chunk chunk;
        int ret;

        if (jAudioCallback) {
            // Producer-Consumer Mode
            JavaVM *jvm;
            if (env->GetJavaVM(&jvm) != JNI_OK) {
                NewJavaException(env, "java/lang/RuntimeException", "Failed getting reference to JVM");
                return nullptr;
            }

            // thread vars
            std::vector<int16_t> sharedAudioBuffer;
            std::mutex mutAudio;
            std::condition_variable cvAudio;
            bool audioReady = false;
            bool audioFinished = false;

            // Spawn the consumer thread
            std::thread jCallbackOutputThread(jCallbackOutputProc, jvm, std::ref(jAudioCallback), std::ref(sharedAudioBuffer),
                                 std::ref(mutAudio), std::ref(cvAudio), std::ref(audioReady), std::ref(audioFinished));

            // The Producer Loop
            while ((ret = piper_synthesize_next(voice.get(), &chunk)) != PIPER_DONE) {
                 if (ret != PIPER_OK) break;

                 if (chunk.num_samples > 0) {
                      std::vector<int16_t> chunkSamples;
                      chunkSamples.reserve(chunk.num_samples);

                      // Convert float samples to int16
                      for (size_t i = 0; i < chunk.num_samples; ++i) {
                          float val = chunk.samples[i];
                          val = std::max(-1.0f, std::min(1.0f, val));
                          chunkSamples.push_back(static_cast<int16_t>(val * 32767.0f));
                      }

                      // Signal to the consumer that audio data is ready
                      {
                          std::unique_lock<std::mutex> lockAudio(mutAudio);
                          sharedAudioBuffer.insert(sharedAudioBuffer.end(), chunkSamples.begin(), chunkSamples.end());
                          audioReady = true;
                      }
                      cvAudio.notify_one(); // Wake up the consumer thread
                 }
            }

            // Signal the Consumer that we are completely done
            {
                std::unique_lock<std::mutex> lockAudio(mutAudio);
                audioFinished = true;
                audioReady = true;
            }
            cvAudio.notify_one(); // Wake up the consumer thread

            // Wait for the background thread to finish pushing the last chunk to Java
            jCallbackOutputThread.join();

            return nullptr;
        } else {
            // Blocking Mode/Synchronous Batch Mode
            std::vector<int16_t> fullAudioBuffer;
            while ((ret = piper_synthesize_next(voice.get(), &chunk)) != PIPER_DONE) {
                 if (ret != PIPER_OK) break;
                 if (chunk.num_samples > 0) {
                      // Convert float samples to int16
                      for (size_t i = 0; i < chunk.num_samples; ++i) {
                          float val = chunk.samples[i];
                          val = std::max(-1.0f, std::min(1.0f, val));
                          fullAudioBuffer.push_back(static_cast<int16_t>(val * 32767.0f));
                      }
                 }
            }

            // Return the full audio buffer
            jshortArray jAudioBuffer = env->NewShortArray(fullAudioBuffer.size());
            jshort *jSamples = env->GetShortArrayElements(jAudioBuffer, nullptr);
            for (size_t index = 0; index < fullAudioBuffer.size(); ++index) {
                jSamples[index] = (jshort) fullAudioBuffer[index];
            }
            env->ReleaseShortArrayElements(jAudioBuffer, jSamples, 0);
            return jAudioBuffer;
        }
    } catch (const std::exception&) {
        swallow_cpp_exception_and_throw_java(env);
        return nullptr;
    }
}

JNIEXPORT jstring JNICALL Java_io_github_jvoice_piperjni_PiperJNI_getVersion(JNIEnv *env, jobject /*thisObject*/) {
    return env->NewStringUTF(_PIPER_VERSION);
}
