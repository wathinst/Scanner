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

import android.content.Intent;
import android.net.Uri;
import com.google.zxing.BarcodeFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Pattern;

public class DecodeFormatManager {

	private static final Pattern COMMA_PATTERN = Pattern.compile(",");

	public static final Set<BarcodeFormat> PRODUCT_FORMATS;
	public static final Set<BarcodeFormat> ONE_D_FORMATS;
	public static final Set<BarcodeFormat> QR_CODE_FORMATS;
	public static final Set<BarcodeFormat> DATA_MATRIX_FORMATS;

	// 二维码解码
	private static final Set<BarcodeFormat> INDUSTRIAL_FORMATS;

	static {
		PRODUCT_FORMATS = EnumSet.of(BarcodeFormat.UPC_A,
				BarcodeFormat.UPC_E,
				BarcodeFormat.EAN_13,
				BarcodeFormat.EAN_8,
				BarcodeFormat.RSS_14,
				BarcodeFormat.RSS_EXPANDED);
		INDUSTRIAL_FORMATS = EnumSet.of(BarcodeFormat.CODE_39,
				BarcodeFormat.CODE_93,
				BarcodeFormat.CODE_128,
				BarcodeFormat.ITF,
				BarcodeFormat.CODABAR);
		ONE_D_FORMATS = EnumSet.copyOf(PRODUCT_FORMATS);
		ONE_D_FORMATS.addAll(INDUSTRIAL_FORMATS);

		QR_CODE_FORMATS = EnumSet.of(BarcodeFormat.QR_CODE,
				BarcodeFormat.DATA_MATRIX,
				BarcodeFormat.AZTEC,
				BarcodeFormat.MAXICODE);

		DATA_MATRIX_FORMATS = EnumSet.of(BarcodeFormat.DATA_MATRIX);
	}

	private DecodeFormatManager() {
	}

	static Collection<BarcodeFormat> parseDecodeFormats(Intent intent) {
		List<String> scanFormats = null;
		String scanFormatsString = intent.getStringExtra(Intents.Scan.SCAN_FORMATS);
		if (scanFormatsString != null) {
			scanFormats = Arrays.asList(COMMA_PATTERN.split(scanFormatsString));
		}
		return parseDecodeFormats(scanFormats, intent.getStringExtra(Intents.Scan.MODE));
	}

	static Collection<BarcodeFormat> parseDecodeFormats(Uri inputUri) {
		List<String> formats = inputUri.getQueryParameters(Intents.Scan.SCAN_FORMATS);
		if (formats != null && formats.size() == 1 && formats.get(0) != null) {
			formats = Arrays.asList(COMMA_PATTERN.split(formats.get(0)));
		}
		return parseDecodeFormats(formats, inputUri.getQueryParameter(Intents.Scan.MODE));
	}

	private static Collection<BarcodeFormat> parseDecodeFormats(Iterable<String> scanFormats,
															String decodeMode) {
		if (scanFormats != null) {
			Vector<BarcodeFormat> formats = new Vector<BarcodeFormat>();
			try {
				for (String format : scanFormats) {
					formats.add(BarcodeFormat.valueOf(format));
				}
				return formats;
			} catch (IllegalArgumentException iae) {
				// ignore it then
			}
		}
		if (decodeMode != null) {
			if (Intents.Scan.PRODUCT_MODE.equals(decodeMode)) {
				return PRODUCT_FORMATS;
			}
			if (Intents.Scan.QR_CODE_MODE.equals(decodeMode)) {
				return QR_CODE_FORMATS;
			}
			if (Intents.Scan.DATA_MATRIX_MODE.equals(decodeMode)) {
				return DATA_MATRIX_FORMATS;
			}
			if (Intents.Scan.ONE_D_MODE.equals(decodeMode)) {
				return ONE_D_FORMATS;
			}
		}
		return null;
	}

	public static Collection<BarcodeFormat> getQrCodeFormats() {
		return QR_CODE_FORMATS;
	}

	public static Collection<BarcodeFormat> getBarCodeFormats() {
		return ONE_D_FORMATS;
	}
}
