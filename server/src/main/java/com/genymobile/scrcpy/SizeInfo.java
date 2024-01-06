package com.genymobile.scrcpy;

import com.google.gson.Gson;

public class SizeInfo {
    int rotation;
    int width = 0;
    int height = 0;

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public SizeInfo(){}

    public SizeInfo(ScreenInfo screenInfo){
        this.rotation = screenInfo.getDeviceRotation();
        Size size  = screenInfo.getVideoSize();
        this.width = size.getWidth();
        this.height = size.getHeight();
    }

    public String toJson(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}
