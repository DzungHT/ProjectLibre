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

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.options.TaskDefaultOption;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.scheduling.SchedulingType;
import com.projectlibre1.strings.Messages;

/**
 * Edits the default values applied when creating a new task
 */
public final class TaskDefaultSettingsDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final int[] SCHEDULING_TYPES = {
			SchedulingType.FIXED_UNITS, SchedulingType.FIXED_DURATION, SchedulingType.FIXED_WORK };
	private static final String[] SCHEDULING_TYPE_KEYS = {
			"SchedulingType.FixedUnits", "SchedulingType.FixedDuration", "SchedulingType.FixedWork" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	JComboBox schedulingType;

	public static boolean doDialog() {
		return new TaskDefaultSettingsDialog().doModal();
	}

	private TaskDefaultSettingsDialog() {
		super(GraphicManager.getFrameInstance(), Messages.getString("TaskDefaultSettingsDialog.Title"), true); //$NON-NLS-1$
		String[] labels = new String[SCHEDULING_TYPE_KEYS.length];
		for (int i = 0; i < labels.length; i++)
			labels[i] = Messages.getString(SCHEDULING_TYPE_KEYS[i]);
		schedulingType = new JComboBox(labels);
	}

	protected boolean bind(boolean get) {
		TaskDefaultOption option = TaskDefaultOption.getInstance();
		if (get) {
			for (int i = 0; i < SCHEDULING_TYPES.length; i++)
				if (SCHEDULING_TYPES[i] == option.getSchedulingType())
					schedulingType.setSelectedIndex(i);
		} else {
			option.setSchedulingType(SCHEDULING_TYPES[schedulingType.getSelectedIndex()]);
			option.save();
		}
		return true;
	}

	public JComponent createContentPanel() {
		bind(true);
		FormLayout layout = new FormLayout("default, 3dlu, 120dlu:grow", // cols //$NON-NLS-1$
				"p, 6dlu, p"); // rows //$NON-NLS-1$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		builder.append(new JLabel(Messages.getString("TaskDefaultSettingsDialog.Description")), 3); //$NON-NLS-1$
		builder.nextLine(2);
		builder.append(Messages.getString("Field.taskType"), schedulingType); //$NON-NLS-1$
		return builder.getPanel();
	}
}
