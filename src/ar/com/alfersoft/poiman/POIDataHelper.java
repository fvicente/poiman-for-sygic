package ar.com.alfersoft.poiman;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

public class POIDataHelper {

	private static final int DATABASE_VERSION		= 1;
	private static final String DATABASE_NAME		= "poiman.db";
	private static final String TABLE_NAME_MAPS		= "maps";
	private static final String TABLE_NAME_GROUPS	= "groups";
	private static final String TABLE_NAME_POIS		= "pois";

	private static SQLiteOpenHelper helper;
	private static SQLiteDatabase db;
	private SQLiteStatement insertMapStmt;
	private SQLiteStatement insertGroupStmt;
	private SQLiteStatement insertPOIStmt;
	private SQLiteStatement updatePOIStmt;
	private SQLiteStatement updatePOISelectedStmt;
	
	private static final String INSERT_MAP = "INSERT INTO " 
		+ TABLE_NAME_MAPS + "(name) VALUES (?)";
	private static final String INSERT_GROUP = "INSERT INTO " 
		+ TABLE_NAME_GROUPS + "(map_id, name) VALUES (?, ?)";
	private static final String INSERT_POI = "INSERT INTO " 
		+ TABLE_NAME_POIS + "(map_id, group_id, description, note, url, image, selected) VALUES (?, ?, ?, ?, ?, ?, ?)";
	private static final String UPDATE_POI = "UPDATE " 
		+ TABLE_NAME_POIS + " SET map_id=?, group_id=?, description=?, note=?, url=?, image=?, selected=? WHERE id=?";
	private static final String UPDATE_POI_SELECTED = "UPDATE " 
		+ TABLE_NAME_POIS + " SET selected=? WHERE id=?";

	public POIDataHelper(Context context) {

		helper = new SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

			@Override
			public void onCreate(SQLiteDatabase db) {
				// Delete POIs XML file
				POIUtil.tryToDelete(POIActivity.POIS_FILE);
				// Create tables
				db.execSQL("CREATE TABLE " + TABLE_NAME_MAPS + " (id INTEGER PRIMARY KEY, name TEXT)");
				db.execSQL("CREATE TABLE " + TABLE_NAME_GROUPS + " (id INTEGER PRIMARY KEY, map_id INTEGER, name TEXT)");
				db.execSQL("CREATE TABLE " + TABLE_NAME_POIS + " (id INTEGER PRIMARY KEY, map_id INTEGER, group_id INTEGER, description TEXT, note TEXT, url TEXT, image TEXT, selected INTEGER)");
			}

			@Override
			public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
				Log.w("POIDataHelper", "Upgrading database");
			}
		};
	}

    public void init() {
        db = helper.getWritableDatabase();
        helper.close();
    }

    // opens the database
    public POIDataHelper open() {
        db = helper.getWritableDatabase();
		this.insertMapStmt = db.compileStatement(INSERT_MAP);
		this.insertGroupStmt = db.compileStatement(INSERT_GROUP);
		this.insertPOIStmt = db.compileStatement(INSERT_POI);
		this.updatePOIStmt = db.compileStatement(UPDATE_POI);
		this.updatePOISelectedStmt = db.compileStatement(UPDATE_POI_SELECTED);
        return this;
    }

    // closes the database
    public void close() {
    	helper.close();
    }

    public long insertMap(POIMap map) {
		this.insertMapStmt.bindString(1, map.name);
		return this.insertMapStmt.executeInsert();
	}

	public long insertGroup(long mapId, POIGroup group) {
		this.insertGroupStmt.bindLong(1, mapId);
		this.insertGroupStmt.bindString(2, group.name);
		return this.insertGroupStmt.executeInsert();
	}

	public long insertPOI(POI poi) {
		this.insertPOIStmt.bindLong(1, poi.mapId);
		this.insertPOIStmt.bindLong(2, poi.groupId);
		this.insertPOIStmt.bindString(3, poi.description);
		this.insertPOIStmt.bindString(4, poi.note);
		this.insertPOIStmt.bindString(5, poi.url.toString());
		this.insertPOIStmt.bindString(6, poi.image.toString());
		this.insertPOIStmt.bindLong(7, poi.selected);
		return this.insertPOIStmt.executeInsert();
	}

	public void updatePOI(POI poi) {
		this.updatePOIStmt.bindLong(1, poi.mapId);
		this.updatePOIStmt.bindLong(2, poi.groupId);
		this.updatePOIStmt.bindString(3, poi.description);
		this.updatePOIStmt.bindString(4, poi.note);
		this.updatePOIStmt.bindString(5, poi.url.toString());
		this.updatePOIStmt.bindString(6, poi.image.toString());
		this.updatePOIStmt.bindLong(7, poi.selected);
		this.updatePOIStmt.bindLong(8, poi.id);
		this.updatePOIStmt.execute();
	}

	public void updatePOISelected(POI poi) {
		this.updatePOISelectedStmt.bindLong(1, poi.selected);
		this.updatePOISelectedStmt.bindLong(2, poi.id);
		this.updatePOISelectedStmt.execute();
	}

	public void resetPOISelected(Activity activity, String dir) {
		db.execSQL("UPDATE " + TABLE_NAME_POIS + " SET selected = 0 WHERE selected != 0");
		// List of UPI files already installed
		final File upis[] = POIUtil.listFilesAsArray(new File(POIUtil.getRootDir() + POIUtil.DIR_SYGIC + "/" + dir), new FilenameFilter() {
			public boolean accept(File dir, String filename) {
				return filename.toLowerCase().endsWith(".upi");
			}
		}, true);
		if ((upis != null) && (upis.length > 0)) {
			Log.d("POIMan", "DIR -> " + dir);
			for (File f : upis) {
				final String name = f.getName();
				final int len = name.length();
				if (len > 4) {
					final ContentValues values = new ContentValues();
					values.put("selected", 1);
					final int affectedRows = db.update(TABLE_NAME_POIS, values, "url LIKE '%/" + name.substring(0, len - 4).replaceAll("_", ":_").replaceAll("%", ":%") + ".ov2%' ESCAPE ':'", new String[] {});
					if (affectedRows == 0) {
						// maybe the name is defined by the description, let's try
						final int rows = db.update(TABLE_NAME_POIS, values, "description = ?", new String[] { name.substring(0, len - 4).replaceAll(" ", "_") });
					}
				}
			}
		}
	}

	public void deleteAll() {
		db.delete(TABLE_NAME_MAPS, null, null);
		db.delete(TABLE_NAME_GROUPS, null, null);
		db.delete(TABLE_NAME_POIS, null, null);
	}

	public HashMap<String, POIMap> getMaps(boolean tree) {
		final HashMap<String, POIMap> maps = new HashMap<String, POIMap>();
		final Cursor cursorMaps = db.query(TABLE_NAME_MAPS + " M",
				new String[] { "M.id AS id", "M.name AS name", "(SELECT count() FROM pois P WHERE P.selected = 1 AND P.map_id = M.id) AS selected_count" },
				null, null, null, null, null);
		if (cursorMaps.moveToFirst()) {
			do {
				// Create map
				final String mapName = cursorMaps.getString(1);
				final long mapId = cursorMaps.getLong(0);
				final POIMap map = new POIMap(mapId, mapName, cursorMaps.getLong(2) > 0);
				maps.put(mapName, map);
				if (tree) {
					// Get groups for each map
					final Cursor cursorGroups = db.query(TABLE_NAME_GROUPS, new String[] { "id", "name" }, "map_id = " + mapId, null, null, null, null);
					if (cursorGroups.moveToFirst()) {
						do {
							// Create group
							final String groupName = cursorGroups.getString(1);
							final long groupId = cursorGroups.getLong(0);
							final POIGroup group = new POIGroup(groupId, groupName, false);
							map.groups.put(groupName, group);
							// Get POIs for each group
							final Cursor cursorPOIs = db.query(TABLE_NAME_POIS, new String[] { "id", "description", "note", "url", "image", "selected" }, "group_id = " + groupId, null, null, null, null);
							if (cursorPOIs.moveToFirst()) {
								do {
									// Create POI
									final long poiId = cursorPOIs.getLong(0);
									final String poiDesc = cursorPOIs.getString(1);
									final String poiNote = cursorPOIs.getString(2);
									try {
										final URL poiUrl = new URL(cursorPOIs.getString(3));
										final URL poiImage = new URL(cursorPOIs.getString(4));
										group.pois.add(new POI(poiId, mapId, groupId, poiDesc, poiNote, poiUrl, poiImage, cursorPOIs.getInt(5)));
									} catch (MalformedURLException e) {
									}
								} while (cursorPOIs.moveToNext());
								if (cursorPOIs != null && !cursorPOIs.isClosed()) {
									cursorPOIs.close();
								}
							}						
						} while (cursorGroups.moveToNext());
						if (cursorGroups != null && !cursorGroups.isClosed()) {
							cursorGroups.close();
						}
					}
				}
			} while (cursorMaps.moveToNext());
		}
		if (cursorMaps != null && !cursorMaps.isClosed()) {
			cursorMaps.close();
		}
		return maps;
	}

	public HashMap<String, POIGroup> getGroups(long mapId) {
		final HashMap<String, POIGroup> groups = new HashMap<String, POIGroup>();
		final Cursor cursorGroups = db.query(TABLE_NAME_GROUPS + " G", new String[] {
				"G.id AS id", "G.name AS name",
				"(SELECT count() FROM pois P WHERE P.selected = 1 AND P.group_id = G.id) AS selected_count" },
				"G.map_id = " + mapId, null, null, null, null);
		if (cursorGroups.moveToFirst()) {
			do {
				// Create group
				final String groupName = cursorGroups.getString(1);
				final long groupId = cursorGroups.getLong(0);
				final POIGroup group = new POIGroup(groupId, groupName, cursorGroups.getLong(2) > 0);
				groups.put(groupName, group);
			} while (cursorGroups.moveToNext());
		}
		if (cursorGroups != null && !cursorGroups.isClosed()) {
			cursorGroups.close();
		}
		return groups;
	}

	public ArrayList<POI> getPOIs(long groupId) {
		final ArrayList<POI> pois = new ArrayList<POI>();
		final Cursor cursorPOIs = db.query(TABLE_NAME_POIS, new String[] { "id", "map_id", "description", "note", "url", "image", "selected" }, "group_id = " + groupId, null, null, null, null);
		if (cursorPOIs.moveToFirst()) {
			do {
				// Create POI
				final long poiId = cursorPOIs.getLong(0);
				final long mapId = cursorPOIs.getLong(1);
				final String poiDesc = cursorPOIs.getString(2);
				final String poiNote = cursorPOIs.getString(3);
				try {
					final URL poiUrl = new URL(cursorPOIs.getString(4));
					final URL poiImage = new URL(cursorPOIs.getString(5));
					pois.add(new POI(poiId, mapId, groupId, poiDesc, poiNote, poiUrl, poiImage, cursorPOIs.getInt(6)));
				} catch (MalformedURLException e) {
				}
			} while (cursorPOIs.moveToNext());
		}
		if (cursorPOIs != null && !cursorPOIs.isClosed()) {
			cursorPOIs.close();
		}
		return pois;
	}

	public ArrayList<POI> getSelectedPOIs() {
		return (getSelectedPOIs(-1));
	}

	public ArrayList<POI> getSelectedPOIs(long map) {
		final ArrayList<POI> pois = new ArrayList<POI>();
		final Cursor cursorPOIs = db.query(TABLE_NAME_POIS, new String[] { "id", "map_id", "group_id", "description", "note", "url", "image", "selected" }, (map == -1) ? "selected = 1" : ("map_id = " + map + " AND selected = 1"), null, null, null, null);
		if (cursorPOIs.moveToFirst()) {
			do {
				// Create POI
				final long poiId = cursorPOIs.getLong(0);
				final long mapId = cursorPOIs.getLong(1);
				final long groupId = cursorPOIs.getLong(2);
				final String poiDesc = cursorPOIs.getString(3);
				final String poiNote = cursorPOIs.getString(4);
				try {
					final URL poiUrl = new URL(cursorPOIs.getString(5));
					final URL poiImage = new URL(cursorPOIs.getString(6));
					pois.add(new POI(poiId, mapId, groupId, poiDesc, poiNote, poiUrl, poiImage, cursorPOIs.getInt(7)));
				} catch (MalformedURLException e) {
				}
			} while (cursorPOIs.moveToNext());
		}
		if (cursorPOIs != null && !cursorPOIs.isClosed()) {
			cursorPOIs.close();
		}
		return pois;
	}
}
