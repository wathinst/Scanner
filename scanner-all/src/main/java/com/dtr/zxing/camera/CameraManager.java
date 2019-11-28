/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtr.zxing.camera;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

import com.dtr.zxing.camera.open.OpenCameraInterface;

import java.io.IOException;

/**
 * This object wraps the Camera service object and expects to be the only one
 * talking to it. The implementation encapsulates the steps needed to take
 * preview-sized images, which are used for both preview and decoding.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 */
public class CameraManager {

	private static final String TAG = CameraManager.class.getSimpleName();

	private static CameraManager cameraManager;

	private final Context context;
	private final CameraConfigurationManager configManager;
	private Camera camera;
	private AutoFocusManager autoFocusManager;

	public static int FRAME_WIDTH = -1;
	public static int FRAME_HEIGHT = -1;
	public static int FRAME_MARGINTOP = -1;

	private boolean initialized;
	private boolean previewing;
	private int requestedCameraId = -1;

	private Rect framingRect;
	private Rect framingRectInPreview;

	/**
	 * Preview frames are delivered here, which we pass on to the registered
	 * handler. Make sure to clear the handler so it will only receive one
	 * message.
	 */
	private final PreviewCallback previewCallback;

	/**
	 * Initializes this static object with the Context of the calling Activity.
	 *
	 * @param context The Activity which wants to use the camera.
	 */
	public static void init(Context context) {
		if (cameraManager == null) {
			cameraManager = new CameraManager(context);
		}
	}

	/**
	 * Gets the CameraManager singleton instance.
	 *
	 * @return A reference to the CameraManager singleton.
	 */
	public static CameraManager get() {
		return cameraManager;
	}

	public CameraManager(Context context) {
		this.context = context;
		this.configManager = new CameraConfigurationManager(context);
		previewCallback = new PreviewCallback(configManager);
	}

	/**
	 * Opens the camera driver and initializes the hardware parameters.
	 * 
	 * @param holder
	 *            The surface object which the camera will draw preview frames
	 *            into.
	 * @throws java.io.IOException
	 *             Indicates the camera driver failed to open.
	 */
	public synchronized void openDriver(SurfaceHolder holder)
			throws IOException {
		Camera theCamera = camera;
		if (theCamera == null) {

			if (requestedCameraId >= 0) {
				theCamera = OpenCameraInterface.open(requestedCameraId);
			} else {
				theCamera = OpenCameraInterface.open();
			}

			if (theCamera == null) {
				throw new IOException();
			}
			camera = theCamera;
		}
		theCamera.setPreviewDisplay(holder);

		if (!initialized) {
			initialized = true;
			configManager.initFromCameraParameters(theCamera);
		}

		Camera.Parameters parameters = theCamera.getParameters();
		String parametersFlattened = parameters == null ? null : parameters
				.flatten(); // Save
		// these,
		// temporarily
		try {
			configManager.setDesiredCameraParameters(theCamera, false);
		} catch (RuntimeException re) {
			// Driver failed
			Log.w(TAG,
					"Camera rejected parameters. Setting only minimal safe-mode parameters");
			Log.i(TAG, "Resetting to saved camera params: "
					+ parametersFlattened);
			// Reset:
			if (parametersFlattened != null) {
				parameters = theCamera.getParameters();
				parameters.unflatten(parametersFlattened);
				try {
					theCamera.setParameters(parameters);
					configManager.setDesiredCameraParameters(theCamera, true);
				} catch (RuntimeException re2) {
					// Well, darn. Give up
					Log.w(TAG,
							"Camera rejected even safe-mode parameters! No configuration");
				}
			}
		}
	}

	public synchronized boolean isOpen() {
		return camera != null;
	}

	/**
	 * Closes the camera driver if still in use.
	 */
	public synchronized void closeDriver() {
		if (camera != null) {
			camera.release();
			camera = null;
			// Make sure to clear these each time we close the camera, so that
			// any scanning rect
			// requested by intent is forgotten.
		}
	}

	/**
	 * Asks the camera hardware to begin drawing preview frames to the screen.
	 */
	public synchronized void startPreview() {
		Camera theCamera = camera;
		if (theCamera != null && !previewing) {
			theCamera.startPreview();
			previewing = true;
			autoFocusManager = new AutoFocusManager(context, camera);
		}
	}

	/**
	 * Tells the camera to stop drawing preview frames.
	 */
	public synchronized void stopPreview() {
		if (autoFocusManager != null) {
			autoFocusManager.stop();
			autoFocusManager = null;
		}
		if (camera != null && previewing) {
			camera.stopPreview();
			previewCallback.setHandler(null, 0);
			previewing = false;
		}
	}

	/**
	 * A single preview frame will be returned to the handler supplied. The data
	 * will arrive as byte[] in the message.obj field, with width and height
	 * encoded as message.arg1 and message.arg2, respectively.
	 * 
	 * @param handler
	 *            The handler to send the message to.
	 * @param message
	 *            The what field of the message to be sent.
	 */
	public synchronized void requestPreviewFrame(Handler handler, int message) {
		Camera theCamera = camera;
		if (theCamera != null && previewing) {
			previewCallback.setHandler(handler, message);
			theCamera.setOneShotPreviewCallback(previewCallback);
		}
	}

	/**
	 * Calculates the framing rect which the UI should draw to show the user where to place the
	 * barcode. This target helps with alignment as well as forces the user to hold the device
	 * far enough away to ensure the image will be in focus.
	 *
	 * @return The rectangle to draw on screen in window coordinates.
	 */
	public Rect getFramingRect() {
		try {
			Point screenResolution = configManager.getScreenResolution();
			if (camera == null) {
				return null;
			}

			int leftOffset = (screenResolution.x - FRAME_WIDTH) / 2;

			int topOffset;
			if (FRAME_MARGINTOP != -1) {
				topOffset = FRAME_MARGINTOP;
			} else {
				topOffset = (screenResolution.y - FRAME_HEIGHT) / 2;
			}
			framingRect = new Rect(leftOffset, topOffset, leftOffset + FRAME_WIDTH, topOffset + FRAME_HEIGHT);
			return framingRect;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Like {@link #getFramingRect} but coordinates are in terms of the preview frame,
	 * not UI / screen.
	 */
	public synchronized Rect getFramingRectInPreview(){
		if (framingRectInPreview == null) {
			Rect rect = new Rect(getFramingRect());
			Point cameraResolution = configManager.getCameraResolution();
			Point screenResolution = configManager.getScreenResolution();
			rect.left = rect.left * cameraResolution.y / screenResolution.x;
			rect.right = rect.right * cameraResolution.y / screenResolution.x;
			rect.top = rect.top * cameraResolution.x / screenResolution.y;
			rect.bottom = rect.bottom * cameraResolution.x / screenResolution.y;
			framingRectInPreview = rect;
		}
		return framingRectInPreview;
	}


	/**
	 * Allows third party apps to specify the camera ID, rather than determine
	 * it automatically based on available cameras and their orientation.
	 * 
	 * @param cameraId
	 *            camera ID of the camera to use. A negative value means
	 *            "no preference".
	 */
	public synchronized void setManualCameraId(int cameraId) {
		requestedCameraId = cameraId;
	}

	/**
	 * 获取相机分辨率
	 * 
	 * @return
	 */
	public Point getCameraResolution() {
		return configManager.getCameraResolution();
	}

	public Size getPreviewSize() {
		if (null != camera) {
			return camera.getParameters().getPreviewSize();
		}
		return null;
	}

	public Camera getCamera() {
		return camera;
	}

	/**
	 * 打开闪光灯
	 * 
	 * @return
	 */
	public boolean turnOn() {
        boolean result = false;
		try {
            if (camera != null) {
                Parameters parameter = camera.getParameters();
                parameter.setFlashMode(Parameters.FLASH_MODE_TORCH);
                camera.setParameters(parameter);
            }
            result = true;
        } catch (Exception e){
            Log.e(TAG,"打开闪光灯失败",e);
        }
        return result;
	}

	/**
	 * 关闭闪光灯
	 * 
	 * @return
	 */
	public boolean turnOff() {
        boolean result = false;
        try {
            if (camera != null) {
                Parameters parameter = camera.getParameters();
                parameter.setFlashMode(Parameters.FLASH_MODE_OFF);
                camera.setParameters(parameter);
            }
            result = true;
        } catch (Exception e){
            Log.e(TAG,"关闭闪光灯失败",e);
        }
        return result;
	}

}
