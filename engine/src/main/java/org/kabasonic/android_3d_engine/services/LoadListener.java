package org.kabasonic.android_3d_engine.services;

import org.kabasonic.android_3d_engine.model.Object3DData;

public interface LoadListener {

    void onStart();

    void onProgress(String progress);

    void onLoadError(Exception ex);

    void onLoad(Object3DData data);

    void onLoadComplete();
}
