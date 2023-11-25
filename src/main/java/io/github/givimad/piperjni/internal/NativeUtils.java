/*
 * Class NativeUtils is published under the The MIT License:
 *
 * Copyright (c) 2012 Adam Heinrich <adam@adamh.cz>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.givimad.piperjni.internal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A simple library class which helps with loading dynamic libraries stored in the
 * JAR archive. These libraries usually contain implementation of some methods in
 * native code (using JNI - Java Native Interface).
 *
 * @see <a href="http://adamheinrich.com/blog/2012/how-to-load-native-jni-library-from-jar">http://adamheinrich.com/blog/2012/how-to-load-native-jni-library-from-jar</a>
 * @see <a href="https://github.com/adamheinrich/native-utils">https://github.com/adamheinrich/native-utils</a>
 *
 */
public class NativeUtils {

    /**
     * The minimum length a prefix for a file has to have according to {@link File#createTempFile(String, String)}}.
     */
    private static final int MIN_PREFIX_LENGTH = 3;
    private static final String NATIVE_FOLDER_PATH_PREFIX = "piper-jni-native";

    /**
     * Temporary directory which will contain the DLLs.
     */
    private static Path temporaryDir;
    /**
     * Espeak data path in temporary directory.
     */
    private static Path espeakNGDir;
    /**
     * Espeak data path in temporary directory.
     */
    private static Path tashkeelModelFile;

    /**
     * Private constructor - this class will never be instanced
     */
    private NativeUtils() {
    }

    /**
     * Loads library from current JAR archive
     *
     * The file from JAR is copied into system temporary directory and then loaded. The temporary file is deleted after
     * exiting.
     * Method uses String as filename because the pathname is "abstract", not system-dependent.
     *
     * @param path The path of file inside JAR as absolute path (beginning with '/'), e.g. /package/File.ext
     * @throws IOException If temporary file creation or read/write operation fails
     * @throws IllegalArgumentException If source file (param path) does not exist
     * @throws IllegalArgumentException If the path is not absolute or if the filename is shorter than three characters
     * (restriction of {@link File#createTempFile(java.lang.String, java.lang.String)}).
     * @throws FileNotFoundException If the file could not be found inside the JAR.
     */
    public static void loadLibraryFromJar(String path) throws IOException {
        if (null == path || !path.startsWith("/")) {
            throw new IllegalArgumentException("The path has to be absolute (start with '/').");
        }
        // Obtain filename from path
        String[] parts = path.split("/");
        String filename = (parts.length > 1) ? parts[parts.length - 1] : null;
        // Check if the filename is okay
        if (filename == null || filename.length() < MIN_PREFIX_LENGTH) {
            throw new IllegalArgumentException("The filename has to be at least 3 characters long.");
        }
        Path temp = extractToTempDir(path, filename);
        try {
            System.load(temp.toAbsolutePath().toString());
        } finally {
            if (isPosixCompliant()) {
                // Assume POSIX compliant file system, can be deleted after loading
                Files.delete(temp);
            } else {
                // Assume non-POSIX, and don't delete until last file descriptor closed
                temp.toFile().deleteOnExit();
            }
        }
    }
    public static Path getESpeakNGData() throws IOException {
        if(espeakNGDir == null || !Files.exists(espeakNGDir)) {
            String zipName = "espeak-ng-data.zip";
            Path espeakNGZip = extractToTempDir( "/"+zipName, zipName);
            espeakNGDir = espeakNGZip.getParent().resolve(zipName.substring(0, zipName.indexOf(".")));
            extractZipTo(espeakNGZip, espeakNGDir);
            Files.delete(espeakNGZip);
        }
        return espeakNGDir.toAbsolutePath();
    }
    public static Path getTashkeelModel() throws IOException {
        if(tashkeelModelFile == null || !Files.exists(tashkeelModelFile)) {
            String modelName = "libtashkeel_model.ort";
            tashkeelModelFile = NativeUtils.extractToTempDir("/" + modelName, modelName);
        }
        return tashkeelModelFile.toAbsolutePath();
    }
    public static void extractZipTo(Path archiveFile, Path destPath) throws IOException {
        Files.createDirectories(destPath); // create dest path folder(s)
        try (ZipFile archive = new ZipFile(archiveFile.toFile())) {
            // sort entries by name to always create folders first
            List<? extends ZipEntry> entries = archive.stream()
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .collect(Collectors.toList());
            // copy each entry in the dest path
            for (ZipEntry entry : entries) {
                Path entryDest = destPath.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectory(entryDest);
                    continue;
                }
                Files.copy(archive.getInputStream(entry), entryDest);
            }
        }

    }
    private static Path extractToTempDir(String path, String filename) throws IOException {
        // Prepare temporary file
        if (temporaryDir == null) {
            temporaryDir = createTempDirectory(NATIVE_FOLDER_PATH_PREFIX);
            temporaryDir.toFile().deleteOnExit();
        }
        Path temp = temporaryDir.resolve(filename);
        try (InputStream is = NativeUtils.class.getResourceAsStream(path)) {
            Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.delete(temp);
            throw e;
        } catch (NullPointerException e) {
            Files.delete(temp);
            throw new FileNotFoundException("File " + path + " was not found inside JAR.");
        }
        return temp;
    }

    private static boolean isPosixCompliant() {
        try {
            return FileSystems.getDefault()
                    .supportedFileAttributeViews()
                    .contains("posix");
        } catch (FileSystemNotFoundException
                | ProviderNotFoundException
                | SecurityException e) {
            return false;
        }
    }

    private static Path createTempDirectory(String prefix) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        Path generatedDir = Path.of(tempDir, prefix + System.nanoTime());
        Files.createDirectory(generatedDir);
        return generatedDir;
    }
}