package com.demo.aiyang.demo;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private TextView down;
    private TextView progress;
    private ProgressBar pb_update;
    private DownloadManager downloadManager;
    private DownloadManager.Request request;
    public static String downloadUrl = "http://www.wanandroid.com/blogimgs/ecb4c318-42f3-454a-a6c4-615ad16f35bd.apk";

    private DownloadReceiver completeReceiver;
    private final Uri CONTENT_URI = Uri.parse("content://downloads/my_downloads");
    private DownloadChangeObserver observer;
    long id;
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Bundle bundle = msg.getData();
            int pro = bundle.getInt("pro");
            pb_update.setProgress(pro);
            progress.setText(String.valueOf(pro) + "%");
        }
    };


    class DownloadChangeObserver extends ContentObserver {

        public DownloadChangeObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            updateView();
        }
    }

    class DownloadReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            Log.i("aaa", "广播监听");
            //  安装APK
            long completeDownLoadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            Intent intentInstall = new Intent();
            Uri uri = null;
            if (completeDownLoadId == id) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { // 兼容6.0以下
                    Log.i("aaa", "<6.0");
                    uri = downloadManager.getUriForDownloadedFile(completeDownLoadId);
                    installPackge(context,intentInstall,uri);
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { // 兼容6.0-70
                    Log.i("aaa", "6.0 - 7.0");
                    File apkFile = queryDownloadedApk(context, completeDownLoadId);
                    uri = Uri.fromFile(apkFile);
                    installPackge(context,intentInstall,uri);
                } else {
                    Log.i("aaa", ">7.0");
                    InstallPackgeAPI28(context);// 兼容Android 8.0
                }
            }
        }
    }


    /**
     *  兼容 8.0 未知来源应用安装
     */
    int Code_INSTALLPACKAGES = 1;
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startInstallPermissionSettingActivity() {
        Uri packageURI = Uri.parse("package:" + getPackageName());
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageURI);
        startActivityForResult(intent, Code_INSTALLPACKAGES);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Code_INSTALLPACKAGES){
            InstallPackgeAPI28(this);
        }
    }
    private void InstallPackgeAPI28(Context context){
        Intent intentInstall = new Intent();
        intentInstall.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // 给目标应用一个临时授权
        File file= new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"app-release.apk");
        Uri uri = FileProvider.getUriForFile(context, getPackageName() + ".fileProvider", file);

        boolean isInstallPermission = false;//是否有8.0安装权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            isInstallPermission = getPackageManager().canRequestPackageInstalls();
            if (isInstallPermission) {
                installPackge(this,intentInstall,uri);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("权限申请")
                        .setMessage("亲，没有权限我会崩溃，请把权限赐予我吧！")
                        .setPositiveButton("赏给你", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    startInstallPermissionSettingActivity();
                                }
                            }
                        }).setNegativeButton("取消",null ).show();
            }
        }else{
            installPackge(this,intentInstall,uri);
        }
    }


    /**
     * 安装APK
     * @param context
     * @param intentInstall
     * @param uri
     */
    private void installPackge(Context context,Intent intentInstall,Uri uri){
        intentInstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intentInstall.setAction(Intent.ACTION_VIEW);
        // 安装应用
        Log.i("aaa", "app下载完成了，开始安装。。。"+uri);
        intentInstall.setDataAndType(uri, "application/vnd.android.package-archive");
        context.startActivity(intentInstall);
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        down = (TextView) findViewById(R.id.down);
        progress = (TextView) findViewById(R.id.progress);
        pb_update = (ProgressBar) findViewById(R.id.pb_update);
        down.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LoadApp();
                requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,Code_PERMISSION);
            }
        });
    }

    private void LoadApp() {
        //创建下载对象
        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setTitle("app-release.apk");
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
        request.setAllowedOverRoaming(false);
        request.setMimeType("application/vnd.android.package-archive");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        //设置文件存放路径
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "app-release.apk");
    }

    private void updateView() {
        int[] bytesAndStatus = new int[]{0, 0, 0};
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        Cursor c = null;
        try {
            c = downloadManager.query(query);
            if (c != null && c.moveToFirst()) {
                //已经下载的字节数
                bytesAndStatus[0] = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                //总需下载的字节数
                bytesAndStatus[1] = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        int pro = (bytesAndStatus[0] * 100) / bytesAndStatus[1];
        Message msg = Message.obtain();
        Bundle bundle = new Bundle();
        bundle.putInt("pro", pro);
        msg.setData(bundle);
        handler.sendMessage(msg);
        Log.i("aaa", "下载进度：" + bytesAndStatus[0] + "/" + bytesAndStatus[1]);
    }

    //通过downLoadId查询下载的apk，解决6.0以后安装的问题
    public static File queryDownloadedApk(Context context, long downloadId) {
        File targetApkFile = null;
        DownloadManager downloader = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        if (downloadId != -1) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            query.setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL);
            Cursor cur = downloader.query(query);
            if (cur != null) {
                if (cur.moveToFirst()) {
                    String uriString = cur.getString(cur.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    if (!TextUtils.isEmpty(uriString)) {
                        targetApkFile = new File(Uri.parse(uriString).getPath());
                    }
                }
                cur.close();
            }
        }
        return targetApkFile;
    }

    //开始下载
    private void onDownBegin() {
        try {
            id = downloadManager.enqueue(request);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //更新UI
            observer = new DownloadChangeObserver(handler);
            getContentResolver().registerContentObserver(CONTENT_URI, true, observer);
            //安装
            completeReceiver = new DownloadReceiver();
            registerReceiver(completeReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            down.setText("正在下载");
            down.setClickable(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (observer != null) {
            getContentResolver().unregisterContentObserver(observer);
            unregisterReceiver(completeReceiver);
        }

    }

    /**
     * 请求权限
     */
    int Code_PERMISSION = 0;
    /**
     * 权限申请
     * @param ManifestPermission
     * @param CODE
     * @return
     */
    private boolean requestPermission(final String ManifestPermission, final int CODE) {
        //1. 检查是否已经有该权限
        if (ContextCompat.checkSelfPermission(this,ManifestPermission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,ManifestPermission)) {
                new AlertDialog.Builder(this)
                        .setTitle("权限申请")
                        .setMessage("亲，没有权限我会崩溃，请把权限赐予我吧！")
                        .setPositiveButton("赏给你", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                                // 用户同意 ，再次申请
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{ManifestPermission}, CODE);
                            }
                        })
                        .setNegativeButton("就不给", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                                // 用户拒绝 ，如果APP必须有权限否则崩溃，那就继续重复询问弹框~~
                            }
                        }).show();
            } else {
                //2. 权限没有开启，请求权限
                ActivityCompat.requestPermissions(this,
                        new String[]{ManifestPermission}, CODE);
            }

        } else {
            //3. 权限已开，处理逻辑
            return true;
        }
        return false;
    }

    //4. 接收申请成功或者失败回调
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Code_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //权限被用户同意,做相应的事情
                onDownBegin();
            } else {
                //权限被用户拒绝，做相应的事情
                Toast.makeText(this,"拒绝了权限",Toast.LENGTH_SHORT);
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

}
