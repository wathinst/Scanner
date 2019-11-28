/*
 * Copyright (C) 2010 ZXing authors
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

package com.dtr.zxing.decode;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;


import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.phynos.scanner.all.R;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

/**
 * 真正的解码操作 相关代码
 */
public class DecodeHandler extends Handler {

	private final MultiFormatReader multiFormatReader;
	private boolean running = true;
	private Handler mHandler;
	private Rect mRect;
	private Size mSize;

	private ImageScanner mImageScanner = null;

	public DecodeHandler(Map<DecodeHintType, Object> hints, Rect rect, Size size, Handler handler) {
		mHandler = handler;
		mRect = rect;
		mSize = size;

		multiFormatReader = new MultiFormatReader();
		multiFormatReader.setHints(hints);

		mImageScanner = new ImageScanner();
		mImageScanner.setConfig(0, Config.X_DENSITY, 3);
		mImageScanner.setConfig(0, Config.Y_DENSITY, 3);
	}

	@Override
	public void handleMessage(Message message) {
		if (!running) {
			return;
		}
		if (message.what == R.id.decode) {
			decode((byte[]) message.obj, message.arg1, message.arg2);

		} else if (message.what == R.id.quit) {
			running = false;
			Looper.myLooper().quit();

		}
	}

	/**
	 * Decode the data within the viewfinder rectangle, and time how long it
	 * took. For efficiency, reuse the same reader objects from one decode to
	 * the next.
	 * 
	 * @param data
	 *            The YUV preview frame.
	 * @param width
	 *            The width of the preview frame.
	 * @param height
	 *            The height of the preview frame.
	 */
	private void decode(byte[] data, int width, int height) {
		//Size size = activity.getCameraManager().getPreviewSize();

		// 这里需要将获取的data翻转一下，因为相机默认拿的的横屏的数据
		byte[] rotatedData = new byte[data.length];
		for (int y = 0; y < mSize.height; y++) {
			for (int x = 0; x < mSize.width; x++) {
				rotatedData[x * mSize.height + mSize.height - y - 1] = data[x + y * mSize.width];
			}
		}

		//先用zxing解码		
		boolean result = decodeByZXing(rotatedData,width,height);
		result = result || decodeByZbar(rotatedData);
		if(!result){
			//如果 都解码失败，则发送消息
			Log.d("zbar-decode", "decode failed");
			if (mHandler != null) {
				Message message = Message.obtain(mHandler, R.id.decode_failed);
				message.sendToTarget();
			}
		}	
	}

	private boolean decodeByZXing(byte[] rotatedData, int width, int height){
		boolean result = false;
		Result rawResult = null;
		MyPlanarYUVLuminanceSource source = buildLuminanceSource(rotatedData, height, width);
		if (source != null) {
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
			//先用 高级算法进行二值化
			try {
				rawResult = multiFormatReader.decodeWithState(bitmap);
			} catch (ReaderException re) {
				// continue
			} finally {
				multiFormatReader.reset();
			}

			//如果失败，再用 直方图算法进行二值化（可以增加低对比度的识别率）
			if(rawResult == null){
				BinaryBitmap bitmap2 = new BinaryBitmap(new GlobalHistogramBinarizer(source));
				try {
					rawResult = multiFormatReader.decodeWithState(bitmap2);
				} catch (ReaderException re) {
					// continue
				} finally {
					multiFormatReader.reset();
				}
			}
		}

		if (rawResult != null) {			
			// Don't log the barcode contents for security.
			Log.d("zxing-decode", "resultStr:" + rawResult);
			if (mHandler != null) {
				result = true;
				Message message = Message.obtain(mHandler, R.id.decode_succeeded,rawResult);
				Bundle bundle = new Bundle();
				bundleThumbnail(source, bundle);
				message.setData(bundle);
				message.sendToTarget();
			}
		} else {			
			result = false;			
		}
		return result;
	}

	private boolean decodeByZbar(byte[] rotatedData){
		// 宽高也要调整
		int tmp = mSize.width;
		mSize.width = mSize.height;
		mSize.height = tmp;

		Image barcode = new Image(mSize.width, mSize.height,"Y800");
		barcode.setData(rotatedData);
		if(mRect == null) {
			Log.d("zbar-decode", "剪切面积为空！");
			return false;
		}
		barcode.setCrop(mRect.left, mRect.top, mRect.width(), mRect.height());

		int result = mImageScanner.scanImage(barcode);
		String resultStr = null;

		if (result != 0) {
			SymbolSet syms = mImageScanner.getResults();
			for (Symbol sym : syms) {
				resultStr = sym.getData();
			}
		}
		if (!TextUtils.isEmpty(resultStr)) {
			Log.d("zbar-decode", "resultStr:" + resultStr);
			if (mHandler != null) {
				Message message = Message.obtain(mHandler, R.id.decode_succeeded_zbar, resultStr);
				message.sendToTarget();
			}
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 根据YUV图像生成缩略图，将缩略图数据传给界面
	 * @param source
	 * @param bundle
	 */
	private static void bundleThumbnail(MyPlanarYUVLuminanceSource source, Bundle bundle) {
		int[] pixels = source.renderThumbnail();
		int width = source.getThumbnailWidth();
		int height = source.getThumbnailHeight();
		Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);		
		bundle.putByteArray(DecodeThread.BARCODE_BITMAP, out.toByteArray());
	}

	public MyPlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
		//根据取景框 裁剪数据
		if (mRect == null) {
			return null;
		}
		return new MyPlanarYUVLuminanceSource(data, width, height, mRect.left, mRect.top, mRect.width(), mRect.height(), false);
	}

}
