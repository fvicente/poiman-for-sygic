package ar.com.alfersoft.poiman;

import java.util.HashMap;

public class POIMap implements Comparable<POIMap>{
	long	id;
	String	name;
	HashMap<String, POIGroup> groups = new HashMap<String, POIGroup>();
	boolean hasGroupSelected = false;

	public POIMap(String name) {
		this.name = name;
	}

	public POIMap(long id, String name, boolean hasGroupSelected) {
		this.id = id;
		this.name = name;
		this.hasGroupSelected = hasGroupSelected;
	}

	public int compareTo(POIMap other) {
		return name.compareTo(other.name);
	}
}
