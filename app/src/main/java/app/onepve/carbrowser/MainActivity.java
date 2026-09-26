package app.onepve.carbrowser;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_CODE = 1001;
    private static final String PREF_NAME = "car_browser_prefs";
    private static final String PREF_KEY_DARK = "is_dark_mode";
    private static final String DEFAULT_HOME_URL = "file:///android_asset/homepage.html";

    private WebView webView;
    private ProgressBar progressBar;
    private EditText editSearch;
    private ImageButton btnBack, btnForward, btnRefresh, btnHome, btnClose, btnFullscreen, btnExitFullscreen, btnThemeMode;
    private LinearLayout btnBookmarks;
    private FrameLayout fullscreenContainer;
    private View topBar;

    private boolean isPureFullscreen = false;
    private boolean isDarkMode = true;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private BookmarkManager bookmarkManager;
    private SharedPreferences prefs;

    private String pendingDownloadUrl;
    private String pendingUserAgent;
    private String pendingContentDisposition;
    private String pendingMimeType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setImmersiveMode();
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        isDarkMode = prefs.getBoolean(PREF_KEY_DARK, true);
        bookmarkManager = new BookmarkManager(this);

        initViews();
        setupWebView();
        setupListeners();
        applyTheme(isDarkMode);

        // 默认加载本地秒开主页
        webView.loadUrl(DEFAULT_HOME_URL);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersiveMode();
        }
    }

    private void setImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
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
        btnFullscreen = findViewById(R.id.btn_fullscreen);
        btnExitFullscreen = findViewById(R.id.btn_exit_fullscreen);
        btnThemeMode = findViewById(R.id.btn_theme_mode);
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
                if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:///")) {
                    return false;
                }
                return true;
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
                // 确保主页主题状态同步
                if (url != null && url.startsWith("file:///android_asset/")) {
                    webView.evaluateJavascript("setTheme('" + (isDarkMode ? "dark" : "light") + "')", null);
                }
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
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                topBar.setVisibility(View.GONE);
                btnExitFullscreen.setVisibility(View.GONE);
                webView.setVisibility(View.GONE);
                fullscreenContainer.addView(view);
                fullscreenContainer.setVisibility(View.VISIBLE);
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            checkAndDownload(url, userAgent, contentDisposition, mimeType);
        });
    }

    private void hideCustomView() {
        if (customView == null) return;
        fullscreenContainer.removeView(customView);
        fullscreenContainer.setVisibility(View.GONE);
        if (!isPureFullscreen) {
            topBar.setVisibility(View.VISIBLE);
        } else {
            btnExitFullscreen.setVisibility(View.VISIBLE);
        }
        webView.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
        }
        customView = null;
        setImmersiveMode();
    }

    private void enterPureFullscreen() {
        isPureFullscreen = true;
        topBar.setVisibility(View.GONE);
        btnExitFullscreen.setVisibility(View.VISIBLE);
        setImmersiveMode();
        Toast.makeText(this, "已进入纯净全屏，点击右上角浮标恢复", Toast.LENGTH_SHORT).show();
    }

    private void exitPureFullscreen() {
        isPureFullscreen = false;
        topBar.setVisibility(View.VISIBLE);
        btnExitFullscreen.setVisibility(View.GONE);
        setImmersiveMode();
    }

    private void toggleThemeMode() {
        isDarkMode = !isDarkMode;
        prefs.edit().putBoolean(PREF_KEY_DARK, isDarkMode).apply();
        applyTheme(isDarkMode);
        Toast.makeText(this, isDarkMode ? "已切换至黑夜护眼模式" : "已切换至白天明亮模式", Toast.LENGTH_SHORT).show();
    }

    private void applyTheme(boolean darkMode) {
        if (darkMode) {
            topBar.setBackgroundColor(Color.parseColor("#18181c"));
            btnThemeMode.setImageResource(R.drawable.ic_sun);
            editSearch.setTextColor(Color.parseColor("#f3f4f6"));
            editSearch.setHintTextColor(Color.parseColor("#9ca3af"));
        } else {
            topBar.setBackgroundColor(Color.parseColor("#ffffff"));
            btnThemeMode.setImageResource(R.drawable.ic_moon);
            editSearch.setTextColor(Color.parseColor("#111827"));
            editSearch.setHintTextColor(Color.parseColor("#6b7280"));
        }

        // WebSettingsCompat 强制深色或普通
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(webView.getSettings(),
                darkMode ? WebSettingsCompat.FORCE_DARK_ON : WebSettingsCompat.FORCE_DARK_OFF);
        }

        // 通知本地主页 HTML 变更样式
        webView.evaluateJavascript("setTheme('" + (darkMode ? "dark" : "light") + "')", null);
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

        btnFullscreen.setOnClickListener(v -> enterPureFullscreen());

        btnExitFullscreen.setOnClickListener(v -> exitPureFullscreen());

        btnThemeMode.setOnClickListener(v -> toggleThemeMode());

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

        dialog.findViewById(R.id.btn_add_current).setOnClickListener(v -> {
            String curTitle = webView.getTitle();
            String curUrl = webView.getUrl();
            if (curUrl != null && !curUrl.isEmpty() && !curUrl.startsWith("file:///")) {
                if (curTitle == null || curTitle.isEmpty()) curTitle = "网页收藏";
                bookmarkManager.addCustomBookmark(curTitle, curUrl);
                Toast.makeText(MainActivity.this, "已成功添加至收藏夹: " + curTitle, Toast.LENGTH_SHORT).show();
                adapter.setData(bookmarkManager.getAllBookmarks(currentCategory[0]));
            } else {
                Toast.makeText(MainActivity.this, "主页无需重复收藏", Toast.LENGTH_SHORT).show();
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

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        if (isPureFullscreen) {
            exitPureFullscreen();
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
