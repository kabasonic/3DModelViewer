package org.kabasonic.android_3d_engine.services.collada;

import android.app.Activity;

import org.kabasonic.android_3d_engine.model.Object3DData;
import org.kabasonic.android_3d_engine.services.LoadListener;
import org.kabasonic.android_3d_engine.services.LoaderTask;

import java.io.IOException;
import java.net.URI;
import java.util.List;

public class ColladaLoaderTask extends LoaderTask {

    public ColladaLoaderTask(Activity parent, URI uri, LoadListener callback) {
        super(parent, uri, callback);
    }

    @Override
    protected List<Object3DData> build() throws IOException {
        return new ColladaLoader().load(uri, this);
    }
}