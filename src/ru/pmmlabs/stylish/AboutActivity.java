/*******************************************************************************
 *
 *    Copyright (c) Alexey Stepanov
 *
 *    Stylish
 *
 *    AboutActivity
 *
 ******************************************************************************/
package ru.pmmlabs.stylish;

import android.content.Intent;

public class AboutActivity extends com.dolphin.browser.addons.AboutActivity {

	@Override
	protected void onStartCustomAboutActivity() {
		startActivity(new Intent(this, CustomAboutActivity.class));
	}

}
