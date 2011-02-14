package ar.com.alfersoft.poiman;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class POIActivity extends Activity {

	static private final int RC_CHOOSE_GROUPS = 0x1000;
	static private final int RC_PREFERENCES = 0x1001;
	public static final String POIS_FILE = POIUtil.getRootDir() + POIUtil.DIR_POIMAN + "/pois.xml";
	
	private POIDataHelper dataHelper = null;
	private HashMap<String, POIMap> maps = null;

	/**
	 * Parse POI list XML, update database and populate the maps hash table
	 * @param file name of the file to parse
	 * @throws XmlPullParserException
	 * @throws IOException
	 */
	private void parseXML(File file, boolean clean) throws XmlPullParserException, IOException {
		// List of UPI files already installed
		final File upis[] = POIUtil.listFilesAsArray(new File(POIUtil.getRootDir() + POIUtil.DIR_SYGIC + "/" + getSharedPreferences("ar.com.alfersoft.poiman_preferences", 0).getString("maps_dir", "")), new FilenameFilter() {
			public boolean accept(File dir, String filename) {
				return filename.toLowerCase().endsWith(".upi");
			}
		}, true);
		// Make a hash with the <UPI file name without extension, true>
		final HashMap<String, Boolean> upiNames = new HashMap<String, Boolean>();
		final boolean hasUpis = ((upis != null) && (upis.length > 0));
		if (hasUpis) {
			for (File f : upis) {
				final String name = f.getName();
				final int len = name.length();
				if (len > 4) {
					// remove the .upi from the name and make it lower case
					upiNames.put(name.toLowerCase().substring(0, len - 4), true);
				}
			}
		}
		// parse the XML
		final XmlPullParser parser = Xml.newPullParser();
		final FileInputStream in = new FileInputStream(file);
		parser.setInput(in, null);
		String name = null, map = null, group = null, descr = null, note = null, url = null, img = null, format = null;
		if (clean) {
			dataHelper.deleteAll();
		}
		// first get current database info as an object tree
		maps = dataHelper.getMaps(true);
		final Resources res = getResources();
		final URL source = new URL(getSharedPreferences("ar.com.alfersoft.poiman_preferences", 0).getString("poilist_preference", res.getString(R.string.poilist_default_url)));
		for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
			switch (event) {
			case XmlPullParser.START_TAG:
				name = parser.getName();
				if (name.equals("poi")) {
					map = group = descr = note = url = img = "";
				} else if (name.equals("map")) {
					map = parser.nextText();
				} else if (name.equals("group")) {
					group = parser.nextText();
				} else if (name.equals("description")) {
					descr = parser.nextText();
				} else if (name.equals("note")) {
					note = parser.nextText();
				} else if (name.equals("url")) {
					url = parser.nextText();
				} else if (name.equals("image")) {
					img = parser.nextText();
				} else if (name.equals("format")) {
					format = parser.nextText();
				}
				break;
			case XmlPullParser.END_TAG:
				name = parser.getName();
				if (name.equals("poi") && url != null && (url.toLowerCase().contains(".ov2") || (format != null && format.equalsIgnoreCase("ov2")))) {
					if (map == null || map.equals("")) {
						map = res.getString(R.string.unnamed);
					}
					POIMap mapObj = maps.get(map);
					if(mapObj == null) {
						mapObj = new POIMap(map);
						final long mapId = dataHelper.insertMap(mapObj);
						mapObj.id = mapId;
						maps.put(map, mapObj);
					}
					POIGroup groupObj = mapObj.groups.get(group);
					if(groupObj == null) {
						groupObj = new POIGroup(group);
						final long groupId = dataHelper.insertGroup(mapObj.id, groupObj);
						groupObj.id = groupId;
						mapObj.groups.put(group, groupObj);
					}
					URL imgURL, urlObj;
					try {
						if (img.startsWith("/")) {
							imgURL = new URL(source.getProtocol(), source.getHost(), source.getPort(), img);
						} else {
							imgURL = new URL(img);
						}
						if (url.startsWith("/")) {
							urlObj = new URL(source.getProtocol(), source.getHost(), source.getPort(), url);
						} else {
							urlObj = new URL(url);
						}
						final POI poiObj = groupObj.findPOIByURL(urlObj);
						// Check if the POI is already installed
						int selected = 0;
						// Deduct UPI name from OV2
						final String splitted[] = urlObj.getFile().split("/");
						if (hasUpis && splitted != null && splitted.length > 0) {
							final String nameOv2 = splitted[splitted.length - 1].toLowerCase();
							String upi = "";
							if (nameOv2.toLowerCase().endsWith(".ov2")) {
								upi = nameOv2.substring(0, nameOv2.length() - 4);
							} else {
								// seems like the name is not on the URL, some providers
								// give the file name on the description field
								upi = descr.replaceAll(" ", "_").toLowerCase();
							}
							// Check if the upi exists in the Sygic map directory
							final Boolean exists = upiNames.get(upi);
							if (exists != null) {
								selected = 1;
							}
						}
						if(poiObj == null) {
							// Add the new POI
							POI poi = new POI(mapObj.id, groupObj.id, descr, note, urlObj, imgURL, selected);
							final long poiId = dataHelper.insertPOI(poi);
							poi.id = poiId;
							groupObj.pois.add(poi);
						} else if(!poiObj.description.equalsIgnoreCase(descr) ||
								  !poiObj.note.equalsIgnoreCase(note) ||
								  !poiObj.image.equals(imgURL) ||
								  poiObj.selected != selected ||
								  poiObj.mapId != mapObj.id ||
								  poiObj.groupId != groupObj.id) {
							// Something changed, update existing object
							poiObj.mapId = mapObj.id;
							poiObj.groupId = groupObj.id;
							poiObj.description = descr;
							poiObj.note = note;
							poiObj.image = imgURL;
							poiObj.selected = selected;
							poiObj.prevState = selected;
							// Update database
							dataHelper.updatePOI(poiObj);
						}
					} catch(MalformedURLException e) {
						Log.e("POIMan", "Malformed URL -> " + e.toString());
					}
				}
				break;
			}
		}
		in.close();
	}
	
	private void populateList(File poiList) {
    	try {
	        this.setContentView(R.layout.list);
	        final Collection<POIMap> values = maps.values();
	    	final POIMap array[] = new POIMap[values.size()];
	    	values.toArray(array);
	    	Arrays.sort(array);
	    	final TextView title = (TextView) this.findViewById(R.id.list_title);
	    	title.setText((array.length > 0) ? R.string.maps : R.string.no_maps);
	    	final ListView list = (ListView) this.findViewById(R.id.list_view);
	    	final LayoutInflater layoutInflater = getLayoutInflater();
	    	final Activity self = this;
	    	final OnClickListener onclick = new OnClickListener() {
				public void onClick(View view) {
					final POIMap map = (POIMap) view.getTag();
					final Bundle bundle = new Bundle();
					bundle.putString("mapName", map.name);
					bundle.putLong("mapId", map.id);
					final Intent newIntent = new Intent(self, ar.com.alfersoft.poiman.POIGroupList.class);
					newIntent.putExtras(bundle);
					startActivityForResult(newIntent, RC_CHOOSE_GROUPS);
				}
			};
	    	list.setAdapter(new ArrayAdapter<POIMap>(this, R.layout.list_item, array) {
	    		@Override
	    		public View getView(int position, View convertView, ViewGroup parent) {
	    			if (convertView == null) {
	    				convertView = layoutInflater.inflate(R.layout.list_item, parent, false);
	    			}
	    			final POIMap poiMap = array[position];
	    			final ImageView img = (ImageView) convertView.findViewById(R.id.maplist_selected);
	    			img.setImageResource(poiMap.hasGroupSelected ? R.drawable.some_selected_on : R.drawable.some_selected_off);
	    			final TextView t = (TextView) convertView.findViewById(R.id.maplist_text);
					t.setText(poiMap.name);
	    			t.setMaxWidth(list.getWidth() - 55); // 20 from left and 35 from right
	    			convertView.setTag(poiMap);
	    			convertView.setOnClickListener(onclick);
	    			return convertView;
	    		}
	    	});
		} catch (Exception e) {
	    	Log.e("POIMan", e.toString());
		}
	}

	/**
	 * Download POI list file displaying a progress dialog
	 */
	void downloadPOIListFile() {
		final Resources res = this.getResources();
		final POIProgressDialog progress = new POIProgressDialog(this);
		progress.setIndeterminate(false);
		progress.setTitle(res.getString(R.string.downloading));
		progress.setMessage(res.getString(R.string.please_wait));
		progress.setProgress(0);
		progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		final ProgressDialog progressDatabase = new ProgressDialog(this);
		progressDatabase.setIndeterminate(true);
		progressDatabase.setCancelable(false);
		progressDatabase.setTitle(res.getString(R.string.updating_db));
		progressDatabase.setMessage(res.getString(R.string.please_wait));
		progressDatabase.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		final Activity self = this;
		// Confirmation message
		final Dialog dialog = new Dialog(self);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.confirm_poi_list);
		dialog.setTitle(android.R.string.dialog_alert_title);
		((Button)dialog.findViewById(R.id.confirm_cancel)).setOnClickListener(new OnClickListener() {
			public void onClick(View arg) {
				dialog.dismiss();
			}
		});
		final CheckBox cleanDB = (CheckBox)dialog.findViewById(R.id.clean_checkbox);
		cleanDB.setChecked(true);
		((Button)dialog.findViewById(R.id.confirm_ok)).setOnClickListener(new OnClickListener() {
			public void onClick(View arg) {
				dialog.dismiss();
				URLConnection urlConn = null;
				try {
					urlConn = (new URL(getSharedPreferences("ar.com.alfersoft.poiman_preferences", 0).getString("poilist_preference", res.getString(R.string.poilist_default_url)))).openConnection();
				} catch (UnknownHostException e) {
					final AlertDialog.Builder dlg = new AlertDialog.Builder(self)
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setTitle(android.R.string.dialog_alert_title)
						.setMessage(R.string.download_list_error)
						.setPositiveButton(android.R.string.ok, null);
					dlg.show();
					return;
				} catch (Exception e) {
			    	Log.e("POIMan", e.toString());
					return;
				}
				final int size = urlConn.getContentLength();
				progress.setMax(size);
				progress.show();
				final URLConnection conn = urlConn;
				new Thread() {
					public void run() {
						try {
							final int rc = POIUtil.downloadFile(conn, size, POIS_FILE + ".tmp", progress);
							final File tmp = new File(POIS_FILE + ".tmp");
							if (rc == 0) {
								final File pois = new File(POIS_FILE);
								if (tmp.exists()) {
									if (pois.exists()) {
										pois.delete();
									}
									tmp.renameTo(pois);
									// We got a new POI list file!
									dataHelper.open();
									try {
										runOnUiThread(new Runnable() {
											public void run() {
												progress.hide();
												progressDatabase.show();
											}
										});
							    		// now parse the new XML and update the database
										parseXML(pois, cleanDB.isChecked());
									} catch (Exception e) {
										Log.e("POIMan", "downloadPOIListFile -> " + e.toString());
									} finally {
										dataHelper.close();
										runOnUiThread(new Runnable() {
											public void run() {
												populateList(pois);
											}
										});
									}
								}
							} else {
								final AlertDialog.Builder dlg = new AlertDialog.Builder(self)
									.setIcon(android.R.drawable.ic_dialog_alert)
									.setTitle(android.R.string.dialog_alert_title)
									.setMessage(R.string.download_list_error)
									.setPositiveButton(android.R.string.ok, null);
								dlg.show();
							}
							if (tmp.exists()) {
								tmp.delete();
							}
						} catch (UnknownHostException e) {
							runOnUiThread(new Runnable() {
								public void run() {
									final AlertDialog.Builder dlg = new AlertDialog.Builder(self)
										.setIcon(android.R.drawable.ic_dialog_alert)
										.setTitle(android.R.string.dialog_alert_title)
										.setMessage(R.string.download_list_error)
										.setPositiveButton(android.R.string.ok, null);
									dlg.show();
								};
							});
							Log.d("POIMan", "UnknownHostException -> " + e.toString());
						} catch (Exception e) {
							String exc = "";
							for (StackTraceElement st : e.getStackTrace()) {
								exc += st.toString() + "\n";
							}
					    	Log.e("POIMan", exc);
						} finally {
							runOnUiThread(new Runnable() {
								public void run() {
									try{progress.dismiss();} catch(Exception ex) {}
									try{progressDatabase.dismiss();} catch(Exception ex) {}
									POIActivity.this.onCreate(null);
								};
							});
						}
					}
				}.start();
			}
		});
		dialog.show();
	}

	/**
	 * Update all POIs
	 */
	void updateAllPOIs() {
		dataHelper.open();
		final ArrayList<POI> pois = dataHelper.getSelectedPOIs();
		dataHelper.close();
		POIUtil.updatePOIs(this, pois);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.main_menu, menu);
	    return true;
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_download_pois:
			downloadPOIListFile();
			return true;
		case R.id.menu_update_pois:
			updateAllPOIs();
			return true;
		case R.id.menu_preferences:
			startActivityForResult(new Intent(this, ar.com.alfersoft.poiman.PreferencesActivity.class), RC_PREFERENCES);
			return true;
		//case R.id.menu_quit:
		//	finish();
		//	System.exit(0);
		//	return true;
		}
		return super.onMenuItemSelected(featureId, item);
	}

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // find root directory
        boolean sygicFound = false, auraFound = false;
        final String rootDirs[] = { "/sdcard", "/sdcard/external_sd", "/mnt/sdcard/external_sd", "/mnt/sdcard", Environment.getExternalStorageDirectory().toString() };
        File sygicDir = new File("/sdcard" + POIUtil.DIR_SYGIC);
        for (String root : rootDirs) {
            sygicDir = new File(root + POIUtil.DIR_SYGIC);
            if (sygicDir.exists() && sygicDir.isDirectory()) {
            	sygicFound = true;
            	POIUtil.setRootDir(root);
            	break;
            }
            final File auraDir = new File(root + "/Aura" + POIUtil.DIR_SYGIC);
            if (auraDir.exists() && auraDir.isDirectory()) {
            	auraFound = true;
            }
        }
        if (!sygicFound) {
			final AlertDialog.Builder dlg = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(android.R.string.dialog_alert_title)
				.setMessage((!auraFound) ? R.string.sygic_dir_not_found : R.string.aura_not_supported)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						finish();
						System.exit(0);
					}
				});
			dlg.show();
        } else {
        	if (getSharedPreferences("ar.com.alfersoft.poiman_preferences", 0).getString("maps_dir", "").equals("")) {
    			final CharSequence[] dirs = POIUtil.listSubdirs(sygicDir);
    			final POIActivity self = this;
        		AlertDialog.Builder builder = new AlertDialog.Builder(this);
        		builder.setTitle(R.string.map_dir_title);
        		builder.setItems(dirs, new DialogInterface.OnClickListener() {
        		    public void onClick(DialogInterface dialog, int item) {
        		    	final Editor editor = self.getSharedPreferences("ar.com.alfersoft.poiman_preferences", 0).edit();
        		    	editor.putString("maps_dir", dirs[item].toString());
        		    	editor.commit();
        		    	self.onCreate(null);
        		    }
        		});
        		builder.setOnCancelListener(new OnCancelListener() {
					public void onCancel(DialogInterface dialog) {
						finish();
						System.exit(0);
					}
				});
        		builder.create().show();
        	} else {
		        dataHelper = new POIDataHelper(this);
		        dataHelper.init();
		    	(new File(POIUtil.getRootDir() + POIUtil.DIR_POIMAN)).mkdir();
		    	final File file = new File(POIS_FILE);
		    	if (!file.exists()) {
		            this.setContentView(R.layout.main_no_pois);
		            Button btn = (Button) this.findViewById(R.id.btn_download_pois);
		            btn.setOnClickListener(new OnClickListener() {
						public void onClick(View arg0) {
							downloadPOIListFile();
						}
					});
		            return;
		    	} else {
		            dataHelper.open();
		    		maps = dataHelper.getMaps(false);
		    		dataHelper.close();
		    	}
		    	populateList(file);
        	}
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	if (requestCode == RC_CHOOSE_GROUPS || (requestCode == RC_PREFERENCES && resultCode == RESULT_FIRST_USER)) {
    		POIActivity.this.onCreate(null);
    	}
    }
}