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
import java.text.MessageFormat;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.field.Field;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.strings.Messages;
import com.projectlibre1.util.Alert;

/**
 * Options for exporting the WBS to Excel: the columns shown in the task spreadsheet or all the task fields,
 * and the file.
 */
public final class ExportWbsExcelDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final String PREF_DIRECTORY = "wbsExcelDirectory"; //$NON-NLS-1$
	private static final String PREF_ALL = "wbsExcelAllFields"; //$NON-NLS-1$

	private final Preferences preferences = Preferences.userNodeForPackage(ExportWbsExcelDialog.class);
	private final List<Field> displayedFields;
	private final List<Field> allFields;

	JRadioButton displayed;
	JRadioButton all;
	JTextField file;
	JButton browse;

	private List<Field> resultFields;
	private File resultFile;

	public static class Result {
		public final List<Field> fields;
		public final File file;
		Result(List<Field> fields, File file) {
			this.fields = fields;
			this.file = file;
		}
	}

	/**
	 * @return the fields and file to export, or null if cancelled
	 */
	public static Result doDialog(List<Field> displayedFields, List<Field> allFields, String projectName) {
		ExportWbsExcelDialog dialog = new ExportWbsExcelDialog(displayedFields, allFields, projectName);
		return dialog.doModal() ? new Result(dialog.resultFields, dialog.resultFile) : null;
	}

	private ExportWbsExcelDialog(List<Field> displayedFields, List<Field> allFields, String projectName) {
		super(GraphicManager.getFrameInstance(), Messages.getString("ExportWbsExcelDialog.Title"), true); //$NON-NLS-1$
		this.displayedFields = displayedFields;
		this.allFields = allFields;
		displayed = new JRadioButton(MessageFormat.format(Messages.getString("ExportWbsExcelDialog.Displayed"), String.valueOf(displayedFields.size()))); //$NON-NLS-1$
		all = new JRadioButton(MessageFormat.format(Messages.getString("ExportWbsExcelDialog.All"), String.valueOf(allFields.size()))); //$NON-NLS-1$
		ButtonGroup group = new ButtonGroup();
		group.add(displayed);
		group.add(all);
		if (preferences.getBoolean(PREF_ALL, false))
			all.setSelected(true);
		else
			displayed.setSelected(true);

		file = new JTextField(30);
		String name = projectName == null || projectName.trim().length() == 0 ? "WBS" : projectName.trim().replaceAll("[\\\\/:*?\"<>|]", "_"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		String directory = preferences.get(PREF_DIRECTORY, System.getProperty("user.home")); //$NON-NLS-1$
		file.setText(new File(directory, name + ".xlsx").getAbsolutePath()); //$NON-NLS-1$
		browse = new JButton(Messages.getString("ImportUlnlDialog.Browse")); //$NON-NLS-1$
		browse.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseFile();
			}
		});
	}

	protected boolean bind(boolean get) {
		if (get)
			return true;
		String path = file.getText().trim();
		if (path.length() == 0) {
			Alert.warn(Messages.getString("ExportWbsExcelDialog.NoFile"), this); //$NON-NLS-1$
			return false;
		}
		if (!path.toLowerCase().endsWith(".xlsx")) //$NON-NLS-1$
			path += ".xlsx"; //$NON-NLS-1$
		File f = new File(path);
		if (f.exists() && !Alert.okCancel(MessageFormat.format(Messages.getString("ExportWbsExcelDialog.Overwrite"), f.getName()))) //$NON-NLS-1$
			return false;
		resultFile = f;
		resultFields = all.isSelected() ? allFields : displayedFields;
		preferences.putBoolean(PREF_ALL, all.isSelected());
		if (f.getParentFile() != null)
			preferences.put(PREF_DIRECTORY, f.getParentFile().getAbsolutePath());
		return true;
	}

	public JComponent createContentPanel() {
		FormLayout layout = new FormLayout("default, 3dlu, 160dlu:grow, 3dlu, default", ""); //$NON-NLS-1$ //$NON-NLS-2$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		builder.appendSeparator(Messages.getString("ExportWbsExcelDialog.Columns")); //$NON-NLS-1$
		builder.append(displayed, 5);
		builder.nextLine();
		builder.append(all, 5);
		builder.nextLine();
		builder.appendSeparator();
		builder.append(Messages.getString("ImportUlnlDialog.File"), file, browse); //$NON-NLS-1$
		return builder.getPanel();
	}

	private void chooseFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.putClientProperty("FileChooser.useShellFolder", Boolean.FALSE); //$NON-NLS-1$
		chooser.setFileFilter(xlsxFilter());
		chooser.setSelectedFile(new File(file.getText()));
		if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
			file.setText(chooser.getSelectedFile().getAbsolutePath());
	}

	static FileFilter xlsxFilter() {
		return new FileFilter() {
			public boolean accept(File f) {
				return f.isDirectory() || f.getName().toLowerCase().endsWith(".xlsx"); //$NON-NLS-1$
			}
			public String getDescription() {
				return "Excel (*.xlsx)"; //$NON-NLS-1$
			}
		};
	}
}
