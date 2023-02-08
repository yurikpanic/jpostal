package com.mapzen.jpostal;

import java.io.*;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.StandardCopyOption;

/**
 * Based on https://github.com/adamheinrich/native-utils, but with adjustments
 */
public class NativeUtils {
 
    public static final String NATIVE_FOLDER_PATH_PREFIX = "nativeutils";
    public static final String NATIVE_RESOURCES_PREFIX_IN_JAR = "/native/";

    /**
     * Temporary directory which will contain the DLLs.
     */
    private static File temporaryDir;

    /**
     * Private constructor - this class will never be instanced
     */
    private NativeUtils() {
    }

    public static void loadOrFail(String libname) {
        try {
            System.loadLibrary(libname);
        } catch (UnsatisfiedLinkError e) {
            try {
                NativeUtils.loadLibraryFromJar(libname);
            } catch (IOException e1) {
                throw new RuntimeException(String.format("Failed to load dynamic library %s", libname), e1);
            }
        }
    }

    /**
     * Loads library from current JAR archive
     * 
     * The file from JAR is copied into system temporary directory and then loaded. The temporary file is deleted after
     * exiting.
     * Method uses String as filename because the pathname is "abstract", not system-dependent.
     * 
     * @param libname The path of file inside JAR as absolute path (beginning with '/'), e.g. /package/File.ext
     * @throws IOException If temporary file creation or read/write operation fails
     * @throws IllegalArgumentException If source file (param path) does not exist
     * @throws FileNotFoundException If the file could not be found inside the JAR.
     */
    public static void loadLibraryFromJar(String libname) throws IOException {
 
        String suffix = null;
        String osName = System.getProperty("os.name");

        if (osName.contains("Mac OS")) {
            suffix = "jnilib";
        } else if (osName.contains("Windows")) {
            suffix = "dll";
        } else {
            // A bit hairy assumption
            suffix = "so";
        }

        String filename = libname + "." + suffix;
 
        if (temporaryDir == null) {
            temporaryDir = Files.createTempDirectory(NATIVE_FOLDER_PATH_PREFIX).toFile();
            temporaryDir.deleteOnExit();
        }

        File temp = new File(temporaryDir, filename);

        try (InputStream is = NativeUtils.class.getResourceAsStream(NATIVE_RESOURCES_PREFIX_IN_JAR + filename)) {
            Files.copy(is, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            temp.delete();
            throw e;
        } catch (NullPointerException e) {
            temp.delete();
            throw new FileNotFoundException("File " + filename + " was not found inside JAR.");
        }

        try {
            System.load(temp.getAbsolutePath());
        } finally {
            if (isPosixCompliant()) {
                // Assume POSIX compliant file system, can be deleted after loading
                temp.delete();
            } else {
                // Assume non-POSIX, and don't delete until last file descriptor closed
                temp.deleteOnExit();
            }
        }
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

}