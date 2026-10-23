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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.projectlibre1.configuration.FieldDictionary;
import com.projectlibre1.field.Field;
import com.projectlibre1.grouping.core.Node;
import com.projectlibre1.grouping.core.model.NodeModel;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.strings.Messages;

/**
 * Exports the tasks of a project to an .xlsx that {@link WbsExcelImporter} reads back.
 * Row 1 (hidden) holds the field ids, row 2 the column names, then one row per task in outline order.
 * The first two columns are always the Unique ID, used to match the rows on import, and the Outline Level.
 * Cells that cannot be edited are grey.
 */
public class WbsExcelExporter {
	public static final String SHEET_NAME = "WBS"; //$NON-NLS-1$
	public static final String UNIQUE_ID = "Field.uniqueId"; //$NON-NLS-1$
	public static final String OUTLINE_LEVEL = "OutlineLevel"; // not a field, the depth in the outline //$NON-NLS-1$
	public static final String NAME = "Field.name"; //$NON-NLS-1$
	public static final int ID_ROW = 1;
	public static final int HEADER_ROW = 2;
	public static final int FIRST_DATA_ROW = 3;

	private static final int MAX_WIDTH = 60;

	/** Task fields that can be shown as a spreadsheet column */
	public static List<Field> getAllTaskFields() {
		return exportableFields(FieldDictionary.getInstance().getTaskFields());
	}

	/** The fields that can be exported, in order, without duplicates and without the Unique ID */
	public static List<Field> exportableFields(Collection fields) {
		List<Field> result = new ArrayList<Field>();
		for (Iterator i = fields.iterator(); i.hasNext();) {
			Object o = i.next();
			if (!(o instanceof Field))
				continue;
			Field field = (Field) o;
			if (field.isGraphical() || field.isPassword() || UNIQUE_ID.equals(field.getId())
					|| !field.isApplicable(NormalTask.class) || result.contains(field))
				continue;
			result.add(field);
		}
		return result;
	}

	public static void export(Project project, List<Field> fields, File file) throws IOException {
		NodeModel model = project.getTaskOutline();
		int columnCount = fields.size() + 2;
		String[] ids = new String[columnCount];
		String[] headers = new String[columnCount];
		ids[0] = UNIQUE_ID;
		headers[0] = FieldDictionary.getInstance().getFieldFromId(UNIQUE_ID).getName();
		ids[1] = OUTLINE_LEVEL;
		headers[1] = Messages.getString("WbsExcel.OutlineLevel"); //$NON-NLS-1$
		for (int c = 0; c < fields.size(); c++) {
			ids[c + 2] = fields.get(c).getId();
			headers[c + 2] = fields.get(c).getName();
		}

		XlsxSheetWriter writer = new XlsxSheetWriter(SHEET_NAME);
		writer.addHiddenRow(ids);
		writer.addHeaderRow(headers);
		double[] widths = new double[columnCount];
		for (int c = 0; c < columnCount; c++)
			widths[c] = headers[c] == null ? 8 : headers[c].length();
		writeChildren(writer, model, null, 1, fields, widths);

		for (int c = 0; c < columnCount; c++)
			widths[c] = Math.min(MAX_WIDTH, Math.max(8, widths[c] + 2));
		writer.setColumnWidths(widths);
		writer.setFrozenRows(HEADER_ROW);
		writer.setAutoFilterRow(HEADER_ROW);
		writer.write(file);
	}

	private static void writeChildren(XlsxSheetWriter writer, NodeModel model, Node parent, int level, List<Field> fields, double[] widths) {
		for (Iterator i = children(model, parent).iterator(); i.hasNext();) {
			Node node = (Node) i.next();
			if (node.isVoid() || !(node.getImpl() instanceof NormalTask))
				continue;
			NormalTask task = (NormalTask) node.getImpl();
			String[] values = new String[fields.size() + 2];
			int[] styles = new int[values.length];
			values[0] = String.valueOf(task.getUniqueId());
			styles[0] = XlsxSheetWriter.cellStyle(true, 0);
			values[1] = String.valueOf(level);
			styles[1] = XlsxSheetWriter.cellStyle(false, 0);
			for (int c = 0; c < fields.size(); c++) {
				Field field = fields.get(c);
				String text;
				boolean readOnly;
				try {
					text = field.getText(node, model, null);
					readOnly = field.isReadOnly(node, model, null);
				} catch (RuntimeException e) { // a field that cannot be computed for this task
					text = null;
					readOnly = true;
				}
				if (text == null || Field.NOT_APPLICABLE.equals(text)) {
					text = ""; //$NON-NLS-1$
					readOnly = true;
				}
				values[c + 2] = text;
				int indent = NAME.equals(field.getId()) ? level - 1 : 0;
				styles[c + 2] = XlsxSheetWriter.cellStyle(readOnly, indent);
				widths[c + 2] = Math.max(widths[c + 2], text.length() + indent * 2);
			}
			writer.addRow(values, styles);
			writeChildren(writer, model, node, level + 1, fields, widths);
		}
	}

	/** Children of a node in the outline, never null (getChildren returns null for a leaf) */
	static List children(NodeModel model, Node parent) {
		List children = model.getChildren(parent);
		return children == null ? Collections.EMPTY_LIST : children;
	}
}
