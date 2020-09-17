package com.intel.gkl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Loads native libraries from the classpath, usually from a jar file.
 */
public final class NativeLibraryLoader {
    private static final Logger logger;
    private static final String USE_LIBRARY_PATH;
    private static final Set<String> loadedLibraries = new HashSet<>();
    private static final int MAX_PATH;

    private static enum NATIVE_LIB {gkl_compression, gkl_pairhmm, gkl_pairhmm_omp, gkl_smithwaterman, gkl_utils}

    static {
        logger = LogManager.getLogger(NativeLibraryLoader.class);
        USE_LIBRARY_PATH = "USE_LIBRARY_PATH";
        MAX_PATH = 4096;
    }

    // Check library path is not too long
    private static boolean checkPath(String tempPath){
        if(tempPath.length() <= MAX_PATH){
            return true;
        }
        return false;
    }
    // Check libraryName is as expected
    private static boolean checkLibraryName(String libName){
       //
       if(libName == null || libName.length() == 0 || libName.length() > MAX_PATH) {
            logger.warn(" Unexpected library name");
            return false;
        }
        // Validate library names
        for (NATIVE_LIB libItem : NATIVE_LIB.values()) {
            if(libName.matches(libItem.toString())) {
                return true;
            }
        }

        logger.warn(String.format(" Unexpected library name %s",libName));
        return false;
    }

    /**
     * Tries to load the native library from the classpath, usually from a jar file. <p>
     *
     * If the USE_LIBRARY_PATH environment variable is defined, the native library will be loaded from the
     * java.library.path instead of the classpath.
     *
     * @param tempDir  directory where the native library is extracted or null to use the system temp directory
     * @param libraryName  name of the shared library without system dependent modifications
     * @return true if the library was loaded successfully, false otherwise
     */
    public static synchronized boolean load(File tempDir, String libraryName) {

        if(!checkLibraryName(libraryName)) return false;

        if (loadedLibraries.contains(libraryName)) {
            return true;
        }

        final String systemLibraryName = System.mapLibraryName(libraryName);

        // load from the java.library.path
        if (System.getenv(USE_LIBRARY_PATH) != null) {
            final String javaLibraryPath = System.getProperty("java.library.path");

            try {
                logger.warn(String.format("OVERRIDE DEFAULT: Loading %s from %s", systemLibraryName, javaLibraryPath));
                logger.warn(String.format("LD_LIBRARY_PATH = %s", System.getenv("LD_LIBRARY_PATH")));
                System.loadLibrary(libraryName);
                return true;
            } catch (Exception|Error e) {
                logger.warn(String.format("OVERRIDE DEFAULT: Unable to load %s from %s", systemLibraryName, javaLibraryPath));
                return false;
            }
        }

        // load from the java classpath
        final String resourcePath = "native/" +  systemLibraryName;
        final URL inputUrl = NativeLibraryLoader.class.getResource(resourcePath);
        if (inputUrl == null) {
            logger.warn("Unable to find native library: " + resourcePath);
            return false;
        }
        logger.info(String.format("Loading %s from %s", systemLibraryName, inputUrl.toString()));

        try {
            final File temp = File.createTempFile(FilenameUtils.getBaseName(resourcePath),
                    "." + FilenameUtils.getExtension(resourcePath), tempDir);
            FileUtils.copyURLToFile(inputUrl, temp);
            temp.deleteOnExit();
            if(checkPath(temp.getAbsolutePath())) {
                System.load(temp.getAbsolutePath());
                logger.debug(String.format("Extracting %s to %s", systemLibraryName, temp.getAbsolutePath()));
            }
            else{
                logger.warn(String.format("Unable to load %s to %s", systemLibraryName, temp.getAbsolutePath()));
            }
        } catch (Exception|Error e) {
            logger.warn(String.format("Unable to load %s from %s (%s)", systemLibraryName, resourcePath, e.getMessage()));
            return false;
        }

        loadedLibraries.add(libraryName);
        return true;
    }
}
