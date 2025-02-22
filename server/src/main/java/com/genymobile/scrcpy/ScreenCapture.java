package com.genymobile.scrcpy;

import com.genymobile.scrcpy.wrappers.SurfaceControl;

import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.OutputStream;

public class ScreenCapture extends SurfaceCapture implements Device.RotationListener, Device.FoldListener {

    private final Device device;
    private Streamer sizeInfoStreamer;
    private IBinder display;

    public ScreenCapture(Device device) {
        this.device = device;
    }
    public ScreenCapture(Device device,Streamer sizeInfoStreamer) {
        this.device = device;
        this.sizeInfoStreamer = sizeInfoStreamer;
        this.sizeInfoStreamer.writeSizeInfo(new SizeInfo(device.getScreenInfo()));
    }

    @Override
    public void init() {
        display = createDisplay();
        device.setRotationListener(this);
        device.setFoldListener(this);
    }

    @Override
    public void start(Surface surface) {
        ScreenInfo screenInfo = device.getScreenInfo();
        Rect contentRect = screenInfo.getContentRect();

        // does not include the locked video orientation
        Rect unlockedVideoRect = screenInfo.getUnlockedVideoSize().toRect();
        int videoRotation = screenInfo.getVideoRotation();
        int layerStack = device.getLayerStack();
        setDisplaySurface(display, surface, videoRotation, contentRect, unlockedVideoRect, layerStack);
    }

    @Override
    public void release() {
        device.setRotationListener(null);
        device.setFoldListener(null);
        SurfaceControl.destroyDisplay(display);
    }

    @Override
    public Size getSize() {
        return device.getScreenInfo().getVideoSize();
    }

    @Override
    public boolean setMaxSize(int maxSize) {
        device.setMaxSize(maxSize);
        return true;
    }

    @Override
    public void onFoldChanged(int displayId, boolean folded) {
        requestReset();
    }

    @Override
    public void onRotationChanged(int rotation) {
        if (this.sizeInfoStreamer!=null){
            this.sizeInfoStreamer.writeSizeInfo(new SizeInfo(device.getScreenInfo()));
        }
        requestReset();
    }

    private static IBinder createDisplay() {
        // Since Android 12 (preview), secure displays could not be created with shell permissions anymore.
        // On Android 12 preview, SDK_INT is still R (not S), but CODENAME is "S".
        boolean secure = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || (Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !"S".equals(
                Build.VERSION.CODENAME));
        return SurfaceControl.createDisplay("scrcpy", secure);
    }

    private static void setDisplaySurface(IBinder display, Surface surface, int orientation, Rect deviceRect, Rect displayRect, int layerStack) {
        SurfaceControl.openTransaction();
        try {
            SurfaceControl.setDisplaySurface(display, surface);
            SurfaceControl.setDisplayProjection(display, orientation, deviceRect, displayRect);
            SurfaceControl.setDisplayLayerStack(display, layerStack);
        } finally {
            SurfaceControl.closeTransaction();
        }
    }
}
