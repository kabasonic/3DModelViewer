package org.kabasonic.app.model3D.view;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import org.kabasonic.android_3d_engine.camera.CameraController;
import org.kabasonic.android_3d_engine.collision.CollisionController;
import org.kabasonic.android_3d_engine.controller.TouchController;
import org.kabasonic.android_3d_engine.services.SceneLoader;
import org.kabasonic.android_3d_engine.view.ModelRenderer;
import org.kabasonic.android_3d_engine.view.ModelSurfaceView;
import org.kabasonic.util.android.ContentUtils;
import org.kabasonic.util.event.EventListener;

import java.io.IOException;
import java.net.URI;
import java.util.EventObject;

public class ModelActivity extends Activity implements EventListener {

    private static final int REQUEST_CODE_LOAD_TEXTURE = 1000;
    private static final int FULLSCREEN_DELAY = 10000;

    //Type of model if file name has no extension (provided though content provider)
    private int paramType;
    //The file to load. Passed as input parameter
    private URI paramUri;
    //Enter into Android Immersive mode so the renderer is full screen or not
    private boolean immersiveMode;
    //Background GL clear color. Default is light gray
    private float[] backgroundColor = new float[]{0.0f, 0.0f, 0.0f, 1.0f};

    private ModelSurfaceView gLView;
    private TouchController touchController;
    private SceneLoader scene;
    private ModelViewerGUI gui;
    private CollisionController collisionController;


    private Handler handler;
    private CameraController cameraController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("ModelActivity", "Loading activity...");
        super.onCreate(savedInstanceState);

        // Try to get input parameters
        Bundle b = getIntent().getExtras();
        if (b != null) {
            try {
                if (b.getString("uri") != null) {
                    this.paramUri = new URI(b.getString("uri"));
                    Log.i("ModelActivity", "Params: uri '" + paramUri + "'");
                }
                this.paramType = b.getString("type") != null ? Integer.parseInt(b.getString("type")) : -1;
                this.immersiveMode = "true".equalsIgnoreCase(b.getString("immersiveMode"));

                if (b.getString("backgroundColor") != null) {
                    String[] backgroundColors = b.getString("backgroundColor").split(" ");
                    backgroundColor[0] = Float.parseFloat(backgroundColors[0]);
                    backgroundColor[1] = Float.parseFloat(backgroundColors[1]);
                    backgroundColor[2] = Float.parseFloat(backgroundColors[2]);
                    backgroundColor[3] = Float.parseFloat(backgroundColors[3]);
                }
            } catch (Exception ex) {
                Log.e("ModelActivity", "Error parsing activity parameters: " + ex.getMessage(), ex);
            }

        }

        handler = new Handler(getMainLooper());

        // Create our 3D scenario
        Log.i("ModelActivity", "Loading Scene...");
        scene = new SceneLoader(this, paramUri, paramType, gLView);
        try {
            Log.i("ModelActivity", "Loading GLSurfaceView...");
            gLView = new ModelSurfaceView(this, backgroundColor, this.scene);
            gLView.addListener(this);
            setContentView(gLView);
            scene.setView(gLView);
        } catch (Exception e) {
            Log.e("ModelActivity", e.getMessage(), e);
            Toast.makeText(this, "Error loading OpenGL view:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            Log.i("ModelActivity", "Loading TouchController...");
            touchController = new TouchController(this);
            touchController.addListener(this);
        } catch (Exception e) {
            Log.e("ModelActivity", e.getMessage(), e);
            Toast.makeText(this, "Error loading TouchController:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            Log.i("ModelActivity", "Loading CollisionController...");
            collisionController = new CollisionController(gLView, scene);
            collisionController.addListener(scene);
            touchController.addListener(collisionController);
            touchController.addListener(scene);
        } catch (Exception e) {
            Log.e("ModelActivity", e.getMessage(), e);
            Toast.makeText(this, "Error loading CollisionController\n" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            Log.i("ModelActivity", "Loading CameraController...");
            cameraController = new CameraController(scene.getCamera());
            gLView.getModelRenderer().addListener(cameraController);
            touchController.addListener(cameraController);
        } catch (Exception e) {
            Log.e("ModelActivity", e.getMessage(), e);
            Toast.makeText(this, "Error loading CameraController" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            // TODO: finish UI implementation
            Log.i("ModelActivity", "Loading GUI...");
            gui = new ModelViewerGUI(gLView, scene);
            touchController.addListener(gui);
            gLView.addListener(gui);
            scene.addGUIObject(gui);
        } catch (Exception e) {
            Log.e("ModelActivity", e.getMessage(), e);
            Toast.makeText(this, "Error loading GUI" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // load model
        scene.init();

        Log.i("ModelActivity", "Finished loading");
    }

    // This snippet hides the system bars.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case REQUEST_CODE_LOAD_TEXTURE:
                // The URI of the selected file
                final Uri uri = data.getData();
                if (uri != null) {
                    Log.i("ModelActivity", "Loading texture '" + uri + "'");
                    try {
                        ContentUtils.setThreadActivity(this);
                        scene.loadTexture(null, uri);
                    } catch (IOException ex) {
                        Log.e("ModelActivity", "Error loading texture: " + ex.getMessage(), ex);
                        Toast.makeText(this, "Error loading texture '" + uri + "'. " + ex
                                .getMessage(), Toast.LENGTH_LONG).show();
                    } finally {
                        ContentUtils.setThreadActivity(null);
                    }
                }
        }
    }

    @Override
    public boolean onEvent(EventObject event) {
        if (event instanceof ModelRenderer.ViewEvent) {
            ModelRenderer.ViewEvent viewEvent = (ModelRenderer.ViewEvent) event;
            if (viewEvent.getCode() == ModelRenderer.ViewEvent.Code.SURFACE_CHANGED) {
                touchController.setSize(viewEvent.getWidth(), viewEvent.getHeight());
                gLView.setTouchController(touchController);

                // process event in GUI
                if (gui != null) {
                    gui.setSize(viewEvent.getWidth(), viewEvent.getHeight());
                    gui.setVisible(true);
                }
            }
        }
        return true;
    }
}
