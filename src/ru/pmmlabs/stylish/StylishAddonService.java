/*******************************************************************************
 *
 *    Copyright (c) Alexey Stepanov
 *
 *    Stylish
 *
 *    StylishAddonService
 *
 ******************************************************************************/
package ru.pmmlabs.stylish;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import ru.pmmlabs.stylish.StylesBaseContract.RulesTable;
import ru.pmmlabs.stylish.StylesBaseContract.StylesTable;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.RemoteException;
import android.util.Log;
import com.dolphin.browser.addons.AddonService;
import com.dolphin.browser.addons.Browser;
import com.dolphin.browser.addons.IHttpAuthHandler;
//import com.dolphin.browser.addons.IJavascriptInterface; // Dolphin's IJavascriptInterface is properly, but slower 
//import com.dolphin.browser.addons.IWebSettings;	// and buggy than changing tab's title. let it be commented out
//import com.dolphin.browser.addons.WebViews.Listener;
import com.dolphin.browser.addons.IWebView;
import com.dolphin.browser.addons.OnClickListener;
import com.dolphin.browser.addons.WebViews;

public class StylishAddonService extends AddonService {

	protected static final String LOG_TAG = "StylishAddon";
	protected boolean AddonEnabled = true;
	protected Context context;
	protected Browser browser;
	protected StylesBaseHelper sbHelper;
	protected Map<String, Integer> rulesTypes = new HashMap<String, Integer>(); // Constant info (see line 77)
	protected HashMap<Integer, HashMap<String,Boolean>> currentStyles; // <WebView_Id, <Style Name, Enabled>>
	protected String oldTitle;
	private static StylishAddonService sInstance;
		public static StylishAddonService getInstance() {
			return sInstance;
		}
		
	@SuppressLint("UseSparseArrays")
	@Override
	protected void onBrowserConnected(Browser _browser) {
		try {
			_browser.addonBarAction.setIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher));
			_browser.addonBarAction.setTitle(getString(R.string.app_name));
			_browser.addonBarAction.setOnClickListener(mClickListener);
			_browser.addonBarAction.show();
			//_browser.webViews.addListener(mListener);	// for adding JavascriptInterface
			_browser.webViews.addPageListener(mPageListener);// for appending styles and "Install" buttons
			browser = _browser;
			context = getApplicationContext();
			sbHelper = new StylesBaseHelper(context);
			currentStyles = new HashMap<Integer, HashMap<String,Boolean>>();
			
			rulesTypes.put("domain", 0);
			rulesTypes.put("url", 1);
			rulesTypes.put("url-prefix", 2);
			rulesTypes.put("regexp", 3);
			
			sInstance = this;
		} catch (Exception e) {
			Log.e(LOG_TAG, e.toString());
		}
	}
	
	@Override
	protected void onBrowserDisconnected(Browser browser) {
	}
	
	private OnClickListener mClickListener = new OnClickListener() { // click on addon icon: show menu
		@Override
		public void onClick(Browser _browser) {
			Intent intent = new Intent(context, StylishDialog.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		}
	};
	
	// auxiliary functions //
	public String href2domain(String href) {
		if (href == null) return "";
		int domainStartPosition = href.indexOf("://")+3; // webview.getUrl() returns http://...
		int domainEndPosition = href.indexOf("/", domainStartPosition);
		return href.substring(domainStartPosition, domainEndPosition == -1 ? href.length() : domainEndPosition);
	}
	
	private String styleName2fileName(String styleName) { // replace bad characters by underline
		return styleName.replace(':', '_').replace('?', '_').replace('/', '_'); 
	}
	
	private void injectCSS(IWebView webView, String filename) throws RemoteException { // filename = "Style1"
		if (AddonEnabled) webView.loadUrl("javascript:var s=document.createElement('link');"
			+ "document.head.insertBefore(s,document.head.firstChild);"
			+ "s.rel='stylesheet'; s.href='content://mobi.mgeek.TunnyBrowser.htmlfileprovider" 
			+ context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "/" + filename +".css';"
			+ "s.setAttribute('name','"+filename.substring(0, filename.length()-1)+"');");
	}
	
	private void removeCSS(IWebView webView, String filename) throws RemoteException { // filename = "Style"
		webView.loadUrl("javascript:var s=document.getElementsByName('"+filename+"');"
			+"for (i = 0; i < s.length; ++i) { s[i].parentNode.removeChild(s[i]); }");
	}
	// End of auxiliary functions //
	
	private WebViews.PageListener mPageListener = new WebViews.PageListener() { // on page load, apply styles

		@Override
		public void onReceiveTitle(IWebView webView, String title) {
				try { 
					currentStyles.put(webView.getId(), new HashMap<String, Boolean>());
					String href = webView.getUrl();
					String domain = href2domain(href);
					SQLiteDatabase db = sbHelper.getReadableDatabase();					
					Cursor cursor = db.query(StylesTable.TABLE_NAME+" s JOIN "+RulesTable.TABLE_NAME+" r ON s."+StylesTable._ID+"=r."+RulesTable.COLUMN_NAME_STYLE_ID, 
							new String[] { StylesTable.COLUMN_NAME_FILENAME, StylesTable.COLUMN_NAME_NAME, StylesTable.COLUMN_NAME_ENABLED, RulesTable.COLUMN_NAME_RULE_TYPE, RulesTable.COLUMN_NAME_RULE_TEXT }, // The columns to return
							"("+RulesTable.COLUMN_NAME_RULE_TYPE+"="+rulesTypes.get("domain")+" AND ? LIKE '%' || "+RulesTable.COLUMN_NAME_RULE_TEXT+")"
							+" OR ("+RulesTable.COLUMN_NAME_RULE_TYPE+"="+rulesTypes.get("url")+" AND ? = "+RulesTable.COLUMN_NAME_RULE_TEXT+ ")"
							+" OR ("+RulesTable.COLUMN_NAME_RULE_TYPE+"="+rulesTypes.get("url-prefix")+" AND ? LIKE "+RulesTable.COLUMN_NAME_RULE_TEXT+" || '%')"
							+" OR "+RulesTable.COLUMN_NAME_RULE_TYPE+"="+rulesTypes.get("regexp"), // we will check it separately
							new String[] {domain, href, href},
							StylesTable.COLUMN_NAME_FILENAME,
							null,
							null
							);					
					//int count = 0; // for "active styles count in add-on name" feature
					if (cursor.moveToFirst()) {
						do {
							if (cursor.getInt(cursor.getColumnIndexOrThrow(RulesTable.COLUMN_NAME_RULE_TYPE))!=rulesTypes.get("regexp") 
									|| Pattern.matches(cursor.getString(cursor.getColumnIndexOrThrow(RulesTable.COLUMN_NAME_RULE_TEXT)), href)) {	// ~ if (regexp) { if (matches) continue; } else continue;
								String filename = cursor.getString(cursor.getColumnIndexOrThrow(StylesTable.COLUMN_NAME_FILENAME));
								Boolean enabled = cursor.getInt(cursor.getColumnIndexOrThrow(StylesTable.COLUMN_NAME_ENABLED))==1;
								if (enabled) {
									injectCSS(webView, filename);
									//count++; // for "active styles count in add-on name" feature
								}
								currentStyles.get(webView.getId()).put(cursor.getString(cursor.getColumnIndexOrThrow(StylesTable.COLUMN_NAME_NAME)), enabled);					
							 }
						} while (cursor.moveToNext());
					}
// for "active styles count in add-on name" feature. Doesn't work correct because there isn't "Tab changed" listener.
//					if (count > 0) browser.addonBarAction.setTitle(getString(R.string.app_name)+" ("+count+")");
//					else browser.addonBarAction.setTitle(getString(R.string.app_name));
					cursor.close();
					db.close();
	
					///////// add "Install" button
					final char SPECIAL_SYMBOL = '{';
					if (Pattern.matches("https?://userstyles\\.org/styles/\\d+\\S*",href)){
						if (webView.getTitle().charAt(0) != SPECIAL_SYMBOL) {
							oldTitle = webView.getTitle();
							webView.loadUrl("javascript:var s=document.createElement('div');"
									+ "function instButtonAppend(){"
									+ "var p=document.getElementById('main-style-info');"
									+ "if (p) {"
									+ "p.removeChild(document.getElementById('style-install-unknown'));"
									+ "p.appendChild(s);} else setTimeout(instButtonAppend,300);}"
									+ "instButtonAppend();"
									+ "s.setAttribute('class','install-status install');"
									+ "s.id='style-install-mobile-dolphin-android';"
									+ "s.innerHTML='<span class=\"install-symbol\">+</span>"+getString(R.string.install)+"';"
									+ "function getOptions(e){var t=document.getElementById(\"style-settings\");if(!t){return[]}var n=t.getElementsByTagName(\"select\");var r=[];for(var i=0;i<n.length;i++){r.push([n[i].name,n[i].value])}var s=[];var o=t.querySelectorAll(\"input[type='text']\");for(var i=0;i<o.length;i++){if(o[i].value==\"\"){s.push(o[i])}else{r.push([o[i].name,o[i].value])}}o=t.querySelectorAll(\"input[type='radio']:checked\");for(var i=0;i<o.length;i++){switch(o[i].value){case\"user-url\":var u=o[i].id.split(\"-\");var a=\"option-user-url-\"+u[u.length-1];var f=document.getElementById(a);if(f.value==\"\"){s.push(f.parentNode)}else{r.push([o[i].name,f.value])}break;case\"user-upload\":var u=o[i].id.split(\"-\");var a=\"option-user-upload-\"+u[u.length-1];var f=document.getElementById(a);if(!f.uploadedData){s.push(f.parentNode)}else{r.push([o[i].name,f.uploadedData])}break;default:r.push([o[i].name,o[i].value]);break}}if(s.length>0){if(e){alert(\"Choose a value for every setting first.\");s[0].scrollIntoView()}return null}return r}function toQueryString(e){return e.map(function(e){return encodeURIComponent(e[0])+\"=\"+encodeURIComponent(e[1])}).join(\"&\")}" // from site's JS.
									+ "s.addEventListener('click', function() {" 
									/******/+ "this.innerHTML='"+getString(R.string.installing)+"...';"
									///******/+ "dolphinStylish.invoke(0,JSON.stringify({" //for IJavascriptInterface
									/******/+ "document.title = '"+SPECIAL_SYMBOL+"'+document.querySelector('link[rel=\"stylish-code-chrome\"]').href+'?'+toQueryString(getOptions(true));"
									+ "}, false);");
						} else {	// handler for "Install" click
							installStyleFromUrl(webView.getTitle().substring(1));
							webView.loadUrl("javascript:document.title=\""+oldTitle+"\";");
						}
					}
				} catch (RemoteException e) {
					Log.e(LOG_TAG, e.toString());
				}
		}

		@Override
		public void onPageFinished(final IWebView webView, String url) {
				
		}

		@Override
		public void onPageStarted(IWebView webView, String url) {
		}

		@Override
		public boolean onReceivedHttpAuthRequest(IWebView arg0, IHttpAuthHandler arg1, String arg2, String arg3) {
			return false;
		}
	};
	
// for IJavascriptInterface.
//	private Listener mListener = new Listener() { // binding java object to page for style installing
//
//		@Override
//		public void onWebViewCreated(IWebView webView) {
//			try {
//				webView.addJavascriptInterface(mJsInterface, "dolphinStylish");				
//			} catch (RemoteException e) {
//				Log.e(LOG_TAG, e.toString());
//			}
//		}
//
//		@Override
//		public void onUpdateWebSettings(IWebSettings arg0) {
//		}
//	};

//	protected IJavascriptInterface mJsInterface = new IJavascriptInterface.Stub() { 
//		@Override
//		public String invoke(String method, String argsJson) throws RemoteException {
//			try {
//				JSONObject args = new JSONObject(argsJson);
//				switch (Integer.parseInt(method)) {
//				case 0:
//					installStyleFromUrl(args.getString("homepage"), args.getString("name"), args.getString("url")); break;
//				default:
//					Log.e(LOG_TAG, argsJson);
//				}
//			} catch (JSONException e) {
//				Log.e(LOG_TAG, e.toString());
//			}
//			return "";
//		}
	
	/*	Install style from string
		containing code in mozilla format	*/
	protected String installStyleFromString(String homepage, String styleName, String styleCode, String updateURL) {
		String filename = styleName2fileName(styleName);
		/* blocks - it is parts of the code, that are applied by different rules. It stored in separate files. */
		String[] blocks = styleCode.replaceAll("@namespace[^;]+;", "").split(Pattern.quote("@-moz-document")); //we don't support namespaces yet 
		boolean is_moz_doc = blocks.length > 1; // if false, it is global style.
		try { //delete style, if exists
			deleteStyle(styleName);
		} catch (RemoteException e1) {
			Log.e(LOG_TAG, e1.toString());
		}
		//Common values for all blocks of style
		ContentValues values = new ContentValues();
		values.put(StylesTable.COLUMN_NAME_URL, homepage);
		values.put(StylesTable.COLUMN_NAME_NAME, styleName);
		values.put(StylesTable.COLUMN_NAME_ENABLED, 1);
		values.put(StylesTable.COLUMN_NAME_UPDATE, updateURL);
		SQLiteDatabase db = sbHelper.getWritableDatabase();
		// for every block of style:
		for (int i=(is_moz_doc ? 1 : 0); i<blocks.length; i++) {
			String[] rulesAndCode = null;
			if (is_moz_doc) {
				rulesAndCode = blocks[i].split("\\)\\s*\\{", 2) ; // split block by first ) {
				rulesAndCode[0] += ")";
				styleCode = rulesAndCode[1].substring(0, rulesAndCode[1].lastIndexOf("}")); // delete last } in code
			}
			else styleCode = blocks[i];
			// Save code to file
			File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename+i+ ".css");
			FileWriter writer = null;
			try {
				writer = new FileWriter(file);
				writer.append(styleCode);
				writer.flush();
				writer.close();
			} catch (IOException e) {
				Log.e(LOG_TAG, e.toString());
				return getString(R.string.trouble); 
			}
			// Record info about block to DB
			values.put(StylesTable.COLUMN_NAME_FILENAME, filename+i);
			long newRowId = db.insert(StylesTable.TABLE_NAME, null, values);
			values.remove(StylesTable.COLUMN_NAME_FILENAME);
			
			// Save rules to DB	
			if (is_moz_doc) {
				String[] rules = rulesAndCode[0].replaceAll("/\\*.+?\\*/", "").split(",");
				for (String rule : rules) {
					String[] typeAndText = rule.split ("\\(",2); // [ domain, 'google.com') ]
					String text = typeAndText[1].trim().replace("'", "").replace("\"", "");	// [ domain, google.com) ]
					int indexof = text.lastIndexOf(")");
					if (indexof > 0) text = text.substring(0, indexof); // [ domain, google.com ]
					int type_id = rulesTypes.get(typeAndText[0].trim());
					if (type_id==rulesTypes.get("regexp"))
						text = text.replaceAll(Pattern.quote("\\\\"),"\\\\"); // in regexp replace double slash to single
					ContentValues metaValues = new ContentValues();
			    	metaValues.put(RulesTable.COLUMN_NAME_RULE_TYPE, type_id);	
			    	metaValues.put(RulesTable.COLUMN_NAME_RULE_TEXT, text); 
			    	metaValues.put(RulesTable.COLUMN_NAME_STYLE_ID, newRowId);
			    	db.insert(RulesTable.TABLE_NAME, null, metaValues);
			    	try { // inject block to all opened and matched tabs
						for (int tab_id : browser.tabs.getAllTabIds()) {
							IWebView webview = browser.tabs.get(tab_id).getWebView();
							if (currentStyles.get(webview.getId()) != null) {
								String href = webview.getUrl();
								if (href != null) {
									String domain = href2domain(href);
									boolean inject;
									switch (type_id) {
										case 0: inject = domain.endsWith(text); break;
										case 1: inject = href.equals(text); break;
										case 2: inject = href.startsWith(text); break;
										case 3: inject = Pattern.matches(text, href); break;
										default: inject = false;
									}
									if (inject) {
										injectCSS(webview, filename+i);
										currentStyles.get(webview.getId()).put(styleName, true);
									}
								}
							}
						}
					} catch (RemoteException e) {
						Log.e(LOG_TAG, e.toString());
					}
				}
			} else { // if it is global style
				ContentValues metaValues = new ContentValues();		
				metaValues.put(RulesTable.COLUMN_NAME_RULE_TYPE, rulesTypes.get("url-prefix"));	
				metaValues.put(RulesTable.COLUMN_NAME_RULE_TEXT, "");
				metaValues.put(RulesTable.COLUMN_NAME_STYLE_ID, newRowId);
				db.insert(RulesTable.TABLE_NAME, null, metaValues);
				try { // inject block to all opened tabs
					for (int tab_id : browser.tabs.getAllTabIds()) {
						IWebView webview = browser.tabs.get(tab_id).getWebView();
						if (currentStyles.get(webview.getId()) != null) {
							injectCSS(webview, filename+i);
							currentStyles.get(webview.getId()).put(styleName, true);
						}
					}
				} catch (RemoteException e) {
					Log.e(LOG_TAG, e.toString());
				}
			}
		}
		db.close();
		return (is_moz_doc ? "S" : "GLOBAL s" )+"tyle \""+styleName+"\" installed!";
	}
	
	protected String installStyleFromObject(JSONObject userstyle) {
		String styleName = userstyle.optString("name"); 
		String filename = styleName2fileName(styleName);
		JSONArray sections = userstyle.optJSONArray("sections");
		try { //delete style, if exists
			deleteStyle(styleName);
		} catch (RemoteException e) {
			Log.e(LOG_TAG, e.toString());
		}
		//Common values for all blocks of style
		ContentValues values = new ContentValues();
		values.put(StylesTable.COLUMN_NAME_URL, userstyle.optString("url"));
		values.put(StylesTable.COLUMN_NAME_NAME, styleName);
		values.put(StylesTable.COLUMN_NAME_ENABLED, 1);
		values.put(StylesTable.COLUMN_NAME_UPDATE, userstyle.optString("updateUrl"));
		SQLiteDatabase db = sbHelper.getWritableDatabase();
		
		HashMap<String, String> moz_doc2json_types = new HashMap<String, String>();
		moz_doc2json_types.put("domain",	"domains");
		moz_doc2json_types.put("url",		"urls");
		moz_doc2json_types.put("url-prefix","urlPrefixes");
		moz_doc2json_types.put("regexp",	"regexps");
		
		boolean is_global = true;
		
		// for every section of style:
		for (int i=0; i<sections.length(); i++) {
			JSONObject section = sections.optJSONObject(i);
			// Save code to file
			File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename+i+ ".css");
			FileWriter writer = null;
			try {
				writer = new FileWriter(file);
				writer.append(section.optString("code"));
				writer.flush();
				writer.close();
			} catch (IOException e) {
				Log.e(LOG_TAG, e.toString());
				return getString(R.string.trouble); 
			}
			// Record info about block to DB
			values.put(StylesTable.COLUMN_NAME_FILENAME, filename+i);
			long newRowId = db.insert(StylesTable.TABLE_NAME, null, values);
			values.remove(StylesTable.COLUMN_NAME_FILENAME);
			// Save rules to DB	
			ContentValues metaValues = new ContentValues();
			metaValues.put(RulesTable.COLUMN_NAME_STYLE_ID, newRowId);
			// for every rule type:
			for (String moz_doc_rule_type : moz_doc2json_types.keySet()) {
				JSONArray rules = section.optJSONArray(moz_doc2json_types.get(moz_doc_rule_type));
				metaValues.put(RulesTable.COLUMN_NAME_RULE_TYPE, rulesTypes.get(moz_doc_rule_type));
				// for every rule: 
				for (int j=0;j<rules.length();j++) {
					String ruleText = rules.optString(j);
					metaValues.put(RulesTable.COLUMN_NAME_RULE_TEXT, ruleText);
					db.insert(RulesTable.TABLE_NAME, null, metaValues);
					metaValues.remove(RulesTable.COLUMN_NAME_RULE_TEXT);
					try { // inject block to all opened and matched tabs
						for (int tab_id : browser.tabs.getAllTabIds()) {
							IWebView webview = browser.tabs.get(tab_id).getWebView();
							if (currentStyles.get(webview.getId()) != null) {
								String href = webview.getUrl();
								if (href != null) {
									String domain = href2domain(href);
									boolean inject;
									switch (rulesTypes.get(moz_doc_rule_type)) {
										case 0: inject = domain.endsWith(ruleText); break;
										case 1: inject = href.equals(ruleText); break;
										case 2: inject = href.startsWith(ruleText); break;
										case 3: inject = Pattern.matches(ruleText, href); break;
										default: inject = false;
									}
									if (inject) {
										injectCSS(webview, filename+i);
										currentStyles.get(webview.getId()).put(styleName, true);
									}
								}
							}
						}
					} catch (RemoteException e) {
						Log.e(LOG_TAG, e.toString());
					}
					is_global = false;
				}
				metaValues.remove(RulesTable.COLUMN_NAME_RULE_TYPE);
			}
			if (is_global) {	
				metaValues.put(RulesTable.COLUMN_NAME_RULE_TYPE, rulesTypes.get("url-prefix"));	
				metaValues.put(RulesTable.COLUMN_NAME_RULE_TEXT, "");
				db.insert(RulesTable.TABLE_NAME, null, metaValues);
				try { // inject block to all opened tabs
					for (int tab_id : browser.tabs.getAllTabIds()) {
						IWebView webview = browser.tabs.get(tab_id).getWebView();
						if (currentStyles.get(webview.getId()) != null) {
							injectCSS(webview, filename+i);
							currentStyles.get(webview.getId()).put(styleName, true);
						}
					}
				} catch (RemoteException e) {
					Log.e(LOG_TAG, e.toString());
				}
		    }
		}
		db.close();
		return (!is_global ? "S" : "GLOBAL s" )+"tyle \""+styleName+"\" installed!";
	}

	protected void installStyleFromUrl(final String installUrl) { // Download style from internet
		new Thread(new Runnable() {
			public void run() {
				String msg = null;
				if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) { // if sdcard writable
					String line;
					StringBuilder jsonResponse = new StringBuilder();
					try {
						URL url = new URL(installUrl);
						// Read all the text returned by the server
						InputStreamReader is = new InputStreamReader(url.openStream());
						BufferedReader in = new BufferedReader(is);
						while ((line = in.readLine()) != null) {
							jsonResponse.append(line);
							jsonResponse.append(System.getProperty("line.separator"));
						}
						in.close();
						is.close();
						JSONObject userstyle = new JSONObject(jsonResponse.toString());
						msg = installStyleFromObject(userstyle);
						new URL(installUrl.replaceFirst("chrome/(\\d+)\\.json\\S*", "install/$1?source=stylish-do")).openStream().close();
					} catch (Exception e) {
						Log.e(LOG_TAG, e.toString());
						msg = getString(R.string.problemwhiledownloading);
					}
				} else
					msg = getString(R.string.insertsd);
				try {
					browser.tabs.getCurrent().getWebView().loadUrl(
							"javascript:document.getElementById('style-install-mobile-dolphin-android').innerHTML='"+msg+"';");
				} catch (RemoteException e) {
					Log.e(LOG_TAG, e.toString());
				}
			}
		}).start();
	}	

	private void onOffStyleInAllTabs(String styleName, Boolean enabled) throws RemoteException, IllegalArgumentException {
		SQLiteDatabase db = sbHelper.getReadableDatabase();
		int[] getalltabs = browser.tabs.getAllTabIds();
		if (getalltabs != null)
			for (int tab_id : getalltabs) {
				IWebView webview = browser.tabs.get(tab_id).getWebView();
				HashMap<String, Boolean> s = currentStyles.get(webview.getId());
				if (s != null && s.containsKey(styleName)) { // if this style affects current page
					// 1. Load/Unload CSS
					if (enabled) {
						Cursor cursor = db.query(StylesTable.TABLE_NAME, 
							new String[] { StylesTable.COLUMN_NAME_FILENAME }, // The columns to return
							StylesTable.COLUMN_NAME_NAME + " = ?",
							new String[] {styleName},
							null,
							null,
							null
							);
						if (cursor.moveToFirst()) {
							do injectCSS(webview, cursor.getString(cursor.getColumnIndexOrThrow(StylesTable.COLUMN_NAME_FILENAME)));
							while (cursor.moveToNext());
						}
						cursor.close();
					}
					else removeCSS(webview, styleName2fileName(styleName));
					// 2. Correct currentStyles
					s.put(styleName, enabled);
					
					// 3. Correct addonbar (for "active styles count in add-on name" feature)
		//			int count = 0;
		//			for (Boolean k : s.values()) if (k) count++;
		//			if (count > 0) browser.addonBarAction.setTitle(getString(R.string.app_name)+" ("+count+")");
		//			else browser.addonBarAction.setTitle(getString(R.string.app_name));
				}
			}
		db.close();
	}
	
	protected void setStyleState(String style, Boolean enabled) throws RemoteException {
		onOffStyleInAllTabs(style, enabled);
		SQLiteDatabase db = sbHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(StylesTable.COLUMN_NAME_ENABLED, enabled ? 1 : 0);
		db.update(StylesTable.TABLE_NAME, values, StylesTable.COLUMN_NAME_NAME+" = ?", new String[] {style});
		db.close();	
	}

	protected void deleteStyle(String styleName) throws RemoteException {
		onOffStyleInAllTabs(styleName, false);
		for (HashMap<String, Boolean> s : currentStyles.values()) s.remove(styleName);
		SQLiteDatabase db = sbHelper.getWritableDatabase();
		// Check, is there style with given name?
		Cursor cursor = db.query(StylesTable.TABLE_NAME,
				new String[] {StylesTable._ID, StylesTable.COLUMN_NAME_FILENAME},
				StylesTable.COLUMN_NAME_NAME+" = ?", 
				new String[] {styleName},
				null,
				null,
				null);
		if (cursor.moveToFirst()) { // Style exists. Need to delete each block
			String ids = "";
			File file;
			do {
				ids += cursor.getInt(cursor.getColumnIndexOrThrow(StylesTable._ID))+", ";		
				file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
						cursor.getString(cursor.getColumnIndexOrThrow(StylesTable.COLUMN_NAME_FILENAME))+ ".css");
				file.delete();
			}
			while (cursor.moveToNext());
			db.delete (StylesTable.TABLE_NAME,
					StylesTable._ID + " IN ("+ids+" -1)",
					null); // info about blocks in StylesTable
			db.delete(RulesTable.TABLE_NAME,
					RulesTable.COLUMN_NAME_STYLE_ID + " IN ("+ids+" -1)",
					null); // info about rules in RulesTable
		}
		cursor.close();
		db.close();
	}

	protected void switchStateAllStyles() throws RemoteException {
		AddonEnabled = !AddonEnabled;
		if (currentStyles != null) {		// apply changes to current tab
			IWebView webView = browser.tabs.getCurrent().getWebView();
			HashMap<String, Boolean> cStyles = currentStyles.get(webView.getId());
			for (final String style : cStyles.keySet())
				if (cStyles.get(style)) // for all enabled styles on current page, inject or remove css
					for (int i=0;i<10;i++)
						if (AddonEnabled) injectCSS(webView, style+i);
						else removeCSS(webView, styleName2fileName(style));
		}
	}
}
