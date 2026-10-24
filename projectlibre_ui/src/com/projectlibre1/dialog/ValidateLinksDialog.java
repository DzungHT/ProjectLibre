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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.pm.graphic.IconManager;
import com.projectlibre1.pm.criticalpath.ScheduleValidator;
import com.projectlibre1.pm.graphic.frames.DocumentFrame;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.graphic.spreadsheet.SpreadSheet;
import com.projectlibre1.pm.task.Task;
import com.projectlibre1.strings.Messages;

/**
 * Tasks whose dates do not follow their links or constraints, see ScheduleValidator. The dialog is not modal:
 * selecting a row selects its task, to look at it in the Gantt while the dialog stays open.
 */
public final class ValidateLinksDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final int SEVERITY = 0, ID = 1, TASK = 2, RULE = 3, DATE = 4, LIMIT = 5;
	/** Background of the rows of each severity */
	private static final Color[] COLORS = {
			new Color(0xFBE3E3), // error
			new Color(0xFFE0B2), // conflict
			new Color(0xFFF4CC) }; // warning
	private static ValidateLinksDialog shown = null;

	private final DocumentFrame frame;
	private final ScheduleValidator validator;
	private final List<ScheduleValidator.Issue> issues;
	private JTable table;
	private TableRowSorter<IssueModel> sorter;

	/** Shows the result of the validation, in place of the one shown before */
	public static void show(DocumentFrame frame, ScheduleValidator validator) {
		if (shown != null)
			shown.dispose();
		shown = new ValidateLinksDialog(frame, validator);
		shown.pack();
		shown.setLocationRelativeTo(shown.getParent());
		shown.setVisible(true);
	}

	private ValidateLinksDialog(DocumentFrame frame, ScheduleValidator validator) {
		super(GraphicManager.getFrameInstance(), Messages.getString("ValidateLinks.Title") + " - " + frame.getProject().getName(), false); //$NON-NLS-1$ //$NON-NLS-2$
		this.frame = frame;
		this.validator = validator;
		this.issues = validator.getIssues();
	}

	protected boolean hasCloseButton() {
		return true;
	}

	public JComponent createContentPanel() {
		if (issues.isEmpty()) { // only the verdict: the legend of the severities would make it look like a problem
			FormLayout layout = new FormLayout("p", ""); //$NON-NLS-1$ //$NON-NLS-2$
			DefaultFormBuilder builder = new DefaultFormBuilder(layout);
			builder.setDefaultDialogBorder();
			builder.append(new JLabel(MessageFormat.format(Messages.getString("ValidateLinks.AllRight"), //$NON-NLS-1$
					String.valueOf(validator.getLinkCount()), String.valueOf(validator.getConstraintCount())), IconManager.getIcon("dialog.ok"), JLabel.LEFT)); //$NON-NLS-1$
			if (validator.getStartedCount() > 0)
				builder.append(new JLabel(MessageFormat.format(Messages.getString("ValidateLinks.StartedNotChecked"), //$NON-NLS-1$
						String.valueOf(validator.getStartedCount()))));
			return builder.getPanel();
		}
		FormLayout layout = new FormLayout("460dlu:grow", ""); //$NON-NLS-1$ //$NON-NLS-2$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		String summary = MessageFormat.format(Messages.getString("ValidateLinks.Summary"), //$NON-NLS-1$
				String.valueOf(validator.getLinkCount()), String.valueOf(validator.getConstraintCount()), String.valueOf(validator.getStartedCount()),
				String.valueOf(validator.count(ScheduleValidator.ERROR)), String.valueOf(validator.count(ScheduleValidator.CONFLICT)),
				String.valueOf(validator.count(ScheduleValidator.WARNING)));
		builder.append(new JLabel(summary));
		builder.append(createFilter());
		builder.append(createTable());
		builder.append(new JLabel("<html><div style='width:740px'>" + Messages.getString("ValidateLinks.Help") + "</div></html>")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return builder.getPanel();
	}

	private JComponent createTable() {
		IssueModel model = new IssueModel();
		table = new JTable(model);
		sorter = new TableRowSorter<IssueModel>(model);
		table.setRowSorter(sorter);
		table.setDefaultRenderer(Object.class, new IssueRenderer());
		table.setDefaultRenderer(Long.class, new IssueRenderer());
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		int[] widths = { 60, 50, 290, 140, 150, 150 };
		for (int c = 0; c < widths.length; c++)
			table.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				int row = table.getSelectedRow();
				if (!e.getValueIsAdjusting() && row >= 0)
					select(issues.get(table.convertRowIndexToModel(row)).getTask());
			}
		});
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(780, 360));
		return scroll;
	}

	private JComponent createFilter() {
		String[] filters = new String[4];
		filters[0] = MessageFormat.format(Messages.getString("ValidateLinks.All"), String.valueOf(issues.size())); //$NON-NLS-1$
		for (int s = 0; s < 3; s++)
			filters[s + 1] = severityName(s) + " (" + validator.count(s) + ")"; //$NON-NLS-1$ //$NON-NLS-2$
		final JComboBox filter = new JComboBox(filters);
		filter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final int severity = filter.getSelectedIndex() - 1;
				sorter.setRowFilter(severity < 0 ? null : new RowFilter<IssueModel, Integer>() {
					public boolean include(Entry<? extends IssueModel, ? extends Integer> entry) {
						return issues.get(entry.getIdentifier()).getSeverity() == severity;
					}
				});
			}
		});
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		panel.add(new JLabel(Messages.getString("ValidateLinks.Show"))); //$NON-NLS-1$
		panel.add(filter);
		return panel;
	}

	/** Selects the task in the task sheet of the project, if it is shown */
	private void select(Task task) {
		SpreadSheet spreadSheet = frame.getTopSpreadSheet();
		if (spreadSheet == null)
			return;
		spreadSheet.selectObject(task);
		frame.doScrollToTask();
	}

	private static String severityName(int severity) {
		return Messages.getString("ValidateLinks.Severity" + severity); //$NON-NLS-1$
	}

	/** Path of names of a task: its parents, then its name */
	private static String path(Task task) {
		StringBuilder path = new StringBuilder();
		for (Task t = task; t != null; t = t.getWbsParentTask())
			path.insert(0, path.length() == 0 ? t.getName() : t.getName() + " > "); //$NON-NLS-1$
		return path.toString();
	}

	private class IssueModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private final String[] header = { Messages.getString("ValidateLinks.Severity"), Messages.getString("ValidateLinks.Id"), //$NON-NLS-1$ //$NON-NLS-2$
				Messages.getString("ValidateLinks.Task"), Messages.getString("ValidateLinks.Rule"), //$NON-NLS-1$ //$NON-NLS-2$
				Messages.getString("ValidateLinks.Date"), Messages.getString("ValidateLinks.Limit") }; //$NON-NLS-1$ //$NON-NLS-2$
		public int getRowCount() {
			return issues.size();
		}
		public int getColumnCount() {
			return header.length;
		}
		public String getColumnName(int column) {
			return header[column];
		}
		public Class<?> getColumnClass(int column) {
			return column == ID ? Long.class : String.class;
		}
		public Object getValueAt(int row, int column) {
			ScheduleValidator.Issue issue = issues.get(row);
			switch (column) {
			case SEVERITY: return issue.getSeverityText();
			case ID: return Long.valueOf(issue.getTask().getId());
			case TASK: // the name and the one of the parent, the whole path as tooltip
				Task parent = issue.getTask().getWbsParentTask();
				return parent == null ? issue.getTask().getName() : parent.getName() + " > " + issue.getTask().getName(); //$NON-NLS-1$
			case RULE: return issue.getRuleText();
			case DATE: return issue.getDateText();
			default: return issue.getLimitText();
			}
		}
	}

	/** Background by severity, full text as tooltip */
	private class IssueRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			ScheduleValidator.Issue issue = issues.get(table.convertRowIndexToModel(row));
			if (!isSelected)
				c.setBackground(COLORS[issue.getSeverity()]);
			if (table.convertColumnIndexToModel(column) == TASK)
				setToolTipText(path(issue.getTask()));
			else
				setToolTipText(value == null ? null : value.toString());
			return c;
		}
	}
}
