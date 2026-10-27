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
package com.projectlibre1.dialog;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.resource.plan.ResourcePlan;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.strings.Messages;

/**
 * Effort ratio of each activity of the ULNL (GP done by the BA, PT by the DEV, KT by the TESTER): the part of the
 * original estimate planned as work, the rest being kept as reserve, see Project.getEffortRatio(). The table shows,
 * for the ratios typed, the effort planned and the reserve. A project whose ratios were never set starts from the
 * ratios used last.
 */
public final class EffortRatioDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final Preferences preferences = Preferences.userNodeForPackage(EffortRatioDialog.class).node("effortRatios"); //$NON-NLS-1$
	/** Ratios, %, of a first project: the BA plan half of the ULNL, the DEV and the TESTER 80% */
	private static final String[][] DEFAULT_PERCENTS = { { "GP", "50" }, { "PT", "80" }, { "KT", "80" } }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	private static final int MIN_PERCENT = 1, MAX_PERCENT = 200;
	private static final double MILLIS_PER_HOUR = 60 * 60 * 1000;
	/** Units of the effort, as in the Resource Plan, whose unit is shared */
	private static final String[] UNITS = { "MH", "MD", "MM" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	private static final double[] UNIT_HOURS = { 1, NormalTask.HOURS_PER_MAN_DAY, NormalTask.HOURS_PER_MAN_DAY * NormalTask.MAN_DAYS_PER_MAN_MONTH };
	private static final String UNIT = "resourcePlan.unit"; //$NON-NLS-1$
	private static final int DEFAULT_UNIT = 1;
	private static final int WIDTH = 600;
	private static final int ACTIVITY = 0, TASKS = 1, EDITED = 2, ESTIMATE = 3, RATIO = 4, PLANNED = 5, RESERVE = 6;

	private final String[] activities = ResourcePlan.activities();
	private final int[] percents = new int[activities.length];
	private final int[] tasks = new int[activities.length];
	/** Tasks not started whose work is not their planned estimate: changed by hand */
	private final int[] edited = new int[activities.length];
	private final double[] estimateHours = new double[activities.length];
	/** Titles of the columns, {0} being the unit of the effort columns */
	private final String[] titles = { Messages.getString("EffortRatio.Activity"), Messages.getString("EffortRatio.Tasks"), //$NON-NLS-1$ //$NON-NLS-2$
			Messages.getString("EffortRatio.Edited"), Messages.getString("EffortRatio.Estimate"), Messages.getString("EffortRatio.Ratio"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			Messages.getString("EffortRatio.Planned"), Messages.getString("EffortRatio.Reserve") }; //$NON-NLS-1$ //$NON-NLS-2$
	private final NumberFormat unitFormat = NumberFormat.getNumberInstance();
	private int unit = DEFAULT_UNIT; // index in UNITS
	private JTable table;
	private JCheckBox includeEdited;
	private Map<String, Double> ratios;

	/** What the dialog chose */
	public static class Result {
		/** Ratio by activity */
		public final Map<String, Double> ratios;
		/** True to also set the work of the tasks changed by hand */
		public final boolean includeEdited;

		Result(Map<String, Double> ratios, boolean includeEdited) {
			this.ratios = ratios;
			this.includeEdited = includeEdited;
		}
	}

	/** Returns the ratios chosen, or null if cancelled */
	public static Result doDialog(Project project) {
		EffortRatioDialog dialog = new EffortRatioDialog(project);
		return dialog.doModal() ? new Result(dialog.ratios, dialog.includeEdited.isSelected()) : null;
	}

	private EffortRatioDialog(Project project) {
		super(GraphicManager.getFrameInstance(), Messages.getString("EffortRatio.Title"), true); //$NON-NLS-1$
		boolean set = !project.getEffortRatios().isEmpty();
		for (int i = 0; i < activities.length; i++)
			percents[i] = set ? (int) Math.round(project.getEffortRatio(activities[i]) * 100) : preferences.getInt(activities[i], defaultPercent(activities[i]));
		for (Iterator i = project.getTaskOutlineIterator(); i.hasNext();) {
			Object impl = i.next();
			if (!(impl instanceof NormalTask))
				continue;
			NormalTask task = (NormalTask) impl;
			int activity = indexOf(task.getActivity());
			if (activity < 0 || task.isWbsParent() || task.isCancelled() || !task.hasOriginalEstimate())
				continue;
			tasks[activity]++;
			estimateHours[activity] += task.getOriginalEstimate() / MILLIS_PER_HOUR;
			if (task.getActualWork(null) == 0 && !task.isWorkPlannedEstimate(task.getWork(null)))
				edited[activity]++;
		}
		try {
			unit = Math.max(0, Math.min(UNITS.length - 1, preferences.parent().getInt(UNIT, DEFAULT_UNIT)));
		} catch (Exception e) { // preferences not available
		}
		setUnitFormat();
	}

	private static int defaultPercent(String activity) {
		for (String[] percent : DEFAULT_PERCENTS)
			if (percent[0].equals(activity))
				return Integer.parseInt(percent[1]);
		return 100;
	}

	private int indexOf(String activity) {
		if (activity != null)
			for (int i = 0; i < activities.length; i++)
				if (activities[i].equalsIgnoreCase(activity))
					return i;
		return -1;
	}

	protected boolean bind(boolean get) {
		if (get)
			return true;
		if (table.isEditing())
			table.getCellEditor().stopCellEditing();
		ratios = new LinkedHashMap<String, Double>();
		for (int i = 0; i < activities.length; i++) {
			ratios.put(activities[i], percents[i] / 100.0);
			preferences.putInt(activities[i], percents[i]);
		}
		return true;
	}

	public JComponent createContentPanel() {
		final int totalRow = activities.length;
		table = new JTable(new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
			public int getRowCount() {
				return activities.length + 1;
			}
			public int getColumnCount() {
				return titles.length;
			}
			public String getColumnName(int column) {
				return columnName(column, UNITS[unit]);
			}
			public Class<?> getColumnClass(int column) {
				return column == RATIO ? Integer.class : Object.class;
			}
			public Object getValueAt(int row, int column) {
				if (row == totalRow)
					return totalAt(column);
				switch (column) {
				case ACTIVITY:
					return activities[row] + " — " + ResourcePlan.role(activities[row]); //$NON-NLS-1$
				case TASKS:
					return tasks[row];
				case EDITED:
					return edited[row];
				case ESTIMATE:
					return amount(estimateHours[row]);
				case RATIO:
					return percents[row];
				case PLANNED:
					return amount(planned(row));
				default:
					return amount(estimateHours[row] - planned(row));
				}
			}
			private Object totalAt(int column) {
				int count = 0;
				double estimate = 0, planned = 0;
				for (int i = 0; i < activities.length; i++) {
					count += column == EDITED ? edited[i] : tasks[i];
					estimate += estimateHours[i];
					planned += planned(i);
				}
				switch (column) {
				case ACTIVITY:
					return Messages.getString("EffortRatio.Total"); //$NON-NLS-1$
				case TASKS:
				case EDITED:
					return count;
				case ESTIMATE:
					return amount(estimate);
				case RATIO:
					return estimate == 0 ? null : (int) Math.round(planned / estimate * 100);
				case PLANNED:
					return amount(planned);
				default:
					return amount(estimate - planned);
				}
			}
			public boolean isCellEditable(int row, int column) {
				return column == RATIO && row < totalRow;
			}
			public void setValueAt(Object value, int row, int column) {
				if (!(value instanceof Number))
					return;
				percents[row] = Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, ((Number) value).intValue()));
				fireTableRowsUpdated(row, totalRow);
			}
		}) {
			private static final long serialVersionUID = 1L;
			// as in the Resource Plan: white cells can be typed, gray ones cannot, the total is a blue row in bold
			public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
				Component c = super.prepareRenderer(renderer, row, column);
				boolean total = row == totalRow;
				if (!isCellSelected(row, column))
					c.setBackground(total ? ResourcePlanDialog.TOTAL : isCellEditable(row, column) ? getBackground() : ResourcePlanDialog.READ_ONLY);
				c.setFont(total ? getFont().deriveFont(Font.BOLD) : getFont());
				return c;
			}
		};
		DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				setHorizontalAlignment(column == ACTIVITY ? SwingConstants.LEFT : SwingConstants.RIGHT);
				return c;
			}
		};
		table.setDefaultRenderer(Object.class, renderer);
		table.setDefaultRenderer(Integer.class, renderer);
		// each column is as wide as its title in any unit, so that no title is cut whatever the language
		TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
		int tableWidth = 0;
		for (int column = ACTIVITY; column <= RESERVE; column++) {
			TableColumn tableColumn = table.getColumnModel().getColumn(column);
			int titleWidth = 0;
			for (String unitName : UNITS) {
				String title = columnName(column, unitName);
				titleWidth = Math.max(titleWidth, headerRenderer.getTableCellRendererComponent(table, title, false, false, -1, column).getPreferredSize().width);
			}
			tableColumn.setPreferredWidth(Math.max(column == ACTIVITY ? 130 : 70, titleWidth + 12));
			tableWidth += tableColumn.getPreferredWidth();
		}
		table.setRowSelectionAllowed(false);
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(tableWidth + 4, table.getTableHeader().getPreferredSize().height + (activities.length + 1) * table.getRowHeight() + 4));

		int editedTotal = 0;
		for (int count : edited)
			editedTotal += count;
		includeEdited = WrappedText.wrap(new JCheckBox(Messages.getString("EffortRatio.IncludeEdited")), WIDTH); //$NON-NLS-1$
		includeEdited.setEnabled(editedTotal > 0);

		FormLayout layout = new FormLayout(WrappedText.column(WIDTH), ""); //$NON-NLS-1$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		builder.append(WrappedText.label(Messages.getString("EffortRatio.Help"), WIDTH)); //$NON-NLS-1$
		builder.append(unitChooser());
		builder.append(scroll);
		builder.append(includeEdited);
		builder.append(WrappedText.label(Messages.getString("EffortRatio.Note"), WIDTH)); //$NON-NLS-1$
		return builder.getPanel();
	}

	/** Effort planned for an activity with the ratio typed, man-hours */
	private double planned(int activity) {
		return estimateHours[activity] * percents[activity] / 100.0;
	}

	/** Man-hours in the unit shown */
	private String amount(double hours) {
		return unitFormat.format(hours / UNIT_HOURS[unit]);
	}

	/** Title of a column, with the unit for the effort columns */
	private String columnName(int column, String unitName) {
		return column == ESTIMATE || column == PLANNED || column == RESERVE ? MessageFormat.format(titles[column], unitName) : titles[column];
	}

	private void setUnitFormat() {
		unitFormat.setMinimumFractionDigits(0);
		unitFormat.setMaximumFractionDigits(unit == 2 ? 2 : 1); // the man-months are small numbers
	}

	/** A choice of the unit of the effort columns, the same as the one of the Resource Plan: changing one changes both */
	private JComponent unitChooser() {
		final JComboBox units = new JComboBox(UNITS);
		units.setSelectedIndex(unit);
		units.setToolTipText(Messages.getString("EffortRatio.UnitTooltip")); //$NON-NLS-1$
		units.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (table.isEditing())
					table.getCellEditor().stopCellEditing();
				unit = units.getSelectedIndex();
				setUnitFormat();
				for (int column = 0; column < table.getColumnCount(); column++)
					table.getColumnModel().getColumn(column).setHeaderValue(table.getModel().getColumnName(column));
				table.getTableHeader().repaint();
				((AbstractTableModel) table.getModel()).fireTableDataChanged();
				try {
					preferences.parent().putInt(UNIT, unit);
				} catch (Exception ex) { // preferences not available: kept until the dialog is closed
				}
			}
		});
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		JLabel label = new JLabel(Messages.getString("ResourcePlan.Unit")); //$NON-NLS-1$
		label.setToolTipText(units.getToolTipText());
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
		panel.add(label);
		panel.add(units);
		return panel;
	}
}
