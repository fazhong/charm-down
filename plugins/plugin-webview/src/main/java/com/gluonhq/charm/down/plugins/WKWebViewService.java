package com.gluonhq.charm.down.plugins;

import javafx.scene.Parent;

public interface WKWebViewService {
    
    public static final String DEFAULT_EXTERNAL_FOLDER = "WKWebView";

    Parent openWebview(String url);

    String getContent();

}
