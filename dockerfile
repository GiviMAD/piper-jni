FROM maven:3.9.3-eclipse-temurin-17-focal
# use kitware repo to get upper cmake version; fixes armv7l build
RUN curl -s https://apt.kitware.com/kitware-archive.sh | bash -s
RUN apt update && apt install -y --no-install-recommends git build-essential cmake
COPY pom.xml .
COPY .git ./.git
COPY .gitmodules ./.gitmodules
COPY src ./src
COPY CMakeLists.txt ./CMakeLists.txt
COPY build_debian.sh .
RUN git submodule update --init && \
    ./build_debian.sh && \
    rm -rf src/main/native/piper/*
ARG RUN_TEST
RUN if [ $(echo $RUN_TEST) ]; then mvn test -Dtest="PiperJNITest#getPiperVersion" && echo "Done"; fi