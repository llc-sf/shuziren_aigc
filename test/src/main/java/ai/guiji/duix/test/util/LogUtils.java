package ai.guiji.duix.test.util;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ai.guiji.duix.test.BuildConfig;


public class LogUtils {


    private static final int SPACESIZELIMIT = 256;
    private static LogUtils appException;
    private static StringBuilder logBuilder = new StringBuilder();
    private Context context;
    private File logFile;
    private FileOutputStream logStream;

    private final WriteHandler handler;

    private LogUtils(Context context) {
        this.context = context;
        logBuilder = new StringBuilder();
        HandlerThread ht = new HandlerThread("LogUtilsThread", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        ht.start();
        handler = new WriteHandler(ht.getLooper(), this);
    }

    /**
     * 初始化
     *
     * @return
     */
    public synchronized static LogUtils getInstance(Context context) {
        if (appException == null) {
            appException = new LogUtils(context.getApplicationContext());
        }
        return appException;
    }


    public static void logException(String tag, String message, Throwable throwable) {
//        if (BuildConfig.DEBUG) {
//            Log.e(tag, message + "\nThrowable: " + throwable.getMessage());
//            throwable.printStackTrace();
//        } else {
//            FirebaseCrashlytics.getInstance().log(message + "\nThrowable: " + throwable.getMessage());
//        }
    }


    private void initLogFileAndStream() {

        if (logFile != null && logStream != null) {
            if (logFile.length() > SPACESIZELIMIT * 1024) {
                try {
                    if (logStream != null) {
                        logStream.close();
                    }

                    logStream = null;
                    logFile = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                return;
            }
        }
        try {
            if (logFile == null) {
                logFile = new File(FileUtils.initLogFolder(context), "tracker.log");
            }
            logStream = new FileOutputStream(logFile, true);
        } catch (Exception e) {
            e.printStackTrace();
            if (logStream != null) {
                try {
                    logStream.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                logStream = null;
            }
            logFile = null;
        }
    }

    public void logVideo(String content){
//        log("**** video_log **** "+content);
    }

    public void log(String content) {
        if (content == null) {
            return;
        }
        try {
            String log;
            synchronized (LogUtils.class) {
                logBuilder.append("\r\n");
                logBuilder.append(getCurrentTime());
                logBuilder.append("--");
                logBuilder.append(content);
                log = logBuilder.toString();
                logBuilder.setLength(0);
            }
            if (BuildConfig.DEBUG) {
                Log.d("Log", log);
            }
            writeLogWithDelay(log);
//            Message msg = Message.obtain(handler);
//            msg.obj = log;
//            handler.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final StringBuilder temp = new StringBuilder();

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            synchronized (temp) {
                if (!TextUtils.isEmpty(temp)) {
                    Message msg = Message.obtain(handler);
                    msg.obj = temp.toString();
                    temp.setLength(0);
                    handler.sendMessage(msg);
                }
            }
        }
    };

    /**
     * 数据缓冲，写入日志
     *
     * @param content
     */
    private void writeLogWithDelay(String content) {
        synchronized (temp) {
            temp.append(content);
        }
        handler.removeCallbacks(runnable);

        if (temp.length() >= 1024 * 10) {
            handler.post(runnable);
        } else {
            handler.postDelayed(runnable, 500);
        }
    }

    public static void logMethodTrace() {
        StackTraceElement[] elements = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (StackTraceElement element : elements) {
            if (i > 5) {
                break;
            }
            sb.append(element.getLineNumber()).append("\t")
                    .append(element.getClassName())
                    .append("\t")
                    .append(element.getMethodName()).append("\n");
            i++;
        }
        Log.e("Log", sb.toString());
//        log(sb.toString());
    }

    /**
     * 获取运行时堆栈信息
     *
     * @return
     */
    public static String getMethodTrace() {
        StackTraceElement[] elements = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (StackTraceElement element : elements) {
            if (i > 8) {
                break;
            }
            sb.append(element.getLineNumber()).append("\t")
                    .append(element.getClassName())
                    .append("\t")
                    .append(element.getMethodName()).append("\n");
            i++;
        }
        return sb.toString();
    }


    public void printMethodTrace() {
        StackTraceElement[] elements = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (StackTraceElement element : elements) {
            if (i > 5) {
                break;
            }
            sb.append(element.getLineNumber()).append("\t")
                    .append(element.getClassName())
                    .append("\t")
                    .append(element.getMethodName()).append("\n");
            i++;
        }
        Log.e("Log", sb.toString());
    }

    /**
     * 自定义日志输出方法
     *
     * @param content
     */
    public void innerLog(String content) {
        try {
            initLogFileAndStream();
            if (logStream == null) {
                return;
            }

            logStream.write(content.getBytes("UTF-8"));
            logStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 将异常信息转化成字符串的形式
     *
     * @param e
     * @return
     */
    public synchronized void logException(Throwable e, boolean uncatched) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        e.printStackTrace(writer);
        StringBuffer buffer = stringWriter.getBuffer();
        if (uncatched) {
            log("Crash:\n" + buffer.toString().replaceAll("\n", "\r\n"));
        } else {
            log("HandledException:\n" + buffer.toString().replaceAll("\n", "\r\n"));
        }
    }

    //时间格式化工具
    private final SimpleDateFormat mFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    /**
     * 获得当前时间
     *
     * @return
     */
    public String getCurrentTime() {
        try {
            return mFormat.format(new Date(System.currentTimeMillis()));
        } catch (Exception e) {
            return new Date(System.currentTimeMillis()).toString();
        }
    }

    /**
     * 判断文件是否超出大小上限
     *
     * @throws IOException
     */
    public void deleteOldFile() throws IOException {
        File logFile = new File(FileUtils.initLogFolder(context), "tracker.log");
        if (logFile.exists()) {
            FileInputStream fis = new FileInputStream(logFile);
            int size = fis.available();
            fis.close();
            fis = null;
            double memory = (double) size / 1024;
            if (memory > SPACESIZELIMIT) {
                logFile.delete();
                logFile.createNewFile();
            }
        } else {
            logFile.createNewFile();
        }
    }

    /**
     * 获得文件，考虑文件大小上限问题
     *
     * @return
     */
    public File getLogFile() {
        try {
            deleteOldFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        File myFile = new File(FileUtils.initLogFolder(context), "tracker.log");
        return myFile;
    }

    /**
     * 获得文件，考虑文件大小上限问题
     *
     * @return
     */
    public File getFile() {
        try {
            deleteOldFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        File myFile = new File(FileUtils.initLogFolder(context), "crash.log");
        return myFile;
    }

    public synchronized StringBuilder exportLog() {
        File logFile = new File(FileUtils.initLogFolder(context), "tracker.log");
        StringBuilder stringBuilder = new StringBuilder();
        String buffer = "";
        if (logFile.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(logFile);
                InputStreamReader inputStreamReader = new InputStreamReader(fis);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                while ((buffer = bufferedReader.readLine()) != null) {
                    stringBuilder.append(buffer).append("\n");
                }
                return stringBuilder;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    static class WriteHandler extends Handler {
        private WeakReference<LogUtils> logUtilsReference;

        WriteHandler(@NonNull Looper looper, LogUtils logUtils) {
            super(looper);
            logUtilsReference = new WeakReference<>(logUtils);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {

            if (msg.obj == null) {
                return;
            }

            if (logUtilsReference == null) {
                return;
            }
            LogUtils logUtils = logUtilsReference.get();

            String content = (String) msg.obj;
            logUtils.innerLog(content);
        }

    }
}