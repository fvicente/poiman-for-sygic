package ar.com.alfersoft.poiman;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.util.Log;

public class POIUtil {

	private static String rootDir = "/sdcard";
	public static String DIR_SYGIC = "/Maps";
	public static String DIR_SYGIC_ICON = "/Res/icons";
	public static String DIR_POIMAN = "/POIMan";

	public static String getRootDir() {
		return rootDir;
	}

	public static void setRootDir(String root) {
		rootDir = root;
	}

	static public boolean tryToDelete(String file) {
		final File tmp = new File(file);
		if (tmp.exists()) {
			return tmp.delete();
		}
		return false;
	}

	static public boolean moveFile(String from, String to) {
		final File f = new File(from);
		final File t = new File(to);
		if (!f.exists()) {
			return false;
		}
		if (t.exists()) {
			t.delete();
		}
		return f.renameTo(t);
	}

	/**
	 * Download a file from Internet
	 * 
	 * @param url URL to download
	 * @param outFile name of the output file to be created
	 * @param progress progress dialog 
	 * @return 0 on success, 1 on error, 2 if user canceled
	 * @throws IOException
	 */
	static public int downloadFile(URLConnection conn, int size, String outFile, final POIProgressDialog progress) throws IOException {
		final InputStream is = conn.getInputStream();
		final ReadableByteChannel rbc = Channels.newChannel(is);
		final FileOutputStream fos = new FileOutputStream(outFile);
		int rc = 0;

        int position = 0;
        int retries = 3;
        // transfer file by chunks
        while((is.available() > 0 || retries > 0) && ((progress == null) || (progress != null && !progress.isCanceled()))) {
        	final int prevPos = position;
        	position += fos.getChannel().transferFrom(rbc, position, 2048);
        	final int value = position;
        	if (prevPos < position) {
        		retries = 3;
        	}
        	// only update UI when difference is > 10%
        	if ((progress != null) && ((value - progress.getProgress()) > (size / 10) || value == size)) {
				progress.setProgress(value);
        	}
        	// retry in case of failure
        	if (value == size) {
        		break;
        	} else if (is.available() == 0) {
        		retries--;
        		try {
					Thread.sleep(200);
				} catch (InterruptedException e) {}
        	}
        }
        if (position < size) {
        	// file is incomplete
        	rc = 1; 
        }
        if (progress != null && progress.isCanceled()) {
        	rc = 2;
        }
        is.close();
        fos.close();
        return rc;
	}
	
	static public void updatePOIs(Activity activity, ArrayList<POI> poisList) {
		final ArrayList<POI> pois = poisList;
		final Resources res = activity.getResources();
		final int len = pois.size();
		if (len == 0) {
			new AlertDialog.Builder(activity)
				.setTitle(android.R.string.dialog_alert_title)
				.setMessage(R.string.no_pois_to_update)
				.setPositiveButton(android.R.string.ok, null)
				.show();
			return;
		}
		final POIProgressDialog progress = new POIProgressDialog(activity);
		progress.setIndeterminate(false);
		progress.setTitle(res.getString(R.string.updating));
		progress.setMessage(res.getString(R.string.please_wait));
		progress.setMax(len);
		progress.setProgress(0);
		progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		// Ask the user confirmation
		String text = "";
		text += res.getQuantityString(R.plurals.to_update, len, len);
		final String lastUpdate = activity.getSharedPreferences("ar.com.alfersoft.poiman_preferences", 0).getString("last_update", "");
		if (!lastUpdate.equals("")) {
			text += "\n" + String.format(res.getString(R.string.last_update), lastUpdate);
		}
		text += "\n\n" + res.getString(R.string.confirm_update);
		final Activity self = activity;
		final AlertDialog.Builder dlg = new AlertDialog.Builder(activity)
			.setTitle(android.R.string.dialog_alert_title)
			.setMessage(text)
			.setPositiveButton(R.string.update, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					progress.show();
					new Thread() {
						public void run() {
							int tasksDone = 0;
							try {
								boolean rc = true;

								// Update (download and convert) selected POIs
								for (POI poi : pois) {
									final String text = poi.description;
									self.runOnUiThread(new Runnable() {
										public void run() {
											progress.setMessage(String.format(res.getString(R.string.updating_poi), text));
										}
									});
									rc = poi.update(self.getSharedPreferences("ar.com.alfersoft.poiman_preferences", 0).getString("maps_dir", ""),
													self.getSharedPreferences("ar.com.alfersoft.poiman_preferences", 0).getString("username_preference", ""),
													self.getSharedPreferences("ar.com.alfersoft.poiman_preferences", 0).getString("password_preference", ""));
									if (!rc) {
										new AlertDialog.Builder(self)
											.setTitle(android.R.string.dialog_alert_title)
											.setMessage(R.string.download_error)
											.setPositiveButton(android.R.string.ok, null)
											.show();
										break;
									}
									if (progress.isCanceled()) {
										rc = false;
										break;
									}
									if (rc) {
										final int done = ++tasksDone;
										self.runOnUiThread(new Runnable() {
											public void run() {
												progress.setProgress(done);
											}
										});
									} else {
										break;
									}
								}
							} catch (Exception e) {
						    	Log.e("POI", e.toString());
							} finally {
								final int total = tasksDone;
								self.runOnUiThread(new Runnable() {
									public void run() {
										try{progress.dismiss();} catch(Exception ex) {}
										if (total > 0) {
											final String text = res.getQuantityString(R.plurals.updated, total, total);
											new AlertDialog.Builder(self)
												.setTitle(android.R.string.dialog_alert_title)
												.setMessage(text)
												.setPositiveButton(android.R.string.ok, null)
												.show();
											if (total == len) {
						        		    	final Editor editor = self.getSharedPreferences("ar.com.alfersoft.poiman_preferences", 0).edit();
						        		    	editor.putString("last_update", DateFormat.getDateTimeInstance().format(new Date()));
						        		    	editor.commit();
											}
										}
									};
								});
							}
						}
					}.start();
				}
			})
			.setNegativeButton(android.R.string.cancel, null);
		dlg.show();
	}

	public static CharSequence[] listSubdirs(File directory) {
		final Vector<CharSequence> dirs = new Vector<CharSequence>();
		final File[] entries = directory.listFiles();
		if (entries != null) {
			for (File entry : entries) {
				if (entry.isDirectory()) {
					dirs.add(entry.getName());
				}
			}
		}
		final int len = dirs.size();
		if (len == 0) {
			return null;
		}
		final CharSequence[] arr = new CharSequence[len];
		return dirs.toArray(arr);
	}

	public static File[] listFilesAsArray(File directory, FilenameFilter filter, boolean recurse) {
		Collection<File> files = listFiles(directory, filter, recurse);
		File[] arr = new File[files.size()];
		return files.toArray(arr);
	}

	private static Collection<File> listFiles(File directory, FilenameFilter filter, boolean recurse) {
		// List of files / directories
		Vector<File> files = new Vector<File>();
		// Get files / directories in the directory
		File[] entries = directory.listFiles();
		if (entries != null) {
			// Go over entries
			for (File entry : entries) {
				// If there is no filter or the filter accepts the
				// file / directory, add it to the list
				if (filter == null || filter.accept(directory, entry.getName())) {
					files.add(entry);
				}
				// If the file is a directory and the recurse flag
				// is set, recurse into the directory
				if (recurse && entry.isDirectory()) {
					files.addAll(listFiles(entry, filter, recurse));
				}
			}
		}
		// Return collection of files
		return files;		
	}
}
