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
package com.projectlibre1.exchange.ulnl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a WBS tree from the "NL Chuc nang" sheet of a ULNL (effort estimation) workbook.
 * <ul>
 * <li>Level 1: column C when D and E are empty</li>
 * <li>Level 2: column C when D has a value. C may be filled only on the first row of a group
 * or repeated on every row: consecutive rows with the same C are grouped</li>
 * <li>Level 3: column D, consecutive rows with the same D are grouped</li>
 * <li>Level 4: column E, one per row, with the function code of the row (column L by default)</li>
 * <li>Level 5: one task per activity, work in hours read from the activity's column</li>
 * </ul>
 * Rows where C, D and E are all empty (totals) are skipped.
 * <p>
 * Each item has a key made of the names of the items above it and its own name (with the number of the
 * occurrence if the same name is used more than once under the same parent), and the activity code for
 * activities, so that a task can be found again when a new version of the ULNL is imported.
 */
public class UlnlWbsParser {
	public static final String DEFAULT_SHEET = "NL Chuc nang"; //$NON-NLS-1$
	public static final int DEFAULT_START_ROW = 8;
	public static final int HEADER_ROW = 6;
	public static final String[] DEFAULT_ACTIVITY_NAMES = { "Giải pháp", "Phát triển", "Kiểm thử" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	public static final String[] DEFAULT_ACTIVITY_CODES = { "GP", "PT", "KT" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	/** Activities whose tasks are linked by lane (BE, FE, Mobile...), see the function codes */
	public static final String[] LANE_ACTIVITY_CODES = { "PT" }; //$NON-NLS-1$
	public static final String[] DEFAULT_ACTIVITY_COLUMNS = { "F", "G", "I" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	public static final String DEFAULT_FUNCTION_CODE_COLUMN = "L"; //$NON-NLS-1$
	/** Separators of the key: between the names of the levels, before the occurrence number and before the activity code */
	public static final String KEY_LEVEL = " > ", KEY_OCCURRENCE = " #", KEY_ACTIVITY = " | "; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	private static final String C = "C", D = "D", E = "E"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	public static class WbsItem {
		private final String name;
		private final int level;
		private final double work; // hours, only for activities
		private final int activityIndex; // -1 when not an activity
		private final String activityCode; // null when not an activity
		private final List<WbsItem> children = new ArrayList<WbsItem>();
		private final Map<String, Integer> childNames = new HashMap<String, Integer>(); // occurrences of the names of the children
		private String key;
		private String functionCode; // only for level 4 and activities
		private String lane; // from the function code

		WbsItem(String name, int level) {
			this(name, level, 0, -1, null);
		}
		WbsItem(String name, int level, double work, int activityIndex, String activityCode) {
			this.name = name;
			this.level = level;
			this.work = work;
			this.activityIndex = activityIndex;
			this.activityCode = activityCode;
		}
		public String getName() { return name; }
		public int getLevel() { return level; }
		public double getWork() { return work; }
		public List<WbsItem> getChildren() { return children; }
		public int getActivityIndex() { return activityIndex; }
		public boolean isActivity() { return activityIndex >= 0; }
		public String getActivityCode() { return activityCode; }
		public String getKey() { return key; }
		public String getFunctionCode() { return functionCode; }
		public String getLane() { return lane; }
		public void setLane(String lane) { this.lane = lane; }

		/** Adds a child and gives it its key */
		void add(WbsItem child) {
			children.add(child);
			child.key = childKey(key, child, childNames);
		}
	}

	/** Counts per level, total work per activity and number of rows per function code, for the preview */
	public static class Summary {
		public final int[] countByLevel = new int[6];
		public final double[] workByActivity;
		/** Rows (level 4 items) per function code, in the order of first use. The empty code is "" */
		public final Map<String, Integer> rowsByFunctionCode = new LinkedHashMap<String, Integer>();

		Summary(int activities) {
			workByActivity = new double[activities];
		}
	}

	private final String[] activityNames;
	private final String[] activityCodes;
	private final String[] activityColumns;
	private final String functionCodeColumn;
	private final int startRow;

	public UlnlWbsParser(int startRow, String[] activityNames, String[] activityCodes, String[] activityColumns, String functionCodeColumn) {
		this.startRow = startRow;
		this.activityNames = activityNames;
		this.activityCodes = activityCodes;
		this.activityColumns = activityColumns;
		this.functionCodeColumn = functionCodeColumn;
	}

	public List<WbsItem> parse(Map<Integer, Map<String, String>> rows) {
		List<WbsItem> roots = new ArrayList<WbsItem>();
		Map<String, Integer> rootNames = new HashMap<String, Integer>();
		WbsItem level1 = null, level2 = null, level3 = null;
		for (Map.Entry<Integer, Map<String, String>> entry : rows.entrySet()) {
			if (entry.getKey() < startRow)
				continue;
			Map<String, String> row = entry.getValue();
			String c = text(row, C), d = text(row, D), e = text(row, E);
			if (c == null && d == null && e == null)
				continue;

			if (c != null && d == null && e == null) {
				level1 = new WbsItem(c, 1);
				addTo(null, level1, roots, rootNames);
				level2 = level3 = null;
				continue;
			}
			if (c != null && (level2 == null || !level2.getName().equals(c))) {
				level2 = new WbsItem(c, 2);
				addTo(level1, level2, roots, rootNames);
				level3 = null;
			}
			String level3Name = d != null ? d : e;
			if (level3 == null || !level3.getName().equals(level3Name)) {
				level3 = new WbsItem(level3Name, 3);
				addTo(level2 != null ? level2 : level1, level3, roots, rootNames);
			}
			WbsItem level4 = new WbsItem(e != null ? e : d, 4);
			level4.functionCode = functionCodeColumn == null ? null : text(row, functionCodeColumn);
			level3.add(level4);
			for (int i = 0; i < activityNames.length; i++) {
				WbsItem activity = new WbsItem(activityNames[i], 5, number(row, activityColumns[i]), i, activityCodes[i]);
				activity.functionCode = level4.functionCode;
				level4.add(activity);
			}
		}
		return roots;
	}

	public Summary summarize(List<WbsItem> roots) {
		Summary summary = new Summary(activityNames.length);
		summarize(roots, summary);
		return summary;
	}

	private void summarize(List<WbsItem> items, Summary summary) {
		for (WbsItem item : items) {
			summary.countByLevel[item.getLevel()]++;
			if (item.isActivity())
				summary.workByActivity[item.getActivityIndex()] += item.getWork();
			if (item.getLevel() == 4) {
				String code = item.getFunctionCode() == null ? "" : item.getFunctionCode(); //$NON-NLS-1$
				Integer count = summary.rowsByFunctionCode.get(code);
				summary.rowsByFunctionCode.put(code, count == null ? 1 : count + 1);
			}
			summarize(item.getChildren(), summary);
		}
	}

	/** Sets the lane of the level 4 items and of their activities from their function code. Codes not in the map have no lane */
	public static void applyLanes(List<WbsItem> items, Map<String, String> laneByFunctionCode) {
		for (WbsItem item : items) {
			if (item.getLevel() >= 4) {
				String lane = laneByFunctionCode.get(item.getFunctionCode() == null ? "" : item.getFunctionCode()); //$NON-NLS-1$
				item.setLane(lane == null || lane.trim().length() == 0 ? null : lane.trim());
			}
			applyLanes(item.getChildren(), laneByFunctionCode);
		}
	}

	private static void addTo(WbsItem parent, WbsItem child, List<WbsItem> roots, Map<String, Integer> rootNames) {
		if (parent != null) {
			parent.add(child);
			return;
		}
		roots.add(child);
		child.key = childKey(null, child, rootNames);
	}

	/** Key of a child: key of the parent, then the activity code or the name and its occurrence */
	private static String childKey(String parentKey, WbsItem child, Map<String, Integer> names) {
		if (child.isActivity())
			return parentKey + KEY_ACTIVITY + child.getActivityCode();
		Integer count = names.get(child.getName());
		int occurrence = count == null ? 1 : count + 1;
		names.put(child.getName(), occurrence);
		String key = parentKey == null ? child.getName() : parentKey + KEY_LEVEL + child.getName();
		return occurrence == 1 ? key : key + KEY_OCCURRENCE + occurrence;
	}

	private static String text(Map<String, String> row, String column) {
		String s = row.get(column);
		if (s == null)
			return null;
		s = s.trim();
		return s.length() == 0 ? null : s;
	}

	private static double number(Map<String, String> row, String column) {
		String s = text(row, column);
		if (s == null)
			return 0;
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
