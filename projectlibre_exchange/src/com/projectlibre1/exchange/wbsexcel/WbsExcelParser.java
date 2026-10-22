/*******************************************************************************
 * The contents of this file are subject to the Common Public Attribution License 
 * Version 1.0 (the "License"); you may not use this file except in compliance with 
 * the License. You may obtain a copy of the License at 
 * http://www.projectlibre.com/license . The License is based on the Mozilla Public 
 * License Version 1.1 but Sections 14 and 15 have been added to cover use of 
 * software over a computer network and provide for limited attribution for the 
 * Original Developer. In addition, Exhibit A has been modified to be consistent 
 * with Exhibit B. 
 *
 * Software distributed under the License is distributed on an "AS IS" basis, 
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for the 
 * specific language governing rights and limitations under the License. The 
 * Original Code is ProjectLibre. The Original Developer is the Initial Developer 
 * and is ProjectLibre Inc. All portions of the code written by ProjectLibre are 
 * Copyright (c) 2012-2019. All Rights Reserved. All portions of the code written by 
 * ProjectLibre are Copyright (c) 2012-2019. All Rights Reserved. Contributor 
 * ProjectLibre, Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of the 
 * ProjectLibre End-User License Agreement (the ProjectLibre License) in which case 
 * the provisions of the ProjectLibre License are applicable instead of those above. 
 * If you wish to allow use of your version of this file only under the terms of the 
 * ProjectLibre License and not to allow others to use your version of this file 
 * under the CPAL, indicate your decision by deleting the provisions above and 
 * replace them with the notice and other provisions required by the ProjectLibre 
 * License. If you do not delete the provisions above, a recipient may use your 
 * version of this file under either the CPAL or the ProjectLibre Licenses. 
 *
 *
 * [NOTE: The text of this Exhibit A may differ slightly from the text of the notices 
 * in the Source Code files of the Original Code. You should use the text of this 
 * Exhibit A rather than the text found in the Original Code Source Code for Your 
 * Modifications.] 
 *
 * EXHIBIT B. Attribution Information for ProjectLibre required
 *
 * Attribution Copyright Notice: Copyright (c) 2012-2019, ProjectLibre, Inc.
 * Attribution Phrase (not exceeding 10 words): 
 * ProjectLibre, open source project management software.
 * Attribution URL: http://www.projectlibre.com
 * Graphic Image as provided in the Covered Code as file: projectlibre-logo.png with 
 * alternatives listed on http://www.projectlibre.com/logo 
 *
 * Display of Attribution Information is required in Larger Works which are defined 
 * in the CPAL as a work which combines Covered Code or portions thereof with code 
 * not governed by the terms of the CPAL. However, in addition to the other notice 
 * obligations, all copies of the Covered Code in Executable and Source Code form 
 * distributed must, as a form of attribution of the original author, include on 
 * each user interface screen the "ProjectLibre" logo visible to all users. 
 * The ProjectLibre logo should be located horizontally aligned with the menu bar 
 * and left justified on the top left of the screen adjacent to the File menu. The 
 * logo must be at least 144 x 31 pixels. When users click on the "ProjectLibre" 
 * logo it must direct them back to http://www.projectlibre.com. 
 *******************************************************************************/
package com.projectlibre1.exchange.wbsexcel;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.projectlibre1.configuration.FieldDictionary;
import com.projectlibre1.exchange.ulnl.XlsxSheetReader;
import com.projectlibre1.field.Field;
import com.projectlibre1.strings.Messages;

/**
 * Reads a workbook written by {@link WbsExcelExporter}. Columns are found by the field ids of the hidden
 * first row, so they can be reordered or removed in Excel: only the columns left in the file are imported.
 */
public class WbsExcelParser {

	public static class Row {
		final int rowNumber;
		final Long uniqueId; // null for a new task
		final int level; // 1 based, 0 when the file has no Outline Level column
		final Map<Field, String> values;
		Row(int rowNumber, Long uniqueId, int level, Map<Field, String> values) {
			this.rowNumber = rowNumber;
			this.uniqueId = uniqueId;
			this.level = level;
			this.values = values;
		}
		public int getRowNumber() {
			return rowNumber;
		}
	}

	public static class Sheet {
		final List<Field> fields = new ArrayList<Field>(); // columns to import, in file order
		final List<Row> rows = new ArrayList<Row>();
		final List<String> warnings = new ArrayList<String>();
		boolean hasOutlineLevel;
		public List<Field> getFields() {
			return fields;
		}
		public List<Row> getRows() {
			return rows;
		}
		public List<String> getWarnings() {
			return warnings;
		}
		public boolean hasOutlineLevel() {
			return hasOutlineLevel;
		}
	}

	/**
	 * @throws IOException if the file cannot be read or was not exported by ProjectLibre
	 */
	public static Sheet parse(File file) throws IOException {
		XlsxSheetReader reader = new XlsxSheetReader(file);
		List<String> names = reader.getSheetNames();
		if (names.isEmpty())
			throw new IOException(Messages.getString("WbsExcel.NotExported")); //$NON-NLS-1$
		String name = names.contains(WbsExcelExporter.SHEET_NAME) ? WbsExcelExporter.SHEET_NAME : names.get(0);
		return parse(reader.readSheet(name));
	}

	public static Sheet parse(Map<Integer, Map<String, String>> cells) throws IOException {
		Sheet sheet = new Sheet();
		Map<String, String> ids = cells.get(WbsExcelExporter.ID_ROW);
		if (ids == null || !ids.containsValue(WbsExcelExporter.UNIQUE_ID))
			throw new IOException(Messages.getString("WbsExcel.NotExported")); //$NON-NLS-1$

		String uniqueIdColumn = null;
		String levelColumn = null;
		Map<String, Field> fieldColumns = new LinkedHashMap<String, Field>();
		int last = -1;
		for (String column : ids.keySet())
			last = Math.max(last, XlsxSheetReader.columnIndex(column));
		for (int c = 0; c <= last; c++) { // in column order
			String column = XlsxSheetReader.columnName(c);
			String id = ids.get(column);
			if (id == null)
				continue;
			id = id.trim();
			if (WbsExcelExporter.UNIQUE_ID.equals(id)) {
				uniqueIdColumn = column;
			} else if (WbsExcelExporter.OUTLINE_LEVEL.equals(id)) {
				levelColumn = column;
			} else {
				Field field = FieldDictionary.getInstance().getFieldFromId(id);
				if (field == null)
					sheet.warnings.add(MessageFormat.format(Messages.getString("WbsExcel.UnknownColumn"), column, id)); //$NON-NLS-1$
				else if (!sheet.fields.contains(field)) {
					sheet.fields.add(field);
					fieldColumns.put(column, field);
				}
			}
		}
		sheet.hasOutlineLevel = levelColumn != null;

		int previousLevel = 0;
		for (Map.Entry<Integer, Map<String, String>> entry : cells.entrySet()) {
			int r = entry.getKey();
			if (r < WbsExcelExporter.FIRST_DATA_ROW)
				continue;
			Map<String, String> row = entry.getValue();
			boolean empty = true;
			for (String value : row.values())
				if (value.trim().length() > 0)
					empty = false;
			if (empty)
				continue;

			Long uniqueId = null;
			String text = row.get(uniqueIdColumn);
			if (text != null && text.trim().length() > 0) {
				try {
					uniqueId = Long.valueOf(Long.parseLong(text.trim()));
				} catch (NumberFormatException e) {
					sheet.warnings.add(MessageFormat.format(Messages.getString("WbsExcel.BadUniqueId"), String.valueOf(r), text)); //$NON-NLS-1$
				}
			}

			int level = 0;
			if (levelColumn != null) {
				text = row.get(levelColumn);
				try {
					level = Integer.parseInt(text == null ? "" : text.trim()); //$NON-NLS-1$
				} catch (NumberFormatException e) {
					level = Math.max(1, previousLevel);
					sheet.warnings.add(MessageFormat.format(Messages.getString("WbsExcel.BadLevel"), String.valueOf(r), text == null ? "" : text, String.valueOf(level))); //$NON-NLS-1$ //$NON-NLS-2$
				}
				int max = previousLevel + 1;
				if (level < 1 || level > max) {
					int fixed = Math.max(1, Math.min(level, max));
					sheet.warnings.add(MessageFormat.format(Messages.getString("WbsExcel.BadLevel"), String.valueOf(r), String.valueOf(level), String.valueOf(fixed))); //$NON-NLS-1$
					level = fixed;
				}
				previousLevel = level;
			}

			Map<Field, String> values = new LinkedHashMap<Field, String>();
			for (Map.Entry<String, Field> column : fieldColumns.entrySet()) {
				String value = row.get(column.getKey());
				values.put(column.getValue(), value == null ? "" : value.trim()); // empty cell clears the value //$NON-NLS-1$
			}
			sheet.rows.add(new Row(r, uniqueId, level, values));
		}
		return sheet;
	}
}
