package de.karlsve.autolightsoff;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class LightSwitcherActivity extends Activity {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.startService(new Intent(this.getApplicationContext(), LightSwitcher.class));
		this.finish();
	}
	
}
