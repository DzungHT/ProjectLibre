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
import java.text.DateFormat;
import java.util.Arrays;
import java.util.List;

import com.projectlibre1.options.EditOption;
import com.projectlibre1.pm.assignment.board.AssignmentBoard;
import com.projectlibre1.strings.Messages;

/**
 * Exports the Assignment board, see docs/assignment-design.md, to an .xlsx of two sheets: the people of the tasks,
 * one row per task in the order of the WBS, and the load of the people. The changes not applied are exported too,
 * as they are shown in the board.
 */
public class AssignmentExcelExporter {
	private static final double[] TASK_WIDTHS = { 7, 30, 30, 45, 9, 9, 9, 10, 25, 25, 7, 45 };
	private static final double[] LOAD_WIDTHS = { 25, 7, 9, 9, 7, 12, 12, 10, 11, 11, 7, 9, 45 };

	private AssignmentExcelExporter() {
	}

	/**
	 * @param unit name of the unit of the effort (MH, MD, MM)
	 * @param unitHours hours of a unit
	 */
	public static void export(AssignmentBoard board, File file, String unit, double unitHours) throws IOException {
		XlsxSheetWriter.write(file, Arrays.asList(tasks(board, unit, unitHours), load(board, unit, unitHours)));
	}

	/** ID, module, function, task, activity, role, skill, effort, people, suggested, locked, warnings */
	private static XlsxSheetWriter tasks(AssignmentBoard board, String unit, double unitHours) {
		XlsxSheetWriter sheet = new XlsxSheetWriter(Messages.getString("AssignmentExcel.TasksSheet")); //$NON-NLS-1$
		sheet.addHeaderRow(new String[] { text("Id"), text("Module"), text("Function"), text("Task"), text("Activity"), text("Role"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
				text("Skill"), text("Effort") + " (" + unit + ")", text("People"), text("Suggestion"), text("Locked"), text("Issues") }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
		int textStyle = XlsxSheetWriter.cellStyle(false, 0);
		for (AssignmentBoard.Row row : board.getActivities()) {
			AssignmentBoard.Row module = row.getParent();
			while (module != null && module.getParent() != null)
				module = module.getParent();
			List<String> issues = board.getIssues(row);
			StringBuilder warnings = new StringBuilder();
			for (String issue : issues)
				warnings.append(warnings.length() == 0 ? "" : "; ").append(issue); //$NON-NLS-1$ //$NON-NLS-2$
			String[] values = { String.valueOf(row.getTask().getId()), module == null ? "" : module.getTask().getName(), //$NON-NLS-1$
					row.getParent() == null ? "" : row.getParent().getTask().getName(), row.getTask().getName(), //$NON-NLS-1$
					row.getTask().getActivity(), row.getRole(), row.getSkill(), number(row.getHours() / unitHours),
					AssignmentBoard.names(row.getResources()), row.getSuggestion() == null ? "" : row.getSuggestion().getName(), //$NON-NLS-1$
					row.isLocked() ? "x" : "", warnings.toString() }; //$NON-NLS-1$ //$NON-NLS-2$
			int[] styles = new int[values.length];
			Arrays.fill(styles, textStyle);
			styles[0] = XlsxSheetWriter.STYLE_INTEGER;
			styles[7] = XlsxSheetWriter.STYLE_NUMBER;
			sheet.addRow(values, styles);
		}
		sheet.setColumnWidths(TASK_WIDTHS);
		sheet.setFrozenRows(1);
		sheet.setAutoFilterRow(1);
		return sheet;
	}

	/** Person, TBD, role, skill, level, from, to, Max Units, capacity, assigned, tasks, load, warning */
	private static XlsxSheetWriter load(AssignmentBoard board, String unit, double unitHours) {
		XlsxSheetWriter sheet = new XlsxSheetWriter(Messages.getString("AssignmentExcel.LoadSheet")); //$NON-NLS-1$
		sheet.addHeaderRow(new String[] { text("Person"), text("Tbd"), text("Role"), text("Skill"), text("Level"), text("From"), text("To"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
				Messages.getString("AssignmentExcel.Units"), text("Capacity") + " (" + unit + ")", text("Assigned") + " (" + unit + ")", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
				text("TaskCount"), text("Load") + " (%)", text("Issues") }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		DateFormat dateFormat = EditOption.getInstance().getShortDateFormat();
		int textStyle = XlsxSheetWriter.cellStyle(false, 0);
		for (AssignmentBoard.Person person : board.getPersons()) {
			String issue = board.getIssue(person);
			double load = person.getLoad();
			String[] values = { person.getName(), person.isExisting() ? "" : "x", person.getRole(), person.getSkill(), person.getLevelCode(), //$NON-NLS-1$ //$NON-NLS-2$
					dateFormat.format(person.getFrom()), dateFormat.format(person.getTo() - 1), // the end of the working time of a day
					number(person.getUnits() * 100), number(person.getCapacityHours() / unitHours), number(person.getAssignedHours() / unitHours),
					String.valueOf(person.getTasks()), Double.isInfinite(load) ? "∞" : number(load), issue == null ? "" : issue }; //$NON-NLS-1$ //$NON-NLS-2$
			int[] styles = new int[values.length];
			Arrays.fill(styles, textStyle);
			for (int c = 7; c <= 9; c++)
				styles[c] = XlsxSheetWriter.STYLE_NUMBER;
			styles[10] = XlsxSheetWriter.STYLE_INTEGER;
			if (!Double.isInfinite(load))
				styles[11] = XlsxSheetWriter.STYLE_NUMBER;
			sheet.addRow(values, styles);
		}
		sheet.setColumnWidths(LOAD_WIDTHS);
		sheet.setFrozenRows(1);
		sheet.setAutoFilterRow(1);
		return sheet;
	}

	private static String text(String key) {
		return Messages.getString("AssignmentBoard." + key); //$NON-NLS-1$
	}

	/** A number for a cell of number style, with 2 decimals at most */
	private static String number(double value) {
		double rounded = Math.round(value * 100) / 100.0;
		return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
	}
}
