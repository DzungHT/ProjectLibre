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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.exchange.effort.EffortReport;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.strings.Messages;
import com.projectlibre1.util.Alert;

/**
 * Effort of the leaf tasks by activity and status, in man-hours, man-days and man-months,
 * with the task counts. The report can be exported to Excel.
 */
public final class EffortReportDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final String PREF_DIRECTORY = "effortReportDirectory"; //$NON-NLS-1$

	private final Preferences preferences = Preferences.userNodeForPackage(EffortReportDialog.class);
	private final EffortReport report;
	private final String projectName;
	private JButton export;

	public static void doDialog(Project project) {
		new EffortReportDialog(project).doModal();
	}

	private EffortReportDialog(Project project) {
		super(GraphicManager.getFrameInstance(), Messages.getString("EffortReport.Title"), true); //$NON-NLS-1$
		report = new EffortReport(project);
		projectName = project.getName();
	}

	protected boolean hasCloseButton() {
		return true;
	}

	public ButtonPanel createButtonPanel() {
		ButtonPanel panel = super.createButtonPanel();
		export = new JButton(Messages.getString("EffortReport.Export")); //$NON-NLS-1$
		export.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				exportExcel();
			}
		});
		panel.addButton(export);
		return panel;
	}

	public JComponent createContentPanel() {
		FormLayout layout = new FormLayout("320dlu:grow", ""); //$NON-NLS-1$ //$NON-NLS-2$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		int other = report.getCount(EffortReport.OTHER, -1);
		builder.append(new JLabel("<html><div style='width:440px'>" + MessageFormat.format(Messages.getString("EffortReport.Summary"), //$NON-NLS-1$ //$NON-NLS-2$
				String.valueOf(report.getCount(-1, -1)), String.valueOf(other)) + "<br>" + Messages.getString("EffortReport.Note") + "</div></html>")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		for (int unit = 0; unit <= EffortReport.UNIT_COUNT; unit++) { // UNIT_COUNT for the task counts
			builder.appendSeparator(unit == EffortReport.UNIT_COUNT ? Messages.getString("EffortReport.TaskCount") : EffortReport.getUnitName(unit)); //$NON-NLS-1$
			builder.append(createTable(unit));
		}
		return builder.getPanel();
	}

	private JComponent createTable(final int unit) {
		final boolean counts = unit == EffortReport.UNIT_COUNT;
		final NumberFormat format = NumberFormat.getNumberInstance();
		format.setMaximumFractionDigits(counts ? 0 : NormalTask.EFFORT_DECIMALS);
		format.setRoundingMode(RoundingMode.FLOOR);
		final String[] header = EffortReport.summaryHeader(Messages.getString("EffortReport.Activity")); //$NON-NLS-1$
		JTable table = new JTable(new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
			public int getRowCount() {
				return EffortReport.ACTIVITY_COUNT + 1; // and the total
			}
			public int getColumnCount() {
				return header.length;
			}
			public String getColumnName(int column) {
				return header[column];
			}
			public Object getValueAt(int row, int column) {
				int activity = row == EffortReport.ACTIVITY_COUNT ? -1 : row;
				if (column == 0)
					return activity < 0 ? Messages.getString("EffortReport.Total") : EffortReport.getActivityName(activity); //$NON-NLS-1$
				int status = column == header.length - 1 ? -1 : column - 1;
				return counts ? format.format(report.getCount(activity, status)) : format.format(report.getEffort(activity, status, unit));
			}
		});
		setRenderer(table);
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(table.getTableHeader(), BorderLayout.NORTH);
		panel.add(table, BorderLayout.CENTER);
		return panel;
	}

	/** Text on the left, numbers on the right, the total row and column in bold */
	private static void setRenderer(JTable table) {
		table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				boolean total = row == table.getRowCount() - 1 || column == table.getColumnCount() - 1;
				c.setFont(c.getFont().deriveFont(total ? Font.BOLD : Font.PLAIN));
				setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
				return c;
			}
		});
		table.setEnabled(false);
		table.getTableHeader().setReorderingAllowed(false);
	}

	private void exportExcel() {
		JFileChooser chooser = new JFileChooser();
		chooser.putClientProperty("FileChooser.useShellFolder", Boolean.FALSE); //$NON-NLS-1$
		chooser.setFileFilter(ExportWbsExcelDialog.xlsxFilter());
		String name = projectName == null || projectName.trim().length() == 0 ? "Effort" : projectName.trim().replaceAll("[\\\\/:*?\"<>|]", "_") + " - Effort"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		chooser.setSelectedFile(new File(preferences.get(PREF_DIRECTORY, System.getProperty("user.home")), name + ".xlsx")); //$NON-NLS-1$ //$NON-NLS-2$
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			return;
		File f = chooser.getSelectedFile();
		if (!f.getName().toLowerCase().endsWith(".xlsx")) //$NON-NLS-1$
			f = new File(f.getPath() + ".xlsx"); //$NON-NLS-1$
		if (f.exists() && !Alert.okCancel(MessageFormat.format(Messages.getString("ExportWbsExcelDialog.Overwrite"), f.getName()))) //$NON-NLS-1$
			return;
		if (f.getParentFile() != null)
			preferences.put(PREF_DIRECTORY, f.getParentFile().getAbsolutePath());
		Cursor cur = getCursor();
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		try {
			report.export(f);
		} catch (IOException e) {
			setCursor(cur);
			Alert.error(MessageFormat.format(Messages.getString("ExportWbsExcelDialog.WriteError"), f.getName(), e.getMessage())); //$NON-NLS-1$
			return;
		} finally {
			setCursor(cur);
		}
		JOptionPane.showMessageDialog(this, MessageFormat.format(Messages.getString("EffortReport.Exported"), f.getAbsolutePath()), //$NON-NLS-1$
				Messages.getString("EffortReport.Title"), JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$
	}
}
