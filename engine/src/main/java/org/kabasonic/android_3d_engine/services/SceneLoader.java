package org.kabasonic.android_3d_engine.services;

import android.app.Activity;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import org.kabasonic.android_3d_engine.animation.Animator;
import org.kabasonic.android_3d_engine.collision.CollisionEvent;
import org.kabasonic.android_3d_engine.controller.TouchEvent;
import org.kabasonic.android_3d_engine.model.Camera;
import org.kabasonic.android_3d_engine.model.Dimensions;
import org.kabasonic.android_3d_engine.model.Object3DData;
import org.kabasonic.android_3d_engine.model.Transform;
import org.kabasonic.android_3d_engine.objects.Point;
import org.kabasonic.android_3d_engine.services.collada.ColladaLoaderTask;
import org.kabasonic.android_3d_engine.services.stl.STLLoaderTask;
import org.kabasonic.android_3d_engine.services.wavefront.WavefrontLoaderTask;
import org.kabasonic.android_3d_engine.view.ModelSurfaceView;
import org.kabasonic.util.android.ContentUtils;
import org.kabasonic.util.event.EventListener;
import org.kabasonic.util.io.IOUtils;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class SceneLoader implements LoadListener, EventListener {


    //Default max size for dimension on any axis
    private static final float DEFAULT_MAX_MODEL_SIZE = 100;
    //Camera position on Z axis
    private static final float DEFAULT_CAMERA_POSITION = DEFAULT_MAX_MODEL_SIZE / 2 + 25;
    //Parent component
    protected final Activity parent;
    //Model uri
    private final URI uri;
    private final int type;
    // OpenGL view
    private GLSurfaceView glView;
    //List of 3D models
    private List<Object3DData> objects = new ArrayList<>();
    //List of GUI objects
    private List<Object3DData> guiObjects = new ArrayList<>();
    // Point of view camera
    private Camera camera = new Camera(DEFAULT_CAMERA_POSITION);
    //Blender uses different coordinate system.
    //This is a patch to turn camera and SkyBox 90 degree on X axis
    private boolean isFixCoordinateSystem = false;
    // Enable or disable blending (transparency)
    private boolean isBlendingEnabled = true;
    //Force transparency
    private boolean isBlendingForced = false;
    //state machine for drawing modes
    private int drawwMode = 0;
    /**
     * Whether to draw objects as wireframes
     */
    private boolean drawWireframe = false;
    /**
     * Whether to draw using points
     */
    private boolean drawPoints = false;
    /**
     * Whether to draw bounding boxes around objects
     */
    private boolean drawBoundingBox = false;
    /**
     * Whether to draw face normals. Normally used to debug models
     */
    private boolean drawNormals = false;
    /**
     * Whether to draw using textures
     */
    private boolean drawTextures = true;
    /**
     * Whether to draw using colors or use default white color
     */
    private boolean drawColors = true;
    /**
     * Light toggle feature: we have 3 states: no light, light, light + rotation
     */
    private boolean rotatingLight = true;
    /**
     * Light toggle feature: whether to draw using lights
     */
    private boolean drawLighting = true;
    /**
     * Animate model (dae only) or not
     */
    private boolean doAnimation = true;
    /**
     * Animate model (dae only) or not
     */
    private boolean isSmooth = false;
    /**
     * show bind pose only
     */
    private boolean showBindPose = false;
    /**
     * Draw skeleton or not
     */
    private boolean drawSkeleton = false;
    /**
     * Toggle collision detection
     */
    private boolean isCollision = false;
    /**
     * Toggle 3d
     */
    private boolean isStereoscopic = false;
    /**
     * Toggle 3d anaglyph (red, blue glasses)
     */
    private boolean isAnaglyph = false;
    /**
     * Toggle 3d VR glasses
     */
    private boolean isVRGlasses = false;
    /**
     * Object selected by the user
     */
    private Object3DData selectedObject = null;
    /**
     * Light bulb 3d data
     */
    private final Object3DData lightBulb = Point.build(new float[]{0, 0, 0}).setId("light");
    /**
     * Animator
     */
    private Animator animator = new Animator();
    /**
     * Did the user touched the model for the first time?
     */
    private boolean userHasInteracted;
    /**
     * time when model loading has started (for stats)
     */
    private long startTime;

    /**
     * A cache to save original model dimensions before rescaling them to fit in screen
     * This enables rescaling several times
     */
    private Map<Object3DData, Dimensions> originalDimensions = new HashMap<>();
    private Map<Object3DData, Transform> originalTransforms = new HashMap<>();

    public SceneLoader(Activity main, URI uri, int type, GLSurfaceView glView) {
        this.parent = main;
        this.uri = uri;
        this.type = type;
        this.glView = glView;

        lightBulb.setLocation(new float[]{0, 0, DEFAULT_CAMERA_POSITION});
    }

    public void init() {
        camera.setChanged(true); // force first draw
        if (uri == null) {
            return;
        }
        Log.i("SceneLoader", "Loading model " + uri + ". async and parallel..");
        if (uri.toString().toLowerCase().endsWith(".obj") || type == 0) {
            new WavefrontLoaderTask(parent, uri, this).execute();
        } else if (uri.toString().toLowerCase().endsWith(".stl") || type == 1) {
            Log.i("SceneLoader", "Loading STL object from: " + uri);
            new STLLoaderTask(parent, uri, this).execute();
        } else if (uri.toString().toLowerCase().endsWith(".dae") || type == 2) {
            Log.i("SceneLoader", "Loading Collada object from: " + uri);
            new ColladaLoaderTask(parent, uri, this).execute();
        }
    }

    public void fixCoordinateSystem() {
        final List<Object3DData> objects = getObjects();
        for (int i = 0; i < objects.size(); i++) {
            final Object3DData objData = objects.get(i);
            if (objData.getAuthoringTool() != null && objData.getAuthoringTool().toLowerCase().contains("blender")) {
                getCamera().rotate(90f, 1, 0, 0);
                Log.i("SceneLoader", "Fixed coordinate system to 90 degrees on x axis. object: " + objData.getId());
                this.isFixCoordinateSystem = true;
                break;
            }
        }
    }

    public boolean isFixCoordinateSystem() {
        return this.isFixCoordinateSystem;
    }

    public final Camera getCamera() {
        return camera;
    }

    private final void makeToastText(final String text, final int toastDuration) {
        parent.runOnUiThread(() -> Toast.makeText(parent.getApplicationContext(), text, toastDuration).show());
    }

    public final Object3DData getLightBulb() {
        return lightBulb;
    }

    /**
     * Hook for animating the objects before the rendering
     */
    public final void onDrawFrame() {

        animateLight();

        // smooth camera transition
        camera.animate();

        // initial camera animation. animate if user didn't touch the screen
        if (!userHasInteracted) {
            animateCamera();
        }

        if (objects.isEmpty()) return;

        if (doAnimation) {
            for (int i = 0; i < objects.size(); i++) {
                Object3DData obj = objects.get(i);
                animator.update(obj, isShowBindPose());
            }
        }
    }

    private void animateLight() {
        if (!rotatingLight) return;

        // animate light - Do a complete rotation every 5 seconds.
        long time = SystemClock.uptimeMillis() % 5000L;
        float angleInDegrees = (360.0f / 5000.0f) * ((int) time);
        lightBulb.setRotation1(new float[]{0, angleInDegrees, 0});
    }

    private void animateCamera() {
        camera.translateCamera(0.0005f, 0f);
    }

    public final synchronized void addObject(Object3DData obj) {
        Log.i("SceneLoader", "Adding object to scene... " + obj);
        objects.add(obj);
    }

    public final synchronized void addGUIObject(Object3DData obj) {
        // log event
        Log.i("SceneLoader", "Adding GUI object to scene... " + obj);
        // add object
        guiObjects.add(obj);
        // requestRender();
    }

    public final synchronized List<Object3DData> getObjects() {
        return objects;
    }

    public final synchronized List<Object3DData> getGUIObjects() {
        return guiObjects;
    }

    public final boolean isDrawWireframe() {
        return this.drawWireframe;
    }

    public final boolean isDrawPoints() {
        return this.drawPoints;
    }

    public final boolean isDrawBoundingBox() {
        return drawBoundingBox;
    }

    public final boolean isDrawNormals() {
        return drawNormals;
    }

    public final boolean isDoAnimation() {
        return doAnimation;
    }

    public final boolean isShowBindPose() {
        return showBindPose;
    }

    public final boolean isVRGlasses() {
        return isVRGlasses;
    }

    public final boolean isDrawTextures() {
        return drawTextures;
    }

    public final boolean isDrawColors() {
        return drawColors;
    }

    public final boolean isDrawLighting() {
        return drawLighting;
    }

    public final boolean isDrawSkeleton() {
        return drawSkeleton;
    }

    public final boolean isCollision() {
        return isCollision;
    }

    public final boolean isStereoscopic() {
        return isStereoscopic;
    }

    public final boolean isAnaglyph() {
        return isAnaglyph;
    }

    public final boolean isBlendingEnabled() {
        return isBlendingEnabled;
    }

    public final boolean isBlendingForced() {
        return isBlendingForced;
    }

    @Override
    public void onStart() {
        // mark start time
        startTime = SystemClock.uptimeMillis();
        // provide context to allow reading resources
        ContentUtils.setThreadActivity(parent);
    }

    @Override
    public void onProgress(String progress) {
    }

    @Override
    public synchronized void onLoad(Object3DData data) {

        // if we add object, we need to initialize Animation, otherwise ModelRenderer will crash
        if (doAnimation) {
            animator.update(data, isShowBindPose());
        }

        // load new object and rescale all together so they fit in the viewport
        addObject(data);

        // rescale objects so they fit in the viewport
        //rescale(this.getObjects(), DEFAULT_MAX_MODEL_SIZE, new float[3]);

    }

    @Override
    public synchronized void onLoadComplete() {

        // get complete list of objects loaded
        final List<Object3DData> objs = getObjects();

        // show object errors
        List<String> allErrors = new ArrayList<>();
        for (Object3DData data : objs) {
            allErrors.addAll(data.getErrors());
        }
        if (!allErrors.isEmpty()) {
            makeToastText(allErrors.toString(), Toast.LENGTH_LONG);
        }

        // notify user
        final String elapsed = (SystemClock.uptimeMillis() - startTime) / 1000 + " secs";


        // clear thread local
        ContentUtils.setThreadActivity(null);

        // rescale all object so they fit in the screen
        rescale(this.getObjects(), DEFAULT_MAX_MODEL_SIZE, new float[3]);

        // fix coordinate system
        fixCoordinateSystem();
    }

    @Override
    public void onLoadError(Exception ex) {
        Log.e("SceneLoader", ex.getMessage(), ex);
        makeToastText("There was a problem building the model: " + ex.getMessage(), Toast.LENGTH_LONG);
        ContentUtils.setThreadActivity(null);
    }

    public Object3DData getSelectedObject() {
        return selectedObject;
    }

    private void setSelectedObject(Object3DData selectedObject) {
        this.selectedObject = selectedObject;
    }

    public void loadTexture(Object3DData obj, Uri uri) throws IOException {
        if (obj == null && objects.size() != 1) {
            makeToastText("Unavailable", Toast.LENGTH_SHORT);
            return;
        }
        obj = obj != null ? obj : objects.get(0);

        // load new texture
        obj.setTextureData(IOUtils.read(ContentUtils.getInputStream(uri)));

        this.drawTextures = true;
    }

    public final boolean isRotatingLight() {
        return rotatingLight;
    }

    public void setView(ModelSurfaceView view) {
        this.glView = view;
    }

    @Override
    public boolean onEvent(EventObject event) {
        //Log.v("SceneLoader","Processing event... "+event);
        if (event instanceof TouchEvent) {
            userHasInteracted = true;
        } else if (event instanceof CollisionEvent) {
            Object3DData objectToSelect = ((CollisionEvent) event).getObject();
            Object3DData point = ((CollisionEvent) event).getPoint();
            if (isCollision() && point != null) {
                addObject(point);
            } else {
                if (getSelectedObject() == objectToSelect) {
                    Log.i("SceneLoader", "Unselected object " + objectToSelect.getId());
                    Log.d("SceneLoader", "Unselected object " + objectToSelect);
                    setSelectedObject(null);
                } else {
                    Log.i("SceneLoader", "Selected object " + objectToSelect.getId());
                    Log.d("SceneLoader", "Selected object " + objectToSelect);
                    setSelectedObject(objectToSelect);
                }
            }
        }
        return true;
    }

    private void rescale(List<Object3DData> datas, float newScale, float[] newPosition) {

        //if (true) return;

        // check we have objects to scale, otherwise, there should be an issue with LoaderTask
        if (datas == null || datas.isEmpty()) {
            return;
        }

        Log.d("SceneLoader", "Scaling datas... total: " + datas.size());
        // calculate the global max length
        final Object3DData firstObject = datas.get(0);
        final Dimensions currentDimensions;
        if (this.originalDimensions.containsKey(firstObject)) {
            currentDimensions = this.originalDimensions.get(firstObject);
        } else {
            currentDimensions = firstObject.getCurrentDimensions();
            this.originalDimensions.put(firstObject, currentDimensions);
        }
        Log.v("SceneLoader", "Model[0] dimension: " + currentDimensions.toString());

        final float[] corner01 = currentDimensions.getCornerLeftTopNearVector();
        ;
        final float[] corner02 = currentDimensions.getCornerRightBottomFar();
        final float[] center01 = currentDimensions.getCenter();

        float maxLeft = corner01[0];
        float maxTop = corner01[1];
        float maxNear = corner01[2];
        float maxRight = corner02[0];
        float maxBottom = corner02[1];
        float maxFar = corner02[2];
        float maxCenterX = center01[0];
        float maxCenterY = center01[1];
        float maxCenterZ = center01[2];

        for (int i = 1; i < datas.size(); i++) {

            final Object3DData obj = datas.get(i);

            final Dimensions original;
            if (this.originalDimensions.containsKey(obj)) {
                original = this.originalDimensions.get(obj);
                Log.v("SceneLoader", "Found dimension: " + original.toString());
            } else {
                original = obj.getCurrentDimensions();
                this.originalDimensions.put(obj, original);
            }


            Log.v("SceneLoader", "Model[" + i + "] '" + obj.getId() + "' dimension: " + original.toString());
            final float[] corner1 = original.getCornerLeftTopNearVector();
            final float[] corner2 = original.getCornerRightBottomFar();
            final float[] center = original.getCenter();
            float maxLeft2 = corner1[0];
            float maxTop2 = corner1[1];
            float maxNear2 = corner1[2];
            float maxRight2 = corner2[0];
            float maxBottom2 = corner2[1];
            float maxFar2 = corner2[2];
            float centerX = center[0];
            float centerY = center[1];
            float centerZ = center[2];

            if (maxRight2 > maxRight) maxRight = maxRight2;
            if (maxLeft2 < maxLeft) maxLeft = maxLeft2;
            if (maxTop2 > maxTop) maxTop = maxTop2;
            if (maxBottom2 < maxBottom) maxBottom = maxBottom2;
            if (maxNear2 > maxNear) maxNear = maxNear2;
            if (maxFar2 < maxFar) maxFar = maxFar2;
            if (maxCenterX < centerX) maxCenterX = centerX;
            if (maxCenterY < centerY) maxCenterY = centerY;
            if (maxCenterZ < centerZ) maxCenterZ = centerZ;
        }
        float lengthX = maxRight - maxLeft;
        float lengthY = maxTop - maxBottom;
        float lengthZ = maxNear - maxFar;

        float maxLength = lengthX;
        if (lengthY > maxLength) maxLength = lengthY;
        if (lengthZ > maxLength) maxLength = lengthZ;
        Log.v("SceneLoader", "Max length: " + maxLength);

        float maxLocation = 0;
        if (datas.size() > 1) {
            maxLocation = maxCenterX;
            if (maxCenterY > maxLocation) maxLocation = maxCenterY;
            if (maxCenterZ > maxLocation) maxLocation = maxCenterZ;
        }
        Log.v("SceneLoader", "Max location: " + maxLocation);

        // calculate the scale factor
        float scaleFactor = newScale / (maxLength + maxLocation);
        final float[] finalScale = new float[]{scaleFactor, scaleFactor, scaleFactor};
        Log.d("SceneLoader", "New scale: " + scaleFactor);

        // calculate the global center
        float centerX = (maxRight + maxLeft) / 2;
        float centerY = (maxTop + maxBottom) / 2;
        float centerZ = (maxNear + maxFar) / 2;
        Log.d("SceneLoader", "Total center: " + centerX + "," + centerY + "," + centerZ);

        // calculate the new location
        float translationX = -centerX + newPosition[0];
        float translationY = -centerY + newPosition[1];
        float translationZ = -centerZ + newPosition[2];
        final float[] globalDifference = new float[]{translationX * scaleFactor, translationY * scaleFactor, translationZ * scaleFactor};
        Log.d("SceneLoader", "Total translation: " + Arrays.toString(globalDifference));


        for (Object3DData data : datas) {

            final Transform original;
            if (this.originalTransforms.containsKey(data)) {
                original = this.originalTransforms.get(data);
                Log.v("SceneLoader", "Found transform: " + original);
            } else {
                original = data.getTransform();
                this.originalTransforms.put(data, original);
            }

            // rescale
            float localScaleX = scaleFactor * original.getScale()[0];
            float localScaleY = scaleFactor * original.getScale()[1];
            float localScaleZ = scaleFactor * original.getScale()[2];
            data.setScale(new float[]{localScaleX, localScaleY, localScaleZ});
            Log.v("SceneLoader", "Mew model scale: " + Arrays.toString(data.getScale()));

            // relocate
            float localTranlactionX = original.getLocation()[0] * scaleFactor;
            float localTranlactionY = original.getLocation()[1] * scaleFactor;
            float localTranlactionZ = original.getLocation()[2] * scaleFactor;
            data.setLocation(new float[]{localTranlactionX, localTranlactionY, localTranlactionZ});
            Log.v("SceneLoader", "Mew model location: " + Arrays.toString(data.getLocation()));

            // center
            data.translate(globalDifference);
            Log.v("SceneLoader", "Mew model translated: " + Arrays.toString(data.getLocation()));
        }
    }

}
