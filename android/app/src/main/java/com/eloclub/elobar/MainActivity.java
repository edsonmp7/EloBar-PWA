package com.eloclub.elobar;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String START_URL = "https://script.google.com/macros/s/AKfycbyd7UHyQFJA4SsFZuKWmAO___NnfGXq0oNB0M0NWnG2hhLmPHcKTL_ck4yDgB4IqSkOnQ/exec?shell=android-native";
    private static final int FILE_CHOOSER_REQUEST = 1907;

    private FrameLayout root;
    private WebView webView;
    private View splashView;
    private ValueCallback<Uri[]> pendingFileChooser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            configureWindow();
            createRoot();
            createWebView();
            createSplash();
            configureWebView();

            if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
                webView.loadUrl(START_URL);
            } else {
                hideSplash(300L);
            }
        } catch (Throwable error) {
            showFatalError(error);
        }
    }

    private void createRoot() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(18, 18, 18));
        setContentView(root);
    }

    private void createWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(18, 18, 18));
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void createSplash() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(Color.rgb(18, 18, 18));
        panel.setPadding(dp(28), dp(28), dp(28), dp(28));

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.eloclub.elobar.R.drawable.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        panel.addView(logo, new LinearLayout.LayoutParams(dp(220), dp(220)));

        TextView text = new TextView(this);
        text.setText("Abrindo Elo Bar…");
        text.setTextColor(Color.rgb(210, 210, 210));
        text.setTextSize(14f);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.topMargin = dp(18);
        panel.addView(text, textParams);

        splashView = panel;
        root.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideSplash(long delayMs) {
        if (splashView == null || splashView.getVisibility() != View.VISIBLE) return;
        splashView.postDelayed(() -> {
            if (splashView == null) return;
            splashView.animate()
                    .alpha(0f)
                    .setDuration(180L)
                    .withEndAction(() -> {
                        if (splashView != null) splashView.setVisibility(View.GONE);
                    })
                    .start();
        }, delayMs);
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(18, 18, 18));
        window.setNavigationBarColor(Color.rgb(18, 18, 18));
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new EloBarWebViewClient());
        webView.setWebChromeClient(new EloBarWebChromeClient());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                openExternal(Uri.parse(url))
        );
    }

    private boolean isInternalUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || host == null) return false;

        return "script.google.com".equalsIgnoreCase(host)
                || "accounts.google.com".equalsIgnoreCase(host)
                || host.endsWith(".googleusercontent.com")
                || host.equalsIgnoreCase("googleusercontent.com");
    }

    private boolean isAppsScriptSurface(Uri uri) {
        if (uri == null || uri.getHost() == null) return false;
        String host = uri.getHost();
        return "script.google.com".equalsIgnoreCase(host)
                || host.endsWith(".googleusercontent.com")
                || host.equalsIgnoreCase("googleusercontent.com");
    }

    private boolean isGoogleLogin(Uri uri) {
        return uri != null
                && uri.getHost() != null
                && "accounts.google.com".equalsIgnoreCase(uri.getHost());
    }

    private void hideAppsScriptWarningBar() {
        if (webView == null) return;
        String js = "(function(){"
                + "function hide(){"
                + "var w=document.getElementById('warning');"
                + "var t=document.getElementById('warning-text');"
                + "if(w){var r=w.closest&&w.closest('tr');if(r)r.style.display='none';else w.style.display='none';}"
                + "if(t){var s=(t.textContent||'').toLowerCase();"
                + "if(s.indexOf('google apps script')>=0){var p=t.closest&&t.closest('tr');if(p)p.style.display='none';}}"
                + "}"
                + "hide();"
                + "try{new MutationObserver(hide).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}"
                + "setTimeout(hide,150);setTimeout(hide,600);setTimeout(hide,1600);"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    private boolean openExternal(Uri uri) {
        if (uri == null) return false;
        try {
            if ("intent".equalsIgnoreCase(uri.getScheme())) {
                Intent parsed = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                if (parsed.resolveActivity(getPackageManager()) != null) {
                    startActivity(parsed);
                    return true;
                }
                String fallback = parsed.getStringExtra("browser_fallback_url");
                if (fallback != null) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallback)));
                    return true;
                }
                return false;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, "Não há aplicativo disponível para abrir este link.", Toast.LENGTH_SHORT).show();
            return false;
        } catch (Exception error) {
            return false;
        }
    }

    private void showFatalError(Throwable error) {
        try {
            if (root == null) createRoot();
            root.removeAllViews();
            TextView message = new TextView(this);
            message.setTextColor(Color.WHITE);
            message.setTextSize(16f);
            message.setGravity(Gravity.CENTER);
            message.setPadding(dp(40), dp(40), dp(40), dp(40));
            String detail = error == null ? "Erro desconhecido" : error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
            message.setText("Elo Bar não conseguiu iniciar.\n\n" + detail + "\n\nEnvie esta tela para o suporte.");
            root.addView(message, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        } catch (Throwable ignored) {
            Toast.makeText(this, "Falha ao iniciar o Elo Bar.", Toast.LENGTH_LONG).show();
        }
    }

    private final class EloBarWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) return false;
            Uri uri = request.getUrl();
            if (isInternalUrl(uri) || "about".equalsIgnoreCase(uri.getScheme()) || "blob".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            return openExternal(uri);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if (isInternalUrl(uri) || url.startsWith("about:") || url.startsWith("blob:")) {
                return false;
            }
            return openExternal(uri);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            Uri uri = Uri.parse(url);
            if (isAppsScriptSurface(uri)) {
                hideAppsScriptWarningBar();
                hideSplash(450L);
            } else if (isGoogleLogin(uri)) {
                hideSplash(120L);
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request != null && request.isForMainFrame()) {
                hideSplash(100L);
            }
        }
    }

    private final class EloBarWebChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams
        ) {
            if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = filePathCallback;
            try {
                Intent intent = fileChooserParams.createIntent();
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException error) {
                pendingFileChooser = null;
                Toast.makeText(MainActivity.this, "Nenhum seletor de arquivo disponível.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (pendingFileChooser != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                pendingFileChooser.onReceiveValue(result);
                pendingFileChooser = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            configureWindow();
            if (webView != null) webView.onResume();
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (pendingFileChooser != null) {
            pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = null;
        }
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
