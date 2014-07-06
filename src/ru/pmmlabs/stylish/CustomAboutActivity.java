/*******************************************************************************
 *
 *    Copyright (c) Alexey Stepanov
 *
 *    Stylish
 *
 *    CustomAboutActivity
 *
 ******************************************************************************/
package ru.pmmlabs.stylish;

import ru.pmmlabs.stylish.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

public class CustomAboutActivity extends Activity {
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		findViewById(R.id.btnClose).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				finish();
			}
		});
	
	}
}
