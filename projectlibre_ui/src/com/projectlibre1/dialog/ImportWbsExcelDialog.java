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
import java.text.MessageFormat;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.exchange.wbsexcel.WbsExcelImporter;
import com.projectlibre1.exchange.wbsexcel.WbsExcelParser;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.strings.Messages;
import com.projectlibre1.util.Alert;

/**
 * Import of a file written by Export WBS Excel: picks the file, previews what changes
 * and whether the tasks missing from the file are deleted.
 */
public final class ImportWbsExcelDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final String PREF_FILE = "wbsExcelImportFile"; //$NON-NLS-1$

	private final Preferences preferences = Preferences.userNodeForPackage(ImportWbsExcelDialog.class);
	private final Project project;

	JTextField file;
	JButton browse;
	JLabel preview;
	JCheckBox deleteMissing;
	JTextArea warnings;

	private WbsExcelImporter importer;

	public static class Result {
		public final WbsExcelImporter importer;
		public final boolean deleteMissing;
		Result(WbsExcelImporter importer, boolean deleteMissing) {
			this.importer = importer;
			this.deleteMissing = deleteMissing;
		}
	}

	/**
	 * @return the import to apply, or null if cancelled
	 */
	public static Result doDialog(Project project) {
		ImportWbsExcelDialog dialog = new ImportWbsExcelDialog(project);
		return dialog.doModal() ? new Result(dialog.importer, dialog.deleteMissing.isSelected()) : null;
	}

	private ImportWbsExcelDialog(Project project) {
		super(GraphicManager.getFrameInstance(), Messages.getString("ImportWbsExcelDialog.Title"), true); //$NON-NLS-1$
		this.project = project;
		file = new JTextField(30);
		file.setEditable(false);
		browse = new JButton(Messages.getString("ImportUlnlDialog.Browse")); //$NON-NLS-1$
		preview = new JLabel(" "); //$NON-NLS-1$
		deleteMissing = new JCheckBox();
		deleteMissing.setSelected(false);
		warnings = new JTextArea(8, 50);
		warnings.setEditable(false);
		warnings.setLineWrap(true);
		warnings.setWrapStyleWord(true);
		browse.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseFile();
			}
		});
	}

	protected boolean bind(boolean get) {
		if (get) {
			String last = preferences.get(PREF_FILE, null);
			if (importer == null && last != null && new File(last).isFile())
				openFile(new File(last));
			else
				updatePreview();
			return true;
		}
		if (importer == null) {
			Alert.warn(Messages.getString("ImportWbsExcelDialog.NoFile"), this); //$NON-NLS-1$
			return false;
		}
		if (importer.getRowCount() == 0) {
			Alert.warn(Messages.getString("ImportWbsExcelDialog.NoRows"), this); //$NON-NLS-1$
			return false;
		}
		if (deleteMissing.isSelected() && importer.getMissingCount() > 0
				&& !Alert.okCancel(MessageFormat.format(Messages.getString("ImportWbsExcelDialog.ConfirmDelete"), String.valueOf(importer.getMissingCount())))) //$NON-NLS-1$
			return false;
		preferences.put(PREF_FILE, file.getText());
		return true;
	}

	public JComponent createContentPanel() {
		bind(true);
		FormLayout layout = new FormLayout("default, 3dlu, 160dlu:grow, 3dlu, default", ""); //$NON-NLS-1$ //$NON-NLS-2$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		builder.append(Messages.getString("ImportUlnlDialog.File"), file, browse); //$NON-NLS-1$
		builder.nextLine();
		builder.appendSeparator(Messages.getString("ImportUlnlDialog.Preview")); //$NON-NLS-1$
		builder.append(preview, 5);
		builder.nextLine();
		builder.append(deleteMissing, 5);
		builder.nextLine();
		builder.appendSeparator(Messages.getString("ImportWbsExcelDialog.Warnings")); //$NON-NLS-1$
		builder.append(new JScrollPane(warnings), 5);
		return builder.getPanel();
	}

	private void chooseFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.putClientProperty("FileChooser.useShellFolder", Boolean.FALSE); //$NON-NLS-1$
		chooser.setFileFilter(ExportWbsExcelDialog.xlsxFilter());
		if (file.getText().length() > 0)
			chooser.setSelectedFile(new File(file.getText()));
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
			openFile(chooser.getSelectedFile());
	}

	private void openFile(File f) {
		try {
			importer = new WbsExcelImporter(project, WbsExcelParser.parse(f));
			file.setText(f.getAbsolutePath());
		} catch (IOException e) {
			importer = null;
			Alert.error(MessageFormat.format(Messages.getString("ImportUlnlDialog.ReadError"), f.getName(), e.getMessage()), this); //$NON-NLS-1$
		}
		updatePreview();
	}

	/** Same number of lines whatever the file, so the dialog never needs resizing */
	private void updatePreview() {
		if (importer == null) {
			preview.setText("<html>" + Messages.getString("ImportWbsExcelDialog.NoFile") + "<br>&nbsp;</html>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			deleteMissing.setText(MessageFormat.format(Messages.getString("ImportWbsExcelDialog.DeleteMissing"), "0")); //$NON-NLS-1$ //$NON-NLS-2$
			deleteMissing.setEnabled(false);
			warnings.setText(""); //$NON-NLS-1$
			return;
		}
		preview.setText("<html>" + MessageFormat.format(Messages.getString("ImportWbsExcelDialog.PreviewText"), //$NON-NLS-1$ //$NON-NLS-2$
				String.valueOf(importer.getRowCount()), String.valueOf(importer.getUpdatedCount()),
				String.valueOf(importer.getNewCount()), String.valueOf(importer.getMovedCount())) + "</html>"); //$NON-NLS-1$
		deleteMissing.setText(MessageFormat.format(Messages.getString("ImportWbsExcelDialog.DeleteMissing"), String.valueOf(importer.getMissingCount()))); //$NON-NLS-1$
		deleteMissing.setEnabled(importer.getMissingCount() > 0);
		if (importer.getMissingCount() == 0)
			deleteMissing.setSelected(false);
		StringBuilder b = new StringBuilder();
		for (String warning : importer.getWarnings())
			b.append(warning).append('\n');
		warnings.setText(b.toString());
		warnings.setCaretPosition(0);
	}
}
