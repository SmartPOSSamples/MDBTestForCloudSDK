package com.cloudpos.mdbtestforjavadoc.util;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 自动轮转文件写入器
 * - 文件名：log_yyyyMMdd_HHmmss_SSS.txt
 * - 文件大小超过 4MB 时自动新建文件
 * - 线程安全
 */
public class FileHelper {
    private static final String TAG = "FileHelper";
    private static final long MAX_FILE_SIZE = 4 * 1024 * 1024; // 4MB
    private static final String FILE_NAME_PATTERN = "log_%s.txt";
    private static final String DATE_PATTERN = "yyyyMMdd_HHmmss_SSS";

    private final File directory;
    private File currentFile;
    private FileOutputStream fos;
    private OutputStreamWriter writer;
    private final Object lock = new Object(); // 用于线程同步

    /**
     * 构造器
     * @param directory 存放日志文件的目录（必须已存在且有写入权限）
     */
    public FileHelper(File directory) {
        this.directory = directory;
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalArgumentException("Directory does not exist and cannot be created: " + directory);
        }
        // 创建第一个文件
        createNewFile();
    }

    /**
     * 写入一行文本（自动添加换行符）
     * @param text 要写入的文本
     */
    public void writeLine(String text) {
        write(text + System.lineSeparator());
    }

    /**
     * 写入原始文本（不自动换行）
     */
    public void write(String text) {
        synchronized (lock) {
            try {
                // 检查当前文件大小，若超过限制则轮转
                if (currentFile != null && currentFile.length() > MAX_FILE_SIZE) {
                    rotate();
                }

                // 如果 writer 未初始化，则重新打开
                if (writer == null) {
                    openCurrentFile();
                }

                writer.write(text);
                writer.flush();
            } catch (IOException e) {
                Log.e(TAG, "Write failed", e);
                // 发生异常时可尝试重新创建文件
                try {
                    rotate();
                } catch (IOException ex) {
                    Log.e(TAG, "Rotation failed", ex);
                }
            }
        }
    }

    /**
     * 关闭当前文件，释放资源（建议在 Activity 销毁时调用）
     */
    public void close() {
        synchronized (lock) {
            try {
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
                if (fos != null) {
                    fos.close();
                    fos = null;
                }
            } catch (IOException e) {
                Log.e(TAG, "Close failed", e);
            }
        }
    }

    // ---- 私有辅助方法 ----

    /**
     * 创建新文件（基于当前时间戳）
     */
    private void createNewFile() {
        String timestamp = new SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(new Date());
        String fileName = String.format(Locale.getDefault(), FILE_NAME_PATTERN, timestamp);
        currentFile = new File(directory, fileName);
    }

    /**
     * 打开当前文件（追加模式）
     */
    private void openCurrentFile() throws IOException {
        if (currentFile == null) {
            createNewFile();
        }
        fos = new FileOutputStream(currentFile, true); // 追加写入
        writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
    }

    /**
     * 轮转：关闭旧文件，创建新文件
     */
    private void rotate() throws IOException {
        // 关闭当前 writer 和 stream
        if (writer != null) {
            writer.close();
            writer = null;
        }
        if (fos != null) {
            fos.close();
            fos = null;
        }
        // 创建新文件
        createNewFile();
        // 重新打开
        openCurrentFile();
    }
}