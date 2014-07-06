/*******************************************************************************
 *
 *    Copyright (c) Alexey Stepanov
 *
 *    Stylish
 *
 *    StylesBaseContract
 *
 ******************************************************************************/
package ru.pmmlabs.stylish;

import android.provider.BaseColumns;

public final class StylesBaseContract {
	// To prevent someone from accidentally instantiating the contract class,
	// give it an empty constructor.
	public StylesBaseContract() {
	}

	/* Inner class that defines the table contents */
	public static abstract class StylesTable implements BaseColumns {
		public static final String TABLE_NAME = "styles";
		public static final String COLUMN_NAME_URL = "url";		
		public static final String COLUMN_NAME_NAME = "name";
		public static final String COLUMN_NAME_FILENAME = "filename";
		public static final String COLUMN_NAME_ENABLED = "enabled";
		public static final String COLUMN_NAME_UPDATE = "updateURL";
	}
	
	public static abstract class RulesTable implements BaseColumns {
		public static final String TABLE_NAME = "rules";
		public static final String COLUMN_NAME_STYLE_ID = "style_id";
		public static final String COLUMN_NAME_RULE_TYPE = "rule_type";
		public static final String COLUMN_NAME_RULE_TEXT = "rule_text";
	}
}