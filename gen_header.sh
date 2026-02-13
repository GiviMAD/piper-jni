set -xe

LIB_SRC=src/main/java/io/github/givimad/piperjni

javac -h src/main/native \
$LIB_SRC/internal/NativeUtils.java \
$LIB_SRC/PiperVoice.java \
$LIB_SRC/PiperJNI.java

rm -rf $LIB_SRC/*.class $LIB_SRC/internal/*.class
