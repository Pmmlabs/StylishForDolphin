/*******************************************************************************
 *
 *    Copyright (c) Alexey Stepanov
 *
 *    Stylish
 *
 *    StyleEditor
 *
 ******************************************************************************/
package ru.pmmlabs.stylish;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import ru.pmmlabs.stylish.StylesBaseContract.RulesTable;
import ru.pmmlabs.stylish.StylesBaseContract.StylesTable;
import android.annotation.TargetApi;
import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

public class StyleEditor extends Activity {

	protected String LOG_TAG = "StyleEditor";
	public static final String EXTRA_STYLE_NAME = "style_name";
	public static final String EXTRA_STYLE_HOMEPAGE = "home_page";
	public static final String EXTRA_STYLE_UPDATE = "updateURL";
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void settheme() {
		setTheme(android.R.style.Theme_Holo);
	}
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final String styleName = getIntent().getStringExtra(EXTRA_STYLE_NAME);
		final String homepage = getIntent().getStringExtra(EXTRA_STYLE_HOMEPAGE);
		final String updateURL = getIntent().getStringExtra(EXTRA_STYLE_UPDATE);
        if (styleName == null || styleName.length() < 1) {
            finish();
            Toast.makeText(this, "Style name is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        settheme();
		setContentView(R.layout.editor);
		setTitle(getString(R.string.edit)+" \""+styleName+"\"");
		final EditText edCode = (EditText) findViewById(R.id.edCode);
		final StylishAddonService StylishAddon = StylishAddonService.getInstance();
		Map<String, Integer> rTypes = StylishAddon.rulesTypes;
		SQLiteDatabase db = StylishAddon.sbHelper.getReadableDatabase();

		Cursor cursor = db.query(StylesTable.TABLE_NAME + " s JOIN " + RulesTable.TABLE_NAME + " r ON s." + StylesTable._ID
				+ "=r." + RulesTable.COLUMN_NAME_STYLE_ID,
				new String[] { StylesTable.COLUMN_NAME_FILENAME,
				"group_concat(CASE "+RulesTable.COLUMN_NAME_RULE_TYPE
				   +" WHEN "+rTypes.get("domain") +		" THEN 'domain(\"' || "+RulesTable.COLUMN_NAME_RULE_TEXT+" || '\")'"
				   +" WHEN "+rTypes.get("url-prefix") +	" THEN 'url-prefix(\"' || "+RulesTable.COLUMN_NAME_RULE_TEXT+" || '\")'"
				   +" WHEN "+rTypes.get("regexp") +		" THEN 'regexp(\"' || "+RulesTable.COLUMN_NAME_RULE_TEXT+" || '\")'"
				   +" ELSE 'url(\"' || "+RulesTable.COLUMN_NAME_RULE_TEXT+" || '\")' END, ', ') AS rules"
				},
				StylesTable.COLUMN_NAME_NAME + " = ?",
				new String[] { styleName }, StylesTable.COLUMN_NAME_FILENAME, null, null);
		if (cursor.moveToFirst()) {
			do {
			    StringBuilder contents = new StringBuilder();			    
			    try {			     
			      BufferedReader input =  new BufferedReader(new FileReader(
			    		  StylishAddon.context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString()
			    		  +"/"+cursor.getString(cursor.getColumnIndexOrThrow(StylesTable.COLUMN_NAME_FILENAME))+ ".css"));
			      try {
			        String line = null; 
			        while (( line = input.readLine()) != null){
			          contents.append(line);
			          contents.append(System.getProperty("line.separator"));
			        }
			      }
			      finally {
			        input.close();
			      }
			    }
			    catch (IOException ex){
			      Log.e(LOG_TAG,ex.toString());
			    }
	
				edCode.setText(edCode.getText() + System.getProperty("line.separator")
						+"@-moz-document " + cursor.getString(cursor.getColumnIndexOrThrow("rules")) +" {"+System.getProperty("line.separator")
						+ contents.toString() + "}"+System.getProperty("line.separator")
						);	
			} while (cursor.moveToNext());
		}
		cursor.close();
		db.close();

		findViewById(R.id.btnCancel).setOnClickListener(new OnClickListener() { // click to "Cancel" button			
			@Override
			public void onClick(View arg0) {
				finish();					
			}
		});

		findViewById(R.id.btnSave).setOnClickListener(new OnClickListener() { // click to "Save" button				
			@Override
			public void onClick(View arg0) {
				StylishAddon.installStyleFromString(homepage, styleName, edCode.getText().toString(), updateURL);
				finish();					
			}
		});
		
		findViewById(R.id.toggleFind).setOnClickListener(new OnClickListener() { // click to "Find" button				
			@Override
			public void onClick(View v) {
				if (((ToggleButton)v).isChecked()) {
					findViewById(R.id.layoutFind).setVisibility(View.VISIBLE);
					findViewById(R.id.layoutFind).requestFocus();
				}
				else
					findViewById(R.id.layoutFind).setVisibility(View.GONE);
			}
		});
		
		findViewById(R.id.btnNext).setOnClickListener(new OnClickListener() { // click to "Next" button				
			@Override
			public void onClick(View v) {
				String target = ((EditText)findViewById(R.id.edFind)).getText().toString();
				if (target != "") {
					int index = edCode.getText().toString().indexOf(target, edCode.getSelectionEnd()+1);
					if (index == -1) {
						index = edCode.getText().toString().indexOf(target);
						if (index == -1) {
							Toast.makeText(StyleEditor.this, "not found", Toast.LENGTH_SHORT).show();
							return;
						}
						Toast.makeText(StyleEditor.this, "bottom of page reached, continued from the top", Toast.LENGTH_SHORT).show();
					}
					edCode.requestFocus();
					edCode.setSelection(index);
				}
				else 
					Toast.makeText(StyleEditor.this, "text is empty", Toast.LENGTH_SHORT).show();
			}
		});
		
		findViewById(R.id.btnPrev).setOnClickListener(new OnClickListener() { // click to "Prev" button				
			@Override
			public void onClick(View v) {
				String target = ((EditText)findViewById(R.id.edFind)).getText().toString();
				if (target != "") {
					int index = edCode.getText().toString().substring(0, edCode.getSelectionStart()).lastIndexOf(target);
					if (index == -1) {
						index = edCode.getText().toString().lastIndexOf(target);
						if (index == -1) {
							Toast.makeText(StyleEditor.this, "not found", Toast.LENGTH_SHORT).show();
							return;
						}
						Toast.makeText(StyleEditor.this, "top of page reached, continued from the bottom", Toast.LENGTH_SHORT).show();										
					}
					edCode.requestFocus();
					edCode.setSelection(index);
				}
				else 
					Toast.makeText(StyleEditor.this, "text is empty", Toast.LENGTH_SHORT).show();								
			}
		});
	}
}
