package ar.com.alfersoft.poiman;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;

public class POIProgressDialog extends ProgressDialog {
	private boolean progressCanceled = false;

	public POIProgressDialog(Context context) {
		super(context);
		setCancelable(true);
		setOnCancelListener(new OnCancelListener() {
			public void onCancel(DialogInterface arg0) {
				progressCanceled = true;
			}
		});
	}

	public boolean isCanceled() {
		return progressCanceled;
	}
}
