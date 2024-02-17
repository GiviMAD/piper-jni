#include <iostream>
#include <optional>
#include <map>
#include <thread>
#include <mutex>
#include <condition_variable>
#include "io_github_givimad_piperjni_PiperJNI.h"
#include "piper.hpp"
#include <spdlog/spdlog.h>


std::map<int, piper::PiperConfig*> configMap;
std::map<int, piper::Voice*> voiceMap;

// disable library log
class StartUp
{
public:
   StartUp()
   { 
    spdlog::set_level(spdlog::level::off);
   }
};
StartUp startup;

/// From https://stackoverflow.com/a/12014833/6189530
struct NewJavaException {
    NewJavaException(JNIEnv * env, const char* type="", const char* message="")
    {
        jclass newExcCls = env->FindClass(type);
        if (newExcCls != NULL)
            env->ThrowNew(newExcCls, message);
        //if it is null, a NoClassDefFoundError was already thrown
    }
};
void swallow_cpp_exception_and_throw_java(JNIEnv * env) {
    try {
        throw;
    } catch(const std::bad_alloc& rhs) {
        //translate OOM C++ exception to a Java exception
        NewJavaException(env, "java/lang/OutOfMemoryError", rhs.what());
    } catch(const std::ios_base::failure& rhs) { //sample translation
        //translate IO C++ exception to a Java exception
        NewJavaException(env, "java/io/IOException", rhs.what());
    } catch(const std::exception& e) {
        //translate unknown C++ exception to a Java exception
        NewJavaException(env, "java/lang/RuntimeException", e.what());
    } catch(...) {
        //translate unknown C++ exception to a Java error
        NewJavaException(env, "java/lang/Error", "Unknown native exception type");
    }
}
///

int getConfigId() {
    int i = 0;
    while (i++ < 1000) {
        int id = rand();
        if(!configMap.count(id)) {
            return id;
        }
    }
    throw std::runtime_error("Wrapper error: Unable to get config id");
}

int getVoiceId() {
    int i = 0;
    while (i++ < 1000) {
        int id = rand();
        if(!voiceMap.count(id)) {
            return id;
        }
    }
    throw std::runtime_error("Wrapper error: Unable to get voice id");
}

void jCallbackOutputProc(JavaVM *jvm, jobject &jAudioCallback, std::vector<int16_t> &sharedAudioBuffer, std::mutex &mutAudio,
                   std::condition_variable &cvAudio, bool &audioReady,
                   bool &audioFinished);


void jCallbackOutputProc(JavaVM *jvm, jobject &jAudioCallback, std::vector<int16_t> &sharedAudioBuffer, std::mutex &mutAudio,
                   std::condition_variable &cvAudio, bool &audioReady,
                   bool &audioFinished) {
    bool jvmAttached = false;
    JNIEnv *env;
    if (jvm->AttachCurrentThread((void**)&env, NULL) == JNI_OK) {
       jvmAttached = true;
    } else {
       printf("ERROR: Could not attach callback thread to JVM");
    }
    std::vector<int16_t> internalAudioBuffer;
    while (true) {
    {
      std::unique_lock lockAudio{mutAudio};
      cvAudio.wait(lockAudio, [&audioReady] { return audioReady; });

      if (sharedAudioBuffer.empty() && audioFinished) {
        break;
      }

      copy(sharedAudioBuffer.begin(), sharedAudioBuffer.end(),
           back_inserter(internalAudioBuffer));

      sharedAudioBuffer.clear();

      if (!audioFinished) {
        audioReady = false;
      }
    }
    if (jvmAttached && env->ExceptionCheck() != JNI_TRUE) {
        jclass cbClass = env->GetObjectClass(jAudioCallback);
        jmethodID cbMethodId = env->GetMethodID(cbClass, "onAudio", "([S)V");
        jshortArray jAudioBuffer = env->NewShortArray(internalAudioBuffer.size());
        jshort *jSamples = env->GetShortArrayElements(jAudioBuffer, NULL);
        for (int index = 0; index < internalAudioBuffer.size(); ++index) {
            jSamples[index]= (jshort) internalAudioBuffer[index];
        }
        env->ReleaseShortArrayElements(jAudioBuffer, jSamples, 0);
        env->CallVoidMethod(jAudioCallback, cbMethodId, jAudioBuffer);
    }
    internalAudioBuffer.clear();
  }
  if (jvmAttached) {
    jvm->DetachCurrentThread();
  }
}

JNIEXPORT jint JNICALL Java_io_github_givimad_piperjni_PiperJNI_newConfig(JNIEnv *env, jobject thisObject) {
    try {
        int ref = getConfigId();
        piper::PiperConfig* piperConfig = new piper::PiperConfig{};
        configMap.insert({ref, piperConfig});
        return ref;
    } catch (const std::exception&) {
        swallow_cpp_exception_and_throw_java(env);
        return -1;
    }
}

JNIEXPORT void JNICALL Java_io_github_givimad_piperjni_PiperJNI_setESpeakDataPath(JNIEnv *env, jobject thisObject, jint configRef, jstring jValue) {
    if(jValue) {
        const char* cValue = env->GetStringUTFChars(jValue, NULL);
        std::string cppValue(cValue);
        env->ReleaseStringUTFChars(jValue, cValue);
       configMap.at(configRef)->eSpeakDataPath = cppValue;
        configMap.at(configRef)->useESpeak = true;
    } else {
        configMap.at(configRef)->eSpeakDataPath = {};
        configMap.at(configRef)->useESpeak = false;
    }
}

JNIEXPORT void JNICALL Java_io_github_givimad_piperjni_PiperJNI_setTashkeelModelPath(JNIEnv *env, jobject thisObject, jint configRef, jstring jValue) {
    if(jValue) {
        const char* cValue = env->GetStringUTFChars(jValue, NULL);
        std::string cppValue(cValue);
        configMap.at(configRef)->tashkeelModelPath = cppValue;
        env->ReleaseStringUTFChars(jValue, cValue);
        configMap.at(configRef)->useTashkeel = true;
    } else {
        configMap.at(configRef)->tashkeelModelPath = {};
        configMap.at(configRef)->useTashkeel = false;
    }
}

JNIEXPORT void JNICALL Java_io_github_givimad_piperjni_PiperJNI_initializeConfig(JNIEnv *env, jobject thisObject, jint configRef) {
    try {
        piper::PiperConfig *config = configMap.at(configRef);
        piper::initialize(*config);
    } catch (const std::exception&) {
        swallow_cpp_exception_and_throw_java(env);
    }
}

JNIEXPORT void JNICALL Java_io_github_givimad_piperjni_PiperJNI_terminateConfig(JNIEnv *env, jobject thisObject, jint configRef) {
    try {
        piper::PiperConfig *config = configMap.at(configRef);
        piper::terminate(*config);
    } catch (const std::exception&) {
        swallow_cpp_exception_and_throw_java(env);
    }
}

JNIEXPORT void JNICALL Java_io_github_givimad_piperjni_PiperJNI_freeConfig(JNIEnv *env, jobject thisObject, jint configRef) {
    free(configMap.at(configRef));
    configMap.erase(configRef);
}

JNIEXPORT jint JNICALL Java_io_github_givimad_piperjni_PiperJNI_loadVoice(JNIEnv *env, jobject thisObject, jint configRef, jstring modelPath, jstring modelConfigPath, jlong jSpeakerId, jboolean useCUDA) {
    piper::PiperConfig *piperConfig = configMap.at(configRef);
    piper::Voice *voice = new piper::Voice{};
    const char* cModelPath = env->GetStringUTFChars(modelPath, NULL);
    const char* cModelConfigPath = env->GetStringUTFChars(modelConfigPath, NULL);
    std::string cppModelPath(cModelPath);
    std::string cppModelConfigPath(cModelConfigPath);
    env->ReleaseStringUTFChars(modelPath, cModelPath);
    env->ReleaseStringUTFChars(modelConfigPath, cModelConfigPath);
    std::optional<piper::SpeakerId> cppSpeakerId;
    if (jSpeakerId > -1) {
        cppSpeakerId = jSpeakerId;
    }
    int ref;
    try {
        ref = getVoiceId();
        piper::loadVoice(*piperConfig, cppModelPath, cppModelConfigPath, *voice, cppSpeakerId, useCUDA);
    } catch (const std::exception&) {
        swallow_cpp_exception_and_throw_java(env);
        return -1;
    }
    voiceMap.insert({ref, voice});
    return ref;
}

JNIEXPORT jboolean JNICALL Java_io_github_givimad_piperjni_PiperJNI_voiceUsesESpeakPhonemes(JNIEnv *env, jobject thisObject, jint voiceRef) {
    try {
        return (jboolean) voiceMap.at(voiceRef)->phonemizeConfig.phonemeType == piper::eSpeakPhonemes;
    } catch (const std::exception&) {
        swallow_cpp_exception_and_throw_java(env);
        return false;
    }
}
JNIEXPORT jboolean JNICALL Java_io_github_givimad_piperjni_PiperJNI_voiceUsesTashkeelModel(JNIEnv *env, jobject thisClass, jint voiceRef) {
    try {
        return (voiceMap.at(voiceRef)->phonemizeConfig.eSpeak.voice == "ar");
    } catch (const std::exception&) {
        swallow_cpp_exception_and_throw_java(env);
        return false;
    }
}
JNIEXPORT jint JNICALL Java_io_github_givimad_piperjni_PiperJNI_voiceSampleRate(JNIEnv *env, jobject thisObject, jint voiceRef) {
    try {
        return (jint) voiceMap.at(voiceRef)->synthesisConfig.sampleRate;
    } catch (const std::exception&) {
        swallow_cpp_exception_and_throw_java(env);
        return false;
    }
}

JNIEXPORT void JNICALL Java_io_github_givimad_piperjni_PiperJNI_freeVoice(JNIEnv *env, jobject thisObject, jint voiceRef) {
  free(voiceMap.at(voiceRef));
  voiceMap.erase(voiceRef);
}

JNIEXPORT jshortArray JNICALL Java_io_github_givimad_piperjni_PiperJNI_textToAudio(JNIEnv *env, jobject thisObject, jint configRef, jint voiceRef, jstring jText, jobject jAudioCallback) {
    piper::PiperConfig *config = configMap.at(configRef);
    piper::Voice *voice = voiceMap.at(voiceRef);
    piper::SynthesisResult result;
    const char* cText = env->GetStringUTFChars(jText, NULL);
    std::string cppText(cText);
    env->ReleaseStringUTFChars(jText, cText);
    std::vector<int16_t> audioBuffer;
    try {
        if(jAudioCallback) {
            JavaVM *jvm;
            if (env->GetJavaVM(&jvm) != JNI_OK) {
               jclass exClass = env->FindClass("java/lang/IOException");
               env->ThrowNew(exClass, "Failed getting reference to Java VM");
               return NULL;
            }
            // thread vars
            std::vector<int16_t> sharedAudioBuffer;
            std::mutex mutAudio;
            std::condition_variable cvAudio;
            bool audioReady = false;
            bool audioFinished = false;
            std::thread jCallbackOutputThread(jCallbackOutputProc, jvm, std::ref(jAudioCallback), std::ref(sharedAudioBuffer),
                                 std::ref(mutAudio), std::ref(cvAudio), std::ref(audioReady),
                                 std::ref(audioFinished));
            auto audioCallback = [&audioBuffer, &sharedAudioBuffer, &mutAudio,
                                &cvAudio, &audioReady]() {
            // Signal thread that audio is ready
            {
                std::unique_lock lockAudio(mutAudio);
                copy(audioBuffer.begin(), audioBuffer.end(),
                back_inserter(sharedAudioBuffer));
                audioReady = true;
                cvAudio.notify_one();
            }
            };
            piper::textToAudio(*config, *voice, cppText, audioBuffer, result, audioCallback);
            {
                std::unique_lock lockAudio(mutAudio);
                audioReady = true;
                audioFinished = true;
                cvAudio.notify_one();
            }
            jCallbackOutputThread.join();
        } else {
            piper::textToAudio(*config, *voice, cppText, audioBuffer, result, nullptr);
        }
    } catch (const std::exception&) {
           swallow_cpp_exception_and_throw_java(env);
           return NULL;
    }
    if(!jAudioCallback) {
        // return audio samples
        jshortArray jAudioBuffer = env->NewShortArray(audioBuffer.size());
        jshort *jSamples = env->GetShortArrayElements(jAudioBuffer, NULL);
        for (int index = 0; index < audioBuffer.size(); ++index) {
            jSamples[index]= (jshort) audioBuffer[index];
        }
        env->ReleaseShortArrayElements(jAudioBuffer, jSamples, 0);
        return jAudioBuffer;
    } else {
        return NULL;
    }
}

JNIEXPORT jstring JNICALL Java_io_github_givimad_piperjni_PiperJNI_getVersion(JNIEnv *env, jobject thisObject) {
    std::string version = piper::getVersion();
    return env->NewStringUTF(version.c_str());
}
