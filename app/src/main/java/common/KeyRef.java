package common;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;

import eu.trackify.net.R;

public class KeyRef {
	public String key;
	public String status;
	public String user;
	public String updated;
	public String response_txt;

	public String prev_status_id;
	public String new_status_id;
	public String shipment_id; // shipment_id

	public static void PlayBeep() {
		Uri url = Uri.parse("android.resource://" + App.Object.getPackageName() + "/" + R.raw.beep);
		MediaPlayer p = MediaPlayer.create(App.Object, url);
		p.setOnCompletionListener(new OnCompletionListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				mp.release();
			}
		});
		p.start();
	}
}
