package ar.com.alfersoft.poiman;

public class POIRecord {
	int		type, x1, y1, x2, y2, lon, lat, id, nextId;
	int		len = 0;	// record size
	byte	desc[];

	public POIRecord(int lon, int lat, byte desc[]) {
		this.type = 2;
		this.lon = lon;
		this.lat = lat;
		this.desc = desc;
		this.len = 1 + 4 + 8 + (desc.length * 2);
	}

	public POIRecord(int id, int nextId, int x1, int y1, int x2, int y2) {
		this.type = 1;
		this.id = id;
		this.nextId = nextId;
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
	}
}
