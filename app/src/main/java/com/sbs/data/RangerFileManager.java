package com.sbs.data;

import android.content.Context;

import java.io.File;

public final class RangerFileManager {

    private RangerFileManager() {
    }

    public static File getRangerRoot(Context context, String rangerId) {
        return new File(new File(context.getFilesDir(), "rangers"), rangerId);
    }

    public static File ensureRangerRoot(Context context, String rangerId) {
        File root = getRangerRoot(context, rangerId);
        if (!root.exists()) {
            root.mkdirs();
        }
        return root;
    }

    public static boolean deleteRangerRoot(Context context, String rangerId) {
        return deleteRecursively(getRangerRoot(context, rangerId));
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        return file.delete();
    }
}
