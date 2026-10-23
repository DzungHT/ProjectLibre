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

import java.text.MessageFormat;
import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRadioButton;

import net.sf.nachocalendar.CalendarFactory;
import net.sf.nachocalendar.components.DateField;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.options.CalendarOption;
import com.projectlibre1.options.EditOption;
import com.projectlibre1.pm.dependency.autolink.AutoLinkOptions;
import com.projectlibre1.pm.dependency.autolink.AutoLinkPlan;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.scheduling.auto.AutoResource;
import com.projectlibre1.pm.scheduling.auto.AutoScheduleModel;
import com.projectlibre1.pm.scheduling.auto.AutoScheduleOptions;
import com.projectlibre1.pm.scheduling.auto.AutoTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.pm.task.Task;
import com.projectlibre1.strings.Messages;
import com.projectlibre1.util.Alert;
import com.projectlibre1.util.DateTime;

/**
 * Options of Auto Schedule: the start date, and keeping the resources of the assigned tasks or not. The choice of
 * the assignments is remembered. Temporary dialog, before the wizard (see docs/auto-schedule-design.md).
 * Returns the options and the plan of Auto Link without the sequences.
 */
public final class AutoScheduleDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final String PREF_KEEP = "autoScheduleKeep"; //$NON-NLS-1$
	private static final int MAX_CIRCULAR_TASKS_SHOWN = 5;
	private static final int INDENT = 20; // pixels, for the help below the options

	/** What the dialog returns */
	public static class Result {
		private final AutoScheduleOptions options;
		private final AutoLinkPlan linkPlan;
		Result(AutoScheduleOptions options, AutoLinkPlan linkPlan) {
			this.options = options;
			this.linkPlan = linkPlan;
		}
		public AutoScheduleOptions getOptions() {
			return options;
		}
		/** Auto Link without the sequences, to apply first */
		public AutoLinkPlan getLinkPlan() {
			return linkPlan;
		}
	}

	private final Preferences preferences = Preferences.userNodeForPackage(AutoScheduleDialog.class);
	private final Project project;
	private final AutoLinkOptions linkOptions = AutoLinkDialog.savedOptions(false);

	DateField start;
	JRadioButton keep;
	JRadioButton reassign;
	JLabel preview;
	private int resourcesWithRole = 0;
	private Result result = null;

	/** @return the options and the links to apply, or null if cancelled */
	public static Result doDialog(Project project) {
		AutoScheduleDialog dialog = new AutoScheduleDialog(project);
		return dialog.doModal() ? dialog.result : null;
	}

	private AutoScheduleDialog(Project project) {
		super(GraphicManager.getFrameInstance(), Messages.getString("AutoSchedule.Title"), true); //$NON-NLS-1$
		this.project = project;
		start = CalendarFactory.createDateField();
		// the format of the sheets, in local time like the dates of the calendar of the field
		DateFormat format = (DateFormat) EditOption.getInstance().getShortDateFormat().clone();
		format.setTimeZone(TimeZone.getDefault());
		start.setDateFormat(format);
		start.setValue(DateTime.fromGmt(new Date(project.getStartConstraint())));
		keep = new JRadioButton(Messages.getString("AutoSchedule.KeepAssignments")); //$NON-NLS-1$
		reassign = new JRadioButton(Messages.getString("AutoSchedule.ReassignAll")); //$NON-NLS-1$
		ButtonGroup group = new ButtonGroup();
		group.add(keep);
		group.add(reassign);
		if (preferences.getBoolean(PREF_KEEP, true))
			keep.setSelected(true);
		else
			reassign.setSelected(true);
		preview = new JLabel(" "); //$NON-NLS-1$
	}

	private AutoScheduleOptions options() {
		AutoScheduleOptions options = new AutoScheduleOptions(linkOptions);
		long date = DateTime.gmt((Date) start.getValue());
		options.setStart(CalendarOption.getInstance().makeValidStart(date, true));
		options.setKeepAssignments(keep.isSelected());
		return options;
	}

	protected boolean bind(boolean get) {
		if (get)
			return true;
		if (resourcesWithRole == 0) {
			Alert.error(Messages.getString("AutoSchedule.NoRole"), this); //$NON-NLS-1$
			return false;
		}
		AutoLinkPlan plan = AutoLinkPlan.create(project, null, linkOptions);
		if (!plan.getCircularTasks().isEmpty()) {
			Alert.error(circularMessage(plan.getCircularTasks()), this);
			return false;
		}
		preferences.putBoolean(PREF_KEEP, keep.isSelected());
		result = new Result(options(), plan);
		return true;
	}

	public JComponent createContentPanel() {
		updatePreview();
		FormLayout layout = new FormLayout("max(60dlu;pref), 3dlu, 240dlu:grow", ""); //$NON-NLS-1$ //$NON-NLS-2$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		builder.append(Messages.getString("AutoSchedule.Start"), start); //$NON-NLS-1$
		builder.nextLine();
		builder.appendSeparator(Messages.getString("AutoSchedule.Assignments")); //$NON-NLS-1$
		builder.append(keep, 3);
		builder.nextLine();
		builder.append(reassign, 3);
		builder.nextLine();
		JLabel help = new JLabel("<html><div style='width:380px'>" + Messages.getString("AutoSchedule.Help") + "</div></html>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		help.setBorder(BorderFactory.createEmptyBorder(6, INDENT, 0, 0));
		builder.append(help, 3);
		builder.nextLine();
		builder.appendSeparator(Messages.getString("AutoSchedule.Preview")); //$NON-NLS-1$
		builder.append(preview, 3);
		return builder.getPanel();
	}

	/** The tasks to schedule and the resources that can do them */
	private void updatePreview() {
		AutoScheduleOptions options = new AutoScheduleOptions(linkOptions);
		options.setKeepAssignments(true); // to count the assigned tasks
		AutoScheduleModel model = AutoScheduleModel.create(project, options);
		int assigned = 0;
		for (AutoTask task : model.getWorkTasks())
			if (task.isKept())
				assigned++;
		resourcesWithRole = 0;
		for (AutoResource resource : model.getResources())
			if (resource.getRole() != null)
				resourcesWithRole++;
		String text = MessageFormat.format(Messages.getString("AutoSchedule.PreviewText"), //$NON-NLS-1$
				String.valueOf(model.getWorkTasks().size()), String.valueOf(assigned), String.valueOf(resourcesWithRole));
		String warning = resourcesWithRole > 0 ? "&nbsp;" : "<font color='red'>" + Messages.getString("AutoSchedule.NoRole") + "</font>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		preview.setText("<html><div style='width:420px'>" + text + "<br>" + warning + "</div></html>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static String circularMessage(List<Task> tasks) {
		StringBuilder names = new StringBuilder();
		for (int i = 0; i < Math.min(MAX_CIRCULAR_TASKS_SHOWN, tasks.size()); i++)
			names.append("\n- ").append(tasks.get(i).getId()).append(": ").append(tasks.get(i).getName()); //$NON-NLS-1$ //$NON-NLS-2$
		return MessageFormat.format(Messages.getString("AutoSchedule.CircularError"), String.valueOf(tasks.size())) + names; //$NON-NLS-1$
	}
}
