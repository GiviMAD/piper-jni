# Forked from piper project

cmake_minimum_required(VERSION 3.13)

project(piper-jni C CXX)

# Apply patches to piper submodule
set(PIPER_SRC_DIR "${CMAKE_CURRENT_BINARY_DIR}/piper_patched/piper")

# Copy piper source to build directory to apply patches without modifying source tree
file(REMOVE_RECURSE "${CMAKE_CURRENT_BINARY_DIR}/piper_patched")
file(COPY "${CMAKE_CURRENT_SOURCE_DIR}/src/main/native/piper" DESTINATION "${CMAKE_CURRENT_BINARY_DIR}/piper_patched" PATTERN ".git" EXCLUDE)

# Apply patch
execute_process(
    COMMAND patch -p1 -i "${CMAKE_CURRENT_SOURCE_DIR}/src/main/native/piper_fixes.patch"
    WORKING_DIRECTORY "${PIPER_SRC_DIR}"
    RESULT_VARIABLE patch_result
)
if(NOT patch_result EQUAL 0)
    message(FATAL_ERROR "Failed to apply patch to piper source")
endif()

file(READ "${PIPER_SRC_DIR}/setup.py" setup_py_content)
string(REGEX MATCH "version=\"([0-9]+\\.[0-9]+\\.[0-9]+)\"" _ "${setup_py_content}")
if(NOT CMAKE_MATCH_1)
    message(FATAL_ERROR "Failed to extract piper version from setup.py.")
endif()
set(piper_version "\"${CMAKE_MATCH_1}\"")
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# detect jni library
set(JAVA_AWT_LIBRARY NotNeeded)
set(JAVA_JVM_LIBRARY NotNeeded)
find_package(JNI REQUIRED)
if (JNI_FOUND)
    message (STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
endif()

if(MSVC)
  # Force compiler to use UTF-8 for IPA constants
  add_compile_options("$<$<C_COMPILER_ID:MSVC>:/utf-8>")
  add_compile_options("$<$<CXX_COMPILER_ID:MSVC>:/utf-8>")
elseif(NOT APPLE)
  # Linux flags
  string(APPEND CMAKE_CXX_FLAGS " -Wall -Wextra -Wl,-rpath,'$ORIGIN'")
  string(APPEND CMAKE_C_FLAGS " -Wall -Wextra")
endif()

add_library (piper-jni SHARED src/main/native/io_github_givimad_piperjni_PiperJNI.cpp ${PIPER_SRC_DIR}/libpiper/src/piper.cpp)
add_custom_target(espeak-ng-data ALL DEPENDS "${CMAKE_CURRENT_BINARY_DIR}/espeak-ng-data.zip")

# NOTE: external project prefix are shortened because of path length restrictions on Windows
# NOTE: onnxruntime is pulled from piper-phonemize


# ---- fmt ---

if(NOT DEFINED FMT_DIR)
  set(FMT_VERSION "10.0.0")
  set(FMT_DIR "${CMAKE_CURRENT_BINARY_DIR}/fi")

  include(ExternalProject)
  ExternalProject_Add(
    fmt_external
    PREFIX "${CMAKE_CURRENT_BINARY_DIR}/f"
    URL "https://github.com/fmtlib/fmt/archive/refs/tags/${FMT_VERSION}.zip"
    CMAKE_ARGS -DCMAKE_INSTALL_PREFIX:PATH=${FMT_DIR} -DFMT_TEST:BOOL=OFF -DCMAKE_OSX_ARCHITECTURES:STRING=${CMAKE_OSX_ARCHITECTURES} -DCMAKE_OSX_DEPLOYMENT_TARGET:STRING=${CMAKE_OSX_DEPLOYMENT_TARGET}
  )
  add_dependencies(piper-jni fmt_external)
endif()

# ---- spdlog ---

if(NOT DEFINED SPDLOG_DIR)
  set(SPDLOG_DIR "${CMAKE_CURRENT_BINARY_DIR}/si")
  set(SPDLOG_VERSION "1.12.0")
  ExternalProject_Add(
    spdlog_external
    PREFIX "${CMAKE_CURRENT_BINARY_DIR}/s"
    URL "https://github.com/gabime/spdlog/archive/refs/tags/v${SPDLOG_VERSION}.zip"
    CMAKE_ARGS -DCMAKE_INSTALL_PREFIX:PATH=${SPDLOG_DIR} -DCMAKE_OSX_ARCHITECTURES:STRING=${CMAKE_OSX_ARCHITECTURES} -DCMAKE_OSX_DEPLOYMENT_TARGET:STRING=${CMAKE_OSX_DEPLOYMENT_TARGET}
  )
  add_dependencies(piper-jni spdlog_external)
endif()

# ---- piper-phonemize ---

if(NOT DEFINED PIPER_PHONEMIZE_DIR)
  set(PIPER_PHONEMIZE_DIR "${CMAKE_CURRENT_BINARY_DIR}/pi")
  ExternalProject_Add(
    piper_phonemize_external
    PREFIX "${CMAKE_CURRENT_BINARY_DIR}/p"
    URL "https://github.com/GiviMAD/piper-phonemize/archive/refs/heads/master.zip"
    CMAKE_ARGS -DCMAKE_INSTALL_PREFIX:PATH=${PIPER_PHONEMIZE_DIR} -DCMAKE_OSX_ARCHITECTURES:STRING=${CMAKE_OSX_ARCHITECTURES} -DCMAKE_OSX_DEPLOYMENT_TARGET:STRING=${CMAKE_OSX_DEPLOYMENT_TARGET}
  )
  add_dependencies(piper-jni piper_phonemize_external)
  add_dependencies(espeak-ng-data piper_phonemize_external)
endif()

# ---- Declare executable ----

if((NOT MSVC) AND (NOT APPLE))
  # Linux flags
  string(APPEND CMAKE_CXX_FLAGS " -Wall -Wextra -Wl,-rpath,'$ORIGIN'")
  string(APPEND CMAKE_C_FLAGS " -Wall -Wextra")
  target_link_libraries(piper-jni -static-libgcc -static-libstdc++)

  set(PIPER_EXTRA_LIBRARIES "pthread")
endif()

function(create_zip output_file working_dir)
    file(GLOB ZIP_FILES ${PIPER_PHONEMIZE_DIR}/share/espeak-ng-data)
    add_custom_command(
        COMMAND ${CMAKE_COMMAND} -E tar "cf" "${output_file}" --format=zip .
        WORKING_DIRECTORY "${working_dir}"
        OUTPUT  "${output_file}"
        COMMENT "Zipping to ${output_file}."
    )
endfunction()

create_zip("${CMAKE_CURRENT_BINARY_DIR}/espeak-ng-data.zip" "${PIPER_PHONEMIZE_DIR}/share/espeak-ng-data")


target_compile_definitions(piper-jni PUBLIC _PIPER_VERSION=${piper_version})

target_link_libraries(piper-jni
  fmt
  spdlog
  espeak-ng
  piper_phonemize
  onnxruntime
  ${PIPER_EXTRA_LIBRARIES}
)

target_link_directories(piper-jni PUBLIC
  ${FMT_DIR}/lib
  ${SPDLOG_DIR}/lib
  ${PIPER_PHONEMIZE_DIR}/lib
)

target_include_directories(piper-jni PUBLIC
  ${JNI_INCLUDE_DIRS}
  ${PIPER_SRC_DIR}/libpiper/include
  ${FMT_DIR}/include
  ${SPDLOG_DIR}/include
  ${PIPER_PHONEMIZE_DIR}/include
)

# ---- Declare install targets ----

install(
  TARGETS piper-jni
  DESTINATION ${CMAKE_INSTALL_PREFIX})

# Dependencies
install(
  DIRECTORY ${PIPER_PHONEMIZE_DIR}/bin/
  DESTINATION ${CMAKE_INSTALL_PREFIX}
  USE_SOURCE_PERMISSIONS  # keep +x
  FILES_MATCHING
  PATTERN "*.dll"
  PATTERN "*.so*"
  PATTERN "*.dylib"
)

install(
  DIRECTORY ${PIPER_PHONEMIZE_DIR}/lib/
  DESTINATION ${CMAKE_INSTALL_PREFIX}
  FILES_MATCHING
  PATTERN "*.dll"
  PATTERN "*.so*"
  PATTERN "*.dylib"
)

install(
  FILES ${CMAKE_CURRENT_BINARY_DIR}/espeak-ng-data.zip
  DESTINATION ${CMAKE_INSTALL_PREFIX}/..
)

