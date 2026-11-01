package kalvi.transport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URISyntaxException;

/**
 * Finds the project folder (the one that contains web/, lib/, sql/, db.properties) so the program works no matter
 * where it is started from: run.bat, a terminal in another folder, or an IDE's Run button.
 */
public final class AppDir {
    private static Path root;
    private AppDir() { }

    public static synchronized Path root() {
        if (root != null) return root;
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.exists(cwd.resolve("web").resolve("index.html"))) return root = cwd;
        try {
            Path code = Paths.get(AppDir.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (Path p = code; p != null; p = p.getParent())
                if (Files.exists(p.resolve("web").resolve("index.html"))) return root = p;
        } catch (URISyntaxException | SecurityException ignored) { /* fall through to the working directory */ }
        return root = cwd;
    }

    public static Path of(String first, String... more) { return root().resolve(Paths.get(first, more)); }
}
