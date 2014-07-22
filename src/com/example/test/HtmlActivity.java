package com.example.test;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class HtmlActivity extends Activity {
    WebView web;
    class JsObject {
        @JavascriptInterface
        public String test() {
            Toast.makeText(getApplicationContext(), "JS invoke JAVA!", Toast.LENGTH_SHORT).show();
            return "injectedObject";
        }
     }
    WebChromeClient wcc = new WebChromeClient() {
        @Override
        public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
            new AlertDialog.Builder(HtmlActivity.this)
            .setTitle("JS Alert")
            .setMessage(message)
            .create().show();;
            return false;
        }
        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, android.webkit.JsResult result) {
            Log.e("tag", "onJsBeforeUnload");
            return true;
        }
        @Override
        public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
            Log.e("tag", "onJsConfirm");
            return true;
        }
        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, android.webkit.JsPromptResult result) {
            Log.e("tag", "onJsPrompt");
            return true;
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_html);
        web = (WebView) findViewById(R.id.web);
        Button btn1 = (Button) findViewById(R.id.button1);
        Button btn2 = (Button) findViewById(R.id.button2);
        Button btn3 = (Button) findViewById(R.id.button3);
        WebSettings mWebSettings = web.getSettings();
        web.setWebChromeClient(wcc);
        mWebSettings.setJavaScriptEnabled(true);
        btn1.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                web.loadUrl("file:///android_asset/demo.html");
                web.addJavascriptInterface(new JsObject(), "injectedObject");
            }
        });
        btn2.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                web.loadUrl("javascript:fillContent()");
            }
        });
        btn3.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                //web.loadData("", "text/html", null);
                web.loadUrl("javascript:alert(injectedObject.test())");
            }
        });
    }
}
