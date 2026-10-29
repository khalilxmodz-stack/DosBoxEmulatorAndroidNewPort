package io.dosboxstaging.android;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Copies a folder chosen with the system picker (Storage Access Framework) into
 * app-private storage so DOSBox can mount it with ordinary POSIX file access.
 * Nothing is uploaded anywhere; the app has no network permission.
 */
final class FolderImporter {
    static final class Result {
        final int files;
        final String folderName;
        Result(int files, String folderName) { this.files = files; this.folderName = folderName; }
    }

    private FolderImporter() {}

    static Result importTree(ContentResolver cr, Uri tree, File gamesDir) throws IOException {
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        String name = lastSegment(rootId);
        if (name.isEmpty()) name = "GAME";
        File dest = new File(gamesDir, safeName(name));
        int[] count = {0};
        copy(cr, tree, rootId, dest, count);
        return new Result(count[0], dest.getName());
    }

    private static void copy(ContentResolver cr, Uri tree, String docId, File dest, int[] count)
            throws IOException {
        if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException("cannot create " + dest);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] cols = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE };
        try (Cursor c = cr.query(children, cols, null, null, null)) {
            if (c == null) throw new IOException("picker returned no listing");
            while (c.moveToNext()) {
                String id = c.getString(0);
                String childName = safeName(c.getString(1));
                String mime = c.getString(2);
                File target = new File(dest, childName);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    copy(cr, tree, id, target, count);
                } else {
                    Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    try (InputStream in = cr.openInputStream(doc);
                         OutputStream out = new FileOutputStream(target)) {
                        if (in == null) throw new IOException("cannot open " + childName);
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    count[0]++;
                }
            }
        }
    }

    /** Prevents path traversal and characters DOS cannot represent. */
    private static String safeName(String n) {
        if (n == null || n.isEmpty()) return "_";
        String s = n.replace('/', '_').replace('\\', '_').replace(':', '_');
        return (s.equals(".") || s.equals("..")) ? "_" : s;
    }

    private static String lastSegment(String docId) {
        int i = Math.max(docId.lastIndexOf('/'), docId.lastIndexOf(':'));
        return i >= 0 ? docId.substring(i + 1) : docId;
    }
}
