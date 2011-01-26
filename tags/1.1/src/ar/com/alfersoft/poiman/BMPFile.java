package ar.com.alfersoft.poiman;

import java.io.*;

import android.graphics.Bitmap;

public class BMPFile {
	// --- Private constants
	private final static int BITMAPFILEHEADER_SIZE = 14;
	private final static int BITMAPINFOHEADER_SIZE = 40;
	// --- Private variable declaration
	// --- Bitmap file header
	private byte bfType[] = { 'B', 'M' };
	private int bfSize = 0;
	private int bfReserved1 = 0;
	private int bfReserved2 = 0;
	private int bfOffBits = BITMAPFILEHEADER_SIZE + BITMAPINFOHEADER_SIZE;
	// --- Bitmap info header
	private int biSize = BITMAPINFOHEADER_SIZE;
	private int biWidth = 0;
	private int biHeight = 0;
	private int biPlanes = 1;
	private int biBitCount = 32;
	private int biCompression = 0;
	private int biSizeImage = 0x030000;
	private int biXPelsPerMeter = 0x0;
	private int biYPelsPerMeter = 0x0;
	private int biClrUsed = 0;
	private int biClrImportant = 0;
	// --- Bitmap raw data
	private int bitmap[];
	// --- File section
	private BufferedOutputStream fo;

	// --- Default constructor
	public BMPFile() {
	}

	public void saveBitmap(String parFilename, Bitmap parImage, int parWidth, int parHeight) {
		try {
			fo = new BufferedOutputStream(new FileOutputStream(parFilename), 8192);
			save(parImage, parWidth, parHeight);
			fo.close();
		} catch (Exception saveEx) {
			saveEx.printStackTrace();
		}
	}

	/*
	 * The saveMethod is the main method of the process. This method will call
	 * the convertImage method to convert the memory image to a byte array;
	 * method writeBitmapFileHeader creates and writes the bitmap file header;
	 * writeBitmapInfoHeader creates the information header; and writeBitmap
	 * writes the image.
	 */
	private void save(Bitmap parImage, int parWidth, int parHeight) {
		try {
			convertImage(parImage, parWidth, parHeight);
			writeBitmapFileHeader();
			writeBitmapInfoHeader();
			writeBitmap();
		} catch (Exception saveEx) {
			saveEx.printStackTrace();
		}
	}

	/*
	 * convertImage converts the memory image to the bitmap format (BRG). It
	 * also computes some information for the bitmap info header.
	 */
	private boolean convertImage(Bitmap parImage, int parWidth, int parHeight) {
		bitmap = new int[parWidth * parHeight];
		parImage.getPixels(bitmap, 0, parWidth, 0, 0, parWidth, parHeight);
		biSizeImage = ((parWidth * parHeight) * 4);
		bfSize = biSizeImage + BITMAPFILEHEADER_SIZE + BITMAPINFOHEADER_SIZE;
		biWidth = parWidth;
		biHeight = parHeight;
		return (true);
	}

	/*
	 * writeBitmap converts the image returned from the pixel grabber to the
	 * format required. Remember: scan lines are inverted in a bitmap file!
	 * 
	 * Each scan line must be padded to an even 4-byte boundary.
	 */
	private void writeBitmap() {
		int size, value;
		int j;
		int rowCount, rowIndex, lastRowIndex;
		byte rgba[] = new byte[4];
		size = (biWidth * biHeight);
		rowCount = 1;
		rowIndex = size - biWidth - 1;
		lastRowIndex = rowIndex;
		try {
			for (j = 0; j < size; j++) {
				rowIndex++;
				value = bitmap[rowIndex];
				rgba[0] = (byte) (value & 0xFF);
				rgba[1] = (byte) ((value >> 8) & 0xFF);
				rgba[2] = (byte) ((value >> 16) & 0xFF);
				rgba[3] = (byte) ((value >> 24) & 0xFF);
				fo.write(rgba);
				if (rowCount == biWidth) {
					rowCount = 1;
					rowIndex = lastRowIndex - biWidth;
					lastRowIndex = rowIndex;
				} else
					rowCount++;
			}
		} catch (Exception wb) {
			wb.printStackTrace();
		}
	}

	/*
	 * writeBitmapFileHeader writes the bitmap file header to the file.
	 */
	private void writeBitmapFileHeader() {
		try {
			fo.write(bfType);
			fo.write(intToDWord(bfSize));
			fo.write(intToWord(bfReserved1));
			fo.write(intToWord(bfReserved2));
			fo.write(intToDWord(bfOffBits));
		} catch (Exception wbfh) {
			wbfh.printStackTrace();
		}
	}

	/*
	 * 
	 * writeBitmapInfoHeader writes the bitmap information header to the file.
	 */
	private void writeBitmapInfoHeader() {
		try {
			fo.write(intToDWord(biSize));
			fo.write(intToDWord(biWidth));
			fo.write(intToDWord(biHeight));
			fo.write(intToWord(biPlanes));
			fo.write(intToWord(biBitCount));
			fo.write(intToDWord(biCompression));
			fo.write(intToDWord(biSizeImage));
			fo.write(intToDWord(biXPelsPerMeter));
			fo.write(intToDWord(biYPelsPerMeter));
			fo.write(intToDWord(biClrUsed));
			fo.write(intToDWord(biClrImportant));
		} catch (Exception wbih) {
			wbih.printStackTrace();
		}
	}

	/*
	 * 
	 * intToWord converts an int to a word, where the return value is stored in
	 * a 2-byte array.
	 */
	private byte[] intToWord(int parValue) {
		final byte retValue[] = new byte[2];
		retValue[0] = (byte) (parValue & 0x00FF);
		retValue[1] = (byte) ((parValue >> 8) & 0x00FF);
		return (retValue);
	}

	/*
	 * 
	 * intToDWord converts an int to a double word, where the return value is
	 * stored in a 4-byte array.
	 */
	private byte[] intToDWord(int parValue) {
		final byte retValue[] = new byte[4];
		retValue[0] = (byte) (parValue & 0x00FF);
		retValue[1] = (byte) ((parValue >> 8) & 0x000000FF);
		retValue[2] = (byte) ((parValue >> 16) & 0x000000FF);
		retValue[3] = (byte) ((parValue >> 24) & 0x000000FF);
		return (retValue);
	}
}
