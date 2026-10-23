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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.resource.labor.LaborCatalog;
import com.projectlibre1.pm.resource.labor.SkillCatalog;
import com.projectlibre1.strings.Messages;
import com.projectlibre1.util.Alert;

/**
 * Manages the skill catalog: basic skills (BE, FE, Mobile...), which can be the required skill of a task, and
 * combined skills (Fullstack = BE + FE), only given to resources. Saved when OK is pressed.
 */
public final class SkillCatalogDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final int CODE = 0, NAME = 1, ROLE = 2, COMPONENTS = 3;
	private static final String COMPONENT_SEPARATOR = " + "; //$NON-NLS-1$
	private final List<String[]> rows = new ArrayList<String[]>();
	private JTable table;
	private AbstractTableModel model;

	public static void doDialog() {
		new SkillCatalogDialog().doModal();
	}

	private SkillCatalogDialog() {
		super(GraphicManager.getFrameInstance(), Messages.getString("SkillCatalog.Title"), true); //$NON-NLS-1$
		setRows(SkillCatalog.getInstance().getSkills());
	}

	private void setRows(List<SkillCatalog.Skill> skills) {
		rows.clear();
		for (SkillCatalog.Skill skill : skills) {
			StringBuilder components = new StringBuilder();
			for (String component : skill.getComponents())
				components.append(components.length() == 0 ? "" : COMPONENT_SEPARATOR).append(component); //$NON-NLS-1$
			rows.add(new String[] { skill.getCode(), skill.getName(), skill.getRole(), components.toString() });
		}
	}

	protected boolean bind(boolean get) {
		if (get)
			return true;
		if (table.isEditing())
			table.getCellEditor().stopCellEditing();
		List<SkillCatalog.Skill> skills = new ArrayList<SkillCatalog.Skill>();
		for (String[] row : rows) {
			if (row[CODE].trim().length() == 0 && row[NAME].trim().length() == 0 && row[COMPONENTS].trim().length() == 0)
				continue; // a new row left empty
			List<String> components = new ArrayList<String>();
			for (String component : row[COMPONENTS].split("[+;,]")) //$NON-NLS-1$
				if (component.trim().length() > 0)
					components.add(component.trim());
			skills.add(new SkillCatalog.Skill(row[CODE], row[NAME], row[ROLE], components));
		}
		String error = SkillCatalog.validate(skills);
		if (error != null) {
			Alert.error(error, this);
			return false;
		}
		try {
			SkillCatalog.getInstance().replace(skills);
		} catch (Exception e) {
			Alert.error(Messages.getString("SkillCatalog.SaveError") + " " + e.getMessage(), this); //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		return true;
	}

	public JComponent createContentPanel() {
		model = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
			private final String[] header = { Messages.getString("SkillCatalog.Code"), Messages.getString("SkillCatalog.Name"), //$NON-NLS-1$ //$NON-NLS-2$
					Messages.getString("Field.laborRole"), Messages.getString("SkillCatalog.Components") }; //$NON-NLS-1$ //$NON-NLS-2$
			public int getRowCount() {
				return rows.size();
			}
			public int getColumnCount() {
				return header.length;
			}
			public String getColumnName(int column) {
				return header[column];
			}
			public Object getValueAt(int row, int column) {
				return rows.get(row)[column];
			}
			public boolean isCellEditable(int row, int column) {
				return true;
			}
			public void setValueAt(Object value, int row, int column) {
				rows.get(row)[column] = value == null ? "" : value.toString().trim(); //$NON-NLS-1$
				fireTableCellUpdated(row, column);
			}
		};
		table = new JTable(model);
		Set<String> roles = new LinkedHashSet<String>();
		roles.add(""); // any role //$NON-NLS-1$
		roles.addAll(LaborCatalog.getInstance().getRoles());
		JComboBox roleEditor = new JComboBox(roles.toArray());
		roleEditor.setEditable(true);
		table.getColumnModel().getColumn(ROLE).setCellEditor(new DefaultCellEditor(roleEditor));
		table.getColumnModel().getColumn(CODE).setPreferredWidth(90);
		table.getColumnModel().getColumn(NAME).setPreferredWidth(150);
		table.getColumnModel().getColumn(ROLE).setPreferredWidth(80);
		table.getColumnModel().getColumn(COMPONENTS).setPreferredWidth(150);
		table.setRowHeight(Math.max(table.getRowHeight(), roleEditor.getPreferredSize().height));
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(480, 260));

		JButton add = new JButton(Messages.getString("SkillCatalog.Add")); //$NON-NLS-1$
		add.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (table.isEditing())
					table.getCellEditor().stopCellEditing();
				rows.add(new String[] { "", "", "DEV", "" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				model.fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
				table.changeSelection(rows.size() - 1, CODE, false, false);
				table.editCellAt(rows.size() - 1, CODE);
			}
		});
		JButton remove = new JButton(Messages.getString("SkillCatalog.Remove")); //$NON-NLS-1$
		remove.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (table.isEditing())
					table.getCellEditor().cancelCellEditing();
				int[] selected = table.getSelectedRows();
				for (int i = selected.length - 1; i >= 0; i--)
					rows.remove(selected[i]);
				model.fireTableDataChanged();
			}
		});
		JButton defaults = new JButton(Messages.getString("SkillCatalog.Defaults")); //$NON-NLS-1$
		defaults.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (table.isEditing())
					table.getCellEditor().cancelCellEditing();
				setRows(SkillCatalog.defaults());
				model.fireTableDataChanged();
			}
		});
		JPanel buttons = new JPanel();
		buttons.add(add);
		buttons.add(remove);
		buttons.add(defaults);

		FormLayout layout = new FormLayout("320dlu:grow", ""); //$NON-NLS-1$ //$NON-NLS-2$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		builder.append(new JLabel("<html><div style='width:460px'>" + Messages.getString("SkillCatalog.Help") + "</div></html>")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		builder.append(scroll);
		builder.append(buttons);
		return builder.getPanel();
	}
}
