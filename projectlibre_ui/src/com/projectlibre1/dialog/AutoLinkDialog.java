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

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.exchange.ulnl.UlnlWbsParser;
import com.projectlibre1.pm.dependency.autolink.AutoLinkOptions;
import com.projectlibre1.pm.dependency.autolink.AutoLinkPlan;
import com.projectlibre1.pm.dependency.autolink.FinishSummary;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.pm.task.Task;
import com.projectlibre1.strings.Messages;
import com.projectlibre1.util.Alert;

/**
 * Options of Auto Link, with a preview of the links created and removed, of the constraints of the first day of
 * the people and of the estimated finish. The options are remembered. Returns the plan to apply, see AutoLinkPlan.
 */
public final class AutoLinkDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final String PREF_SCOPE = "autoLinkScope"; //$NON-NLS-1$
	private static final String PREF_ACTIVITIES = "autoLinkActivities"; //$NON-NLS-1$
	private static final String PREF_SEQUENCES = "autoLinkSequences"; //$NON-NLS-1$
	private static final String PREF_READY_ORDER = "autoLinkReadyOrder"; //$NON-NLS-1$
	private static final String PREF_UNASSIGNED = "autoLinkUnassigned"; //$NON-NLS-1$
	private static final String PREF_JOIN = "autoLinkJoinConstraints"; //$NON-NLS-1$
	private static final int[] DEFAULT_SCOPES = { AutoLinkOptions.ROW, AutoLinkOptions.FUNCTION };
	private static final int[] SCOPE_VALUES = { AutoLinkOptions.ROW, AutoLinkOptions.FUNCTION };
	private static final int MAX_CIRCULAR_TASKS_SHOWN = 5;
	private static final int WIDTH = 540; // pixels of the content, the long texts wrap

	private final String[] activityNames = UlnlWbsParser.DEFAULT_ACTIVITY_NAMES;
	private final String[] activityCodes = UlnlWbsParser.DEFAULT_ACTIVITY_CODES;
	private final Preferences preferences = Preferences.userNodeForPackage(AutoLinkDialog.class);
	private final Project project;
	private final List<Task> selection;

	JRadioButton wholeProject;
	JRadioButton selectedTasks;
	JCheckBox linkActivities;
	JComboBox[] scopes;
	JCheckBox linkSequences;
	JRadioButton readyOrder;
	JRadioButton wbsOrder;
	JCheckBox linkUnassigned;
	JCheckBox joinConstraints;
	JLabel preview;
	private AutoLinkPlan plan;

	/**
	 * @param selection the selected tasks, null or empty if none
	 * @return the links to create and remove, or null if cancelled
	 */
	public static AutoLinkPlan doDialog(Project project, List<Task> selection) {
		AutoLinkDialog dialog = new AutoLinkDialog(project, selection);
		return dialog.doModal() ? dialog.plan : null;
	}

	private AutoLinkDialog(Project project, List<Task> selection) {
		super(GraphicManager.getFrameInstance(), Messages.getString("AutoLink.Title"), true); //$NON-NLS-1$
		this.project = project;
		this.selection = selection == null || selection.isEmpty() ? null : selection;
		wholeProject = new JRadioButton(Messages.getString("AutoLink.WholeProject")); //$NON-NLS-1$
		selectedTasks = new JRadioButton(Messages.getString("AutoLink.SelectedTasks")); //$NON-NLS-1$
		ButtonGroup group = new ButtonGroup();
		group.add(wholeProject);
		group.add(selectedTasks);
		selectedTasks.setEnabled(this.selection != null);
		if (this.selection != null && preferences.getBoolean(PREF_SCOPE, false))
			selectedTasks.setSelected(true);
		else
			wholeProject.setSelected(true);
		linkActivities = new JCheckBox(Messages.getString("AutoLink.LinkActivities"), preferences.getBoolean(PREF_ACTIVITIES, true)); //$NON-NLS-1$
		String[] scopeNames = { Messages.getString("AutoLink.ScopeRow"), Messages.getString("AutoLink.ScopeFunction") }; //$NON-NLS-1$ //$NON-NLS-2$
		scopes = new JComboBox[activityCodes.length - 1];
		for (int i = 0; i < scopes.length; i++) {
			scopes[i] = new JComboBox(scopeNames);
			int value = preferences.getInt(scopePreference(i), DEFAULT_SCOPES[Math.min(i, DEFAULT_SCOPES.length - 1)]);
			scopes[i].setSelectedIndex(value == AutoLinkOptions.FUNCTION ? 1 : 0);
		}
		linkSequences = new JCheckBox(Messages.getString("AutoLink.LinkSequences"), preferences.getBoolean(PREF_SEQUENCES, true)); //$NON-NLS-1$
		readyOrder = new JRadioButton(Messages.getString("AutoLink.ReadyOrder")); //$NON-NLS-1$
		wbsOrder = new JRadioButton(Messages.getString("AutoLink.WbsOrder")); //$NON-NLS-1$
		ButtonGroup order = new ButtonGroup();
		order.add(readyOrder);
		order.add(wbsOrder);
		if (preferences.getBoolean(PREF_READY_ORDER, true))
			readyOrder.setSelected(true);
		else
			wbsOrder.setSelected(true);
		linkUnassigned = new JCheckBox(Messages.getString("AutoLink.LinkUnassigned"), preferences.getBoolean(PREF_UNASSIGNED, false)); //$NON-NLS-1$
		joinConstraints = new JCheckBox(Messages.getString("AutoLink.JoinConstraints"), preferences.getBoolean(PREF_JOIN, true)); //$NON-NLS-1$
		preview = new JLabel(" "); //$NON-NLS-1$

		ActionListener listener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updatePreview();
			}
		};
		for (AbstractButton button : new AbstractButton[] { wholeProject, selectedTasks, linkActivities, linkSequences,
				readyOrder, wbsOrder, linkUnassigned, joinConstraints })
			button.addActionListener(listener);
		for (JComboBox scope : scopes)
			scope.addActionListener(listener);
	}

	private static String scopePreference(int i) {
		return "autoLinkScope" + i; //$NON-NLS-1$
	}

	protected boolean bind(boolean get) {
		if (get)
			return true;
		updatePreview();
		if (!plan.getCircularTasks().isEmpty()) {
			Alert.error(circularMessage(), this);
			return false;
		}
		preferences.putBoolean(PREF_SCOPE, selectedTasks.isSelected());
		preferences.putBoolean(PREF_ACTIVITIES, linkActivities.isSelected());
		preferences.putBoolean(PREF_SEQUENCES, linkSequences.isSelected());
		preferences.putBoolean(PREF_READY_ORDER, readyOrder.isSelected());
		preferences.putBoolean(PREF_UNASSIGNED, linkUnassigned.isSelected());
		preferences.putBoolean(PREF_JOIN, joinConstraints.isSelected());
		for (int i = 0; i < scopes.length; i++)
			preferences.putInt(scopePreference(i), SCOPE_VALUES[scopes[i].getSelectedIndex()]);
		return true;
	}

	public JComponent createContentPanel() {
		updatePreview();
		// a single column: the options below a check box are indented by a border
		FormLayout layout = new FormLayout(WrappedText.column(WIDTH), ""); //$NON-NLS-1$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		builder.appendSeparator(Messages.getString("AutoLink.Scope")); //$NON-NLS-1$
		builder.append(WrappedText.wrap(wholeProject, WIDTH));
		builder.append(WrappedText.wrap(selectedTasks, WIDTH));
		builder.appendSeparator(Messages.getString("AutoLink.Rules")); //$NON-NLS-1$
		builder.append(WrappedText.wrap(linkActivities, WIDTH));
		for (int i = 0; i < scopes.length; i++) {
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
			row.add(new JLabel(activityNames[i] + " → " + activityNames[i + 1])); //$NON-NLS-1$
			row.add(scopes[i]);
			builder.append(indented(row));
		}
		builder.append(WrappedText.wrap(linkSequences, WIDTH));
		builder.append(indented(WrappedText.label(Messages.getString("AutoLink.SequencesHelp"), WIDTH - WrappedText.INDENT))); //$NON-NLS-1$
		for (AbstractButton option : new AbstractButton[] { readyOrder, wbsOrder, linkUnassigned, joinConstraints })
			builder.append(WrappedText.wrap(indented(option), WIDTH));
		builder.appendSeparator(Messages.getString("AutoLink.Preview")); //$NON-NLS-1$
		builder.append(preview);
		return builder.getPanel();
	}

	private static <T extends JComponent> T indented(T component) {
		component.setBorder(BorderFactory.createEmptyBorder(0, WrappedText.INDENT, 0, 0));
		return component;
	}

	private AutoLinkOptions options() {
		int[] values = new int[scopes.length];
		for (int i = 0; i < scopes.length; i++)
			values[i] = SCOPE_VALUES[scopes[i].getSelectedIndex()];
		AutoLinkOptions options = new AutoLinkOptions(activityCodes, values, UlnlWbsParser.LANE_ACTIVITY_CODES);
		options.setLinkActivities(linkActivities.isSelected());
		options.setLinkSequences(linkSequences.isSelected());
		options.setReadyOrder(readyOrder.isSelected());
		options.setLinkUnassigned(linkUnassigned.isSelected());
		options.setJoinConstraints(joinConstraints.isSelected());
		options.setActivityNames(activityNames);
		return options;
	}

	/** The same lines whatever the options; the dialog grows if a line wraps more */
	private void updatePreview() {
		for (JComboBox scope : scopes)
			scope.setEnabled(linkActivities.isSelected());
		for (JComponent option : new JComponent[] { readyOrder, wbsOrder, linkUnassigned, joinConstraints })
			option.setEnabled(linkSequences.isSelected());
		plan = AutoLinkPlan.create(project, selectedTasks.isSelected() ? selection : null, options());
		int set = 0, removed = 0;
		for (Long date : plan.getConstraints().values())
			if (date == 0)
				removed++;
			else
				set++;
		StringBuilder text = new StringBuilder();
		text.append(MessageFormat.format(Messages.getString("AutoLink.PreviewText"), //$NON-NLS-1$
				String.valueOf(plan.getTaskCount()), String.valueOf(plan.getToCreate().size()), String.valueOf(plan.getToRemove().size()),
				String.valueOf(plan.getKept()), String.valueOf(plan.getExisting())));
		text.append("<br>").append(MessageFormat.format(Messages.getString("AutoLink.PreviewTasks"), //$NON-NLS-1$ //$NON-NLS-2$
				String.valueOf(plan.getPeopleCount()), String.valueOf(set), String.valueOf(removed), String.valueOf(plan.getDelayed().size())));
		text.append("<br>").append(plan.getEstimatedFinish() == 0 ? "&nbsp;" //$NON-NLS-1$ //$NON-NLS-2$
				: FinishSummary.finishText(project, plan.getEstimatedFinish(), "AutoLink.Estimate")); //$NON-NLS-1$
		StringBuilder warnings = new StringBuilder();
		if (linkSequences.isSelected() && plan.getUnassignedCount() > 0)
			warnings.append(MessageFormat.format(Messages.getString(linkUnassigned.isSelected() ? "AutoLink.UnassignedLinked" : "AutoLink.Unassigned"), //$NON-NLS-1$ //$NON-NLS-2$
					String.valueOf(plan.getUnassignedCount()))).append(' ');
		if (plan.getUserConstraintCount() > 0)
			warnings.append(MessageFormat.format(Messages.getString("AutoLink.UserConstraints"), String.valueOf(plan.getUserConstraintCount()))); //$NON-NLS-1$
		text.append("<br>").append(warnings.length() == 0 ? "&nbsp;" : "<font color='#c06000'>" + warnings + "</font>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		text.append("<br>").append(plan.getCircularTasks().isEmpty() ? "&nbsp;" //$NON-NLS-1$ //$NON-NLS-2$
				: "<font color='red'>" + Messages.getString("AutoLink.Circular") + "</font>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		preview.setText(WrappedText.html(text.toString(), WIDTH));
		if (isShowing() && preview.getPreferredSize().height > preview.getHeight())
			pack(); // a longer preview is never cut
	}

	private String circularMessage() {
		StringBuilder names = new StringBuilder();
		List<Task> tasks = plan.getCircularTasks();
		for (int i = 0; i < Math.min(MAX_CIRCULAR_TASKS_SHOWN, tasks.size()); i++)
			names.append("\n- ").append(tasks.get(i).getId()).append(": ").append(tasks.get(i).getName()); //$NON-NLS-1$ //$NON-NLS-2$
		return MessageFormat.format(Messages.getString("AutoLink.CircularError"), String.valueOf(tasks.size())) + names; //$NON-NLS-1$
	}
}
