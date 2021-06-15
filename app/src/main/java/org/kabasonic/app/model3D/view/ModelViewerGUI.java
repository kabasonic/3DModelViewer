package org.kabasonic.app.model3D.view;

import org.kabasonic.android_3d_engine.gui.GUI;
import org.kabasonic.android_3d_engine.services.SceneLoader;
import org.kabasonic.android_3d_engine.view.ModelSurfaceView;

import java.util.EventObject;

final class ModelViewerGUI extends GUI {

    private final ModelSurfaceView glView;
    private final SceneLoader scene;

    ModelViewerGUI(ModelSurfaceView glView, SceneLoader scene) {
        super();
        this.glView = glView;
        this.scene = scene;
        setColor(new float[]{1, 1, 1, 0f});
    }

    public void setSize(int width, int height) {
        super.setSize(width, height);
    }

    @Override
    public boolean onEvent(EventObject event) {
        super.onEvent(event);
            if (event instanceof MoveEvent) {
            float dx = ((MoveEvent) event).getDx();
            float dy = ((MoveEvent) event).getDy();
            float[] newPosition = ((MoveEvent) event).getWidget().getLocation().clone();
            newPosition[1]+=dy;
            ((MoveEvent) event).getWidget().setLocation(newPosition);
        }
        return true;
    }
}
