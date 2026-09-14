package com.testify.client;

import java.io.File;
import java.net.URL;

/**
 * Resolves stored image references (avatars, question visual aids) to URLs that
 * {@code javafx.scene.image.Image} can load. While the app is localhosted, a
 * bare filename or relative path is looked up inside the project's local
 * {@code images/} folder, so no web server is required.
 */
public final class ImageResolver {

    /** Name of the project-local folder that holds images while localhosted. */
    public static final String LOCAL_IMAGE_DIR = "images";

    private ImageResolver() {
    }

    /**
     * Resolves a stored image reference to a loadable URL string.
     *
     * Accepted inputs, in priority order:
     *   1. A full URL (http/https/file/data/jar) → used unchanged.
     *   2. An absolute filesystem path that exists → converted to a file: URL.
     *   3. A filename/relative path found under {@code <project>/images/} → file: URL.
     *   4. A bundled classpath resource under {@code /images/} → resource URL.
     *   5. Anything else → the raw value (Image fails gracefully; callers fall back).
     *
     * @return a loadable URL string, or {@code null} when the input is empty
     */
    public static String resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();

        // 1. Already a full URL — use as-is.
        if (value.matches("(?i)^(https?|file|data|jar):.*")) {
            return value;
        }

        // 2. Absolute filesystem path.
        File direct = new File(value);
        if (direct.isAbsolute() && direct.exists()) {
            return direct.toURI().toString();
        }

        // 3. Relative to the project's local images folder. Try both the module
        //    dir and the workspace root so it works from either run location.
        for (File root : searchRoots()) {
            File candidate = new File(new File(root, LOCAL_IMAGE_DIR), value);
            if (candidate.exists()) {
                return candidate.toURI().toString();
            }
        }

        // 4. Bundled classpath resource under /images/.
        URL resource = ImageResolver.class.getResource("/" + LOCAL_IMAGE_DIR + "/" + value.replace('\\', '/'));
        if (resource != null) {
            return resource.toExternalForm();
        }

        // 5. Give the raw value to JavaFX as a last resort.
        return value;
    }

    /**
     * Returns the writable local images directory (creating it if needed) where
     * uploads are copied. Matches the first location {@link #resolve(String)}
     * searches, so anything copied here is immediately resolvable by filename.
     */
    public static File imagesDir() {
        File dir = new File(searchRoots()[0], LOCAL_IMAGE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Candidate project roots, in the order resolution should try them. */
    private static File[] searchRoots() {
        String userDir = System.getProperty("user.dir");
        return new File[]{
                new File(userDir),
                new File(userDir + File.separator + "client")
        };
    }
}
