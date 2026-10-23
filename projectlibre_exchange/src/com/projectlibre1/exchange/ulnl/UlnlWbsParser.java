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
import java.util.List;
import java.util.Map;

/**
 * Builds a WBS tree from the "NL Chuc nang" sheet of a ULNL (effort estimation) workbook.
 * <ul>
 * <li>Level 1: column C when D and E are empty</li>
 * <li>Level 2: column C when D has a value. C may be filled only on the first row of a group
 * or repeated on every row: consecutive rows with the same C are grouped</li>
 * <li>Level 3: column D, consecutive rows with the same D are grouped</li>
 * <li>Level 4: column E, one per row</li>
 * <li>Level 5: one task per activity, work in hours read from the activity's column</li>
 * </ul>
 * Rows where C, D and E are all empty (totals) are skipped.
 */
public class UlnlWbsParser {
	public static final String DEFAULT_SHEET = "NL Chuc nang"; //$NON-NLS-1$
	public static final int DEFAULT_START_ROW = 8;
	public static final int HEADER_ROW = 6;
	public static final String[] DEFAULT_ACTIVITY_NAMES = { "Giải pháp", "Phát triển", "Kiểm thử" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	public static final String[] DEFAULT_ACTIVITY_COLUMNS = { "F", "G", "I" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	private static final String C = "C", D = "D", E = "E"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	public static class WbsItem {
		private final String name;
		private final int level;
		private final double work; // hours, only for activities
		private final int activityIndex; // -1 when not an activity
		private final List<WbsItem> children = new ArrayList<WbsItem>();

		WbsItem(String name, int level) {
			this(name, level, 0, -1);
		}
		WbsItem(String name, int level, double work, int activityIndex) {
			this.name = name;
			this.level = level;
			this.work = work;
			this.activityIndex = activityIndex;
		}
		public String getName() { return name; }
		public int getLevel() { return level; }
		public double getWork() { return work; }
		public List<WbsItem> getChildren() { return children; }
		public int getActivityIndex() { return activityIndex; }
		public boolean isActivity() { return activityIndex >= 0; }
	}

	/** Counts per level and total work per activity, for the preview */
	public static class Summary {
		public final int[] countByLevel = new int[6];
		public final double[] workByActivity;

		Summary(int activities) {
			workByActivity = new double[activities];
		}
	}

	private final String[] activityNames;
	private final String[] activityColumns;
	private final int startRow;

	public UlnlWbsParser(int startRow, String[] activityNames, String[] activityColumns) {
		this.startRow = startRow;
		this.activityNames = activityNames;
		this.activityColumns = activityColumns;
	}

	public List<WbsItem> parse(Map<Integer, Map<String, String>> rows) {
		List<WbsItem> roots = new ArrayList<WbsItem>();
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
				roots.add(level1);
				level2 = level3 = null;
				continue;
			}
			if (c != null && (level2 == null || !level2.getName().equals(c))) {
				level2 = new WbsItem(c, 2);
				addTo(level1, level2, roots);
				level3 = null;
			}
			String level3Name = d != null ? d : e;
			if (level3 == null || !level3.getName().equals(level3Name)) {
				level3 = new WbsItem(level3Name, 3);
				addTo(level2 != null ? level2 : level1, level3, roots);
			}
			WbsItem level4 = new WbsItem(e != null ? e : d, 4);
			level3.getChildren().add(level4);
			for (int i = 0; i < activityNames.length; i++)
				level4.getChildren().add(new WbsItem(activityNames[i], 5, number(row, activityColumns[i]), i));
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
			summarize(item.getChildren(), summary);
		}
	}

	private static void addTo(WbsItem parent, WbsItem child, List<WbsItem> roots) {
		if (parent == null)
			roots.add(child);
		else
			parent.getChildren().add(child);
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
