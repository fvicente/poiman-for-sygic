package ar.com.alfersoft.poiman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class POIGroupList extends Activity {
	static private final int RC_CHOOSE_POIS = 0x1000;
	POIDataHelper dataHelper = null;
	HashMap<String, POIGroup> groups = null;
	private long mapId = 0;

	public void populateList() {
    	try {
	        this.setContentView(R.layout.list);
	        final Collection<POIGroup> values = groups.values();
	    	final POIGroup array[] = new POIGroup[values.size()];
	    	values.toArray(array);
	    	Arrays.sort(array);
	    	final TextView title = (TextView) this.findViewById(R.id.list_title);
	    	title.setText((array.length > 0) ? R.string.groups : R.string.no_groups);
	    	final ListView list = (ListView) this.findViewById(R.id.list_view);
	    	final LayoutInflater layoutInflater = getLayoutInflater();
	    	final Activity self = this;
	    	final OnClickListener onclick = new OnClickListener() {
				public void onClick(View view) {
					final POIGroup group = (POIGroup) view.getTag();
					final Bundle bundle = new Bundle();
					bundle.putString("groupName", group.name);
					bundle.putLong("groupId", group.id);
					final Intent newIntent = new Intent(self, ar.com.alfersoft.poiman.POIChooseActivity.class);
					newIntent.putExtras(bundle);
					startActivityForResult(newIntent, RC_CHOOSE_POIS);
				}
			};
	    	list.setAdapter(new ArrayAdapter<POIGroup>(this, R.layout.list_item, array){
	    		@Override
	    		public View getView(int position, View convertView, ViewGroup parent) {
	    			if (convertView == null) {
	    				convertView = layoutInflater.inflate(R.layout.list_item, parent, false);
	    			}
	    			final POIGroup poiGroup = array[position];
	    			final ImageView img = (ImageView) convertView.findViewById(R.id.maplist_selected);
	    			img.setImageResource(poiGroup.hasPOISelected ? R.drawable.some_selected_on : R.drawable.some_selected_off);
	    			final TextView t = (TextView) convertView.findViewById(R.id.maplist_text);
					t.setText(poiGroup.name);
	    			t.setMaxWidth(list.getWidth() - 55); // 20 from left and 35 from right
	    			convertView.setOnClickListener(onclick);
	    			convertView.setTag(poiGroup);
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
	    inflater.inflate(R.menu.group_menu, menu);
	    return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_update_pois_map:
			dataHelper.open();
			final ArrayList<POI> poisList = dataHelper.getSelectedPOIs(mapId);
			dataHelper.close();
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
        mapId = this.getIntent().getExtras().getLong("mapId");
		groups = dataHelper.getGroups(mapId);
		dataHelper.close();
        populateList();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	if (requestCode == RC_CHOOSE_POIS) {
    		POIGroupList.this.onCreate(null);
    	}
    }
}
