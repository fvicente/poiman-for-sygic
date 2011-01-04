package ar.com.alfersoft.poiman;

import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

public class POIChooseActivity extends Activity {
	POIDataHelper dataHelper = null;
	ArrayList<POI> pois = null;
	boolean allSelected = false;

	private void applyChanges(boolean exit) {
		final Resources	res = getResources();
		int				toBeAdded = 0, toBeRemoved = 0;
		// Check for changes
		for (POI poi : pois) {
			if (poi.selected != poi.prevState) {
				if (poi.selected == 0) {
					toBeRemoved++;
				} else {
					toBeAdded++;
				}
			}
		}
		final boolean exitAfterApply = exit;
		if (toBeAdded == 0 && toBeRemoved == 0) {
			// Nothing changed
			if (exitAfterApply) {
				POIChooseActivity.this.finish();
			}
			return;
		}
		final int toAdd = toBeAdded;
		final int toRemove = toBeRemoved;
		final int totalTasks = toAdd + toRemove;
		// Ask the user confirmation
		String text = "";
		if (toAdd > 0) {
			text += res.getQuantityString(R.plurals.to_add, toAdd, toAdd);
		}
		if (toRemove > 0) {
			if (text != "") {
				text += "\n";
			}
			text += res.getQuantityString(R.plurals.to_remove, toRemove, toRemove);
		}
		text += "\n\n" + res.getString(R.string.confirm_apply);
		final POIChooseActivity self = this;
		final AlertDialog.Builder dlg = new AlertDialog.Builder(this)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle(R.string.apply_changes)
			.setMessage(text)
			.setNeutralButton(R.string.dont_apply, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					// Exit the activity?
					if (exitAfterApply) {
						POIChooseActivity.this.finish();
					}
				}
			})
			.setPositiveButton(R.string.apply, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					final POIProgressDialog progress = new POIProgressDialog(self);
					progress.setIndeterminate(false);
					progress.setTitle(res.getString(R.string.applying_changes));
					progress.setMessage(res.getString(R.string.please_wait));
					progress.setMax(totalTasks);
					progress.setProgress(0);
					progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
					progress.show();
					dataHelper.open();
					final Thread th = new Thread() {
						public void run() {
							boolean rc = true;
							int tasksDone = 0;

							// Download and convert new selected POIs
							if (toAdd > 0) {
								for (POI poi : pois) {
									if (poi.selected != poi.prevState && poi.selected != 0) {
										final String text = poi.description;
										runOnUiThread(new Runnable() {
											public void run() {
												progress.setMessage(String.format(res.getString(R.string.adding_poi), text));
											}
										});
										rc = poi.update(getSharedPreferences("ar.com.alfersoft.poiman_preferences", 0).getString("maps_dir", ""));
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
											// Update database state
											poi.prevState = poi.selected;
											dataHelper.updatePOISelected(poi);
											final int done = ++tasksDone;
											runOnUiThread(new Runnable() {
												public void run() {
													progress.setProgress(done);
												}
											});
										} else {
											break;
										}
									}
								}
							}
							if (rc && toRemove > 0) {
								// Remove unselected POIs
								for (POI poi : pois) {
									final String text = poi.description;
									runOnUiThread(new Runnable() {
										public void run() {
											progress.setMessage(String.format(res.getString(R.string.removing_poi), text));
										}
									});
									if (poi.selected != poi.prevState && poi.selected == 0) {
										rc = poi.remove(getSharedPreferences("ar.com.alfersoft.poiman_preferences", 0).getString("maps_dir", ""));
										if (progress.isCanceled()) {
											rc = false;
											break;
										}
										if (rc) {
											// Update database state
											poi.prevState = poi.selected;
											dataHelper.updatePOISelected(poi);
											final int done = ++tasksDone;
											runOnUiThread(new Runnable() {
												public void run() {
													progress.setProgress(done);
												}
											});
										} else {
											break;
										}
									}
								}
							}
							runOnUiThread(new Runnable() {
								public void run() {
									try{progress.dismiss();} catch(Exception ex) {}
									// Repaint
									if (!exitAfterApply) {
										POIChooseActivity.this.onCreate(null);
									}
								};
							});
							dataHelper.close();
							// Exit the activity?
							if (rc && exitAfterApply) {
								POIChooseActivity.this.finish();
							}
						}
					};
					try {
						th.start();
					} catch (Exception e) {
				    	Log.e("POIMan", "applyChanges() >>> " + e.toString());
						dataHelper.close();
					}
				}
			});
		if (exitAfterApply) {
			dlg.setNegativeButton(android.R.string.cancel, null);
		}
		dlg.show();
	}

	public void populateList() {
    	try {
	        this.setContentView(R.layout.list);
	    	final POI array[] = new POI[pois.size()];
	    	pois.toArray(array);
	    	Arrays.sort(array);
	    	final TextView title = (TextView) this.findViewById(R.id.list_title);
	    	title.setText((array.length > 0) ? R.string.pois : R.string.no_pois);
	    	final ListView list = (ListView) this.findViewById(R.id.list_view);
	    	final LayoutInflater layoutInflater = getLayoutInflater();
	    	final OnClickListener onclick = new OnClickListener() {
				public void onClick(View view) {
					final POI poi = (POI) view.getTag();
					final CheckBox c = (CheckBox) view;
					poi.selected = (c.isChecked() ? 1 : 0);
				}
			};
	    	list.setAdapter(new ArrayAdapter<POI>(this, R.layout.poi_item, array) {
	    		@Override
	    		public View getView(int position, View convertView, ViewGroup parent) {
	    			if (convertView == null) {
	    				convertView = layoutInflater.inflate(R.layout.poi_item, parent, false);
	    			}
	    			final POI poi = array[position];
	    			final TextView t = (TextView) convertView.findViewById(R.id.poi_text);
					t.setText(poi.description);
					final CheckBox c = (CheckBox) convertView.findViewById(R.id.poi_checkbox);
					c.setChecked(poi.selected != 0);
					c.setTag(poi);
	    			c.setOnClickListener(onclick);
	    			return convertView;
	    		}
	    	});
		} catch (Exception e) {
	    	Log.e("POIMan", "populateList() >>> " + e.toString());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.poi_menu, menu);
	    MenuItem item = menu.findItem(R.id.menu_select_pois);
	    if (allSelected) {
	    	item.setTitle(R.string.deselect_all_pois);
	    	item.setIcon(android.R.drawable.ic_menu_delete);
	    } else {
	    	item.setTitle(R.string.select_all_pois);
	    	item.setIcon(android.R.drawable.ic_menu_agenda);
	    }
	    return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_select_pois:
			for (POI poi : pois) {
				poi.selected = allSelected ? 0 : 1;
			}
			allSelected = !allSelected;
			populateList();
		    if (allSelected) {
		    	item.setTitle(R.string.deselect_all_pois);
		    	item.setIcon(android.R.drawable.ic_menu_delete);
		    } else {
		    	item.setTitle(R.string.select_all_pois);
		    	item.setIcon(android.R.drawable.ic_menu_agenda);
		    }
			return true;
		case R.id.menu_apply:
			applyChanges(false);
			return true;
		case R.id.menu_update_pois_group:
			final ArrayList<POI> poisList = new ArrayList<POI>();
			for (POI poi : pois) {
				if (poi.selected == 1) {
					poisList.add(poi);
				}
			}
			POIUtil.updatePOIs(this, poisList);
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataHelper = new POIDataHelper(this);
        dataHelper.open();
        pois = dataHelper.getPOIs(this.getIntent().getExtras().getLong("groupId"));
		dataHelper.close();
		if (savedInstanceState == null) {
			allSelected = true;
			for (POI poi : pois) {
				if (poi.selected == 0) {
					allSelected = false;
					break;
				}
			}
		} else {
	    	allSelected = savedInstanceState.getBoolean("allSelected");
			for (POI poi : pois) {
				poi.selected = (savedInstanceState.containsKey("group" + poi.id)) ? 1 : 0;
			}
		}
        populateList();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	outState.putBoolean("allSelected", allSelected);
		for (POI poi : pois) {
			if (poi.selected != 0) {
				outState.putBoolean("group" + poi.id, true);
			}
		}
    	super.onSaveInstanceState(outState);
    }

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// Handle the back button
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			applyChanges(true);
			// Say that we've consumed the event
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
}
