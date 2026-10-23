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
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.exchange.ulnl.UlnlWbsDiff;
import com.projectlibre1.exchange.ulnl.UlnlWbsParser;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Task;
import com.projectlibre1.strings.Messages;

/**
 * Changes between the WBS and a new version of the ULNL, like a diff: each change can be applied or not.
 * Unchanged rows are not shown. The changes can be filtered by type, and applied or not all at once.
 */
public final class UlnlDiffDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final int APPLY = 0, TYPE = 1, TASK = 2, CURRENT = 3, NEW = 4;
	/** Background of the rows of each type of change */
	private static final Color[] COLORS = {
			new Color(0xFFF4CC), // estimate
			new Color(0xFFE0B2), // conflict
			new Color(0xE3F0FF), // rename
			new Color(0xE3F0FF), // function code
			new Color(0xE2F5E2), // added
			new Color(0xFBE3E3), // removed
			new Color(0xEDE3FB) }; // restored

	private final UlnlWbsDiff diff;
	private final List<UlnlWbsDiff.Change> changes;
	private final DecimalFormat hours = new DecimalFormat("#,##0.##"); //$NON-NLS-1$
	private JTable table;
	private ChangeModel model;
	private TableRowSorter<ChangeModel> sorter;
	private JComboBox filter;
	private JLabel selected;

	/** @return true if the selected changes are to apply */
	public static boolean doDialog(UlnlWbsDiff diff) {
		return new UlnlDiffDialog(diff).doModal();
	}

	private UlnlDiffDialog(UlnlWbsDiff diff) {
		super(GraphicManager.getFrameInstance(), Messages.getString("UlnlDiff.Title"), true); //$NON-NLS-1$
		this.diff = diff;
		this.changes = diff.getChanges();
	}

	public JComponent createContentPanel() {
		model = new ChangeModel();
		table = new JTable(model);
		sorter = new TableRowSorter<ChangeModel>(model);
		table.setRowSorter(sorter);
		table.setDefaultRenderer(Object.class, new ChangeRenderer());
		int[] widths = { 50, 100, 350, 140, 140 };
		for (int c = 0; c < widths.length; c++)
			table.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(780, 420));

		int[] counts = diff.countByType();
		String[] filters = new String[UlnlWbsDiff.TYPE_COUNT + 1];
		filters[0] = MessageFormat.format(Messages.getString("UlnlDiff.AllChanges"), String.valueOf(changes.size())); //$NON-NLS-1$
		for (int t = 0; t < UlnlWbsDiff.TYPE_COUNT; t++)
			filters[t + 1] = typeName(t) + " (" + counts[t] + ")"; //$NON-NLS-1$ //$NON-NLS-2$
		filter = new JComboBox(filters);
		filter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final int type = filter.getSelectedIndex() - 1;
				sorter.setRowFilter(type < 0 ? null : new RowFilter<ChangeModel, Integer>() {
					public boolean include(Entry<? extends ChangeModel, ? extends Integer> entry) {
						return changes.get(entry.getIdentifier()).getType() == type;
					}
				});
			}
		});
		JButton all = new JButton(Messages.getString("UlnlDiff.ApplyAll")); //$NON-NLS-1$
		all.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setShownApply(true);
			}
		});
		JButton none = new JButton(Messages.getString("UlnlDiff.ApplyNone")); //$NON-NLS-1$
		none.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setShownApply(false);
			}
		});
		selected = new JLabel();
		updateSelected();
		JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		tools.add(new JLabel(Messages.getString("UlnlDiff.Show"))); //$NON-NLS-1$
		tools.add(filter);
		tools.add(all);
		tools.add(none);
		tools.add(selected);

		FormLayout layout = new FormLayout("460dlu:grow", ""); //$NON-NLS-1$ //$NON-NLS-2$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		builder.append(new JLabel("<html>" + MessageFormat.format(Messages.getString("UlnlDiff.Summary"), //$NON-NLS-1$ //$NON-NLS-2$
				String.valueOf(diff.getUnchanged()), String.valueOf(counts[UlnlWbsDiff.ESTIMATE]), String.valueOf(counts[UlnlWbsDiff.CONFLICT]),
				String.valueOf(counts[UlnlWbsDiff.RENAME]), String.valueOf(counts[UlnlWbsDiff.FUNCTION]), String.valueOf(counts[UlnlWbsDiff.ADDED]),
				String.valueOf(counts[UlnlWbsDiff.REMOVED]), String.valueOf(counts[UlnlWbsDiff.RESTORED])) + "</html>")); //$NON-NLS-1$
		builder.append(tools);
		builder.append(scroll);
		builder.append(new JLabel("<html><div style='width:740px'>" + Messages.getString("UlnlDiff.Help") + "</div></html>")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return builder.getPanel();
	}

	/** Applies or not the changes shown by the filter */
	private void setShownApply(boolean apply) {
		if (table.isEditing())
			table.getCellEditor().stopCellEditing();
		for (int row = 0; row < table.getRowCount(); row++)
			changes.get(table.convertRowIndexToModel(row)).setApply(apply);
		model.fireTableDataChanged();
		updateSelected();
	}

	private void updateSelected() {
		int n = 0;
		for (UlnlWbsDiff.Change change : changes)
			if (change.isApply())
				n++;
		selected.setText(MessageFormat.format(Messages.getString("UlnlDiff.Selected"), String.valueOf(n), String.valueOf(changes.size()))); //$NON-NLS-1$
	}

	private static String typeName(int type) {
		return Messages.getString("UlnlDiff.Type" + type); //$NON-NLS-1$
	}

	private String hours(long millis) {
		return hours.format(millis / 3600000.0) + " h"; //$NON-NLS-1$
	}

	/** Path of names of a row: its parents, then its name */
	private String path(UlnlWbsDiff.Change change) {
		StringBuilder path = new StringBuilder();
		if (change.getItem() != null) {
			for (UlnlWbsParser.WbsItem item = change.getItem(); item != null; item = diff.getParent(item))
				path.insert(0, path.length() == 0 ? item.getName() : item.getName() + " > "); //$NON-NLS-1$
		} else {
			for (Task task = change.getTask(); task != null; task = task.getWbsParentTask())
				path.insert(0, path.length() == 0 ? task.getName() : task.getName() + " > "); //$NON-NLS-1$
		}
		return path.toString();
	}

	private String current(UlnlWbsDiff.Change change) {
		NormalTask task = change.getTask();
		switch (change.getType()) {
		case UlnlWbsDiff.ESTIMATE:
			return "OE " + hours(task.getOriginalEstimate()); //$NON-NLS-1$
		case UlnlWbsDiff.CONFLICT:
			return "OE " + hours(task.getOriginalEstimate()) + ", Work " + hours(task.getWork(null)); //$NON-NLS-1$ //$NON-NLS-2$
		case UlnlWbsDiff.RENAME:
			return task.getName();
		case UlnlWbsDiff.FUNCTION:
			return function(task.getFunctionCode(), task.getLane());
		case UlnlWbsDiff.ADDED:
			return ""; //$NON-NLS-1$
		case UlnlWbsDiff.REMOVED:
			return MessageFormat.format(Messages.getString("UlnlDiff.Tasks"), String.valueOf(change.getTaskCount())); //$NON-NLS-1$
		default:
			return Messages.getString("UlnlDiff.Cancelled") + (task.getCancelReason() == null ? "" : ": " + task.getCancelReason()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
	}

	private String next(UlnlWbsDiff.Change change) {
		UlnlWbsParser.WbsItem item = change.getItem();
		switch (change.getType()) {
		case UlnlWbsDiff.ESTIMATE:
		case UlnlWbsDiff.CONFLICT:
			return hours(change.getNewEstimate());
		case UlnlWbsDiff.RENAME:
			return item.getName();
		case UlnlWbsDiff.FUNCTION:
			return function(item.getFunctionCode(), item.getLane());
		case UlnlWbsDiff.ADDED:
			return MessageFormat.format(Messages.getString("UlnlDiff.NewTasks"), String.valueOf(change.getTaskCount())); //$NON-NLS-1$
		case UlnlWbsDiff.REMOVED:
			return Messages.getString("UlnlDiff.Cancel"); //$NON-NLS-1$
		default:
			return Messages.getString("UlnlDiff.Uncancel"); //$NON-NLS-1$
		}
	}

	private static String function(String code, String lane) {
		return (code == null ? "-" : code) + (lane == null ? "" : " (" + lane + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	private class ChangeModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private final String[] header = { Messages.getString("UlnlDiff.Apply"), Messages.getString("UlnlDiff.Type"), //$NON-NLS-1$ //$NON-NLS-2$
				Messages.getString("UlnlDiff.Task"), Messages.getString("UlnlDiff.Current"), Messages.getString("UlnlDiff.New") }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		public int getRowCount() {
			return changes.size();
		}
		public int getColumnCount() {
			return header.length;
		}
		public String getColumnName(int column) {
			return header[column];
		}
		public Class<?> getColumnClass(int column) {
			return column == APPLY ? Boolean.class : String.class;
		}
		public boolean isCellEditable(int row, int column) {
			return column == APPLY;
		}
		public Object getValueAt(int row, int column) {
			UlnlWbsDiff.Change change = changes.get(row);
			switch (column) {
			case APPLY: return change.isApply();
			case TYPE: return typeName(change.getType());
			case TASK: return path(change);
			case CURRENT: return current(change);
			default: return next(change);
			}
		}
		public void setValueAt(Object value, int row, int column) {
			changes.get(row).setApply(Boolean.TRUE.equals(value));
			fireTableCellUpdated(row, column);
			updateSelected();
		}
	}

	/** Background by type of change, full path as tooltip */
	private class ChangeRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (!isSelected)
				c.setBackground(COLORS[changes.get(table.convertRowIndexToModel(row)).getType()]);
			setToolTipText(value == null ? null : value.toString());
			return c;
		}
	}
}
