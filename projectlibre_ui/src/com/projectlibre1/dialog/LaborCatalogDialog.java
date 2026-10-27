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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.resource.labor.LaborCatalog;
import com.projectlibre1.strings.Messages;
import com.projectlibre1.util.Alert;

/**
 * Shows the labor categories (role, level, point, price of a man-month) and imports them from the CSV of the NLTC
 * table. The imported categories replace the catalog when OK is pressed.
 */
public final class LaborCatalogDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final String PREF_DIRECTORY = "laborCatalogDirectory"; //$NON-NLS-1$
	private final Preferences preferences = Preferences.userNodeForPackage(LaborCatalogDialog.class);
	private List<LaborCatalog.Category> categories;
	private List<LaborCatalog.Category> imported = null;
	private AbstractTableModel model;
	private JLabel status;

	public static void doDialog() {
		new LaborCatalogDialog().doModal();
	}

	private LaborCatalogDialog() {
		super(GraphicManager.getFrameInstance(), Messages.getString("LaborCatalog.Title"), true); //$NON-NLS-1$
		categories = LaborCatalog.getInstance().getCategories();
	}

	protected boolean bind(boolean get) {
		if (get || imported == null)
			return true;
		try {
			LaborCatalog.getInstance().replace(imported);
		} catch (Exception e) {
			Alert.error(Messages.getString("LaborCatalog.SaveError") + " " + e.getMessage(), this); //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		return true;
	}

	public JComponent createContentPanel() {
		final NumberFormat number = NumberFormat.getNumberInstance();
		model = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
			private final String[] header = { Messages.getString("Field.laborRole"), Messages.getString("Field.laborLevel"), //$NON-NLS-1$ //$NON-NLS-2$
					Messages.getString("Field.laborLevelName"), Messages.getString("Field.laborLevelCode"), //$NON-NLS-1$ //$NON-NLS-2$
					Messages.getString("Field.laborPoint"), Messages.getString("Field.laborPrice") }; //$NON-NLS-1$ //$NON-NLS-2$
			public int getRowCount() {
				return categories.size();
			}
			public int getColumnCount() {
				return header.length;
			}
			public String getColumnName(int column) {
				return header[column];
			}
			public Class<?> getColumnClass(int column) {
				return column == 1 ? Integer.class : column >= 4 ? Double.class : String.class;
			}
			public Object getValueAt(int row, int column) {
				LaborCatalog.Category category = categories.get(row);
				switch (column) {
				case 0:
					return category.getRole();
				case 1:
					return category.getLevel();
				case 2:
					return category.getLevelName();
				case 3:
					return category.getLevelCode();
				case 4:
					return category.getPoint();
				default:
					return category.getPrice();
				}
			}
		};
		JTable table = new JTable(model);
		table.setAutoCreateRowSorter(true);
		table.setDefaultRenderer(Double.class, new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;
			protected void setValue(Object value) {
				setHorizontalAlignment(SwingConstants.RIGHT);
				setText(value == null ? "" : number.format(value)); //$NON-NLS-1$
			}
		});
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(560, 360));

		JButton importButton = new JButton(Messages.getString("LaborCatalog.Import")); //$NON-NLS-1$
		importButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				importFile();
			}
		});
		status = new JLabel(categories.isEmpty() ? Messages.getString("LaborCatalog.Empty") : " "); //$NON-NLS-1$ //$NON-NLS-2$
		JPanel buttons = new JPanel();
		buttons.add(importButton);

		FormLayout layout = new FormLayout(WrappedText.column(540), ""); //$NON-NLS-1$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		builder.append(new JLabel(WrappedText.html(Messages.getString("LaborCatalog.Help"), 540))); //$NON-NLS-1$
		builder.append(scroll);
		builder.append(status);
		builder.append(buttons);
		return builder.getPanel();
	}

	private void importFile() {
		JFileChooser chooser = new JFileChooser(preferences.get(PREF_DIRECTORY, System.getProperty("user.home"))); //$NON-NLS-1$
		chooser.putClientProperty("FileChooser.useShellFolder", Boolean.FALSE); //$NON-NLS-1$
		chooser.setFileFilter(new FileFilter() {
			public boolean accept(File f) {
				return f.isDirectory() || f.getName().toLowerCase().endsWith(".csv"); //$NON-NLS-1$
			}
			public String getDescription() {
				return "CSV (*.csv)"; //$NON-NLS-1$
			}
		});
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
			return;
		File file = chooser.getSelectedFile();
		if (file.getParentFile() != null)
			preferences.put(PREF_DIRECTORY, file.getParentFile().getPath());
		List<LaborCatalog.Category> read;
		try {
			read = LaborCatalog.importFile(file);
		} catch (Exception e) {
			Alert.error(Messages.getString("LaborCatalog.ImportError") + " " + e.getMessage(), this); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		if (read.isEmpty()) {
			Alert.error(Messages.getString("LaborCatalog.NoCategory"), this); //$NON-NLS-1$
			return;
		}
		imported = read;
		categories = read;
		model.fireTableDataChanged();
		status.setText(MessageFormat.format(Messages.getString("LaborCatalog.Imported"), String.valueOf(read.size()), file.getName())); //$NON-NLS-1$
	}
}
