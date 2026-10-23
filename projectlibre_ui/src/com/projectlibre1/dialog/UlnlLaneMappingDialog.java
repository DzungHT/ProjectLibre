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
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.ArrayList;
import com.projectlibre1.util.Alert;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.resource.labor.SkillCatalog;
import com.projectlibre1.strings.Messages;

/**
 * Maps the function codes found in a ULNL to a lane, the required skill of their development tasks (BE, FE,
 * Mobile...), see SkillCatalog. The mapping is remembered: codes seen before get their last skill, new codes are
 * shown in bold. A skill can be chosen from the basic skills of the catalog and the ones used before, or typed:
 * typed skills are added to the catalog.
 */
public final class UlnlLaneMappingDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	/** Role of the skills typed here */
	private static final String DEVELOPER_ROLE = "DEV"; //$NON-NLS-1$
	private static final String EMPTY_CODE_KEY = "<empty>"; // the empty code cannot be a preference key //$NON-NLS-1$
	private static final Preferences preferences = Preferences.userNodeForPackage(UlnlLaneMappingDialog.class).node("ulnlLanes"); //$NON-NLS-1$

	private final List<String> codes;
	private final List<Integer> counts;
	private final List<String> lanes = new ArrayList<String>();
	private final boolean[] isNew;
	private final String[] laneChoices;
	private JTable table;
	private JComboBox bulkLane;
	private Map<String, String> result;

	/**
	 * @param rowsByFunctionCode number of rows per function code, the empty code being ""
	 * @return lane by function code (no entry for codes without lane), or null if cancelled
	 */
	public static Map<String, String> doDialog(Map<String, Integer> rowsByFunctionCode) {
		UlnlLaneMappingDialog dialog = new UlnlLaneMappingDialog(rowsByFunctionCode);
		return dialog.doModal() ? dialog.result : null;
	}

	private UlnlLaneMappingDialog(Map<String, Integer> rowsByFunctionCode) {
		super(GraphicManager.getFrameInstance(), Messages.getString("UlnlLaneMapping.Title"), true); //$NON-NLS-1$
		codes = new ArrayList<String>(rowsByFunctionCode.keySet());
		counts = new ArrayList<Integer>(rowsByFunctionCode.values());
		isNew = new boolean[codes.size()];
		Set<String> choices = new LinkedHashSet<String>();
		for (SkillCatalog.Skill skill : SkillCatalog.getInstance().getBasicSkills())
			choices.add(skill.getCode());
		for (int i = 0; i < codes.size(); i++) {
			String lane = preferences.get(preferenceKey(codes.get(i)), null);
			isNew[i] = lane == null;
			lanes.add(lane == null ? "" : lane); //$NON-NLS-1$
		}
		try { // lanes used before, for other ULNL
			for (String key : preferences.keys()) {
				String lane = preferences.get(key, ""); //$NON-NLS-1$
				if (lane.length() > 0)
					choices.add(lane);
			}
		} catch (BackingStoreException e) {
		}
		choices.add(""); // no lane //$NON-NLS-1$
		laneChoices = choices.toArray(new String[choices.size()]);
	}

	protected boolean bind(boolean get) {
		if (get)
			return true;
		if (table.isEditing())
			table.getCellEditor().stopCellEditing();
		result = new LinkedHashMap<String, String>();
		SkillCatalog catalog = SkillCatalog.getInstance();
		Set<String> newSkills = new LinkedHashSet<String>();
		for (int i = 0; i < codes.size(); i++) {
			String lane = lanes.get(i).trim();
			SkillCatalog.Skill skill = catalog.find(lane);
			if (skill != null)
				lane = skill.getCode(); // the case of the catalog
			else if (lane.length() > 0)
				newSkills.add(lane);
			String key = preferenceKey(codes.get(i));
			if (key.length() <= Preferences.MAX_KEY_LENGTH) // longer codes are not remembered
				preferences.put(key, lane);
			if (lane.length() > 0)
				result.put(codes.get(i), lane);
		}
		try {
			catalog.addBasic(newSkills, DEVELOPER_ROLE);
		} catch (Exception e) {
			Alert.error(Messages.getString("SkillCatalog.SaveError") + " " + e.getMessage(), this); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return true;
	}

	public JComponent createContentPanel() {
		table = new JTable(new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
			private final String[] header = { Messages.getString("UlnlLaneMapping.Code"), //$NON-NLS-1$
					Messages.getString("UlnlLaneMapping.Rows"), Messages.getString("UlnlLaneMapping.Lane") }; //$NON-NLS-1$ //$NON-NLS-2$
			public int getRowCount() {
				return codes.size();
			}
			public int getColumnCount() {
				return header.length;
			}
			public String getColumnName(int column) {
				return header[column];
			}
			public Object getValueAt(int row, int column) {
				switch (column) {
				case 0:
					return codes.get(row).length() == 0 ? Messages.getString("UlnlLaneMapping.EmptyCode") : codes.get(row); //$NON-NLS-1$
				case 1:
					return counts.get(row);
				default:
					return lanes.get(row);
				}
			}
			public boolean isCellEditable(int row, int column) {
				return column == 2;
			}
			public void setValueAt(Object value, int row, int column) {
				lanes.set(row, value == null ? "" : value.toString().trim()); //$NON-NLS-1$
				fireTableCellUpdated(row, column);
			}
		});
		JComboBox laneEditor = new JComboBox(laneChoices);
		laneEditor.setEditable(true);
		table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(laneEditor));
		table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				setHorizontalAlignment(column == 1 ? SwingConstants.RIGHT : SwingConstants.LEFT);
				c.setFont(isNew[row] ? c.getFont().deriveFont(Font.BOLD) : c.getFont().deriveFont(Font.PLAIN));
				return c;
			}
		});
		table.getColumnModel().getColumn(0).setPreferredWidth(220);
		table.getColumnModel().getColumn(1).setPreferredWidth(60);
		table.getColumnModel().getColumn(2).setPreferredWidth(120);
		table.setRowHeight(Math.max(table.getRowHeight(), laneEditor.getPreferredSize().height));
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(440, Math.min(400, 40 + codes.size() * table.getRowHeight())));

		bulkLane = new JComboBox(laneChoices);
		bulkLane.setEditable(true);
		JButton apply = new JButton(Messages.getString("UlnlLaneMapping.ApplyToSelection")); //$NON-NLS-1$
		apply.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				applyToSelection();
			}
		});
		JPanel bulk = new JPanel();
		bulk.add(bulkLane);
		bulk.add(apply);

		int newCodes = 0;
		for (boolean b : isNew)
			if (b)
				newCodes++;
		FormLayout layout = new FormLayout("300dlu:grow", ""); //$NON-NLS-1$ //$NON-NLS-2$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		builder.append(new JLabel("<html><div style='width:420px'>" + MessageFormat.format(Messages.getString("UlnlLaneMapping.Help"), //$NON-NLS-1$ //$NON-NLS-2$
				String.valueOf(codes.size()), String.valueOf(newCodes)) + "</div></html>")); //$NON-NLS-1$
		builder.append(scroll);
		builder.append(bulk);
		return builder.getPanel();
	}

	/** Sets the lane chosen in the combo box to the selected codes */
	private void applyToSelection() {
		if (table.isEditing())
			table.getCellEditor().stopCellEditing();
		Object lane = bulkLane.getEditor().getItem();
		for (int row : table.getSelectedRows())
			table.getModel().setValueAt(lane, table.convertRowIndexToModel(row), 2);
	}

	private static String preferenceKey(String code) {
		return code.length() == 0 ? EMPTY_CODE_KEY : code;
	}
}
