package com.ch.install.flutter_install_apk;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.PluginRegistry;

import static android.content.Context.DOWNLOAD_SERVICE;

public class FlutterInstallApkPlugin implements MethodCallHandler {

    private static final String CHANNEL = "io.flutter.plugins/flutter_install_apk";
    private final Context context;
    private String apkPath, appId;
    private DownloadManager downloadManager;
    long downloadId;
    boolean isRun = false;
    private DownloadManager.Query downloadQuery;
    private final MethodChannel channel;
    private final DecimalFormat df = new DecimalFormat("0.00");
    private final Handler handler;

    private FlutterInstallApkPlugin(PluginRegistry.Registrar registrar, MethodChannel channel) {
        this.context = registrar.context();
        this.channel = channel;
        handler = new Handler(Looper.getMainLooper());
    }


    public static void registerWith(PluginRegistry.Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), CHANNEL);
        channel.setMethodCallHandler(new FlutterInstallApkPlugin(registrar, channel));
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {

        String methodName = call.method;
        appId = call.argument("appId");
        if (methodName.equals("download_apk")) {
            downloadApk(call.argument("apkUrl").toString());
            result.success("Success");
        } else {
            result.notImplemented();
        }
    }


    @SuppressLint("SimpleDateFormat")
    void downloadApk(String apkUrl) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        String apkName = "/apk/bobo" + format.format(new Date()) + ".apk";
        //设置下载的路径
        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), apkName);
        apkPath = file.getAbsolutePath();
        if (file.exists()) {
            installApk();
            return;
        } else {
            deleteUselessApk(file.getParent());
        }
        isRun = true;
        downloadManager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle("下载");
        request.setDescription("apk正在下载");
        request.setDestinationUri(Uri.fromFile(file));
        downloadId = downloadManager.enqueue(request);
        downloadQuery = new DownloadManager.Query();
        downloadQuery.setFilterById(downloadId);
        context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        getDownloadProgress();
    }

    // 查询下载进度，文件总大小多少，已经下载多少？
    @SuppressLint("DefaultLocale")
    private void query() {
        Cursor cursor = downloadManager.query(downloadQuery);
        if (cursor != null && cursor.moveToFirst()) {
            int totalSizeBytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
            int bytesDownloadSoFarIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
            // 下载的文件总大小
            int totalSizeBytes = cursor.getInt(totalSizeBytesIndex);
            // 截止目前已经下载的文件总大小
            int bytesDownloadSoFar = cursor.getInt(bytesDownloadSoFarIndex);
            final String progress = df.format((double) bytesDownloadSoFar / totalSizeBytes);
            if (totalSizeBytes <= 0 || progress.equals("0.00")) {
                cursor.close();
                return;
            }
            try {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isRun) {
                            channel.invokeMethod("progressListener", progress);
                        }
                    }
                });
            } catch (Exception e) {
                System.out.println("-------Exception------->" + e.getMessage());
            }
            if (totalSizeBytes > 0 && totalSizeBytes == bytesDownloadSoFar) {
                isRun = false;
            }
            cursor.close();
        }
    }

    /**
     * 安装apk方法
     */
    private void installApk() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        File file = new File(apkPath);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //参数1 上下文, 参数2 Provider主机地址 和配置文件中保持一致   参数3  共享的文件
            Uri apkUri = FileProvider.getUriForFile(context, appId + ".fileProvider.install", file);
            //添加这一句表示对目标应用临时授权该Uri所代表的文件
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        } else {
            Uri uri = Uri.fromFile(file);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
        }
        context.startActivity(intent);
    }


    private void getDownloadProgress() {
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (isRun) {
                    query();
                }
            }
        }, 100, 1000, TimeUnit.MILLISECONDS);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!TextUtils.equals(action, DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
                return;
            }
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (downloadId == -1) {
                return;
            }
            DownloadManager downloadManager = (DownloadManager) context.getApplicationContext().getSystemService(Context.DOWNLOAD_SERVICE);
            int status = getDownloadStatus(downloadManager, downloadId);
            if (status != DownloadManager.STATUS_SUCCESSFUL) { //下载状态不等于成功就跳出
                return;
            }
            installApk();
        }

        /**
         * 获取下载状态
         *
         * @param downloadManager
         * @param downloadId
         * @return
         */
        private int getDownloadStatus(DownloadManager downloadManager, long downloadId) {
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
            Cursor c = downloadManager.query(query);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        return c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    }
                } finally {
                    c.close();
                }
            }
            return -1;
        }
    };

    //删除无用安装包
    private void deleteUselessApk(String dir) {
        try {
            // 如果dir不以文件分隔符结尾，自动添加文件分隔符
            if (!dir.endsWith(File.separator)) {
                dir = dir + File.separator;
            }
            File dirFile = new File(dir);
            // 如果dir对应的文件不存在，或者不是一个目录，则退出
            // 删除文件夹中的所有文件包括子目录
            File[] files = dirFile.listFiles();
            if (files.length == 0) {
                return;
            }
            for (File file : files) {
                // 删除子文件
                if (file.isFile()) {
                    file.delete();
                }
            }
        } catch (Exception e) {
            System.out.println("-------deleteUselessApk---error----->" + e.getMessage());
        }

    }
}
