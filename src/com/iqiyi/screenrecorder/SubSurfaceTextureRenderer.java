package com.iqiyi.screenrecorder;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

@SuppressLint("NewApi")
public class SubSurfaceTextureRenderer extends SurfaceTextureRenderer implements OnFrameAvailableListener {

	private boolean isFrameAvailable = false;
	private SurfaceTexture mSurfaceTexture;
	private float[] mSurfaceTextureTransform;
	private int[] textures = new int[1];
	
	public SubSurfaceTextureRenderer(SurfaceTexture texture) {
		super(texture);
		mSurfaceTextureTransform = new float[16];
	}

	@Override
	protected boolean draw() {
		// TODO Auto-generated method stub
		synchronized (this) {
			if (isFrameAvailable) {
				mSurfaceTexture.updateTexImage();
				mSurfaceTexture.getTransformMatrix(mSurfaceTextureTransform);
				isFrameAvailable = false;
			} else {
				return false;
			}

		}
		return true;
	}

	private void setupTexture() {

		// Generate the actual texture
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glGenTextures(1, textures, 0);
		checkGlError("Texture generate");

		GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
		checkGlError("Texture generate");

		mSurfaceTexture = new SurfaceTexture(textures[0]);
		mSurfaceTexture.setOnFrameAvailableListener(this);
	}
	
	public void checkGlError(String op) {
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.e("SurfaceTest", op + ": glError " + GLUtils.getEGLErrorString(error));
		}
	}
	
	@Override
	protected void initGLComponents() {
		// TODO Auto-generated method stub
		setupTexture();
	}

	@Override
	protected void deinitGLComponents() {
		// TODO Auto-generated method stub
		GLES20.glDeleteTextures(1, textures, 0);
        mSurfaceTexture.release();
        mSurfaceTexture.setOnFrameAvailableListener(null);
	}

	public SurfaceTexture getmSurfaceTexture() {
		return mSurfaceTexture;
	}
	
	@Override
	public void onFrameAvailable(SurfaceTexture surfaceTexture) {
		// TODO Auto-generated method stub
		synchronized (this) {
			isFrameAvailable = true;
		}
	}

}
