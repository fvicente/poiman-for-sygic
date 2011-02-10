package ar.com.alfersoft.poiman;

import java.io.*;

import android.graphics.Bitmap;
import java.util.*;
import java.util.Map.Entry;

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
	// --- Bitmap palette
	private int palette[];
	// --- File section
	private BufferedOutputStream fo;

	// --- Default constructor
	public BMPFile(int bitCount) {
		biBitCount = bitCount;
		if (biBitCount != 32 && biBitCount != 8) {
			biBitCount = 32;
		}
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
			// Get image pixels
			bitmap = new int[parWidth * parHeight];
			parImage.getPixels(bitmap, 0, parWidth, 0, 0, parWidth, parHeight);
			// Recalculate header variables
			biWidth = parWidth;
			biHeight = parHeight;
			biSizeImage = biWidth * biHeight;
			if (biBitCount == 8) {
				// Get palette bits according to the image
				calculatePalette();
				bfOffBits = BITMAPFILEHEADER_SIZE + BITMAPINFOHEADER_SIZE + (palette.length * 4);
				bfSize = BITMAPFILEHEADER_SIZE + BITMAPINFOHEADER_SIZE + (palette.length * 4) + biSizeImage;
			}
			writeBitmapFileHeader();
			writeBitmapInfoHeader();
			if (biBitCount == 8) {
				writePalette();
			}
			writeBitmap();
		} catch (Exception saveEx) {
			saveEx.printStackTrace();
		}
	}

	private void calculatePalette() {
		// use HashMap for better performance
		HashMap<Integer, Integer> colorWeight = new HashMap<Integer, Integer>(256);
		for(int i = 0; i < bitmap.length; i++) {
			if (!colorWeight.containsKey(bitmap[i]))
				colorWeight.put(bitmap[i], 1);
			else
				colorWeight.put(bitmap[i], colorWeight.get(bitmap[i])+1);
		}
		// Sort hashtable by frequency of colors (greatest to lowest)
		ArrayList<Entry<Integer, Integer>> list = new ArrayList<Entry<Integer, Integer>>(colorWeight.entrySet());
		Collections.sort(list, new Comparator<Entry<Integer, Integer>>() {
		            public int compare(Entry<Integer, Integer> e1, Entry<Integer, Integer> e2) {
		               return  e2.getValue().compareTo(e1.getValue());
		            }		
		        });
		// get color palette
		int paletteColors = Math.min(list.size(), 256);
		palette = new int[paletteColors];
		for (int i=0; i < paletteColors; i++) {
			palette[i] = list.get(i).getKey();
		}
		biClrUsed = paletteColors;
		biClrImportant = 0;
	}
	
	/**
	 * Write color palette
	 */
	private void writePalette() {
		int colorARGB;
		int j;		
		byte bgra[] = new byte[4];		
		try {
			for (j = 0; j < palette.length; j++) {
				colorARGB = palette[j];
				bgra[3] = (byte) ((colorARGB >> 24) & 0xFF);	// Alpha
				bgra[2] = (byte) ((colorARGB >> 16) & 0xFF);	// Red
				bgra[1] = (byte) ((colorARGB >> 8) & 0xFF);		// Green
				bgra[0] = (byte) (colorARGB & 0xFF);			// Blue
				fo.write(bgra);
			}
		} catch (Exception wb) 
		{
			wb.printStackTrace();
		}
	}

	/*
	 * writeBitmap converts the image returned from the pixel grabber to the
	 * format required. Remember: scan lines are inverted in a bitmap file!
	 * 
	 * Each scan line must be padded to an even 4-byte boundary.
	 */
	private void writeBitmap() {
		int size, value, pixelARGB;
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
				if (biBitCount == 8) {
					pixelARGB = bitmap[rowIndex];
					// write the palette position of the nearest color
					fo.write(getNearestColor(pixelARGB));
				} else {
					value = bitmap[rowIndex];
					rgba[0] = (byte) (value & 0xFF);
					rgba[1] = (byte) ((value >> 8) & 0xFF);
					rgba[2] = (byte) ((value >> 16) & 0xFF);
					rgba[3] = (byte) ((value >> 24) & 0xFF);
					fo.write(rgba);
				}
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

	/**
	 * Return palette position of the nearest color for given pixel
	 * @param pixelARGB
	 * @return
	 */
	private byte getNearestColor(int pixelARGB) {
		byte index = 0;
		int minDiff = 0xFF;

		for (int i = 0; i < palette.length; i++) {
			// pixel color
			byte bPixelBlue = (byte) ((pixelARGB) & 0xFF);
			byte bPixelGreen = (byte) ((pixelARGB >> 8) & 0xFF);
			byte bPixelRed = (byte) ((pixelARGB >> 16) & 0xFF);
			
			// palette colors
			byte bPalRed = (byte) ((palette[i] >> 16) & 0xFF);	// Red
			byte bPalGreen = (byte) ((palette[i] >> 8) & 0xFF);	// Green
			byte bPalBlue = (byte) ((palette[i]) & 0xFF);		// Blue			

			// Get difference between components ( red green blue )
            // of given color and appropriate components of pallete color
            int bDiff = (byte) Math.abs((int) bPalBlue - (int) bPixelBlue);
            int gDiff = (byte) Math.abs((int) bPalGreen - (int) bPixelGreen);
            int rDiff = (byte) Math.abs((int) bPalRed - (int) bPixelRed);

            // Get max difference
            int currentDiff = Math.max(Math.max(bDiff, gDiff),rDiff);
            if (currentDiff < minDiff) {
                minDiff = currentDiff;
                index = (byte)i;
            }
        }
		return index;
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
