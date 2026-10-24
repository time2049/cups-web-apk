package com.cupsweb.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;
    private static final int MENU_CHANGE = 1;
    private static final int MENU_REFRESH = 2;

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private SharedPreferences sp;
    private boolean errorDialogShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("cfg", Context.MODE_PRIVATE);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !errorDialogShowing) {
                    errorDialogShowing = true;
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("无法连接服务器")
                            .setMessage(error.getDescription() + "\n\n请检查地址和网络。")
                            .setPositiveButton("修改地址", (d, w) -> {
                                errorDialogShowing = false;
                                askUrl(true);
                            })
                            .setNegativeButton("重试", (d, w) -> {
                                errorDialogShowing = false;
                                web.reload();
                            })
                            .setOnCancelListener(d -> errorDialogShowing = false)
                            .show();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("证书不受信任")
                        .setMessage("该服务器的 HTTPS 证书无法验证（可能是自签名证书）。是否仍然继续？")
                        .setPositiveButton("继续", (d, w) -> handler.proceed())
                        .setNegativeButton("取消", (d, w) -> handler.cancel())
                        .setCancelable(false)
                        .show();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) r.addRequestHeader("Cookie", cookie);
                r.addRequestHeader("User-Agent", userAgent);
                r.setMimeType(mimeType);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimeType));
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(this, "开始下载，完成后见通知栏", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "下载失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        String url = sp.getString("url", "");
        if (url.isEmpty()) {
            askUrl(false);
        } else {
            web.loadUrl(url);
        }
    }

    private void askUrl(boolean cancelable) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        String cur = sp.getString("url", "");
        input.setText(cur.isEmpty() ? "http://" : cur);
        input.setSelection(input.getText().length());

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("输入 CUPS Web 服务器地址")
                .setMessage("例如 http://192.168.1.10:8080")
                .setView(input)
                .setCancelable(cancelable)
                .setPositiveButton("确定", (d, w) -> {
                    String u = input.getText().toString().trim();
                    if (u.isEmpty()) {
                        askUrl(cancelable);
                        return;
                    }
                    if (!u.startsWith("http://") && !u.startsWith("https://")) {
                        u = "http://" + u;
                    }
                    sp.edit().putString("url", u).apply();
                    web.loadUrl(u);
                });
        if (cancelable) {
            b.setNegativeButton("取消", null);
        }
        b.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_REFRESH, 0, "刷新");
        menu.add(0, MENU_CHANGE, 1, "更换服务器地址");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_CHANGE) {
            askUrl(true);
            return true;
        } else if (item.getItemId() == MENU_REFRESH) {
            web.reload();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
