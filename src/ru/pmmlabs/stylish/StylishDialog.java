/*******************************************************************************
 *
 *    Copyright (c) Alexey Stepanov
 *
 *    Stylish
 *
 *    StylishDialog
 *
 ******************************************************************************/
package ru.pmmlabs.stylish;

import java.util.HashMap;
import com.dolphin.browser.addons.Browser;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.Toast;

public class StylishDialog extends Activity {
	protected static final String LOG_TAG = "StylishDialog";
	private HashMap<String, Boolean> currentStyles = null;
	private Browser browser;
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void settheme() {
		setTheme(android.R.style.Theme_Holo_Dialog);
	}
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		final StylishAddonService StylishAddon = StylishAddonService.getInstance();
		if (StylishAddon == null || StylishAddon.currentStyles == null) {
			Toast.makeText(getApplicationContext(), "Dolphin Browser is not connected. Please, restart browser.", Toast.LENGTH_SHORT).show();
			return;
		}
		browser = StylishAddon.browser;
		try {
			currentStyles = StylishAddon.currentStyles.get(browser.tabs.getCurrent().getWebView().getId());
		} catch (RemoteException e) {
			Log.e(LOG_TAG , e.toString());
		}
		settheme(); // for new OS set Holo theme
		setContentView(R.layout.complex_dialog);
		LinearLayout linear = (LinearLayout) findViewById(R.id.linear);
		((CompoundButton)findViewById(R.id.btnDisable)).setChecked(StylishAddon.AddonEnabled);
		
		// for "create style" function
		String curUrl = null;
		try {
			curUrl = browser.tabs.getCurrent().getWebView().getUrl();
		} catch (RemoteException e) {
			Log.e(LOG_TAG , e.toString());
		}
		final String curDomain = StylishAddon.href2domain(curUrl);
		((Button)findViewById(R.id.btnCreate)).setText(((Button)findViewById(R.id.btnCreate)).getText()+" "+curDomain);
		//
		
		if (currentStyles != null) 
			for (final String style : currentStyles.keySet())
			{
				CheckBox cbx = new CheckBox(this);
				cbx.setText(style);
				cbx.setChecked(currentStyles.get(style));
				cbx.setOnCheckedChangeListener(new OnCheckedChangeListener() {
					
					@Override
					public void onCheckedChanged(CompoundButton arg0, boolean isChecked) { // click on style checkbox
						try {
							StylishAddon.setStyleState(style, isChecked);
						} catch (RemoteException e) {
							Log.e(LOG_TAG , e.toString());
						}
						finish();
					}
				});
				linear.addView(cbx);
			}
		
		findViewById(R.id.btnManage).setOnClickListener(new View.OnClickListener() {// Click on "Manage styles"

			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(StylishDialog.this, StyleManager.class);
		        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		        startActivity (intent);
		        finish();
			}
		});
		
		findViewById(R.id.btnDisable).setOnClickListener(new View.OnClickListener() {// Click on "Disable all"

			@Override
			public void onClick(View arg0) {
				try {
					StylishAddon.switchStateAllStyles();
				} catch (Exception e) {
					Log.e(LOG_TAG, e.toString());
				}
				finish();
			}
		});
		
		findViewById(R.id.btnFind).setOnClickListener(new View.OnClickListener() {// Click on "Find styles for this site"

			@Override
			public void onClick(View arg0) {
				try {
					String url = browser.tabs.getCurrent().getWebView().getUrl();
					browser.tabs.create("https://userstyles.org/styles/search/"+(url != null ? url : "about:blank"), false);
				} catch (Exception e) {
					Log.e(LOG_TAG, e.toString());
				}
				finish();
			}
		});
		
		findViewById(R.id.btnCreate).setOnClickListener(new View.OnClickListener() { // Click on "Create style"
			
			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(StylishDialog.this, StyleEditor.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.putExtra(StyleEditor.EXTRA_STYLE_NAME, curDomain);
				intent.putExtra(StyleEditor.EXTRA_STYLE_HOMEPAGE, "");
				intent.putExtra(StyleEditor.EXTRA_STYLE_UPDATE, "");
				startActivity (intent);					
			}
		});
	}

}
