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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.exchange.ulnl.UlnlWbsParser;
import com.projectlibre1.exchange.ulnl.XlsxSheetReader;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.strings.Messages;
import com.projectlibre1.util.Alert;

/**
 * Options for importing a WBS from a ULNL workbook: file, sheet, first data row
 * and the column holding the work of each activity.
 */
public final class ImportUlnlDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final String PREF_FILE = "ulnlFile"; //$NON-NLS-1$
	private static final String PREF_COLUMN = "ulnlColumn"; //$NON-NLS-1$
	private static final String FIRST_WORK_COLUMN = "F"; //$NON-NLS-1$
	private static final String LAST_WORK_COLUMN = "AD"; // at least, more if the sheet uses more columns //$NON-NLS-1$

	private final String[] activityNames = UlnlWbsParser.DEFAULT_ACTIVITY_NAMES;
	private final Preferences preferences = Preferences.userNodeForPackage(ImportUlnlDialog.class);

	JTextField file;
	JButton browse;
	JComboBox sheet;
	JSpinner startRow;
	JComboBox[] columns;
	JLabel preview;

	private XlsxSheetReader reader;
	private Map<Integer, Map<String, String>> rows;
	private List<UlnlWbsParser.WbsItem> result;
	private boolean updating;

	/**
	 * @return the WBS to import, or null if cancelled
	 */
	public static List<UlnlWbsParser.WbsItem> doDialog() {
		ImportUlnlDialog dialog = new ImportUlnlDialog();
		return dialog.doModal() ? dialog.result : null;
	}

	private ImportUlnlDialog() {
		super(GraphicManager.getFrameInstance(), Messages.getString("ImportUlnlDialog.Title"), true); //$NON-NLS-1$
		file = new JTextField(30);
		file.setEditable(false);
		browse = new JButton(Messages.getString("ImportUlnlDialog.Browse")); //$NON-NLS-1$
		sheet = new JComboBox();
		startRow = new JSpinner(new SpinnerNumberModel(UlnlWbsParser.DEFAULT_START_ROW, 1, 100000, 1));
		columns = new JComboBox[activityNames.length];
		for (int i = 0; i < columns.length; i++)
			columns[i] = new JComboBox();
		preview = new JLabel(" "); //$NON-NLS-1$

		browse.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseFile();
			}
		});
		sheet.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!updating)
					loadSheet();
			}
		});
		startRow.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updatePreview();
			}
		});
		ActionListener columnListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!updating)
					updatePreview();
			}
		};
		for (int i = 0; i < columns.length; i++)
			columns[i].addActionListener(columnListener);
	}

	protected boolean bind(boolean get) {
		if (get) {
			fillColumns(rows);
			String last = preferences.get(PREF_FILE, null);
			if (rows == null && last != null && new File(last).isFile())
				openFile(new File(last));
			return true;
		}
		if (rows == null) {
			Alert.warn(Messages.getString("ImportUlnlDialog.NoFile"), this); //$NON-NLS-1$
			return false;
		}
		result = parser().parse(rows);
		if (result.isEmpty()) {
			Alert.warn(Messages.getString("ImportUlnlDialog.NoTasks"), this); //$NON-NLS-1$
			return false;
		}
		if (!Alert.okCancel(Messages.getString("ImportUlnlDialog.ConfirmReplace"))) //$NON-NLS-1$
			return false;
		preferences.put(PREF_FILE, file.getText());
		for (int i = 0; i < columns.length; i++)
			preferences.put(PREF_COLUMN + i, selectedColumn(i));
		return true;
	}

	public JComponent createContentPanel() {
		bind(true);
		updatePreview();
		FormLayout layout = new FormLayout("default, 3dlu, 160dlu:grow, 3dlu, default", ""); // cols //$NON-NLS-1$ //$NON-NLS-2$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		builder.append(Messages.getString("ImportUlnlDialog.File"), file, browse); //$NON-NLS-1$
		builder.nextLine();
		builder.append(Messages.getString("ImportUlnlDialog.Sheet"), sheet); //$NON-NLS-1$
		builder.nextLine();
		builder.append(Messages.getString("ImportUlnlDialog.StartRow"), startRow); //$NON-NLS-1$
		builder.nextLine();
		builder.appendSeparator(Messages.getString("ImportUlnlDialog.WorkColumns")); //$NON-NLS-1$
		for (int i = 0; i < columns.length; i++) {
			builder.append(activityNames[i], columns[i]);
			builder.nextLine();
		}
		builder.appendSeparator(Messages.getString("ImportUlnlDialog.Preview")); //$NON-NLS-1$
		builder.append(preview, 5);
		return builder.getPanel();
	}

	private static JFileChooser chooser = null;
	private void chooseFile() {
		if (chooser == null) {
			chooser = new JFileChooser();
			chooser.putClientProperty("FileChooser.useShellFolder", Boolean.FALSE); //$NON-NLS-1$
			chooser.setFileFilter(new FileFilter() {
				public boolean accept(File f) {
					return f.isDirectory() || f.getName().toLowerCase().endsWith(".xlsx"); //$NON-NLS-1$
				}
				public String getDescription() {
					return "Excel (*.xlsx)"; //$NON-NLS-1$
				}
			});
		}
		if (file.getText().length() > 0)
			chooser.setSelectedFile(new File(file.getText()));
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
			openFile(chooser.getSelectedFile());
	}

	private void openFile(File f) {
		try {
			reader = new XlsxSheetReader(f);
		} catch (IOException e) {
			reader = null;
			rows = null;
			Alert.error(MessageFormat.format(Messages.getString("ImportUlnlDialog.ReadError"), f.getName(), e.getMessage()), this); //$NON-NLS-1$
			updatePreview();
			return;
		}
		file.setText(f.getAbsolutePath());
		updating = true;
		sheet.removeAllItems();
		for (String name : reader.getSheetNames())
			sheet.addItem(name);
		if (reader.getSheetNames().contains(UlnlWbsParser.DEFAULT_SHEET))
			sheet.setSelectedItem(UlnlWbsParser.DEFAULT_SHEET);
		updating = false;
		loadSheet();
	}

	private void loadSheet() {
		rows = null;
		if (reader != null && sheet.getSelectedItem() != null) {
			try {
				rows = reader.readSheet((String) sheet.getSelectedItem());
			} catch (IOException e) {
				Alert.error(MessageFormat.format(Messages.getString("ImportUlnlDialog.ReadError"), file.getText(), e.getMessage()), this); //$NON-NLS-1$
			}
		}
		fillColumns(rows);
		updatePreview();
	}

	/** Lists the work columns with their header, keeping the current choice or the saved one */
	private void fillColumns(Map<Integer, Map<String, String>> rows) {
		updating = true;
		int first = XlsxSheetReader.columnIndex(FIRST_WORK_COLUMN);
		int last = XlsxSheetReader.columnIndex(LAST_WORK_COLUMN);
		if (rows != null) // up to the last column used in the sheet
			for (Map<String, String> row : rows.values())
				for (String column : row.keySet())
					last = Math.max(last, XlsxSheetReader.columnIndex(column));
		String[] selections = new String[columns.length];
		for (int i = 0; i < columns.length; i++) {
			selections[i] = columns[i].getItemCount() > 0 ? selectedColumn(i)
					: preferences.get(PREF_COLUMN + i, UlnlWbsParser.DEFAULT_ACTIVITY_COLUMNS[i]);
			last = Math.max(last, XlsxSheetReader.columnIndex(selections[i]));
		}
		for (int i = 0; i < columns.length; i++) {
			String selected = selections[i];
			columns[i].removeAllItems();
			for (int c = first; c <= last; c++) {
				ColumnItem item = new ColumnItem(XlsxSheetReader.columnName(c), header(rows, XlsxSheetReader.columnName(c)));
				columns[i].addItem(item);
				if (item.column.equals(selected))
					columns[i].setSelectedItem(item);
			}
		}
		updating = false;
	}

	private static String header(Map<Integer, Map<String, String>> rows, String column) {
		if (rows == null)
			return null;
		String header = null;
		for (int r = UlnlWbsParser.HEADER_ROW; r > 0 && header == null; r--) { // header row, else the group header above
			Map<String, String> row = rows.get(r);
			header = row == null ? null : row.get(column);
		}
		return header == null ? null : header.replaceAll("\\s+", " ").trim(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private String selectedColumn(int i) {
		ColumnItem item = (ColumnItem) columns[i].getSelectedItem();
		return item == null ? UlnlWbsParser.DEFAULT_ACTIVITY_COLUMNS[i] : item.column;
	}

	private UlnlWbsParser parser() {
		String[] selected = new String[columns.length];
		for (int i = 0; i < columns.length; i++)
			selected[i] = selectedColumn(i);
		return new UlnlWbsParser(((Number) startRow.getValue()).intValue(), activityNames, selected);
	}

	/**
	 * Always the same number of lines, so the dialog never needs resizing:
	 * calling pack() on a visible AbstractDialog rebuilds its whole content.
	 */
	private void updatePreview() {
		StringBuilder work = new StringBuilder();
		String text;
		if (rows == null) {
			text = Messages.getString("ImportUlnlDialog.NoFile"); //$NON-NLS-1$
			for (int i = 0; i < activityNames.length; i++)
				work.append("<br>&nbsp;"); //$NON-NLS-1$
		} else {
			UlnlWbsParser parser = parser();
			UlnlWbsParser.Summary summary = parser.summarize(parser.parse(rows));
			DecimalFormat hours = new DecimalFormat("#,##0.##"); //$NON-NLS-1$
			for (int i = 0; i < activityNames.length; i++)
				work.append("<br>").append(activityNames[i]).append(": ").append(hours.format(summary.workByActivity[i])).append(" h"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			int[] count = summary.countByLevel;
			text = MessageFormat.format(Messages.getString("ImportUlnlDialog.PreviewText"), //$NON-NLS-1$
					count[1], count[2], count[3], count[4], count[5]);
		}
		preview.setText("<html>" + text + work + "</html>"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static class ColumnItem {
		final String column;
		final String header;
		ColumnItem(String column, String header) {
			this.column = column;
			this.header = header;
		}
		public String toString() {
			return header == null ? column : column + " - " + header; //$NON-NLS-1$
		}
	}
}
