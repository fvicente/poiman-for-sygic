package ar.com.alfersoft.poiman;

import java.io.File;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;

public class PreferencesActivity extends PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//Log.d("POIMan", "Shared preferences name: " + getPreferenceManager().getSharedPreferencesName());
		addPreferencesFromResource(R.xml.preferences);
		final ListPreference list = (ListPreference)getPreferenceManager().findPreference("maps_dir");
		if (list != null) {
			final Resources res = this.getResources();
			final CharSequence[] dirs = POIUtil.listSubdirs(new File(POIUtil.DIR_SYGIC_ROOT));
			list.setEntries(dirs);
			list.setEntryValues(dirs);
			list.setDefaultValue(getSharedPreferences("ar.com.alfersoft.poiman_preferences", 0).getString("maps_dir", ""));
			final Activity self = this;
			list.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					final ProgressDialog progressDatabase = new ProgressDialog(self);
					progressDatabase.setIndeterminate(true);
					progressDatabase.setCancelable(false);
					progressDatabase.setTitle(res.getString(R.string.updating_db));
					progressDatabase.setMessage(res.getString(R.string.please_wait));
					progressDatabase.setProgressStyle(ProgressDialog.STYLE_SPINNER);
					progressDatabase.show();
					final POIDataHelper dataHelper = new POIDataHelper(self);
					dataHelper.open();
					dataHelper.resetPOISelected(self, (String)newValue);
					dataHelper.close();
					try{progressDatabase.dismiss();} catch(Exception ex) {}
					self.setResult(RESULT_FIRST_USER);
					return true;
				}
			});
		}
	}

    static void show(Context context) {
		final Intent intent = new Intent(context, PreferencesActivity.class);
		context.startActivity(intent);
	}
}