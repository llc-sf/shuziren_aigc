package ai.guiji.duix.test.util;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FileUtils {
    private static final long FILE_COPY_BUFFER_SIZE = 1024 * 1024;

    /**
     * 初始化Log保存目录
     *
     * @param context
     * @return
     */
    public static String initLogFolder(Context context) {

        File file = null;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            file = context.getExternalFilesDir("Logs");
        } else {
            file = new File(context.getFilesDir(), "Logs");
        }
        if (!file.exists()) {
            file.mkdirs();
            // 如果没有创建成功，再创建一次
            if (!file.isDirectory()) {
                file.mkdirs();
            }
        }
        return file.getAbsolutePath();
    }

    /**
     * 新的封面路径保存
     * 初始化封面图片保存的路径
     * 兼容Android 11
     *
     * @param context
     * @return
     */
    public static String initArtworkFolderV2(Context context) {
        File parent = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        File file = new File(parent, "artwork");

        if (!file.exists()) {
            file.mkdirs();
        }
        return file.getAbsolutePath();
    }


    /**
     * 获取备份文件夹
     *
     * @param context
     * @param isManual
     * @return
     */
    public static File getPlayListBackupFolder(Context context, boolean isManual) {
        File file = null;
        if (isManual) {
            file = new File(context.getExternalFilesDir("Backup"), "PlayList_Manual_Bak");
        } else {
            file = new File(context.getExternalFilesDir("Backup"), "PlayList_Auto_Bak");
        }
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }


    /**
     * 向本地写入文件
     *
     * @param path
     * @param content
     */
    public static void writeFile(String path, String content) {
        FileOutputStream outputStream = null;
        try {
            File file = new File(path);
            outputStream = new FileOutputStream(file);
            byte[] bytes = content.getBytes();
            outputStream.write(bytes);
            outputStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 读取文件
     *
     * @param path
     * @return
     */
    public static String readFile(String path) {
        String reString = "";
        BufferedReader reader = null;
        try {
            File file = new File(path);
            reader = new BufferedReader(new FileReader(file));
            String tempString = null;
            // 一次读入一行，直到读入null为文件结束
            while ((tempString = reader.readLine()) != null) {
                // 显示行号
                reString += tempString;
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                }
            }
        }
        return reString;
    }

    public static boolean deleteFile(File file) {
        List<File> filesToDelete = new ArrayList<>();
        filesToDelete.add(file);
        while (!filesToDelete.isEmpty()) {
            File fileToDelete = filesToDelete.remove(0);
            if (!fileToDelete.isDirectory()) {
                if (!fileToDelete.delete()) {

                    return false;
                }
            }
            File[] normalFiles = fileToDelete.listFiles(file1 -> !file1.isDirectory());
            if (normalFiles != null) {
                for (int i = 0; i < normalFiles.length; i++) {
                    if (!normalFiles[i].delete()) {
                        return false;
                    }
                }
            }

            File[] subFolders = fileToDelete.listFiles(file1 -> file1.isDirectory());
            if (subFolders != null) {
                for (File folder : subFolders) {
                    filesToDelete.add(folder);
                }
            }
        }

        return true;
    }

    /**
     * 递归copy文件
     *
     * @param src
     * @param target
     */
    public static void copy(File src, File target) {
        if (!src.exists() || !src.canRead()) {
            Log.d("FileUtils", "Src File NotFound Or Not Read");
            return;
        }
        if (src.isDirectory()) {
            Log.d("FileUtils", "Src isDirectory");
            if (!target.exists()) {
                target.mkdirs();
            }
            String[] files = src.list();
            if (files == null || files.length == 0) {
                return;
            }
            for (String file : files) {
                Log.d("FileUtils", "Src isDirectory Child = " + file);
                File srcFile = new File(src, file);
                File desFile = new File(target, file);
                Log.d("FileUtils", "Src isDirectory srcFile = " + srcFile);
                Log.d("FileUtils", "Src isDirectory desFile = " + desFile);
                copy(srcFile, desFile);
            }
        } else {
            Log.d("FileUtils", "Src IsFile");
            try (FileInputStream fis = new FileInputStream(src);
                 FileOutputStream fos = new FileOutputStream(target)
            ) {
                byte[] bytes = new byte[1024];
                int len = -1;
                while ((len = fis.read(bytes)) != -1) {
                    fos.write(bytes, 0, len);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.i("FileUtils", "copy: " + e.getMessage());
            }
        }

        Log.d("FileUtils", "Copy All Src = " + src.getAbsolutePath() +
                " Target = " + target.getAbsolutePath());
    }

    /**
     * 删除文件或者文件夹
     *
     * @param file
     * @return
     */
    public static boolean delete(File file) {
        if (!file.exists()) {
            Log.d("FileUtils", "File Can't Delete FileNotFound");
            return false;
        }
        if (!file.canWrite()) {
            Log.d("FileUtils", "File Can't Delete File Can't Write");
            return false;
        }

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null && files.length != 0) {
                for (File f : files) {
                    delete(f);
                }
            }
        }
        return file.delete();
    }

    /**
     * 重命名文件 或者文件夹 如有有重名的文件，则覆盖
     *
     * @param file
     * @param newName
     */
    public static boolean rename(File file, String newName) {
        if (file.exists()) {
            File newFile = new File(file.getParent(), newName);
            if (newFile.exists()) {
                delete(newFile);
            }
            return file.renameTo(newFile);
        } else {
            return false;
        }
    }


    /**
     * 通过地址获取InputStream
     */
    @Nullable
    public static InputStream getFileInputStream(Context context, String path) throws Exception {
        InputStream stream = null;
        if (path == null) return null;
        if (!TextUtils.isEmpty(path) && path.startsWith("content:")) {
            stream = context.getContentResolver().openInputStream(Uri.parse(path));
        } else if (!TextUtils.isEmpty(path) && path.startsWith("file:")) {
            return getFileInputStream(context, Uri.parse(path).getPath());
        } else {
            File coverFile = new File(path);
            stream = new FileInputStream(coverFile);
        }
        return stream;
    }



    /**
     * 获取文件大小
     *
     * @param size
     * @return String
     */
    public static String getFileSize(long size) {
        if (size <= 0) {
            return "0B";
        }
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.00").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * 获取文件大小（使用Locale.US格式化）
     *
     * @param size
     * @return String
     */
    public static String getFileSizeUS(long size) {
        if (size <= 0) {
            return "0B";
        }
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format(Locale.US, "%.2f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }



    public static boolean isSubPath(String path, String parent) {
        File subFile = new File(path);
        File parentFile = new File(parent);
        return subFile.equals(parentFile) || subFile.getParentFile().equals(parentFile);
    }



}