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
package com.projectlibre1.exchange.effort;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.projectlibre1.exchange.ulnl.UlnlWbsParser;
import com.projectlibre1.exchange.wbsexcel.XlsxSheetWriter;
import com.projectlibre1.grouping.core.Node;
import com.projectlibre1.grouping.core.model.NodeModel;
import com.projectlibre1.grouping.core.transform.filtering.TaskNameFilter;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.pm.task.Task;
import com.projectlibre1.strings.Messages;

/**
 * Effort of the leaf tasks by activity (the task name: Giai phap, Phat trien, Kiem thu, other)
 * and by status (not started, in progress, done from the percent complete).
 * Effort is the task work in man-hours, man-days (8 hours) and man-months (22 man-days),
 * rounded down to 3 decimals after the sum.
 */
public class EffortReport {
	public static final double HOURS_PER_DAY = NormalTask.HOURS_PER_MAN_DAY;
	public static final double DAYS_PER_MONTH = NormalTask.MAN_DAYS_PER_MAN_MONTH;

	public static final int NOT_STARTED = 0, IN_PROGRESS = 1, DONE = 2;
	public static final int STATUS_COUNT = 3;
	/** Activities are those of the ULNL import, then one for the other leaf tasks */
	public static final int ACTIVITY_COUNT = UlnlWbsParser.DEFAULT_ACTIVITY_NAMES.length + 1;
	public static final int OTHER = ACTIVITY_COUNT - 1;

	public static final int HOURS = 0, DAYS = 1, MONTHS = 2;
	public static final int UNIT_COUNT = 3;

	private static final String[] KEYS = new String[OTHER];
	static {
		for (int a = 0; a < OTHER; a++)
			KEYS[a] = TaskNameFilter.normalize(UlnlWbsParser.DEFAULT_ACTIVITY_NAMES[a]);
	}

	public static class Line {
		public final Task task;
		public final String parentName;
		public final int activity;
		public final int status;
		public final double hours;
		Line(Task task, String parentName, int activity, int status, double hours) {
			this.task = task;
			this.parentName = parentName;
			this.activity = activity;
			this.status = status;
			this.hours = hours;
		}
	}

	private final String projectName;
	private final List<Line> lines = new ArrayList<Line>();
	private final double[][] hours = new double[ACTIVITY_COUNT][STATUS_COUNT];
	private final int[][] counts = new int[ACTIVITY_COUNT][STATUS_COUNT];

	public EffortReport(Project project) {
		projectName = project.getName();
		NodeModel model = project.getTaskOutline();
		collect(model, null, null);
	}

	private void collect(NodeModel model, Node parent, String parentName) {
		for (Iterator i = children(model, parent).iterator(); i.hasNext();) {
			Node node = (Node) i.next();
			if (node.isVoid() || !(node.getImpl() instanceof NormalTask))
				continue;
			NormalTask task = (NormalTask) node.getImpl();
			if (task.isWbsParent() || !children(model, node).isEmpty()) {
				collect(model, node, task.getName());
			} else {
				Line line = new Line(task, parentName, activityOf(task.getName()), statusOf(task), task.getWorkHours());
				lines.add(line);
				hours[line.activity][line.status] += line.hours;
				counts[line.activity][line.status]++;
			}
		}
	}

	private static List children(NodeModel model, Node parent) {
		List children = model.getChildren(parent);
		return children == null ? Collections.EMPTY_LIST : children;
	}

	/** Activity from the task name, ignoring case and accents: "Phat trien BE" is Phat trien */
	public static int activityOf(String name) {
		if (name == null)
			return OTHER;
		String n = TaskNameFilter.normalize(name.trim());
		for (int a = 0; a < KEYS.length; a++)
			if (n.startsWith(KEYS[a]))
				return a;
		return OTHER;
	}

	public static int statusOf(Task task) {
		double pc = task.getPercentComplete();
		if (pc <= 0)
			return NOT_STARTED;
		return pc >= 1 || task.isComplete() ? DONE : IN_PROGRESS;
	}

	/** Effort in a unit, rounded down to 3 decimals */
	public static double convert(double hours, int unit) {
		switch (unit) {
		case DAYS: return NormalTask.roundEffort(hours / HOURS_PER_DAY);
		case MONTHS: return NormalTask.roundEffort(hours / HOURS_PER_DAY / DAYS_PER_MONTH);
		default: return NormalTask.roundEffort(hours);
		}
	}

	public static String getActivityName(int activity) {
		return activity == OTHER ? Messages.getString("EffortReport.Other") : UlnlWbsParser.DEFAULT_ACTIVITY_NAMES[activity]; //$NON-NLS-1$
	}
	public static String getStatusName(int status) {
		return Messages.getString("EffortReport.Status" + status); //$NON-NLS-1$
	}
	public static String getUnitName(int unit) {
		return Messages.getString("EffortReport.Unit" + unit); //$NON-NLS-1$
	}

	public List<Line> getLines() {
		return lines;
	}

	/** @param activity an activity, or -1 for all
	 *  @param status a status, or -1 for all */
	public double getHours(int activity, int status) {
		double sum = 0;
		for (int a = 0; a < ACTIVITY_COUNT; a++)
			for (int s = 0; s < STATUS_COUNT; s++)
				if ((activity < 0 || activity == a) && (status < 0 || status == s))
					sum += hours[a][s];
		return sum;
	}
	public double getEffort(int activity, int status, int unit) {
		return convert(getHours(activity, status), unit);
	}
	/** Same arguments as {@link #getHours(int, int)} */
	public int getCount(int activity, int status) {
		int sum = 0;
		for (int a = 0; a < ACTIVITY_COUNT; a++)
			for (int s = 0; s < STATUS_COUNT; s++)
				if ((activity < 0 || activity == a) && (status < 0 || status == s))
					sum += counts[a][s];
		return sum;
	}

	/** Header of the summary tables: activity, each status, total */
	public static String[] summaryHeader(String first) {
		String[] header = new String[STATUS_COUNT + 2];
		header[0] = first;
		for (int s = 0; s < STATUS_COUNT; s++)
			header[s + 1] = getStatusName(s);
		header[STATUS_COUNT + 1] = Messages.getString("EffortReport.Total"); //$NON-NLS-1$
		return header;
	}

	/**
	 * One sheet: a summary table per unit, the task counts, then one row per leaf task.
	 */
	public void export(File file) throws IOException {
		XlsxSheetWriter writer = new XlsxSheetWriter(Messages.getString("EffortReport.Sheet")); //$NON-NLS-1$
		String title = Messages.getString("EffortReport.Title"); //$NON-NLS-1$
		if (projectName != null && projectName.trim().length() > 0)
			title += " - " + projectName.trim(); //$NON-NLS-1$
		writer.addRow(new String[] {title}, new int[] {XlsxSheetWriter.STYLE_TITLE});
		writer.addRow(new String[] {Messages.getString("EffortReport.Note")}, new int[] {0}); //$NON-NLS-1$

		for (int unit = 0; unit <= UNIT_COUNT; unit++) { // UNIT_COUNT for the task counts
			writer.addRow(new String[0], new int[0]);
			String name = unit == UNIT_COUNT ? Messages.getString("EffortReport.TaskCount") : getUnitName(unit); //$NON-NLS-1$
			writer.addRow(new String[] {name}, new int[] {XlsxSheetWriter.STYLE_TITLE});
			writer.addHeaderRow(summaryHeader(Messages.getString("EffortReport.Activity"))); //$NON-NLS-1$
			for (int a = 0; a <= ACTIVITY_COUNT; a++) { // ACTIVITY_COUNT for the total
				boolean total = a == ACTIVITY_COUNT;
				int activity = total ? -1 : a;
				String[] values = new String[STATUS_COUNT + 2];
				int[] styles = new int[values.length];
				values[0] = total ? Messages.getString("EffortReport.Total") : getActivityName(a); //$NON-NLS-1$
				styles[0] = total ? XlsxSheetWriter.STYLE_TOTAL : XlsxSheetWriter.cellStyle(false, 0);
				for (int s = 0; s <= STATUS_COUNT; s++) {
					int status = s == STATUS_COUNT ? -1 : s;
					values[s + 1] = unit == UNIT_COUNT ? String.valueOf(getCount(activity, status)) : number(getEffort(activity, status, unit));
					styles[s + 1] = unit == UNIT_COUNT ? (total ? XlsxSheetWriter.STYLE_TOTAL_INTEGER : XlsxSheetWriter.STYLE_INTEGER)
						: (total ? XlsxSheetWriter.STYLE_TOTAL_NUMBER : XlsxSheetWriter.STYLE_NUMBER);
				}
				writer.addRow(values, styles);
			}
		}

		writer.addRow(new String[0], new int[0]);
		writer.addRow(new String[] {Messages.getString("EffortReport.Details")}, new int[] {XlsxSheetWriter.STYLE_TITLE}); //$NON-NLS-1$
		writer.addHeaderRow(new String[] {
			Messages.getString("EffortReport.Id"), //$NON-NLS-1$
			Messages.getString("EffortReport.Parent"), //$NON-NLS-1$
			Messages.getString("EffortReport.Name"), //$NON-NLS-1$
			Messages.getString("EffortReport.Activity"), //$NON-NLS-1$
			Messages.getString("EffortReport.StatusColumn"), //$NON-NLS-1$
			Messages.getString("EffortReport.PercentComplete"), //$NON-NLS-1$
			getUnitName(HOURS), getUnitName(DAYS), getUnitName(MONTHS)});
		writer.setAutoFilterRow(writer.getRowCount());
		int text = XlsxSheetWriter.cellStyle(false, 0);
		for (Line line : lines) {
			writer.addRow(new String[] {
				String.valueOf(line.task.getId()),
				line.parentName == null ? "" : line.parentName, //$NON-NLS-1$
				line.task.getName(),
				getActivityName(line.activity),
				getStatusName(line.status),
				number(line.task.getPercentComplete() * 100),
				number(line.hours), number(convert(line.hours, DAYS)), number(convert(line.hours, MONTHS))
			}, new int[] {XlsxSheetWriter.STYLE_INTEGER, text, text, text, text, XlsxSheetWriter.STYLE_NUMBER,
				XlsxSheetWriter.STYLE_NUMBER, XlsxSheetWriter.STYLE_NUMBER, XlsxSheetWriter.STYLE_NUMBER});
		}
		writer.setColumnWidths(new double[] {18, 40, 30, 16, 16, 14, 14, 14, 14});
		writer.write(file);
	}

	private static String number(double value) {
		return BigDecimal.valueOf(NormalTask.roundEffort(value)).stripTrailingZeros().toPlainString();
	}
}
