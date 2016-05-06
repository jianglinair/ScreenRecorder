package com.iqiyi.screenrecorder;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
@SuppressLint("NewApi")
public abstract class JSurfaceTexture {

	public SurfaceTexture mSurfaceTexture;
	protected boolean isTextureUpdated; 
	public int textureName = -1; 
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@SuppressLint("NewApi")
	public SurfaceTexture createSurfaceTexture() {
		int tex[] = new int[1];
		GLES20.glGenTextures(1, tex, 0);
//		GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
		GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0]);
		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		final SurfaceTexture texture = new SurfaceTexture(tex[0]);
		texture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
			
			@Override
			public void onFrameAvailable(SurfaceTexture surfaceTexture) {
				isTextureUpdated = true;
			}
		});
		return texture;
	}
	
	public void update() {
		if (isTextureUpdated) {
			isTextureUpdated = false;
			mSurfaceTexture.updateTexImage();
			// cameraTexture.getTransformMatrix(mtx);
		}
	}
		  
//		 abstract public void bindBuffer(GLShaderProgram glsl); 
		  
		 public void release() { 
		  if (textureName > 0) { 
		   GLES20.glDeleteTextures(0, new int[]{textureName}, 0); 
		   textureName = -1; 
		  } 
		  if (mSurfaceTexture != null) { 
			  mSurfaceTexture.release(); 
			  mSurfaceTexture = null; 
		  } 
		 } 
}
