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
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.prefs.Preferences;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.exchange.ulnl.UlnlWbsParser;
import com.projectlibre1.pm.dependency.autolink.AutoLinkOptions;
import com.projectlibre1.pm.dependency.autolink.AutoLinkPlan;
import com.projectlibre1.pm.dependency.autolink.FinishSummary;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.pm.task.Task;
import com.projectlibre1.strings.Messages;
import com.projectlibre1.util.Alert;

/**
 * Options of Auto Link, with a preview of the links created and removed, of the constraints of the first day of
 * the people and of the estimated finish. The links between activities are rules (source activity, target
 * activity, level N or N-1) the user adds and removes. The options are remembered. Returns the plan to apply, see
 * AutoLinkPlan.
 */
public final class AutoLinkDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final String PREF_SCOPE = "autoLinkScope"; //$NON-NLS-1$
	private static final String PREF_ACTIVITIES = "autoLinkActivities"; //$NON-NLS-1$
	private static final String PREF_RULES = "autoLinkRules"; //$NON-NLS-1$
	private static final String PREF_SEQUENCES = "autoLinkSequences"; //$NON-NLS-1$
	private static final String PREF_READY_ORDER = "autoLinkReadyOrder"; //$NON-NLS-1$
	private static final String PREF_UNASSIGNED = "autoLinkUnassigned"; //$NON-NLS-1$
	private static final String PREF_JOIN = "autoLinkJoinConstraints"; //$NON-NLS-1$
	private static final int[] LEVEL_VALUES = { AutoLinkOptions.PARENT, AutoLinkOptions.GRANDPARENT };
	private static final int MAX_CIRCULAR_TASKS_SHOWN = 5;
	private static final int WIDTH = 540; // pixels of the content, the long texts wrap

	private final String[] activityNames = UlnlWbsParser.DEFAULT_ACTIVITY_NAMES;
	private final String[] activityCodes = UlnlWbsParser.DEFAULT_ACTIVITY_CODES;
	private final Preferences preferences = Preferences.userNodeForPackage(AutoLinkDialog.class);
	private final Project project;
	private final List<Task> selection;
	private final String[] choices; // activity codes of the rules: the default ones, then the ones of the project

	JRadioButton wholeProject;
	JRadioButton selectedTasks;
	JCheckBox linkActivities;
	final List<RuleRow> rules = new ArrayList<RuleRow>();
	JPanel rulesPanel;
	JButton addRule;
	JButton defaultRules;
	JCheckBox linkSequences;
	JRadioButton readyOrder;
	JRadioButton wbsOrder;
	JCheckBox linkUnassigned;
	JCheckBox joinConstraints;
	JLabel preview;
	private final ActionListener listener;
	private AutoLinkPlan plan;

	/** A rule in the dialog: source activity → target activity, level, and a button to remove it */
	private class RuleRow {
		final JComboBox from = activityCombo();
		final JComboBox to = activityCombo();
		final JComboBox level = new JComboBox(new String[] { Messages.getString("AutoLink.LevelParent"), //$NON-NLS-1$
				Messages.getString("AutoLink.LevelGrandparent") }); //$NON-NLS-1$
		final JButton remove = new JButton(Messages.getString("AutoLink.RemoveRule")); //$NON-NLS-1$
		final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

		RuleRow(AutoLinkOptions.Rule rule) {
			from.setSelectedItem(rule.getFrom());
			to.setSelectedItem(rule.getTo());
			level.setSelectedIndex(rule.getLevels() == AutoLinkOptions.GRANDPARENT ? 1 : 0);
			panel.add(from);
			panel.add(new JLabel("→")); //$NON-NLS-1$
			panel.add(to);
			panel.add(level);
			panel.add(remove);
			from.addActionListener(listener);
			to.addActionListener(listener);
			level.addActionListener(listener);
			remove.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					rules.remove(RuleRow.this);
					rulesChanged();
				}
			});
		}
		AutoLinkOptions.Rule rule() {
			return new AutoLinkOptions.Rule((String) from.getSelectedItem(), (String) to.getSelectedItem(),
					LEVEL_VALUES[level.getSelectedIndex()]);
		}
		void setEnabled(boolean enabled) {
			for (Component component : panel.getComponents())
				component.setEnabled(enabled);
		}
	}

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
		listener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updatePreview();
			}
		};
		List<AutoLinkOptions.Rule> saved = savedRules(preferences);
		choices = activityChoices(project, saved);

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
		rulesPanel = new JPanel();
		rulesPanel.setLayout(new BoxLayout(rulesPanel, BoxLayout.Y_AXIS));
		for (AutoLinkOptions.Rule rule : saved)
			rules.add(new RuleRow(rule));
		addRule = new JButton(Messages.getString("AutoLink.AddRule")); //$NON-NLS-1$
		addRule.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				rules.add(new RuleRow(newRule()));
				rulesChanged();
			}
		});
		defaultRules = new JButton(Messages.getString("AutoLink.DefaultRules")); //$NON-NLS-1$
		defaultRules.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				rules.clear();
				for (AutoLinkOptions.Rule rule : AutoLinkOptions.defaultRules(activityCodes))
					rules.add(new RuleRow(rule));
				rulesChanged();
			}
		});
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

		for (AbstractButton button : new AbstractButton[] { wholeProject, selectedTasks, linkActivities, linkSequences,
				readyOrder, wbsOrder, linkUnassigned, joinConstraints })
			button.addActionListener(listener);
	}

	/** The rules saved, else the ones of the old options (a level for each pair of default activities) */
	private static List<AutoLinkOptions.Rule> savedRules(Preferences preferences) {
		List<AutoLinkOptions.Rule> rules = AutoLinkOptions.Rule.parse(preferences.get(PREF_RULES, null));
		if (rules != null)
			return rules;
		rules = new ArrayList<AutoLinkOptions.Rule>();
		for (AutoLinkOptions.Rule rule : AutoLinkOptions.defaultRules(UlnlWbsParser.DEFAULT_ACTIVITY_CODES))
			rules.add(new AutoLinkOptions.Rule(rule.getFrom(), rule.getTo(),
					preferences.getInt(PREF_SCOPE + rules.size(), rule.getLevels())));
		return rules;
	}

	/** The default activities, then the other ones of the tasks of the project and of the rules, sorted */
	private static String[] activityChoices(Project project, List<AutoLinkOptions.Rule> rules) {
		List<String> codes = new ArrayList<String>(Arrays.asList(UlnlWbsParser.DEFAULT_ACTIVITY_CODES));
		TreeSet<String> others = new TreeSet<String>();
		for (Iterator i = project.getTaskOutlineIterator(); i.hasNext();) {
			Object task = i.next();
			if (task instanceof NormalTask && ((NormalTask) task).getActivity() != null)
				others.add(((NormalTask) task).getActivity());
		}
		for (AutoLinkOptions.Rule rule : rules) {
			others.add(rule.getFrom());
			others.add(rule.getTo());
		}
		others.removeAll(codes);
		codes.addAll(others);
		return codes.toArray(new String[codes.size()]);
	}

	/** A new rule: from the target of the last rule to the activity after it */
	private AutoLinkOptions.Rule newRule() {
		String from = rules.isEmpty() ? choices[0] : (String) rules.get(rules.size() - 1).to.getSelectedItem();
		int index = Arrays.asList(choices).indexOf(from);
		String to = choices[Math.min(index + 1, choices.length - 1)];
		return new AutoLinkOptions.Rule(from, to, AutoLinkOptions.PARENT);
	}

	private JComboBox activityCombo() {
		JComboBox combo = new JComboBox(choices);
		combo.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;
			public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				return super.getListCellRendererComponent(list, activityLabel((String) value), index, isSelected, cellHasFocus);
			}
		});
		return combo;
	}

	/** The code and the name of an activity, like "GP - Giải pháp" */
	private String activityLabel(String code) {
		int index = Arrays.asList(activityCodes).indexOf(code);
		return index < 0 || index >= activityNames.length ? code : code + " - " + activityNames[index]; //$NON-NLS-1$
	}

	private void rulesChanged() {
		fillRulesPanel();
		updatePreview();
		rulesPanel.revalidate();
		if (isShowing())
			pack(); // a rule more is never cut
	}

	private void fillRulesPanel() {
		rulesPanel.removeAll();
		for (RuleRow rule : rules) {
			rule.panel.setAlignmentX(Component.LEFT_ALIGNMENT);
			rulesPanel.add(rule.panel);
		}
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		buttons.add(addRule);
		buttons.add(defaultRules);
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		rulesPanel.add(buttons);
	}

	/** The options of the last Auto Link, with the links of the tasks of each person, for the whole project */
	public static AutoLinkOptions savedSequenceOptions() {
		Preferences preferences = Preferences.userNodeForPackage(AutoLinkDialog.class);
		AutoLinkOptions options = new AutoLinkOptions(UlnlWbsParser.DEFAULT_ACTIVITY_CODES, savedRules(preferences), UlnlWbsParser.LANE_ACTIVITY_CODES);
		options.setLinkActivities(preferences.getBoolean(PREF_ACTIVITIES, true));
		options.setLinkSequences(true);
		options.setReadyOrder(preferences.getBoolean(PREF_READY_ORDER, true));
		options.setLinkUnassigned(preferences.getBoolean(PREF_UNASSIGNED, false));
		options.setJoinConstraints(preferences.getBoolean(PREF_JOIN, true));
		options.setActivityNames(UlnlWbsParser.DEFAULT_ACTIVITY_NAMES);
		return options;
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
		preferences.put(PREF_RULES, AutoLinkOptions.Rule.format(currentRules()));
		preferences.putBoolean(PREF_SEQUENCES, linkSequences.isSelected());
		preferences.putBoolean(PREF_READY_ORDER, readyOrder.isSelected());
		preferences.putBoolean(PREF_UNASSIGNED, linkUnassigned.isSelected());
		preferences.putBoolean(PREF_JOIN, joinConstraints.isSelected());
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
		builder.append(indented(WrappedText.label(Messages.getString("AutoLink.RulesHelp"), WIDTH - WrappedText.INDENT))); //$NON-NLS-1$
		fillRulesPanel();
		builder.append(indented(rulesPanel));
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

	private List<AutoLinkOptions.Rule> currentRules() {
		List<AutoLinkOptions.Rule> list = new ArrayList<AutoLinkOptions.Rule>();
		for (RuleRow rule : rules)
			list.add(rule.rule());
		return list;
	}

	private AutoLinkOptions options() {
		AutoLinkOptions options = new AutoLinkOptions(activityCodes, currentRules(), UlnlWbsParser.LANE_ACTIVITY_CODES);
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
		for (RuleRow rule : rules)
			rule.setEnabled(linkActivities.isSelected());
		addRule.setEnabled(linkActivities.isSelected());
		defaultRules.setEnabled(linkActivities.isSelected());
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
		if (linkActivities.isSelected())
			for (AutoLinkOptions.Rule rule : currentRules())
				if (rule.getFrom().equals(rule.getTo())) {
					warnings.append(MessageFormat.format(Messages.getString("AutoLink.SameActivity"), rule.getFrom())).append(' '); //$NON-NLS-1$
					break;
				}
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
