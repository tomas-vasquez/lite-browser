package com.ultralite.browser;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(TabWebViewPlugin.class);
        super.onCreate(savedInstanceState);
    }
}