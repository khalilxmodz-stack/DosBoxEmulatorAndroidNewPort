package io.dosboxstaging.android;

import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Toast;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Hosts the real DOSBox Staging core (libmain.so) inside SDL's Android activity.
 *
 * Storage layout (all app-private, no storage permission needed):
 *   files/resources/          extracted from APK assets (shaders, keyboard layouts ...)
 *   files/config/dosbox/      dosbox-staging.conf (created once, user-editable via adb)
 *   files/games/              mounted as C: ; the folder importer copies games here
 */
public class MainActivity extends SDLActivity {
    private static final int REQ_IMPORT = 4101;
    private static final String RES_MARKER = "resources/.extracted-";

    private TouchControls controls;

    // Implemented in src/platform/android/android_glue.cpp
    private static native int nativeSetup(String filesDir);

    @Override
    protected String[] getLibraries() {
        // libSDL3.so must be loaded before libmain.so (which links against it).
        return new String[] { "SDL3", "main" };
    }

    /** SDL loads the libraries here; set env + cwd right after, before DOSBox starts. */
    @Override
    protected void loadLibraries() {
        super.loadLibraries();
        nativeSetup(getFilesDir().getAbsolutePath());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            prepareFiles();
        } catch (IOException e) {
            Toast.makeText(this, "Setup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        super.onCreate(savedInstanceState);

        controls = new TouchControls(this, () -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(i, REQ_IMPORT);
        });
        addContentView(controls, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Never leave a virtual key held when the app stops receiving touch events. */
    @Override
    protected void onPause() {
        if (controls != null) controls.releaseAll();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus && controls != null) controls.releaseAll();
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_IMPORT || result != RESULT_OK || data == null) return;
        final Uri tree = data.getData();
        if (tree == null) return;
        final File gamesDir = new File(getFilesDir(), "games");
        new Thread(() -> {
            try {
                FolderImporter.Result r = FolderImporter.importTree(getContentResolver(), tree, gamesDir);
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.import_done, r.files, r.folderName)
                                + "  (type RESCAN in DOSBox)", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.import_failed, String.valueOf(e.getMessage())),
                        Toast.LENGTH_LONG).show());
            }
        }, "game-import").start();
    }

    // ---- first-run file preparation -------------------------------------------------

    private void prepareFiles() throws IOException {
        File files = getFilesDir();
        File games = new File(files, "games");
        File confDir = new File(files, "config/dosbox");
        if (!games.isDirectory() && !games.mkdirs()) throw new IOException("cannot create games dir");
        if (!confDir.isDirectory() && !confDir.mkdirs()) throw new IOException("cannot create config dir");

        // Re-extract resources whenever the app version changes.
        String version = String.valueOf(getVersionCode());
        File marker = new File(files, RES_MARKER + version);
        if (!marker.exists()) {
            deleteRecursive(new File(files, "resources"));
            copyAssetDir(getAssets(), "resources", new File(files, "resources"));
            if (!marker.createNewFile()) throw new IOException("cannot write marker");
        }

        File conf = new File(confDir, "dosbox-staging.conf");
        if (!conf.exists()) {
            String text = "[sdl]\nfullscreen = true\n\n[autoexec]\n@echo off\n"
                    + "mount c \"" + games.getAbsolutePath() + "\"\nc:\n";
            try (OutputStream os = new FileOutputStream(conf)) {
                os.write(text.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @SuppressWarnings("deprecation")
    private long getVersionCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void copyAssetDir(AssetManager am, String assetPath, File dest) throws IOException {
        String[] children = am.list(assetPath);
        if (children == null || children.length == 0) {          // file
            File parent = dest.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("mkdir failed: " + parent);
            }
            try (InputStream in = am.open(assetPath); OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return;
        }
        if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException("mkdir failed: " + dest);
        for (String c : children) copyAssetDir(am, assetPath + "/" + c, new File(dest, c));
    }

    private static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
