package ar.com.alfersoft.poiman;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class POI implements Comparable<POI> {
	long	id, mapId, groupId;
	String	description, note;
	URL		url, image;
	int		selected, prevState;
	
	/**
	 * Convert integer to little endian
	 * 
	 * @param value integer to convert
	 * @return the number converted to little endian
	 */
	private int getInt(int value) {
		final ByteBuffer bb = ByteBuffer.allocate(Integer.SIZE/8);
		bb.putInt(value);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		return(bb.getInt(0));
	}

	/**
	 * Write string to an output stream inverting byte order on the unicode string
	 * 
	 * @param out output stream
	 * @param str string to write
	 * @throws IOException
	 */
	private void writeString(DataOutputStream out, byte str[]) throws IOException {
		final int len = str.length;
		for (int i = 0; i < len; i++) {
			out.writeByte(str[i]);
			out.writeByte(0);
		}
	}

	/**
	 * Convert OV2 POI file (TomTom) to UPI format (Sygic)
	 * @param source source file name to convert
	 * @param dest destination file name
	 * @param name name of the POI file to save inside the UPI generated
	 * @param imageName name of the image (bmp) associated to this POI file, to save inside the UPI generated
	 * @return true on success, false on failure
	 * @throws IOException
	 */
	private boolean convertOv2ToUpi(String source, String dest, String name, String imageName) throws IOException {
		final ArrayList<POIRecord> recs = new ArrayList<POIRecord>();
		final File file = new File(source);
		if(!file.exists()) {
			throw new IOException("Unable to open file " + source);
		}
		Log.d("POIMan", ">>>>>>>> convertOv2ToUpi Parsing OV2");
		final DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file), 8192));
		POIRecord rec;
		int offset = 0;
		while(in.available() > 0) {
			int recsize = 0;
			int type = in.readByte();
			offset++;
			switch(type) {
			case 0:		// deleted record - skip
				recsize = getInt(in.readInt());
				offset += 4;
				in.skipBytes(recsize - 1 - 4);
				offset += recsize - 1 - 4;
				break;
			case 1:		// skipper record - skip
				int nextId = getInt(in.readInt()) + offset;
				rec = new POIRecord(offset, nextId, in.readInt(), in.readInt(), in.readInt(), in.readInt());
				offset += 20;
				recs.add(rec);
				break;
			case 2:		// simple POI record
			case 3:		// extended POI record
				int longitude = 0, latitude = 0, len = 0;
				recsize = in.readInt();
				longitude = in.readInt();
				latitude = in.readInt();
				len = getInt(recsize) - 1 - 4 - 4 - 4;
				byte buf[] = new byte[len];
				if(in.read(buf) != len) {
					Log.e("POIMan", "convertOv2ToUpi >>> Error reading record type: " + type);
					return(false);
				}
				offset += 12 + len;
				rec = new POIRecord(longitude, latitude, buf);
				recs.add(rec);
				break;
			default:	// invalid type, maybe indicate file corrupted
				Log.e("POIMan", "convertOv2ToUpi >>> Invalid record type: " + type);
				return(false);
			}
		}
		in.close();
		Log.d("POIMan", ">>>>>>>> convertOv2ToUpi Writting UPI");
		// delete skipper records leaving only one level
		final int lenfat = recs.size();
		int cnt = 0;
		Log.d("POIMan", "Purging skipper records");
		for (int i = lenfat - 1; i >= 0; i--) {
			rec = recs.get(i);
			if(rec.type == 1) {
				cnt++;
				if(cnt > 1) {
					recs.remove(i);
					cnt--;
					//Log.d("POIMan", "Removing record #" + i);
				}
			} else {
				cnt = 0;
			}
		}
		// save output file
		final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(dest)), 8192));
		out.writeByte((name.length() * 2) + 2);
		writeString(out, name.getBytes());
		out.writeChar(0);
		byte recfiller[] = { 2, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		out.write(recfiller);
		out.writeByte((imageName.length() * 2) + 2);
		writeString(out, imageName.getBytes());
		out.writeChar(0);

		final int len = recs.size();
		int size = 0;
		POIRecord rec2;
		Log.d("POIMan", "convertOv2ToUpi >>> Records: " + len);
		for (int i = 0; i < len; i++) {
			rec = recs.get(i);
			switch(rec.type) {
			case 1:
				out.writeByte(1);
				// calculate size from here (plus previous byte) until next type 1
				size = 21;
				for (int k = i + 1; k < len; k++) {
					rec2 = recs.get(k);
					if (rec2.type == 1) {
						break;
					} else {
						size += rec2.len;
					}
				}
				out.writeInt(getInt(size));
				out.writeInt(rec.x1);
				out.writeInt(rec.y1);
				out.writeInt(rec.x2);
				out.writeInt(rec.y2);
				break;
			case 2:
				out.writeByte(3);
				// size of previous byte + this int + description length (wide) + null (wide)
				out.writeInt(getInt(rec.len));
				out.writeInt(rec.lon);
				out.writeInt(rec.lat);
				writeString(out, rec.desc);
				break;
			}
		}
		out.close();
		Log.d("POIMan", ">>>>>>>> convertOv2ToUpi Done!");
		return(true);
	}

	public POI(long mapId, long groupId, String description, String note, URL url, URL image, int selected) {
		this.mapId = mapId;
		this.groupId = groupId;
		this.description = description;
		this.note = note;
		this.url = url;
		this.image = image;
		this.selected = selected;
		this.prevState = selected;
	}
	
	public POI(long id, long mapId, long groupId, String description, String note, URL url, URL image, int selected) {
		this.id = id;
		this.mapId = mapId;
		this.groupId = groupId;
		this.description = description;
		this.note = note;
		this.url = url;
		this.image = image;
		this.selected = selected;
		this.prevState = selected;
	}

	public int compareTo(POI poi) {
		return this.description.compareTo(poi.description);
	}

	/**
	 * Download the OV2 POI file, convert it to Sygic format (UPI) and install it
	 * @return true on success, otherwise false
	 */
	public boolean update(String dir, String username, String password) {
		boolean rc = false;
		try {
			String dest = POIUtil.getRootDir() + POIUtil.DIR_SYGIC + "/" + dir;
			// Extract image name from URL
			String imageName = "";
			final String splittedBmp[] = image.getFile().split("/");
			if (splittedBmp != null && splittedBmp.length > 0) {
				imageName = splittedBmp[splittedBmp.length - 1];
			}
			final String bmpDest = POIUtil.getRootDir() + POIUtil.DIR_SYGIC_ICON + "/" + imageName;
			final String splitted[] = url.getFile().split("/");
			String basename = "";
			// Check for valid URL (we expect the .ov2 extension at the end)
			if (splitted != null && splitted.length > 0 && splitted[splitted.length - 1].toLowerCase().endsWith(".ov2")) {
				basename = splitted[splitted.length - 1].substring(0, splitted[splitted.length - 1].length() - 4);
			} else if(description != null) {
				// seems like the name is not on the URL, some providers
				// give the file name on the description field
				basename = description.replaceAll(" ", "_");
			}
			if (!basename.equals("")) {
				// Download the POI file
				final String poiTmp = POIUtil.getRootDir() + POIUtil.DIR_POIMAN + "/" + basename + ".ov2.tmp";
				final URL substUrl = new URL(url.toString().replaceAll("%Username%", username).replaceAll("%Password%", password));
				Log.d("POIMan", "Trying to download -> " + url.toString());
				final URLConnection conn = substUrl.openConnection();
				final int size = conn.getContentLength();
				final int rcDownload = POIUtil.downloadFile(conn, size, poiTmp, null);
				if (rcDownload == 0) {
					Log.d("POIMan", "POI downloaded -> " + substUrl.toString());
					// Convert to UPI
					rc = convertOv2ToUpi(poiTmp, dest + "/" + basename + ".upi", basename.replace('_', ' '), imageName);
					POIUtil.tryToDelete(poiTmp);
					// Try to download the image, won't return false on error
					if (rc && imageName != "") {
						final String bmpTmp = POIUtil.getRootDir() + POIUtil.DIR_POIMAN + "/" + basename + ".bmp.tmp";
						final URLConnection connBmp = image.openConnection();
						final int sizeBmp = connBmp.getContentLength();
						final int rcBmp = POIUtil.downloadFile(connBmp, sizeBmp, bmpTmp, null);
						if (rcBmp == 0) {
					        final Bitmap bmp = BitmapFactory.decodeFile(bmpTmp);
					        if (bmp != null) {
					        	final Bitmap resizedBmp = Bitmap.createScaledBitmap(bmp, 27, 27, false);
					        	final BMPFile outBmp = new BMPFile();
					        	outBmp.saveBitmap(bmpDest, resizedBmp, 27, 27);
					        } else {
					        	POIUtil.moveFile(bmpTmp, bmpDest);
					        }
						}
						POIUtil.tryToDelete(bmpTmp);
					}
				}
			}
		} catch (IOException e) {
			Log.e("POIMan", "update() >>> " + e.toString());
		}	
		return rc;
	}
	
	/**
	 * Remove existing POI and its image file
	 * @return true on success, otherwise false
	 */
	public boolean remove(String dir) {
		boolean rc = false;
		String dest = POIUtil.getRootDir() + POIUtil.DIR_SYGIC + "/" + dir;
		// Extract image name from URL
		String imageName = "";
		final String splittedBmp[] = image.getFile().split("/");
		if (splittedBmp != null && splittedBmp.length > 0) {
			imageName = splittedBmp[splittedBmp.length - 1];
			final String bmpDest = POIUtil.getRootDir() + POIUtil.DIR_SYGIC_ICON + "/" + imageName;
			POIUtil.tryToDelete(bmpDest);
		}
		final String splitted[] = url.getFile().split("/");
		String basename = "";
		// Check for valid URL (we expect the .ov2 extension at the end)
		if (splitted != null && splitted.length > 0 && splitted[splitted.length - 1].toLowerCase().endsWith(".ov2")) {
			basename = splitted[splitted.length - 1].substring(0, splitted[splitted.length - 1].length() - 4);
		} else if(description != null) {
			// seems like the name is not on the URL, some providers
			// give the file name on the description field
			basename = description.replaceAll(" ", "_");
		}
		if (!basename.equals("")) {
			final String upi = dest + "/" + basename + ".upi";
			rc = POIUtil.tryToDelete(upi);
		}
		return rc;
	}
}
