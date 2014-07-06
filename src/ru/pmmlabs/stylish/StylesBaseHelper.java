/*******************************************************************************
 *
 *    Copyright (c) Alexey Stepanov
 *
 *    Stylish
 *
 *    StylesBaseHelper
 *
 ******************************************************************************/
package ru.pmmlabs.stylish;

import ru.pmmlabs.stylish.StylesBaseContract.RulesTable;
import ru.pmmlabs.stylish.StylesBaseContract.StylesTable;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class StylesBaseHelper extends SQLiteOpenHelper {

	private static final String TEXT_TYPE = " TEXT";
	private static final String INT_TYPE = " INTEGER NOT NULL";
	private static final String SQL_CREATE_TABLE1 = "CREATE TABLE " + StylesTable.TABLE_NAME + " ("
			+ StylesTable._ID + INT_TYPE + " PRIMARY KEY AUTOINCREMENT,"
			+ StylesTable.COLUMN_NAME_URL + TEXT_TYPE + ","			
			+ StylesTable.COLUMN_NAME_NAME + TEXT_TYPE + ","
			+ StylesTable.COLUMN_NAME_FILENAME + TEXT_TYPE + ","
			+ StylesTable.COLUMN_NAME_ENABLED + INT_TYPE + ","
			+ StylesTable.COLUMN_NAME_UPDATE + TEXT_TYPE + " )";
	private static final String SQL_CREATE_TABLE2 =	"CREATE TABLE " + RulesTable.TABLE_NAME + " ("
			+ RulesTable._ID + INT_TYPE + " PRIMARY KEY AUTOINCREMENT,"
			+ RulesTable.COLUMN_NAME_STYLE_ID + INT_TYPE + "," 
			+ RulesTable.COLUMN_NAME_RULE_TYPE + INT_TYPE + ","
			+ RulesTable.COLUMN_NAME_RULE_TEXT + TEXT_TYPE +" )";

	private static final String SQL_DELETE_TABLE1 = "DROP TABLE IF EXISTS " + StylesTable.TABLE_NAME;
	private static final String SQL_DELETE_TABLE2 = "DROP TABLE IF EXISTS " + RulesTable.TABLE_NAME;
	// If you change the database schema, you must increment the database version.
	public static final int DATABASE_VERSION = 1;
	public static final String DATABASE_NAME = "StylesBase.db";

	public StylesBaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	public void onCreate(SQLiteDatabase db) {
		db.execSQL(SQL_CREATE_TABLE1);
		db.execSQL(SQL_CREATE_TABLE2);
	}

	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL(SQL_DELETE_TABLE1);
		db.execSQL(SQL_DELETE_TABLE2);
		onCreate(db);
	}

	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		onUpgrade(db, oldVersion, newVersion);
	}
}