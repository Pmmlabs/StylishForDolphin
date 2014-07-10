/*******************************************************************************
 *
 *    Copyright (c) Alexey Stepanov
 *
 *    Stylish
 *
 *    StyleManager
 *
 ******************************************************************************/
package ru.pmmlabs.stylish;

import java.util.Map;

import ru.pmmlabs.stylish.StylesBaseContract.RulesTable;
import ru.pmmlabs.stylish.StylesBaseContract.StylesTable;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
//experimental//import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

public class StyleManager extends Activity {

	protected String LOG_TAG = "StyleManager";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTheme(android.R.style.Theme_Holo);
		setContentView(R.layout.manager);
		setTitle(getString(R.string.managestyles));
		final LinearLayout general = (LinearLayout) findViewById(R.id.general);

		final StylishAddonService StylishAddon = StylishAddonService.getInstance();
		Map<String, Integer> rTypes = StylishAddon.rulesTypes;
		SQLiteDatabase db = StylishAddon.sbHelper.getReadableDatabase();

		Cursor cursor = db.query(StylesTable.TABLE_NAME + " s JOIN " + RulesTable.TABLE_NAME + " r ON s." + StylesTable._ID
				+ "=r." + RulesTable.COLUMN_NAME_STYLE_ID,
				new String[] { StylesTable.COLUMN_NAME_NAME, StylesTable.COLUMN_NAME_ENABLED, StylesTable.COLUMN_NAME_URL, StylesTable.COLUMN_NAME_UPDATE,
				"group_concat(CASE "+RulesTable.COLUMN_NAME_RULE_TYPE
				   +" WHEN "+rTypes.get("domain") +		" THEN "+RulesTable.COLUMN_NAME_RULE_TEXT
				   +" WHEN "+rTypes.get("url") +		" THEN '\"' || "+RulesTable.COLUMN_NAME_RULE_TEXT+" || '\"'"
				   +" WHEN "+rTypes.get("url-prefix") +	" THEN '\"' || "+RulesTable.COLUMN_NAME_RULE_TEXT+" || '*\"'"
				   +" WHEN "+rTypes.get("regexp") +		" THEN '(' || "+RulesTable.COLUMN_NAME_RULE_TEXT+" || ')'"
				   +" ELSE 'something with rule type ="+RulesTable.COLUMN_NAME_RULE_TYPE+"' END, ', ') AS rules"
				},
				null, null, StylesTable.COLUMN_NAME_NAME, null, StylesTable.COLUMN_NAME_ENABLED + " DESC");
		if (cursor.moveToFirst()) {
			do {
				final String homepage = cursor.getString(cursor.getColumnIndexOrThrow(StylesTable.COLUMN_NAME_URL));
				final String updateURL = cursor.getString(cursor.getColumnIndexOrThrow(StylesTable.COLUMN_NAME_UPDATE));
				final String name = cursor.getString(cursor.getColumnIndexOrThrow(StylesTable.COLUMN_NAME_NAME));
				final Boolean enabled = cursor.getInt(cursor.getColumnIndexOrThrow(StylesTable.COLUMN_NAME_ENABLED)) == 1;
				
				final TextView title = new TextView(this);
				title.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
				title.setText(name);
				title.setTextAppearance(this, android.R.style.TextAppearance_Large);
				if (!enabled) title.setTextColor(android.graphics.Color.GRAY);

				TextView description = new TextView(this);
				LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				descriptionParams.weight = 1;
				description.setLayoutParams(descriptionParams);
				String affects = cursor.getString(cursor.getColumnIndexOrThrow("rules"));
				description.setText(getString(R.string.affects)+": "+(affects.equals("\"*\"") ? getString(R.string.alltheweb) : affects));
				
				LinearLayout titleAndDesc = new LinearLayout(this);
				LinearLayout.LayoutParams titleAndDescParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT);
				//experimental//LinearLayout.LayoutParams titleAndDescParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				titleAndDesc.setOrientation(LinearLayout.VERTICAL);
				titleAndDesc.setLayoutParams(titleAndDescParams);			
				titleAndDescParams.weight = 1;				
				titleAndDesc.addView(title);
				titleAndDesc.addView(description);
				
				titleAndDesc.setOnClickListener(new View.OnClickListener() { // go to style homepage
					
					@Override
					public void onClick(View v) {
						if (homepage != "")
						try {
							StylishAddon.browser.tabs.create(homepage, false);
						} catch (RemoteException e) {
							Log.e(LOG_TAG, e.toString());
						} catch (IllegalArgumentException e) {
							Log.e(LOG_TAG, e.toString());
						}
						else
							Toast.makeText(getApplicationContext(), "This style has no homepage..", Toast.LENGTH_SHORT).show();
						finish();
					}
				});

				LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				buttonParams.gravity = Gravity.END;
				
				Button btnEdit = new Button(this);
				btnEdit.setLayoutParams(buttonParams);
				btnEdit.setText(getString(R.string.edit));
				btnEdit.setOnClickListener(new OnClickListener() { // edit style

					@Override
					public void onClick(View v) {
						Intent intent = new Intent(StyleManager.this, StyleEditor.class);
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						intent.putExtra(StyleEditor.EXTRA_STYLE_NAME, name);
						intent.putExtra(StyleEditor.EXTRA_STYLE_HOMEPAGE, homepage);
						intent.putExtra(StyleEditor.EXTRA_STYLE_UPDATE, updateURL);
						startActivity (intent);			
					}					
				});
				
				Button btnUpdate = new Button(this);
				btnUpdate.setLayoutParams(buttonParams);
				btnUpdate.setText(getString(R.string.update));
				btnUpdate.setOnClickListener(new OnClickListener() { // update style

					@Override
					public void onClick(View v) {
						if (updateURL != "") {
							StylishAddon.installStyleFromUrl(homepage, name, updateURL);
							title.setTextColor(android.graphics.Color.GREEN);
						}
					}					
				});
				
				ToggleButton btnOnOff = new ToggleButton(this);
				btnOnOff.setLayoutParams(buttonParams);
				btnOnOff.setTextOff(getString(R.string.enable));
				btnOnOff.setTextOn(getString(R.string.disable));
				btnOnOff.setChecked(enabled);
				btnOnOff.setOnClickListener(new OnClickListener() { // enable or disable style

					@Override
					public void onClick(View v) {
						try {
							StylishAddon.setStyleState(name,((ToggleButton)v).isChecked());
							title.setTextColor(((ToggleButton)v).isChecked() ? android.graphics.Color.WHITE : android.graphics.Color.GRAY);
						} catch (RemoteException e) {
							Log.e(LOG_TAG, e.toString());
						}						
					}					
				});

				Button btnDelete = new Button(this);
				btnDelete.setLayoutParams(buttonParams);
				btnDelete.setText(getString(R.string.delete));
				

				final LinearLayout L = new LinearLayout(this);
				//experimental//final RelativeLayout L = new RelativeLayout(this);
				L.setOrientation(LinearLayout.HORIZONTAL);
				L.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
				L.addView(titleAndDesc);
				L.addView(btnEdit);
				L.addView(btnUpdate);
				L.addView(btnOnOff);
				L.addView(btnDelete);
				general.addView(L);
				btnDelete.setOnClickListener(new OnClickListener() { //delete style
					
					@Override
					public void onClick(View v) {
						try {
							StylishAddon.setStyleState(name,false);
							StylishAddon.deleteStyle(name);
						} catch (RemoteException e) {
							Log.e(LOG_TAG , e.toString());
						}
						general.removeView(L);
					}
				});
			} while (cursor.moveToNext());
		}
		cursor.close();
		db.close();
	}
}
