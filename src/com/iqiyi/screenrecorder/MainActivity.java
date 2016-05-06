package com.iqiyi.screenrecorder;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

@SuppressLint("NewApi")
public class MainActivity extends Activity {

	private static final String TAG = "ScreenRecorder";
	private static final int REQUEST_CODE_CAPTURE_PERM = 1234;
	private static final String VIDEO_MIME_TYPE = "video/avc";
	private MediaProjectionManager mMediaProjectionManager;
	private MediaProjection mMediaProjection;
	private boolean mMuxerStarted = false;
	private Surface mSurface;
	private VirtualDisplay mVirtualDisplay;
	private MediaMuxer mMuxer;
	private MediaCodec mVideoEncoder;
	private MediaCodec.BufferInfo mVideoBufferInfo;
	private int mTrackIndex = -1;
	 
	private final Handler mDrainHandler = new Handler(Looper.getMainLooper());
	private Runnable mDrainEncoderRunnable = new Runnable() {
	    @Override
	    public void run() {
	        drainEncoder();
	    }
	};
	
	private TestThread mTestThread;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		getView();
		mMediaProjectionManager = (MediaProjectionManager) getSystemService( android.content.Context.MEDIA_PROJECTION_SERVICE);
		
		mTestThread = new TestThread(this);
		mTestThread.setName("TestThread");
		mTestThread.start();
	}
	
	private TextView ctrlBtn;
	private void getView() {
		ctrlBtn = (TextView) findViewById(R.id.button_control);
		changeButtonStatus();
		
		ctrlBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				if(!mMuxerStarted) {
					requestPermission();
				} else {
					releaseEncoders();
				}
			}
		});
	}
	
	private void changeButtonStatus() {
		if(!mMuxerStarted) {
			ctrlBtn.setText(getResources().getString(R.string.start_recording));
			ctrlBtn.setBackground(getResources().getDrawable(R.drawable.selector_green_bg, null));
		} else {
			ctrlBtn.setText(getResources().getString(R.string.stop_recording));
			ctrlBtn.setBackground(getResources().getDrawable(R.drawable.selector_red_bg, null));
		}
	}
	
	private void requestPermission() {
		Intent permissionIntent = mMediaProjectionManager.createScreenCaptureIntent();
		startActivityForResult(permissionIntent, REQUEST_CODE_CAPTURE_PERM);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// TODO Auto-generated method stub
		super.onActivityResult(requestCode, resultCode, data);
		if (REQUEST_CODE_CAPTURE_PERM == requestCode) {
	        if (resultCode == RESULT_OK) {
	            mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
	            startRecording(); // defined below
	        } else {
	            // user did not grant permissions
	        }
	    }
	}
	
	private void startRecording() {
		DisplayManager dm = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
	    Display defaultDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY);
	    if (defaultDisplay == null) {
	        throw new RuntimeException("No display found.");
	    }
	    
	    // Get the display size and density.
	    DisplayMetrics metrics = getResources().getDisplayMetrics();
	    int screenWidth = metrics.widthPixels;
	    int screenHeight = metrics.heightPixels;
	    int screenDensity = metrics.densityDpi;
	   
	    
//	    prepareVideoEncoder(screenWidth, screenHeight);
	    try {
	    	mMuxer = new MediaMuxer(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/video.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
	    } catch (IOException ioe) {
	        throw new RuntimeException("MediaMuxer creation failed", ioe);
	    }
	    // Start the video input.
	    mVirtualDisplay = mMediaProjection.createVirtualDisplay("Recording Display", screenWidth,
	    		screenHeight, screenDensity, 0 /* flags */, mSurface,
	            null /* callback */, null /* handler */);
	    
	    // Start the encoders
//	    drainEncoder();
	}

	private static class TestHandler extends Handler {
		
		public static final int MSG_FRAME_AVAILABLE = 2;
		WeakReference<TestThread> mWeakThread;
		
		public TestHandler(TestThread thread) {
			Log.i(TAG, "[TestHandler] object is created");
			mWeakThread = new WeakReference<MainActivity.TestThread>(thread);
			}
		
		public void handleMessage(android.os.Message msg) {
			TestThread mTestThread = mWeakThread.get();
			
			switch (msg.what) {
			case MSG_FRAME_AVAILABLE: {
				mTestThread.updateTexImage();
				break;
			}
			default:
				throw new RuntimeException("Unknown message " + msg.what);
			}
		};
	}
	
	private static class TestThread extends Thread implements SurfaceTexture.OnFrameAvailableListener {

		private volatile SurfaceTexture mTestSurfaceTexture;
		private volatile TestHandler mTestHandler;
		private long mLastUpdateTime, mUpdateNow;
		private MainActivity mActivity;
		public TestThread(MainActivity activity) {
			mActivity = activity;
		}
		
		@Override
		public void run() {
			Looper.prepare();
			Log.i(TAG, "[TestThread] is running");
			mTestHandler = new TestHandler(this);
			createSurfaceTexture();
			Looper.loop();
		}
		
		private void updateTexImage() {
			Log.i(TAG, "[TestThread] updateTexImage");
			mTestSurfaceTexture.updateTexImage();
		}
		
		public TestHandler getTestHandler() {
			return mTestHandler;
		}
		
		@Override
		public void onFrameAvailable(SurfaceTexture surfaceTexture) {
			// TODO Auto-generated method stub
			synchronized (mTestSurfaceTexture) {
				Log.i(TAG, "[TestThread] onFrameAvailable");
				mUpdateNow = System.currentTimeMillis();
				long timeDiff = mUpdateNow - mLastUpdateTime;
				if(timeDiff >= 40) {
					mLastUpdateTime = mUpdateNow;
					mTestHandler.sendEmptyMessage(TestHandler.MSG_FRAME_AVAILABLE);
				}
				Log.i(TAG, "[onFrameAvailable]|mUpdateNow: " + mUpdateNow + ", mLastUpdateTime: " + mLastUpdateTime + ", timeDiff: " + timeDiff);
			}
		}
		
		private void createSurfaceTexture() {
			int tex[] = new int[1];
			GLES20.glGenTextures(1, tex, 0);
//			GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0]);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
			mTestSurfaceTexture = new SurfaceTexture(tex[0]);
			
			createSurface() ;
			
			mTestSurfaceTexture.setOnFrameAvailableListener(this);
		}
		
		private void createSurface() {
			mActivity.mSurface = new Surface(mTestSurfaceTexture);
		}
		
	}
	
	private void prepareVideoEncoder(int screenWidth, int screenHeight) {
		mVideoBufferInfo = new MediaCodec.BufferInfo();
	    MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, screenWidth, screenHeight);
	    int frameRate = 30; // 30 fps
	 
	    // Set some required properties. The media codec may fail if these aren't defined.
	    format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
	            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
	    format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000); // 6Mbps
	    format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
	    format.setInteger(MediaFormat.KEY_CAPTURE_RATE, frameRate);
	    format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / frameRate);
	    format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
	    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // 1 seconds between I-frames
	 
	    // Create a MediaCodec encoder and configure it. Get a Surface we can use for recording into.
	    try {
	        mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
	        mVideoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//	        mSurface = mVideoEncoder.createInputSurface();
	        mVideoEncoder.setInputSurface(mSurface);
	        mVideoEncoder.start();
	    } catch (IOException e) {
	        releaseEncoders();
	    }
	}

	protected boolean drainEncoder() {
		mDrainHandler.removeCallbacks(mDrainEncoderRunnable);
	    while (true) {
	    	
	        int bufferIndex = mVideoEncoder.dequeueOutputBuffer(mVideoBufferInfo, 0);
	 
	        if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
	            // nothing available yet
	            break;
	        } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
	            // should happen before receiving buffers, and should only happen once
	            if (mTrackIndex >= 0) {
	                throw new RuntimeException("format changed twice");
	            }
	            mTrackIndex = mMuxer.addTrack(mVideoEncoder.getOutputFormat());
	            if (!mMuxerStarted && mTrackIndex >= 0) {
	                mMuxer.start();
	                mMuxerStarted = true;
	                changeButtonStatus();
	            }
	        } else if (bufferIndex < 0) {
	            // not sure what's going on, ignore it
	        } else {
	            ByteBuffer encodedData = mVideoEncoder.getOutputBuffer(bufferIndex);
	            if (encodedData == null) {
	                throw new RuntimeException("couldn't fetch buffer at index " + bufferIndex);
	            }
	 
	            if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
	                mVideoBufferInfo.size = 0;
	            }
	 
	            if (mVideoBufferInfo.size != 0) {
	                if (mMuxerStarted) {
	                    encodedData.position(mVideoBufferInfo.offset);
	                    encodedData.limit(mVideoBufferInfo.offset + mVideoBufferInfo.size);
	                    mMuxer.writeSampleData(mTrackIndex, encodedData, mVideoBufferInfo);
	                } else {
	                    // muxer not started
	                }
	            }
	 
	            mVideoEncoder.releaseOutputBuffer(bufferIndex, false);
	 
	            if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
	                break;
	            }
	        }
	    }
	 
	    mDrainHandler.postDelayed(mDrainEncoderRunnable, 10);
	    return false;
	}
	
	private void releaseEncoders() {
		mDrainHandler.removeCallbacks(mDrainEncoderRunnable);
	    if (mMuxer != null) {
	        if (mMuxerStarted) {
	            mMuxer.stop();
	        }
	        mMuxer.release();
	        mMuxer = null;
	        mMuxerStarted = false;
	        changeButtonStatus();
	    }
	    if (mVideoEncoder != null) {
	        mVideoEncoder.stop();
	        mVideoEncoder.release();
	        mVideoEncoder = null;
	    }
	    if (mSurface != null) {
	    	mSurface.release();
	    	mSurface = null;
	    }
	    if (mMediaProjection != null) {
	        mMediaProjection.stop();
	        mMediaProjection = null;
	    }
	    mVideoBufferInfo = null;
	    mDrainEncoderRunnable = null;
	    mTrackIndex = -1;
	}
}
