package com.gluonhq.impl.charm.down.plugins.android;

import android.Manifest;
import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.transition.Scene;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import com.gluonhq.charm.down.plugins.android.AndroidAugmentedRealityService;
import com.gluonhq.charm.down.plugins.ar.ARModel;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafxports.android.FXActivity;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import com.gluonhq.charm.down.plugins.AugmentedRealityService;

/**
 *
 * @author johan
 */
public class AndroidBridge implements GLSurfaceView.Renderer {

    private static final Logger LOG = Logger.getLogger(AndroidBridge.class.getName());
    static boolean debug;

    GLSurfaceView surfaceView;
    BackgroundRenderer backgroundRenderer;
    private final ComplexObjectRenderer virtualObject = new ComplexObjectRenderer();
    private DisplayRotationHelper displayRotationHelper;
    private TapHelper tapHelper;

    private final FXActivity androidContext;
    private Session session;
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();

    private final float[] anchorMatrix = new float[16];
    private static final float[] DEFAULT_COLOR = new float[]{0f, 0f, 0f, 0f};
    private View originalView;
    private float scaleFactor = 1f;
    private ARModel arModel;
    private final AndroidAugmentedRealityService parent;
    private boolean debugAR;

    private static class ColoredAnchor {

        public final Anchor anchor;
        public final float[] color;

        public ColoredAnchor(Anchor a, float[] color4f) {
            this.anchor = a;
            this.color = color4f;
        }
    }
    private final ArrayList<ColoredAnchor> anchors = new ArrayList<>();

    public AndroidBridge(AndroidAugmentedRealityService parent, boolean debug) {
        this.parent = parent;
        AndroidBridge.debug = debug;
        this.androidContext = FXActivity.getInstance();
        
        // Perform initial check as soon as possible
        checkAvailability();
    }
    
    public void setModel(ARModel model) {
        this.arModel = model;
        this.scaleFactor = (float) arModel.getScale();
    }
    
    public void enableDebug(boolean enable) {
        this.debugAR = enable;
    }
    
    public final AugmentedRealityService.Availability checkAvailability() {
        try {
            final ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(androidContext);
            LOG.log(Level.INFO, "ARCore availability: " + availability.toString());
            switch (availability) {
                case UNSUPPORTED_DEVICE_NOT_CAPABLE: 
                    if (debug) LOG.log(Level.INFO, "ARCore not supported");
                    return AugmentedRealityService.Availability.AR_NOT_SUPPORTED;
                case SUPPORTED_APK_TOO_OLD: 
                    if (debug) LOG.log(Level.INFO, "ARCore supported but apk too old");
                    return AugmentedRealityService.Availability.ARCORE_OUTDATED;
                case SUPPORTED_NOT_INSTALLED: 
                    if (debug) LOG.log(Level.INFO, "ARCore supported but not installed");
                    return AugmentedRealityService.Availability.ARCORE_NOT_INSTALLED;
                case UNKNOWN_CHECKING: 
                case UNKNOWN_TIMED_OUT: 
                case UNKNOWN_ERROR: 
                    if (debug) LOG.log(Level.INFO, "ARCore not installed. Treated as not supported");
                    return AugmentedRealityService.Availability.AR_NOT_SUPPORTED;
                case SUPPORTED_INSTALLED: 
                    if (debug) LOG.log(Level.INFO, "ARCore supported and installed");
                    return AugmentedRealityService.Availability.AR_SUPPORTED;
                default: break;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error checking ARCore availability", e);
        }
        return AugmentedRealityService.Availability.AR_NOT_SUPPORTED;
    }
    
    public boolean show(Runnable onCancel) {
        try {
            surfaceView = setupSurfaceView(androidContext, onCancel);
            displayRotationHelper = new DisplayRotationHelper(androidContext);

            boolean cameraEnabled = PermissionRequestActivity.verifyPermissions(Manifest.permission.CAMERA);
            if (! cameraEnabled) {
                LOG.log(Level.WARNING, "Camera disabled");
                return false;
            }
            if (debug) LOG.log(Level.INFO, "Camera permission done");
            
            session = new Session(androidContext);
            Config config = session.getConfig();
            session.configure(config);

            if (debug) LOG.log(Level.INFO, "Session = " + session);
            session.resume();
            backgroundRenderer = new BackgroundRenderer();
            surfaceView.onResume();
            androidContext.runOnUiThread(() -> {
                displayRotationHelper.onResume();
                View focus = androidContext.getCurrentFocus();
                if (focus != null) {
                    originalView = focus;
                }
                Scene scene = androidContext.getContentScene();
                if (debug) LOG.log(Level.INFO, "Switch content, focus = " + focus + " and scene = " + scene);
                androidContext.setContentView(surfaceView, 
                        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                tapHelper = new TapHelper(androidContext);
                surfaceView.setOnTouchListener(tapHelper);
                if (debug) LOG.log(Level.INFO, "Switched content to new surfaceview");
            });
            if (debug) LOG.log(Level.INFO, "Setcontentview done");
        } catch (UnavailableApkTooOldException e) {
            LOG.log(Level.WARNING, "Please update ARCore", e);
            return false;
        } catch (UnavailableArcoreNotInstalledException e) {
            LOG.log(Level.WARNING, "Please install ARCore", e);
            return false;
        } catch (UnavailableSdkTooOldException e) {
            LOG.log(Level.WARNING, "Please update this app", e);
            return false;
        } catch (CameraNotAvailableException e) {
            LOG.log(Level.WARNING, "Camera not available", e);
            return false;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return true;
    }

    private GLSurfaceView setupSurfaceView(Context context, Runnable onCancel) {
        GLSurfaceView answer = new GLSurfaceView(context);

        answer.setPreserveEGLContextOnPause(true);
        answer.setEGLContextClientVersion(2);
        answer.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        answer.setRenderer(this);
        answer.setKeepScreenOn(true);
        answer.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        answer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        answer.setFocusable(true);
        answer.setFocusableInTouchMode(true);
        answer.requestFocus();
        answer.setOnKeyListener((v, k, e) -> {
            if (k == KeyEvent.KEYCODE_BACK) {
                if (debug) LOG.log(Level.INFO, "Back pressed, cancelling AR");
                stopAR(onCancel);
                return true;
            }
            return false;
        });
        return answer;
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglc) {
        if (debug) LOG.log(Level.INFO, "Surface created");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        try {
            backgroundRenderer.createOnGlThread(androidContext);
            planeRenderer.createOnGlThread(androidContext, "models/trigrid.png");

            pointCloudRenderer.createOnGlThread(androidContext);

            if (arModel != null && arModel.getObjFilename() != null) {
                if (debug) LOG.log(Level.INFO, "Adding ARModel obj");
                virtualObject.createOnGlThread(androidContext, arModel.getObjFilename(), arModel.getTextureFile());
            }
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "There was an error creating the surface", t);
            t.printStackTrace();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        if (debug) LOG.log(Level.INFO, "Surface changed, w = " + width + ", h = " + height);
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        if (session == null) {
            if (debug) LOG.log(Level.INFO, "No session is available");
            return;
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        displayRotationHelper.updateSessionIfNeeded(session);

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());
            Frame frame = session.update();
            Camera camera = frame.getCamera();
            handleTap(frame, camera);
            backgroundRenderer.draw(frame);

            // If not tracking, don't draw 3d objects.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                return;
            }

            // Get projection matrix.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);
            
            if (debugAR) {
                // Visualize tracked points.
                PointCloud pointCloud = frame.acquirePointCloud();
                pointCloudRenderer.update(pointCloud);
                pointCloudRenderer.draw(viewmtx, projmtx);
                pointCloud.release();
                
                // Visualize planes.
                planeRenderer.drawPlanes(
                        session.getAllTrackables(Plane.class), 
                        camera.getDisplayOrientedPose(), projmtx);
            }

            // Compute lighting from average intensity of the image.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            // Visualize anchors created by touch
            for (ColoredAnchor coloredAnchor : anchors) {
                if (coloredAnchor.anchor.getTrackingState() != TrackingState.TRACKING) {
                    continue;
                }
                // Get the current pose of an Anchor in world space. The Anchor pose is updated
                // during calls to session.update() as ARCore refines its estimate of the world.
                coloredAnchor.anchor.getPose().toMatrix(anchorMatrix, 0);

                // Update and draw the model and its shadow.
                if (arModel != null && arModel.getObjFilename() != null) {
                    virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
                    virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Error on draw frame", t);
            t.printStackTrace();
        }

    }

    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
            for (HitResult hit : frame.hitTest(tap)) {
                // Check if any plane was hit, and if it was hit inside the plane polygon
                Trackable trackable = hit.getTrackable();
                if (debug) LOG.log(Level.INFO, "hit " + trackable.toString());
                // Creates an anchor if a plane or an oriented point was hit.
                if ((trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                        && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                    || (trackable instanceof Point
                        && ((Point) trackable).getOrientationMode() == OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
                    // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    if (anchors.size() >= 20) {
                        anchors.get(0).anchor.detach();
                        anchors.remove(0);
                    }

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
                    if (debug) LOG.log(Level.INFO, "adding colored anchor ");
                    anchors.add(new ColoredAnchor(hit.createAnchor(), DEFAULT_COLOR));
                    break;
                }
                    
            }
        }
    }
    
    public void stopAR(Runnable runnable) {
        if (originalView == null) {
            if (debug) LOG.log(Level.WARNING, "OriginalView was null");
            return;
        }
        
        androidContext.runOnUiThread(() -> {
            if (debug) LOG.log(Level.INFO, "Put everything on hold");
            onHold();

            if (surfaceView.getParent() != null) {
                ((ViewGroup) surfaceView.getParent()).removeView(surfaceView);
            }

            if (originalView.getParent() != null) {
                ((ViewGroup) originalView.getParent()).removeView(originalView);
            }
            
            if (debug) LOG.log(Level.INFO, "Return to original view now");
            androidContext.setContentView(originalView,
                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            if (runnable != null) {
                if (debug) LOG.log(Level.INFO, "Resume callback");
                Platform.runLater(runnable);
            }
        });
    }
    
    private void onHold() {
       if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }
    
}
