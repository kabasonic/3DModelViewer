package org.kabasonic.android_3d_engine.objects;

import android.opengl.GLES20;

import org.kabasonic.android_3d_engine.model.Object3DData;
import org.kabasonic.util.io.IOUtils;

public final class Line {

    public static Object3DData build(float[] line) {
        return new Object3DData(IOUtils.createFloatBuffer(line.length).put(line))
                .setDrawMode(GLES20.GL_LINES).setId("Line");
    }
}
