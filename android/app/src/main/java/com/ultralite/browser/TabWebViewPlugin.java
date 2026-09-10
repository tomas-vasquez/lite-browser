package com.ultralite.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.HashMap;
import java.util.Map;

@CapacitorPlugin(name = "TabWebView")
public class TabWebViewPlugin extends Plugin {

    private final Map<String, WebView> webViews = new HashMap<>();
    private String activeTabId = null;
    private ImageButton fab = null;
    private ProgressBar progressBar = null;
    private ViewGroup root = null;

    private void ensureUi() {
        if (fab != null) return;
        Activity activity = getActivity();
        root = (ViewGroup) activity.findViewById(android.R.id.content);
        if (root == null) return;

        fab = new ImageButton(activity);
        fab.setImageResource(R.drawable.ic_tabs);
        fab.setScaleType(ImageButton.ScaleType.CENTER_CROP);
        fab.setPadding(0, 0, 0, 0);
        fab.setContentDescription("Cambiar de pestaña");
        fab.setOnClickListener(v -> {
            hideAllTabsUi();
            notifyListeners("onFabTap", new JSObject());
        });

        int fabSize = dp(54);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(fabSize, fabSize);
        flp.gravity = Gravity.BOTTOM | Gravity.END;
        flp.setMargins(0, 0, dp(16), dp(18));
        fab.setLayoutParams(flp);

        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setShape(GradientDrawable.OVAL);
        fabBg.setColor(0xFF8B5CF6);
        fab.setBackground(fabBg);
        fab.setVisibility(View.GONE);

        progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(3));
        plp.gravity = Gravity.TOP;
        progressBar.setLayoutParams(plp);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF8B5CF6));
        progressBar.setVisibility(View.GONE);

        root.addView(progressBar);
        root.addView(fab);

        getActivity().getOnBackPressedDispatcher().addCallback(getActivity(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        WebView wv = activeTabId != null ? webViews.get(activeTabId) : null;
                        if (wv == null || wv.getVisibility() != View.VISIBLE) {
                            setEnabled(false);
                            getActivity().getOnBackPressedDispatcher().onBackPressed();
                            setEnabled(true);
                            return;
                        }
                        if (wv.canGoBack()) {
                            wv.goBack();
                        } else {
                            String tabId = activeTabId;
                            hideAllTabsUi();
                            activeTabId = null;
                            JSObject data = new JSObject();
                            data.put("tabId", tabId);
                            notifyListeners("onTabBackHome", data);
                        }
                    }
                });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView buildWebView(String id, String url) {
        Activity activity = getActivity();
        WebView wv = new WebView(activity);
        wv.setId(View.generateViewId());
        wv.setTag(id);
        wv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        CookieManager.getInstance().setAcceptCookie(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
        }

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                emit(view, url, view.getTitle(), 10);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                emit(view, url, view.getTitle(), 100);
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                emit(view, view.getUrl(), view.getTitle(), newProgress);
            }
        });

        wv.setVisibility(View.GONE);
        root.addView(wv);
        if (url != null) wv.loadUrl(url);
        return wv;
    }

    private void emit(WebView view, String url, String title, int progress) {
        String tabId = (String) view.getTag();
        if (tabId != null) {
            if (url != null) {
                JSObject u = new JSObject();
                u.put("tabId", tabId);
                u.put("url", url);
                notifyListeners("onUrlChange", u);
            }
            if (title != null) {
                JSObject t = new JSObject();
                t.put("tabId", tabId);
                t.put("title", title);
                notifyListeners("onTitleChange", t);
            }
            if (progress > 0 && tabId.equals(activeTabId)) {
                if (progress >= 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(progress);
                }
            }
        }
    }

    private void hideAllTabsUi() {
        for (WebView wv : webViews.values()) wv.setVisibility(View.GONE);
        if (fab != null) fab.setVisibility(View.GONE);
        if (progressBar != null) progressBar.setVisibility(View.GONE);
    }

    private int dp(int value) {
        return (int) (value * getActivity().getResources().getDisplayMetrics().density + 0.5f);
    }

    @PluginMethod
    public void createTab(PluginCall call) {
        ensureUi();
        String id = call.getString("id");
        String url = call.getString("url");
        if (id == null || webViews.containsKey(id)) {
            call.resolve();
            return;
        }
        webViews.put(id, buildWebView(id, url));
        call.resolve();
    }

    @PluginMethod
    public void showTab(PluginCall call) {
        ensureUi();
        String id = call.getString("id");
        WebView wv = webViews.get(id);
        if (wv == null) {
            call.reject("tab no existe: " + id);
            return;
        }
        hideAllTabsUi();
        wv.setVisibility(View.VISIBLE);
        wv.requestFocus();
        fab.setVisibility(View.VISIBLE);
        activeTabId = id;
        JSObject data = new JSObject();
        data.put("tabId", id);
        notifyListeners("onTabShown", data);
        call.resolve();
    }

    @PluginMethod
    public void hideTabs(PluginCall call) {
        ensureUi();
        hideAllTabsUi();
        activeTabId = null;
        notifyListeners("onTabsHidden", new JSObject());
        call.resolve();
    }

    @PluginMethod
    public void loadTab(PluginCall call) {
        ensureUi();
        String id = call.getString("id");
        String url = call.getString("url");
        WebView wv = webViews.get(id);
        if (wv == null) {
            wv = buildWebView(id, url);
            webViews.put(id, wv);
        } else if (url != null) {
            wv.loadUrl(url);
        }
        call.resolve();
    }

    @PluginMethod
    public void closeTab(PluginCall call) {
        String id = call.getString("id");
        WebView wv = webViews.remove(id);
        if (wv != null) {
            ViewGroup parent = (ViewGroup) wv.getParent();
            if (parent != null) parent.removeView(wv);
            wv.destroy();
        }
        call.resolve();
    }
}