package ru.lisdevs.messenger;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

public class RegistarationActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vk_web_auth);

        webView = (WebView) findViewById(R.id.webview);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("https://id.vk.ru/auth?action=eyJuYW1lIjoibm9fcGFzc3dvcmRfZmxvdyIsInBhcmFtcyI6eyJ0eXBlIjoic2lnbl91cCIsImNzcmZfaGFzaCI6ImUxY2EzYjcyNjI1MmY0YjkwZSJ9fQ%3D%3D&scheme=light&is_redesigned=1&uuid=cqmzdi&response_type=silent_token&v=1.3.0&app_id=7913379&redirect_uri=https%3A%2F%2Fvk.ru%2Fjoin&br=s/");

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
