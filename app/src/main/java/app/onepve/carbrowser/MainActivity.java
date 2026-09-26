package app.onepve.carbrowser;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_CODE = 1001;
    private static final String DEFAULT_HOME_URL = "file:///android_asset/homepage.html";

    private WebView webView;
    private ProgressBar progressBar;
    private EditText editSearch;
    private ImageButton btnBack, btnForward, btnRefresh, btnHome, btnClose;
    private LinearLayout btnBookmarks;
    private FrameLayout fullscreenContainer;
    private View topBar;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private BookmarkManager bookmarkManager;
    private String pendingDownloadUrl;
    private String pendingUserAgent;
    private String pendingContentDisposition;
    private String pendingMimeType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bookmarkManager = new BookmarkManager(this);
        initViews();
        setupWebView();
        setupListeners();

        // 默认加载必应
        webView.loadUrl(DEFAULT_HOME_URL);
    }

    private void initViews() {
        topBar = findViewById(R.id.top_bar);
        webView = findViewById(R.id.web_view);
        progressBar = findViewById(R.id.progress_bar);
        editSearch = findViewById(R.id.edit_search);
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnHome = findViewById(R.id.btn_home);
        btnClose = findViewById(R.id.btn_close);
        btnBookmarks = findViewById(R.id.btn_bookmarks);
        fullscreenContainer = findViewById(R.id.fullscreen_container);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false;
                }
                return true; // 拦截并忽略未知 schema (如 alipay, weixin 协议防报错)
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                if (url.startsWith("file:///android_asset/")) {
                    editSearch.setText("");
                    editSearch.setHint(R.string.search_hint);
                } else {
                    editSearch.setText(url);
                }
                updateNavButtons();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                updateNavButtons();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                // 当未聚焦输入框时，可在标题与URL间切换
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                // 全屏视频模式
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                topBar.setVisibility(View.GONE);
                webView.setVisibility(View.GONE);
                fullscreenContainer.addView(view);
                fullscreenContainer.setVisibility(View.VISIBLE);
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });

        // 核心要求：下载拦截并存储至 /storage/emulated/0/Download
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            checkAndDownload(url, userAgent, contentDisposition, mimeType);
        });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                Toast.makeText(this, "已经是第一页了", Toast.LENGTH_SHORT).show();
            }
        });

        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) {
                webView.goForward();
            }
        });

        btnRefresh.setOnClickListener(v -> webView.reload());

        btnHome.setOnClickListener(v -> webView.loadUrl(DEFAULT_HOME_URL));

        btnBookmarks.setOnClickListener(v -> showBookmarksDialog());

        btnClose.setOnClickListener(v -> finish());

        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                hideKeyboard();
                performSearch(editSearch.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    private void updateNavButtons() {
        btnBack.setAlpha(webView.canGoBack() ? 1.0f : 0.4f);
        btnForward.setAlpha(webView.canGoForward() ? 1.0f : 0.4f);
    }

    private void performSearch(String text) {
        if (text.isEmpty()) return;
        if (text.startsWith("http://") || text.startsWith("https://")) {
            webView.loadUrl(text);
        } else if (text.contains(".") && !text.contains(" ")) {
            webView.loadUrl("https://" + text);
        } else {
            webView.loadUrl("https://www.baidu.com/s?wd=" + text);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void showBookmarksDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_bookmarks);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.85),
                             (int) (getResources().getDisplayMetrics().heightPixels * 0.85));
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        RecyclerView rv = dialog.findViewById(R.id.recycler_bookmarks);
        rv.setLayoutManager(new GridLayoutManager(this, 3));

        final String[] currentCategory = {"all"};
        BookmarkAdapter adapter = new BookmarkAdapter(bookmarkManager.getAllBookmarks("all"), new BookmarkAdapter.OnBookmarkClickListener() {
            @Override
            public void onBookmarkClick(BookmarkItem item) {
                dialog.dismiss();
                webView.loadUrl(item.url);
            }

            @Override
            public void onBookmarkLongClick(BookmarkItem item) {
                if (!item.isPreset) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("删除收藏")
                        .setMessage("确定从常用收藏中删除「" + item.title + "」？")
                        .setPositiveButton("删除", (d, which) -> {
                            bookmarkManager.removeCustomBookmark(item.id);
                            rv.setAdapter(new BookmarkAdapter(bookmarkManager.getAllBookmarks(currentCategory[0]), this));
                        })
                        .setNegativeButton("取消", null)
                        .show();
                } else {
                    Toast.makeText(MainActivity.this, "预置常用网站不支持删除", Toast.LENGTH_SHORT).show();
                }
            }
        });
        rv.setAdapter(adapter);

        // 分类标签构建
        LinearLayout layoutCats = dialog.findViewById(R.id.layout_categories);
        String[][] categories = {
            {"all", "全部"},
            {"video", "影音娱乐"},
            {"audio", "听书阅读"},
            {"search", "搜索与AI"},
            {"car", "汽车资讯"}
        };

        for (String[] cat : categories) {
            Button btn = new Button(this);
            btn.setText(cat[1]);
            btn.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            btn.setBackgroundResource(R.drawable.bg_capsule_btn);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );
            lp.setMargins(0, 0, 16, 0);
            btn.setLayoutParams(lp);
            btn.setPadding(24, 0, 24, 0);
            btn.setOnClickListener(v -> {
                currentCategory[0] = cat[0];
                adapter.setData(bookmarkManager.getAllBookmarks(cat[0]));
            });
            layoutCats.addView(btn);
        }

        // 收藏当前网页
        dialog.findViewById(R.id.btn_add_current).setOnClickListener(v -> {
            String curTitle = webView.getTitle();
            String curUrl = webView.getUrl();
            if (curUrl != null && !curUrl.isEmpty()) {
                if (curTitle == null || curTitle.isEmpty()) curTitle = "网页收藏";
                bookmarkManager.addCustomBookmark(curTitle, curUrl);
                Toast.makeText(MainActivity.this, "已成功添加至收藏夹: " + curTitle, Toast.LENGTH_SHORT).show();
                adapter.setData(bookmarkManager.getAllBookmarks(currentCategory[0]));
            }
        });

        dialog.findViewById(R.id.btn_close_dialog).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void checkAndDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownloadUrl = url;
            pendingUserAgent = userAgent;
            pendingContentDisposition = contentDisposition;
            pendingMimeType = mimeType;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_CODE);
        } else {
            DownloadHelper.startDownload(this, url, userAgent, contentDisposition, mimeType);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingDownloadUrl != null) {
                DownloadHelper.startDownload(this, pendingDownloadUrl, pendingUserAgent, pendingContentDisposition, pendingMimeType);
                pendingDownloadUrl = null;
            }
        } else {
            Toast.makeText(this, "需存储权限以保存文件到 Download 目录", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideCustomView() {
        if (customView == null) return;
        fullscreenContainer.removeView(customView);
        fullscreenContainer.setVisibility(View.GONE);
        topBar.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
        }
        customView = null;
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            webView.clearHistory();
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
