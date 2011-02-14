package ar.com.alfersoft.poiman;

import java.net.URL;
import java.util.ArrayList;

public class POIGroup implements Comparable<POIGroup> {
	long			id;
	String			name;
	ArrayList<POI>	pois = new ArrayList<POI>();
	boolean			hasPOISelected = false;

	public POIGroup(String name) {
		this.name = name;
	}

	public POIGroup(long id, String name, boolean hasPOISelected) {
		this.id = id;
		this.name = name;
		this.hasPOISelected = hasPOISelected;
	}
	
	public POI findPOIByURL(URL url) {
		for (POI poiObj : pois) {
			if(poiObj.url.toString().equalsIgnoreCase(url.toString())) {
				return(poiObj);
			}
		}
		return null;
	}

	public int compareTo(POIGroup poi) {
		return this.name.compareTo(poi.name);
	}
}
