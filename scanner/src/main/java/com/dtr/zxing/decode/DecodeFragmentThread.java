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

package com.dtr.zxing.decode;

import android.os.Handler;
import android.os.Looper;
import com.dtr.zxing.activity.CaptureFragment;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * This thread does all the heavy lifting of decoding the images.
 * 解码的线程，真正的解码相关工作在其关联的handle中处理
 * @author dswitkin@google.com (Daniel Switkin)
 */
public class DecodeFragmentThread extends Thread {

	public static final String BARCODE_BITMAP = "barcode_bitmap";

	/** 定义三种模式：条形码、二维码、全部  */
	public static final int BARCODE_MODE = 0X100;
	public static final int QRCODE_MODE = 0X200;
	public static final int ALL_MODE = 0X300;

	private final CaptureFragment fragment;
	private final Map<DecodeHintType, Object> hints;
	private Handler handler;
	private final CountDownLatch handlerInitLatch;

	public DecodeFragmentThread(CaptureFragment fragment, int decodeMode) {

		this.fragment = fragment;
		handlerInitLatch = new CountDownLatch(1);

		hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);

		Collection<BarcodeFormat> decodeFormats = new ArrayList<BarcodeFormat>();
		decodeFormats.addAll(EnumSet.of(BarcodeFormat.AZTEC));
		decodeFormats.addAll(EnumSet.of(BarcodeFormat.PDF_417));

		switch (decodeMode) {
			case BARCODE_MODE:
				decodeFormats.addAll(DecodeFormatManager.getBarCodeFormats());
				break;

			case QRCODE_MODE:
				decodeFormats.addAll(DecodeFormatManager.getQrCodeFormats());
				break;

			case ALL_MODE:
				decodeFormats.addAll(DecodeFormatManager.getBarCodeFormats());
				decodeFormats.addAll(DecodeFormatManager.getQrCodeFormats());
				break;

			default:
				break;
		}

		hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
	}

	public Handler getHandler() {
		try {
			handlerInitLatch.await();
		} catch (InterruptedException ie) {
			// continue?
		}
		return handler;
	}

	@Override
	public void run() {
		Looper.prepare();
		handler = new DecodeFragmentHandler(fragment, hints);
		handlerInitLatch.countDown();
		Looper.loop();
	}

}
