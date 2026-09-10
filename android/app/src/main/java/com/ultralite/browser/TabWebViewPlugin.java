package com.ultralite.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

@CapacitorPlugin(name = "TabWebView")
public class TabWebViewPlugin extends Plugin {

    private final Map<String, WebView> webViews = new HashMap<>();
    private String activeTabId = null;
    private ImageButton fab = null;
    private ProgressBar progressBar = null;
    private ViewGroup root = null;
    private ViewGroup switcherRoot = null;
    private LinearLayout switcherList = null;
    private TextView switcherTitle = null;
    private int navBottomInset = 0;

    // Los PluginMethod de Capacitor corren en un hilo de trabajo; toda la UI debe ir al main thread.
    private void runOnMain(Runnable r) {
        getActivity().runOnUiThread(r);
    }

    private int navInset() {
        if (Build.VERSION.SDK_INT >= 30 && root != null) {
            WindowInsets insets = root.getRootWindowInsets();
            if (insets != null) return insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
        }
        return 0;
    }

    private void ensureUi() {
        if (fab != null) return;
        Activity activity = getActivity();
        root = (ViewGroup) activity.findViewById(android.R.id.content);
        if (root == null) return;
        navBottomInset = navInset();

        // FAB idéntico al .fab-tabs de React
        fab = new ImageButton(activity);
        fab.setImageResource(R.drawable.ic_tabs);
        fab.setScaleType(ImageButton.ScaleType.CENTER);
        fab.setPadding(0, 0, 0, 0);
        fab.setContentDescription("Cambiar de pestaña");
        fab.setOnClickListener(v -> notifyListeners("onFabTap", new JSObject()));

        int fabSize = dp(44);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(fabSize, fabSize);
        flp.gravity = Gravity.BOTTOM | Gravity.END;
        flp.setMargins(0, 0, dp(14), navBottomInset + dp(14));
        fab.setLayoutParams(flp);
        fab.setElevation(dp(6));

        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setShape(GradientDrawable.OVAL);
        fabBg.setColor(0xFF212533);
        fabBg.setStroke(dp(1), 0xFF2E3345);
        fab.setBackground(fabBg);
        fab.setVisibility(View.GONE);

        progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(3));
        plp.gravity = Gravity.TOP;
        progressBar.setLayoutParams(plp);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setProgressTintList(ColorStateList.valueOf(0xFF8B5CF6));
        progressBar.setVisibility(View.GONE);

        buildSwitcherUi(activity);

        root.addView(progressBar);
        root.addView(fab);
        root.addView(switcherRoot);

        getActivity().getOnBackPressedDispatcher().addCallback(getActivity(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (switcherRoot != null && switcherRoot.getVisibility() == View.VISIBLE) {
                            hideSwitcherUi();
                            return;
                        }
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

    private void buildSwitcherUi(Activity activity) {
        switcherRoot = new FrameLayout(activity);
        switcherRoot.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        switcherRoot.setBackgroundColor(0x73000000);
        switcherRoot.setVisibility(View.GONE);
        switcherRoot.setOnClickListener(v -> hideSwitcherUi());

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        sheet.setLayoutParams(slp);
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setShape(GradientDrawable.RECTANGLE);
        sheetBg.setColor(0xFF181B24);
        sheetBg.setStroke(dp(1), 0xFF2E3345);
        sheetBg.setCornerRadii(new float[]{dp(20), dp(20), dp(20), dp(20), 0, 0, 0, 0});
        sheet.setBackground(sheetBg);
        sheet.setPadding(dp(14), dp(12), dp(14), Math.max(dp(14), navBottomInset + dp(14)));

        // Header: "Pestañas (n)" + cerrar
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        switcherTitle = new TextView(activity);
        switcherTitle.setText("Pestañas");
        switcherTitle.setTextColor(0xFFF1F5F9);
        switcherTitle.setTextSize(16);
        switcherTitle.setTypeface(switcherTitle.getTypeface(), android.graphics.Typeface.BOLD);
        switcherTitle.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(switcherTitle);

        ImageButton closeBtn = new ImageButton(activity);
        closeBtn.setImageResource(R.drawable.ic_close);
        closeBtn.setImageTintList(ColorStateList.valueOf(0xFF94A3B8));
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setPadding(0, 0, 0, 0);
        closeBtn.setContentDescription("Cerrar");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(40), dp(40));
        closeBtn.setLayoutParams(clp);
        closeBtn.setOnClickListener(v -> hideSwitcherUi());
        header.addView(closeBtn);
        sheet.addView(header);

        // Lista de pestañas (scrollable)
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        int listMax = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.52f);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listMax));
        switcherList = new LinearLayout(activity);
        switcherList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(switcherList);
        sheet.addView(scroll);

        // "Nueva pestaña"
        TextView newBtn = new TextView(activity);
        newBtn.setText("+  Nueva pestaña");
        newBtn.setTextColor(0xFFA78BFA);
        newBtn.setTextSize(14);
        newBtn.setGravity(Gravity.CENTER);
        newBtn.setPadding(0, dp(12), 0, dp(12));
        GradientDrawable nbg = new GradientDrawable();
        nbg.setShape(GradientDrawable.RECTANGLE);
        nbg.setColor(Color.TRANSPARENT);
        nbg.setStroke(dp(1), 0xFF2E3345);
        nbg.setCornerRadius(dp(12));
        newBtn.setBackground(nbg);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(10);
        newBtn.setLayoutParams(nlp);
        newBtn.setOnClickListener(v -> {
            hideSwitcherUi();
            notifyListeners("onSwitcherNew", new JSObject());
        });
        sheet.addView(newBtn);

        switcherRoot.addView(sheet);
    }

    private void buildSwitcherRows(JSArray tabsArray) {
        switcherList.removeAllViews();
        int n = tabsArray == null ? 0 : tabsArray.length();
        switcherTitle.setText("Pestañas (" + n + ")");
        if (tabsArray == null) return;
        Activity activity = getActivity();
        java.util.List<Object> tabs;
        try {
            tabs = tabsArray.toList();
        } catch (JSONException e) {
            return;
        }
        for (Object o : tabs) {
            if (!(o instanceof JSONObject)) continue;
            JSONObject t = (JSONObject) o;
            String id = t.optString("id");
            String title = t.optString("title", "Pestaña");
            boolean active = t.optBoolean("active");
            boolean isNew = t.optBoolean("isNew");
            buildListRow(activity, id, title, active, isNew);
        }
    }

    private void buildListRow(Activity activity, String id, String title,
                              boolean active, boolean isNew) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rlp);

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setShape(GradientDrawable.RECTANGLE);
        rowBg.setColor(0xFF212533);
        rowBg.setCornerRadius(dp(12));
        if (active) rowBg.setStroke(dp(1), 0xFF8B5CF6);
        row.setBackground(rowBg);

        View dot = new View(activity);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dlp.setMargins(0, 0, dp(10), 0);
        dot.setLayoutParams(dlp);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(active || isNew ? 0xFF8B5CF6 : 0xFF475569);
        dot.setBackground(dotBg);
        row.addView(dot);

        TextView label = new TextView(activity);
        label.setText(title);
        label.setTextColor(active ? 0xFFF1F5F9 : 0xFFD8DFEA);
        label.setTextSize(14);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(llp);
        row.addView(label);

        ImageButton close = new ImageButton(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setImageTintList(ColorStateList.valueOf(0xFF94A3B8));
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setPadding(0, 0, 0, 0);
        close.setContentDescription("Cerrar pestaña");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(28), dp(28));
        close.setLayoutParams(clp);

        row.setOnClickListener(v -> {
            hideSwitcherUi();
            JSObject d = new JSObject();
            d.put("tabId", id);
            notifyListeners("onSwitcherSelect", d);
        });
        close.setOnClickListener(v -> {
            JSObject d = new JSObject();
            d.put("tabId", id);
            notifyListeners("onSwitcherCloseTab", d);
        });
        row.addView(close);
        switcherList.addView(row);
    }

    private void hideSwitcherUi() {
        if (switcherRoot != null) switcherRoot.setVisibility(View.GONE);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView buildWebView(String id, String url) {
        Activity activity = getActivity();
        ensureUi();
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
        if (fab != null) root.bringChildToFront(fab);
        if (progressBar != null) root.bringChildToFront(progressBar);
        if (url != null) wv.loadUrl(url);
        return wv;
    }

    private void emit(WebView view, String url, String title, int progress) {
        String tabId = (String) view.getTag();
        if (tabId == null) return;
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
        if (progress > 0 && tabId.equals(activeTabId) && progressBar != null) {
            if (progress >= 100) {
                progressBar.setVisibility(View.GONE);
            } else {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(progress);
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
        String id = call.getString("id");
        String url = call.getString("url");
        runOnMain(() -> {
            ensureUi();
            if (id == null || webViews.containsKey(id)) {
                call.resolve();
                return;
            }
            webViews.put(id, buildWebView(id, url));
            call.resolve();
        });
    }

    @PluginMethod
    public void showTab(PluginCall call) {
        String id = call.getString("id");
        runOnMain(() -> {
            ensureUi();
            WebView wv = webViews.get(id);
            if (wv == null) {
                call.reject("tab no existe: " + id);
                return;
            }
            hideAllTabsUi();
            wv.setVisibility(View.VISIBLE);
            wv.requestFocus();
            if (fab != null) fab.bringToFront();
            if (progressBar != null) progressBar.bringToFront();
            fab.setVisibility(View.VISIBLE);
            activeTabId = id;
            JSObject data = new JSObject();
            data.put("tabId", id);
            notifyListeners("onTabShown", data);
            call.resolve();
        });
    }

    @PluginMethod
    public void hideTabs(PluginCall call) {
        runOnMain(() -> {
            ensureUi();
            hideAllTabsUi();
            activeTabId = null;
            notifyListeners("onTabsHidden", new JSObject());
            call.resolve();
        });
    }

    @PluginMethod
    public void loadTab(PluginCall call) {
        String id = call.getString("id");
        String url = call.getString("url");
        runOnMain(() -> {
            ensureUi();
            WebView wv = webViews.get(id);
            if (wv == null) {
                wv = buildWebView(id, url);
                webViews.put(id, wv);
            } else if (url != null) {
                wv.loadUrl(url);
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void closeTab(PluginCall call) {
        String id = call.getString("id");
        runOnMain(() -> {
            WebView wv = webViews.remove(id);
            if (wv != null) {
                ViewGroup parent = (ViewGroup) wv.getParent();
                if (parent != null) parent.removeView(wv);
                wv.destroy();
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void showSwitcher(PluginCall call) {
        runOnMain(() -> {
            ensureUi();
            JSArray tabs = call.getArray("tabs");
            buildSwitcherRows(tabs);
            switcherRoot.setVisibility(View.VISIBLE);
            if (root != null) root.bringChildToFront(switcherRoot);
            call.resolve();
        });
    }

    @PluginMethod
    public void hideSwitcher(PluginCall call) {
        runOnMain(() -> {
            hideSwitcherUi();
            call.resolve();
        });
    }
}