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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import com.projectlibre1.options.EditOption;
import com.projectlibre1.pm.assignment.board.AssignmentBoard;
import com.projectlibre1.pm.assignment.board.SuggestOptions;
import com.projectlibre1.pm.graphic.IconManager;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.resource.Resource;
import com.projectlibre1.pm.resource.labor.LaborCatalog;
import com.projectlibre1.pm.resource.plan.ResourcePlan;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.strings.Messages;

/**
 * Assignment board, see AssignmentBoard: the tasks of the activities in their WBS on the left, the people with a
 * role and their load on the right. The tasks selected are given to a person, or by cluster (a person for each role
 * and skill of the tasks below a summary). The changes are only kept in the board: Apply returns it, and the caller
 * writes it in the project with AssignmentBoardWriter.
 */
public final class AssignmentBoardDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	// columns of the tasks
	private static final int T_LOCK = 0, T_NAME = 1, T_ACTIVITY = 2, T_ROLE = 3, T_SKILL = 4, T_EFFORT = 5, T_PEOPLE = 6, T_SUGGESTION = 7, T_ISSUES = 8;
	private static final String[] T_HEADERS = { "LockColumn", "Task", "Activity", "Role", "Skill", "Effort", "People", "Suggestion", "Issues" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
	private static final int[] T_WIDTHS = { 22, 270, 45, 55, 45, 55, 130, 130, 120 };
	// columns of the people
	private static final int P_NAME = 0, P_ROLE = 1, P_SKILL = 2, P_LEVEL = 3, P_FROM = 4, P_TO = 5, P_TASKS = 6, P_ASSIGNED = 7, P_CAPACITY = 8, P_LOAD = 9;
	private static final String[] P_HEADERS = { "Person", "Role", "Skill", "Level", "From", "To", "TaskCount", "Assigned", "Capacity", "Load" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
	private static final int[] P_WIDTHS = { 140, 60, 45, 45, 70, 70, 45, 60, 65, 110 };
	/** Units of the effort, as in the Resource Plan, whose choice is shared */
	private static final String[] UNITS = { "MH", "MD", "MM" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	private static final double[] UNIT_HOURS = { 1, NormalTask.HOURS_PER_MAN_DAY, NormalTask.HOURS_PER_MAN_DAY * NormalTask.MAN_DAYS_PER_MAN_MONTH };
	private static final String UNIT = "resourcePlan.unit"; //$NON-NLS-1$
	private static final int DEFAULT_UNIT = 1;
	private static final int INDENT = 16;
	private static final int MAX_NAMES = 3;
	private static final Color WARNING_COLOR = new Color(0xb2, 0x6a, 0x00), FAILED_COLOR = new Color(0xc0, 0x00, 0x00);
	private static final Color BAR_OK = new Color(0x4a, 0x90, 0xd9), BAR_WARNING = new Color(0xf0, 0xa0, 0x30), BAR_FAILED = new Color(0xd9, 0x40, 0x40);
	private static final Color HIGHLIGHT = new Color(0xff, 0xf2, 0xc4), CHANGED = new Color(0x00, 0x55, 0xcc), DIMMED = new Color(0xa0, 0xa0, 0xa0);
	private static final Color SUGGESTED = new Color(0x2e, 0x7d, 0x32), SUGGESTED_BAR = new Color(0xa5, 0xd6, 0xa7);

	private final AssignmentBoard board;
	private final SuggestOptions suggestOptions = SuggestOptions.load();
	private final DateFormat dateFormat = EditOption.getInstance().getShortDateFormat();
	private final NumberFormat decimal = NumberFormat.getNumberInstance();
	private final Icon warningIcon = IconManager.getIcon("indicator.availability"); //$NON-NLS-1$
	private final Icon lockIcon = new LockIcon(false), partLockIcon = new LockIcon(true);
	private int unit = DEFAULT_UNIT;
	/** What the last action did not do, shown after the status until the next action, "" for nothing */
	private String note = ""; //$NON-NLS-1$

	/** The rows shown: the filter passed, below the summaries not collapsed */
	private final List<AssignmentBoard.Row> rows = new ArrayList<AssignmentBoard.Row>();
	private final Set<AssignmentBoard.Row> collapsed = Collections.newSetFromMap(new IdentityHashMap<AssignmentBoard.Row, Boolean>());
	private final List<AssignmentBoard.Person> persons = new ArrayList<AssignmentBoard.Person>();
	private JTable taskTable, personTable;
	private AbstractTableModel taskModel, personModel;
	private JComboBox roleFilter, skillFilter, personRoleFilter, unitChoice;
	private JCheckBox unassignedOnly, issuesOnly;
	private JLabel status;
	/** The tasks selected, kept when the rows are shown again; the tasks they give with the filter */
	private List<AssignmentBoard.Row> selectedRows = new ArrayList<AssignmentBoard.Row>();
	/** The rows selected, to know if a task is below a summary selected */
	private Set<AssignmentBoard.Row> selectedSet = Collections.newSetFromMap(new IdentityHashMap<AssignmentBoard.Row, Boolean>());
	private List<AssignmentBoard.Row> selectedTasks = new ArrayList<AssignmentBoard.Row>();
	private boolean updating = false;

	/** @return the board to write in the project, or null if closed */
	public static AssignmentBoard doDialog(Project project) {
		AssignmentBoard board = AssignmentBoard.create(project);
		if (board.getActivities().isEmpty()) {
			JOptionPane.showMessageDialog(GraphicManager.getFrameInstance(), Messages.getString("AssignmentBoard.NoTasks"), //$NON-NLS-1$
					Messages.getString("AssignmentBoard.Title"), JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$
			return null;
		}
		AssignmentBoardDialog dialog = new AssignmentBoardDialog(board);
		return dialog.doModal() ? board : null;
	}

	private AssignmentBoardDialog(AssignmentBoard board) {
		super(GraphicManager.getFrameInstance(), Messages.getString("AssignmentBoard.Title"), true); //$NON-NLS-1$
		this.board = board;
		decimal.setMaximumFractionDigits(1);
		decimal.setMinimumFractionDigits(0);
		try {
			unit = Math.max(0, Math.min(UNITS.length - 1, Preferences.userNodeForPackage(AssignmentBoardDialog.class).getInt(UNIT, DEFAULT_UNIT)));
		} catch (Exception e) { // preferences not available
		}
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // closing the window asks to drop the changes, see onCancel()
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				onCancel();
			}
		});
	}

	/** No buttons at the bottom of AbstractDialog: Apply and Close are in the content */
	protected boolean hasOkAndCancelButtons() {
		return false;
	}

	/** Enter does not apply the changes: only the Apply button does */
	public void onOk() {
	}

	private void apply() {
		super.onOk();
	}

	/** Closes without changing the project, after asking to drop the changes */
	protected void onCancel() {
		if (board.isChanged() && JOptionPane.showConfirmDialog(this, Messages.getString("AssignmentBoard.DropChanges"), getTitle(), //$NON-NLS-1$
				JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
			return;
		super.onCancel();
	}

	public JComponent createContentPanel() {
		createTaskTable();
		createPersonTable();

		roleFilter = new JComboBox();
		skillFilter = new JComboBox();
		roleFilter.addItem(Messages.getString("AssignmentBoard.All")); //$NON-NLS-1$
		skillFilter.addItem(Messages.getString("AssignmentBoard.All")); //$NON-NLS-1$
		Set<String> roles = new LinkedHashSet<String>(), skills = new LinkedHashSet<String>();
		for (AssignmentBoard.Row row : board.getActivities()) {
			roles.add(row.getRole());
			if (row.getSkill().length() > 0)
				skills.add(row.getSkill());
		}
		for (String role : roles)
			roleFilter.addItem(role);
		for (String skill : skills)
			skillFilter.addItem(skill);
		unassignedOnly = new JCheckBox(Messages.getString("AssignmentBoard.UnassignedOnly")); //$NON-NLS-1$
		issuesOnly = new JCheckBox(Messages.getString("AssignmentBoard.IssuesOnly")); //$NON-NLS-1$
		ActionListener filter = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showRows();
			}
		};
		roleFilter.addActionListener(filter);
		skillFilter.addActionListener(filter);
		unassignedOnly.addActionListener(filter);
		issuesOnly.addActionListener(filter);

		personRoleFilter = new JComboBox();
		personRoleFilter.addItem(Messages.getString("AssignmentBoard.All")); //$NON-NLS-1$
		Set<String> personRoles = new LinkedHashSet<String>();
		for (AssignmentBoard.Person person : board.getPersons())
			personRoles.add(person.getRole());
		for (String role : personRoles)
			personRoleFilter.addItem(role);
		personRoleFilter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showPersons();
			}
		});

		unitChoice = new JComboBox(UNITS);
		unitChoice.setSelectedIndex(unit);
		unitChoice.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				unit = unitChoice.getSelectedIndex();
				try {
					Preferences.userNodeForPackage(AssignmentBoardDialog.class).putInt(UNIT, unit);
				} catch (Exception ex) { // preferences not available
				}
				taskModel.fireTableRowsUpdated(0, Math.max(0, rows.size() - 1));
				personModel.fireTableRowsUpdated(0, Math.max(0, persons.size() - 1));
			}
		});

		JPanel taskFilters = flow(label("AssignmentBoard.FilterRole"), roleFilter, label("AssignmentBoard.FilterSkill"), skillFilter, //$NON-NLS-1$ //$NON-NLS-2$
				unassignedOnly, issuesOnly);
		JPanel taskTools = flow(button("AssignmentBoard.ExpandAll", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				collapsed.clear();
				showRows();
			}
		}), button("AssignmentBoard.CollapseAll", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				collapseAll(board.getRoots());
				showRows();
			}
		}), label("AssignmentBoard.Unit"), unitChoice); //$NON-NLS-1$
		JPanel taskHeader = new JPanel(new BorderLayout());
		taskHeader.add(taskFilters, BorderLayout.WEST);
		taskHeader.add(taskTools, BorderLayout.EAST);
		JPanel taskPanel = new JPanel(new BorderLayout(0, 4));
		taskPanel.add(taskHeader, BorderLayout.NORTH);
		JScrollPane taskScroll = new JScrollPane(taskTable);
		taskScroll.setPreferredSize(new Dimension(760, 520));
		taskPanel.add(taskScroll, BorderLayout.CENTER);

		JPanel personPanel = new JPanel(new BorderLayout(0, 4));
		personPanel.add(flow(label("AssignmentBoard.FilterRole"), personRoleFilter), BorderLayout.NORTH); //$NON-NLS-1$
		JScrollPane personScroll = new JScrollPane(personTable);
		personScroll.setPreferredSize(new Dimension(710, 520));
		personPanel.add(personScroll, BorderLayout.CENTER);
		personPanel.add(help("AssignmentBoard.PeopleHelp"), BorderLayout.SOUTH); //$NON-NLS-1$

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, taskPanel, personPanel);
		split.setResizeWeight(0.6);
		split.setBorder(null);

		JPanel actions = flow(button("AssignmentBoard.Assign", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				assignSelected();
			}
		}), button("AssignmentBoard.AssignCluster", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				assignCluster();
			}
		}), button("AssignmentBoard.Unassign", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				unassign();
			}
		}), separator(), button("AssignmentBoard.Suggest", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				suggest();
			}
		}), button("AssignmentBoard.Accept", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				accept();
			}
		}), separator(), button("AssignmentBoard.Lock", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				lock(true);
			}
		}), button("AssignmentBoard.Unlock", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				lock(false);
			}
		}));
		JPanel closing = flow(button("AssignmentBoard.Apply", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				apply();
			}
		}), button("AssignmentBoard.Close", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				onCancel();
			}
		}));
		status = new JLabel();
		JPanel bottom = new JPanel(new BorderLayout());
		bottom.add(actions, BorderLayout.NORTH);
		bottom.add(status, BorderLayout.WEST);
		bottom.add(closing, BorderLayout.EAST);

		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		panel.add(split, BorderLayout.CENTER);
		panel.add(bottom, BorderLayout.SOUTH);
		showRows();
		showPersons();
		return panel;
	}

	private static JPanel flow(JComponent... components) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		for (JComponent component : components)
			panel.add(component);
		return panel;
	}

	private static JLabel label(String key) {
		return new JLabel(Messages.getString(key));
	}

	private static JComponent separator() {
		JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
		separator.setPreferredSize(new Dimension(2, 22));
		return separator;
	}

	private static JButton button(String key, ActionListener listener) {
		JButton button = new JButton(Messages.getString(key));
		String tooltip = Messages.getString(key + "Tooltip"); //$NON-NLS-1$
		if (!tooltip.startsWith("!")) // a missing key is !key! //$NON-NLS-1$
			button.setToolTipText(tooltip);
		button.addActionListener(listener);
		return button;
	}

	private static JLabel help(String key) {
		return new JLabel("<html><div style='width:400px'>" + Messages.getString(key) + "</div></html>"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void collapseAll(List<AssignmentBoard.Row> list) {
		for (AssignmentBoard.Row row : list)
			if (!row.isActivity()) {
				collapsed.add(row);
				collapseAll(row.getChildren());
			}
	}

	// ---------------------------------------------------------------- filter and rows

	/** True if a task passes the filters */
	private boolean passes(AssignmentBoard.Row row) {
		if (roleFilter.getSelectedIndex() > 0 && !roleFilter.getSelectedItem().equals(row.getRole()))
			return false;
		if (skillFilter.getSelectedIndex() > 0 && !skillFilter.getSelectedItem().equals(row.getSkill()))
			return false;
		if (unassignedOnly.isSelected() && !row.getResources().isEmpty())
			return false;
		if (issuesOnly.isSelected() && !board.hasIssues(row))
			return false;
		return true;
	}

	/** The tasks passing the filters below rows, or the rows themselves */
	private List<AssignmentBoard.Row> filtered(List<AssignmentBoard.Row> list) {
		List<AssignmentBoard.Row> result = new ArrayList<AssignmentBoard.Row>();
		for (AssignmentBoard.Row row : AssignmentBoard.activities(list))
			if (passes(row))
				result.add(row);
		return result;
	}

	/** Shows the rows again, after a change of the filters, of the people or of a collapse, keeping the selection */
	private void showRows() {
		updating = true;
		try {
			rows.clear();
			addRows(board.getRoots());
			taskModel.fireTableDataChanged();
			for (AssignmentBoard.Row row : selectedRows) {
				int index = indexOf(rows, row);
				if (index >= 0)
					taskTable.addRowSelectionInterval(index, index);
			}
		} finally {
			updating = false;
		}
		readSelection();
	}

	private void addRows(List<AssignmentBoard.Row> list) {
		for (AssignmentBoard.Row row : list) {
			if (row.isActivity()) {
				if (passes(row))
					rows.add(row);
			} else if (!filtered(Collections.singletonList(row)).isEmpty()) {
				rows.add(row);
				if (!collapsed.contains(row))
					addRows(row.getChildren());
			}
		}
	}

	private static <T> int indexOf(List<T> list, T object) {
		for (int i = 0; i < list.size(); i++)
			if (list.get(i) == object)
				return i;
		return -1;
	}

	private void readSelection() {
		if (updating)
			return;
		selectedRows = new ArrayList<AssignmentBoard.Row>();
		for (int index : taskTable.getSelectedRows())
			selectedRows.add(rows.get(index));
		selectedSet = Collections.newSetFromMap(new IdentityHashMap<AssignmentBoard.Row, Boolean>());
		selectedSet.addAll(selectedRows);
		selectedTasks = filtered(selectedRows);
		personModel.fireTableRowsUpdated(0, Math.max(0, persons.size() - 1)); // the people who cannot do them are dimmed
		showStatus();
	}

	private void showPersons() {
		AssignmentBoard.Person selected = selectedPerson();
		persons.clear();
		for (AssignmentBoard.Person person : board.getPersons())
			if (personRoleFilter.getSelectedIndex() <= 0 || personRoleFilter.getSelectedItem().equals(person.getRole()))
				persons.add(person);
		personModel.fireTableDataChanged();
		int index = indexOf(persons, selected);
		if (index >= 0)
			personTable.setRowSelectionInterval(index, index);
	}

	private AssignmentBoard.Person selectedPerson() {
		int index = personTable == null ? -1 : personTable.getSelectedRow();
		return index < 0 || index >= persons.size() ? null : persons.get(index);
	}

	private void showStatus() {
		int total = board.getActivities().size(), unassigned = board.getUnassignedCount();
		status.setText(MessageFormat.format(Messages.getString("AssignmentBoard.Status"), String.valueOf(total - unassigned), //$NON-NLS-1$
				String.valueOf(total), String.valueOf(unassigned), String.valueOf(board.getIssueCount()),
				String.valueOf(board.getChangedRows().size()), String.valueOf(selectedTasks.size()),
				String.valueOf(board.getSuggestionCount()), String.valueOf(board.getLockedCount())) + note);
	}

	/** After a change of the people: the loads, the rows and the people. Shows a note on the locked tasks kept */
	private void changed(int kept) {
		note = kept == 0 ? "" : MessageFormat.format(Messages.getString("AssignmentBoard.LockedKept"), String.valueOf(kept)); //$NON-NLS-1$ //$NON-NLS-2$
		board.calculate();
		showRows();
		personModel.fireTableRowsUpdated(0, Math.max(0, persons.size() - 1));
		showStatus();
	}

	// ---------------------------------------------------------------- actions

	private boolean checkTasks() {
		if (!selectedTasks.isEmpty())
			return true;
		JOptionPane.showMessageDialog(this, Messages.getString("AssignmentBoard.ChooseTasks"), getTitle(), JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$
		return false;
	}

	/** Gives the tasks selected to the person selected */
	private void assignSelected() {
		if (!checkTasks())
			return;
		AssignmentBoard.Person person = selectedPerson();
		if (person == null) {
			JOptionPane.showMessageDialog(this, Messages.getString("AssignmentBoard.ChoosePerson"), getTitle(), JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$
			return;
		}
		assignTo(person);
	}

	/** Gives the tasks selected to a person, after asking if some are not of its role or skill. Below a summary selected,
	 * only the tasks the person can do go to it, the others keep their people */
	private void assignTo(AssignmentBoard.Person person) {
		List<AssignmentBoard.Row> tasks = tasksFor(person);
		if (tasks.isEmpty()) {
			JOptionPane.showMessageDialog(this, MessageFormat.format(Messages.getString("AssignmentBoard.NoTaskFits"), person.getName()), //$NON-NLS-1$
					getTitle(), JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		int wrong = 0;
		for (AssignmentBoard.Row row : tasks)
			if (!AssignmentBoard.canDo(person, row))
				wrong++;
		if (wrong > 0 && JOptionPane.showConfirmDialog(this, MessageFormat.format(Messages.getString("AssignmentBoard.ConfirmMismatch"), //$NON-NLS-1$
				String.valueOf(wrong), person.getName()), getTitle(), JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
			return;
		int skipped = selectedTasks.size() - tasks.size();
		changed(board.assign(tasks, person.getResource()));
		if (skipped > 0) {
			note += MessageFormat.format(Messages.getString("AssignmentBoard.OthersKept"), String.valueOf(skipped), person.getName()); //$NON-NLS-1$
			showStatus();
		}
	}

	/** The tasks selected that go to a person: the tasks selected themselves, and below a summary selected only the
	 * tasks the person can do (its role, and its skill when the task needs one) */
	private List<AssignmentBoard.Row> tasksFor(AssignmentBoard.Person person) {
		List<AssignmentBoard.Row> result = new ArrayList<AssignmentBoard.Row>();
		for (AssignmentBoard.Row row : selectedTasks)
			if (!belowSelectedSummary(row) || AssignmentBoard.canDo(person, row))
				result.add(row);
		return result;
	}

	private boolean belowSelectedSummary(AssignmentBoard.Row row) {
		for (AssignmentBoard.Row parent = row.getParent(); parent != null; parent = parent.getParent())
			if (selectedSet.contains(parent))
				return true;
		return false;
	}

	private void unassign() {
		if (!checkTasks())
			return;
		changed(board.assign(selectedTasks, null));
	}

	/** Asks the options of the suggestions, then suggests a person for the tasks without people and not locked */
	private void suggest() {
		final JCheckBox keepFunction = new JCheckBox(Messages.getString("AssignmentBoard.KeepFunction")); //$NON-NLS-1$
		final JCheckBox keepCluster = new JCheckBox(Messages.getString("AssignmentBoard.KeepCluster")); //$NON-NLS-1$
		final JCheckBox preferExisting = new JCheckBox(Messages.getString("AssignmentBoard.PreferExisting")); //$NON-NLS-1$
		keepFunction.setSelected(suggestOptions.isKeepFunction());
		keepCluster.setSelected(suggestOptions.isKeepCluster());
		preferExisting.setSelected(suggestOptions.isPreferExisting());
		JButton defaults = new JButton(Messages.getString("ResourcePlan.Defaults")); //$NON-NLS-1$
		defaults.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				SuggestOptions d = new SuggestOptions();
				keepFunction.setSelected(d.isKeepFunction());
				keepCluster.setSelected(d.isKeepCluster());
				preferExisting.setSelected(d.isPreferExisting());
			}
		});
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 3, 3, 3);
		c.anchor = GridBagConstraints.WEST;
		c.gridx = 0;
		c.gridy = GridBagConstraints.RELATIVE;
		panel.add(new JLabel("<html><div style='width:420px'>" + Messages.getString("AssignmentBoard.SuggestHelp") + "</div></html>"), c); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		panel.add(keepFunction, c);
		panel.add(keepCluster, c);
		panel.add(preferExisting, c);
		panel.add(defaults, c);
		if (JOptionPane.showConfirmDialog(this, panel, Messages.getString("AssignmentBoard.Suggest"), JOptionPane.OK_CANCEL_OPTION, //$NON-NLS-1$
				JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
			return;
		suggestOptions.setKeepFunction(keepFunction.isSelected());
		suggestOptions.setKeepCluster(keepCluster.isSelected());
		suggestOptions.setPreferExisting(preferExisting.isSelected());
		suggestOptions.save();
		board.suggest(suggestOptions);
		changed(0);
	}

	/** The suggestions of the tasks selected become their people, or the ones of all the tasks if none is selected */
	private void accept() {
		List<AssignmentBoard.Row> tasks = selectedTasks.isEmpty() ? board.getActivities() : selectedTasks;
		int suggested = 0;
		for (AssignmentBoard.Row row : tasks)
			if (row.getSuggestion() != null && !row.isLocked())
				suggested++;
		if (suggested == 0) {
			JOptionPane.showMessageDialog(this, Messages.getString("AssignmentBoard.NoSuggestion"), getTitle(), JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$
			return;
		}
		if (selectedTasks.isEmpty() && JOptionPane.showConfirmDialog(this, MessageFormat.format(Messages.getString("AssignmentBoard.AcceptAll"), //$NON-NLS-1$
				String.valueOf(suggested)), getTitle(), JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
			return;
		board.acceptSuggestions(tasks);
		changed(0);
	}

	private void lock(boolean locked) {
		if (!checkTasks())
			return;
		board.setLocked(selectedTasks, locked);
		changed(0);
	}

	/** Locks a task, or the tasks below a summary that pass the filters, or unlocks them if they all are */
	private void toggleLock(AssignmentBoard.Row row) {
		List<AssignmentBoard.Row> tasks = filtered(Collections.singletonList(row));
		board.setLocked(tasks, lockState(tasks) != ALL_LOCKED);
		changed(0);
	}

	private static final int NONE_LOCKED = 0, SOME_LOCKED = 1, ALL_LOCKED = 2;

	private static int lockState(List<AssignmentBoard.Row> tasks) {
		int locked = 0;
		for (AssignmentBoard.Row task : tasks)
			if (task.isLocked())
				locked++;
		return locked == 0 ? NONE_LOCKED : locked == tasks.size() ? ALL_LOCKED : SOME_LOCKED;
	}

	/**
	 * A person for each role and skill of the tasks selected (BA, DEV BE, DEV FE, TESTER...): each task goes to the
	 * person of its role and skill. A group left unchanged keeps its people
	 */
	private void assignCluster() {
		if (!checkTasks())
			return;
		Map<String, List<AssignmentBoard.Row>> groups = new LinkedHashMap<String, List<AssignmentBoard.Row>>();
		for (AssignmentBoard.Row row : selectedTasks) {
			String key = ResourcePlan.key(row.getRole(), row.getSkill());
			List<AssignmentBoard.Row> group = groups.get(key);
			if (group == null)
				groups.put(key, group = new ArrayList<AssignmentBoard.Row>());
			group.add(row);
		}
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 3, 3, 3);
		c.anchor = GridBagConstraints.WEST;
		c.gridwidth = 2;
		c.gridy = 0;
		panel.add(new JLabel("<html><div style='width:380px'>" + Messages.getString("AssignmentBoard.ClusterHelp") + "</div></html>"), c); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		c.gridwidth = 1;
		Map<String, JComboBox> choices = new LinkedHashMap<String, JComboBox>();
		for (Map.Entry<String, List<AssignmentBoard.Row>> group : groups.entrySet()) {
			double hours = 0;
			for (AssignmentBoard.Row row : group.getValue())
				hours += row.getHours();
			JComboBox choice = new JComboBox();
			choice.addItem(Messages.getString("AssignmentBoard.NoChange")); //$NON-NLS-1$
			AssignmentBoard.Row first = group.getValue().get(0);
			for (AssignmentBoard.Person person : board.getPersons())
				if (AssignmentBoard.canDo(person, first))
					choice.addItem(new PersonChoice(person));
			choices.put(group.getKey(), choice);
			c.gridy++;
			c.gridx = 0;
			panel.add(new JLabel(MessageFormat.format(Messages.getString("AssignmentBoard.ClusterGroup"), group.getKey(), //$NON-NLS-1$
					String.valueOf(group.getValue().size()), effort(hours))), c);
			c.gridx = 1;
			panel.add(choice, c);
		}
		if (JOptionPane.showConfirmDialog(this, panel, Messages.getString("AssignmentBoard.AssignCluster"), JOptionPane.OK_CANCEL_OPTION, //$NON-NLS-1$
				JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
			return;
		int kept = 0;
		for (Map.Entry<String, JComboBox> choice : choices.entrySet())
			if (choice.getValue().getSelectedItem() instanceof PersonChoice)
				kept += board.assign(groups.get(choice.getKey()), ((PersonChoice) choice.getValue().getSelectedItem()).person.getResource());
		changed(kept);
	}

	/** A person in a list: its name and its load */
	private class PersonChoice {
		final AssignmentBoard.Person person;
		PersonChoice(AssignmentBoard.Person person) {
			this.person = person;
		}
		public String toString() {
			return person.getName() + " (" + load(person) + ")"; //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	// ---------------------------------------------------------------- formats

	private String effort(double hours) {
		return decimal.format(hours / UNIT_HOURS[unit]) + " " + UNITS[unit]; //$NON-NLS-1$
	}

	private static String load(AssignmentBoard.Person person) {
		return percent(person.getLoad());
	}

	private static String percent(double load) {
		return Double.isInfinite(load) ? "∞ %" : Math.round(load) + "%"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String level(AssignmentBoard.Person person) {
		if (person.getLevel() < 0)
			return ""; //$NON-NLS-1$
		LaborCatalog.Category category = LaborCatalog.getInstance().find(person.getRole(), person.getLevel());
		return category == null ? LaborCatalog.levelAbbreviation(person.getLevel()) : category.getLevelCode();
	}

	/** People of a task, or of the tasks below a summary: the first names and the number of the others */
	private static String people(AssignmentBoard.Row row) {
		if (row.isActivity())
			return AssignmentBoard.names(row.getResources());
		Set<Resource> resources = new LinkedHashSet<Resource>();
		for (AssignmentBoard.Row task : AssignmentBoard.activities(Collections.singletonList(row)))
			resources.addAll(task.getResources());
		List<Resource> list = new ArrayList<Resource>(resources);
		if (list.size() <= MAX_NAMES)
			return AssignmentBoard.names(list);
		return AssignmentBoard.names(list.subList(0, MAX_NAMES)) + MessageFormat.format(Messages.getString("AssignmentBoard.Others"), //$NON-NLS-1$
				String.valueOf(list.size() - MAX_NAMES));
	}

	/** Person suggested for a task, or the number of suggestions below a summary */
	private static String suggestion(AssignmentBoard.Row row) {
		if (row.isActivity())
			return row.getSuggestion() == null ? "" : row.getSuggestion().getName(); //$NON-NLS-1$
		int count = 0;
		for (AssignmentBoard.Row task : AssignmentBoard.activities(Collections.singletonList(row)))
			if (task.getSuggestion() != null)
				count++;
		return count == 0 ? "" : MessageFormat.format(Messages.getString("AssignmentBoard.SummarySuggestions"), String.valueOf(count)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** The issues of a task, or the number of tasks not assigned and with issues below a summary */
	private String issues(AssignmentBoard.Row row) {
		if (row.isActivity()) {
			List<String> issues = board.getIssues(row);
			return issues.isEmpty() ? "" : issues.get(0); //$NON-NLS-1$
		}
		int unassigned = 0, wrong = 0;
		for (AssignmentBoard.Row task : AssignmentBoard.activities(Collections.singletonList(row)))
			if (task.getResources().isEmpty())
				unassigned++;
			else if (board.hasIssues(task))
				wrong++;
		StringBuilder text = new StringBuilder();
		if (unassigned > 0)
			text.append(MessageFormat.format(Messages.getString("AssignmentBoard.SummaryUnassigned"), String.valueOf(unassigned))); //$NON-NLS-1$
		if (wrong > 0)
			text.append(text.length() == 0 ? "" : ", ").append(MessageFormat.format(Messages.getString("AssignmentBoard.SummaryIssues"), String.valueOf(wrong))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return text.toString();
	}

	private static String html(List<String> lines) {
		StringBuilder html = new StringBuilder("<html>"); //$NON-NLS-1$
		for (int i = 0; i < lines.size(); i++)
			html.append(i == 0 ? "" : "<br>").append(lines.get(i).replace("&", "&amp;").replace("<", "&lt;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		return html.append("</html>").toString(); //$NON-NLS-1$
	}

	// ---------------------------------------------------------------- tables

	private void createTaskTable() {
		taskModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
			public int getRowCount() {
				return rows.size();
			}
			public int getColumnCount() {
				return T_HEADERS.length;
			}
			public String getColumnName(int column) {
				return Messages.getString("AssignmentBoard." + T_HEADERS[column]); //$NON-NLS-1$
			}
			public Object getValueAt(int index, int column) {
				AssignmentBoard.Row row = rows.get(index);
				switch (column) {
				case T_LOCK: return ""; // an icon, see TaskRenderer //$NON-NLS-1$
				case T_NAME: return row.getTask().getName();
				case T_ACTIVITY: return row.isActivity() ? row.getTask().getActivity() : ""; //$NON-NLS-1$
				case T_ROLE: return row.isActivity() ? row.getRole() : ""; //$NON-NLS-1$
				case T_SKILL: return row.isActivity() ? row.getSkill() : ""; //$NON-NLS-1$
				case T_EFFORT: return decimal.format((row.isActivity() ? row.getHours() : hours(filtered(Collections.singletonList(row)))) / UNIT_HOURS[unit]);
				case T_PEOPLE: return people(row);
				case T_SUGGESTION: return suggestion(row);
				case T_ISSUES: return issues(row);
				default: return null;
				}
			}
		};
		taskTable = new JTable(taskModel) {
			private static final long serialVersionUID = 1L;
			public String getToolTipText(MouseEvent e) {
				int index = rowAtPoint(e.getPoint()), column = columnAtPoint(e.getPoint());
				if (index < 0 || column < 0)
					return null;
				AssignmentBoard.Row row = rows.get(index);
				int c = convertColumnIndexToModel(column);
				if (c == T_ISSUES && row.isActivity()) {
					List<String> issues = board.getIssues(row);
					return issues.isEmpty() ? null : html(issues);
				}
				if (c == T_LOCK)
					return Messages.getString("AssignmentBoard.LockTooltip"); //$NON-NLS-1$
				if (c == T_PEOPLE || c == T_SUGGESTION || c == T_ISSUES || c == T_NAME) {
					Object value = getValueAt(index, column);
					return value == null || value.toString().length() == 0 ? null : value.toString();
				}
				return null;
			}
		};
		taskTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		taskTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		taskTable.getTableHeader().setReorderingAllowed(false);
		for (int i = 0; i < T_WIDTHS.length; i++)
			taskTable.getColumnModel().getColumn(i).setPreferredWidth(T_WIDTHS[i]);
		TaskRenderer renderer = new TaskRenderer();
		for (int i = 0; i < T_HEADERS.length; i++)
			taskTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
		taskTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if (!e.getValueIsAdjusting())
					readSelection();
			}
		});
		taskTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				int index = taskTable.rowAtPoint(e.getPoint()), column = taskTable.columnAtPoint(e.getPoint());
				if (index < 0 || column < 0)
					return;
				AssignmentBoard.Row row = rows.get(index);
				if (taskTable.convertColumnIndexToModel(column) == T_LOCK) {
					toggleLock(row);
					return;
				}
				if (row.isActivity())
					return;
				boolean onArrow = taskTable.convertColumnIndexToModel(column) == T_NAME
						&& e.getX() - taskTable.getCellRect(index, column, false).x < (row.getLevel() + 1) * INDENT + 4;
				if (onArrow || e.getClickCount() == 2) {
					if (!collapsed.remove(row))
						collapsed.add(row);
					showRows();
				}
			}
		});
		// the tasks selected are dragged onto a person of the other table
		taskTable.setDragEnabled(true);
		taskTable.setTransferHandler(new TransferHandler() {
			private static final long serialVersionUID = 1L;
			public int getSourceActions(JComponent c) {
				return selectedTasks.isEmpty() ? NONE : MOVE;
			}
			protected Transferable createTransferable(JComponent c) {
				return new StringSelection(TASKS_FLAVOR);
			}
		});
	}

	/** Text of the tasks dragged: the tasks are the ones selected */
	private static final String TASKS_FLAVOR = "AssignmentBoard.tasks"; //$NON-NLS-1$

	/** Drops the tasks dragged from the table of the tasks onto a person: they go to the person */
	private class PersonDropHandler extends TransferHandler {
		private static final long serialVersionUID = 1L;
		public boolean canImport(TransferSupport support) {
			if (!support.isDrop() || !support.isDataFlavorSupported(DataFlavor.stringFlavor))
				return false;
			JTable.DropLocation location = (JTable.DropLocation) support.getDropLocation();
			return location.getRow() >= 0 && location.getRow() < persons.size();
		}
		public boolean importData(TransferSupport support) {
			if (!canImport(support))
				return false;
			try {
				if (!TASKS_FLAVOR.equals(support.getTransferable().getTransferData(DataFlavor.stringFlavor)))
					return false;
			} catch (Exception e) {
				return false;
			}
			final AssignmentBoard.Person person = persons.get(((JTable.DropLocation) support.getDropLocation()).getRow());
			SwingUtilities.invokeLater(new Runnable() { // after the drop, so that a question can be asked
				public void run() {
					assignTo(person);
				}
			});
			return true;
		}
	}

	private static double hours(List<AssignmentBoard.Row> list) {
		double hours = 0;
		for (AssignmentBoard.Row row : list)
			hours += row.getHours();
		return hours;
	}

	/** True if the person selected does a task, or one below a summary */
	private boolean ofSelectedPerson(AssignmentBoard.Row row) {
		AssignmentBoard.Person person = selectedPerson();
		if (person == null)
			return false;
		for (AssignmentBoard.Row task : AssignmentBoard.activities(Collections.singletonList(row)))
			if (task.getResources().contains(person.getResource()))
				return true;
		return false;
	}

	/**
	 * Tasks: the names indented by their level with an arrow on the summaries, which are in bold; the people changed in
	 * blue; the issues with a warning icon; the tasks of the person selected highlighted
	 */
	private class TaskRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int index, int column) {
			JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, index, column);
			AssignmentBoard.Row row = rows.get(index);
			int c = table.convertColumnIndexToModel(column);
			label.setIcon(null);
			label.setHorizontalAlignment(c == T_EFFORT ? SwingConstants.RIGHT : c == T_LOCK ? SwingConstants.CENTER : SwingConstants.LEFT);
			label.setFont(row.isActivity() ? table.getFont() : table.getFont().deriveFont(Font.BOLD));
			if (c == T_LOCK) {
				int state = lockState(row.isActivity() ? Collections.singletonList(row) : filtered(Collections.singletonList(row)));
				label.setIcon(state == ALL_LOCKED ? lockIcon : state == SOME_LOCKED ? partLockIcon : null);
			}
			if (c == T_SUGGESTION && row.isActivity())
				label.setFont(table.getFont().deriveFont(Font.ITALIC));
			if (c == T_NAME) {
				String arrow = row.isActivity() ? "    " : collapsed.contains(row) ? "▸ " : "▾ "; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				label.setText(arrow + value);
				label.setBorder(BorderFactory.createEmptyBorder(0, row.getLevel() * INDENT + 2, 0, 0));
			}
			if (c == T_ISSUES && value != null && value.toString().length() > 0)
				label.setIcon(warningIcon);
			if (!isSelected) {
				label.setBackground(ofSelectedPerson(row) ? HIGHLIGHT : table.getBackground());
				label.setForeground(c == T_PEOPLE && row.isActivity() && row.isPeopleChanged() ? CHANGED
						: c == T_SUGGESTION ? SUGGESTED : c == T_ISSUES ? WARNING_COLOR : table.getForeground());
			}
			return label;
		}
	}

	private void createPersonTable() {
		personModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
			public int getRowCount() {
				return persons.size();
			}
			public int getColumnCount() {
				return P_HEADERS.length;
			}
			public String getColumnName(int column) {
				return Messages.getString("AssignmentBoard." + P_HEADERS[column]); //$NON-NLS-1$
			}
			public Object getValueAt(int index, int column) {
				AssignmentBoard.Person person = persons.get(index);
				switch (column) {
				case P_NAME: return person.getName();
				case P_ROLE: return person.getRole();
				case P_SKILL: return person.getSkill();
				case P_LEVEL: return level(person);
				case P_FROM: return dateFormat.format(new Date(person.getFrom()));
				case P_TO: return dateFormat.format(new Date(person.getTo() - 1)); // the end of the working time of a day
				case P_TASKS: return String.valueOf(person.getTasks());
				case P_ASSIGNED: return decimal.format(person.getAssignedHours() / UNIT_HOURS[unit]);
				case P_CAPACITY: return decimal.format(person.getCapacityHours() / UNIT_HOURS[unit]);
				case P_LOAD: return load(person);
				default: return null;
				}
			}
		};
		personTable = new JTable(personModel) {
			private static final long serialVersionUID = 1L;
			public String getToolTipText(MouseEvent e) {
				int index = rowAtPoint(e.getPoint());
				if (index < 0)
					return null;
				AssignmentBoard.Person person = persons.get(index);
				List<String> lines = new ArrayList<String>();
				lines.add(person.getName() + (person.isExisting() ? "" : " — " + Messages.getString("AssignmentBoard.Tbd"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				String issue = board.getIssue(person);
				if (issue != null)
					lines.add(issue);
				if (person.getSuggestedHours() > 0)
					lines.add(MessageFormat.format(Messages.getString("AssignmentBoard.SuggestedLoad"), effort(person.getSuggestedHours()), //$NON-NLS-1$
							percent(person.getLoadWithSuggestions())));
				if (!canDoSelected(person))
					lines.add(Messages.getString("AssignmentBoard.CannotDo")); //$NON-NLS-1$
				return html(lines);
			}
		};
		personTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		personTable.setDropMode(DropMode.ON);
		personTable.setTransferHandler(new PersonDropHandler());
		personTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		personTable.getTableHeader().setReorderingAllowed(false);
		for (int i = 0; i < P_WIDTHS.length; i++)
			personTable.getColumnModel().getColumn(i).setPreferredWidth(P_WIDTHS[i]);
		PersonRenderer renderer = new PersonRenderer();
		for (int i = 0; i < P_HEADERS.length; i++)
			personTable.getColumnModel().getColumn(i).setCellRenderer(i == P_LOAD ? new LoadRenderer() : renderer);
		personTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if (!e.getValueIsAdjusting())
					taskTable.repaint(); // the tasks of the person are highlighted
			}
		});
		personTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && personTable.rowAtPoint(e.getPoint()) >= 0)
					assignSelected();
			}
		});
	}

	/** True if a person can do all the tasks selected that would go to it, and at least one (or if none is selected) */
	private boolean canDoSelected(AssignmentBoard.Person person) {
		List<AssignmentBoard.Row> tasks = tasksFor(person);
		if (tasks.isEmpty())
			return selectedTasks.isEmpty();
		for (AssignmentBoard.Row row : tasks)
			if (!AssignmentBoard.canDo(person, row))
				return false;
		return true;
	}

	/** People: dimmed if they cannot do the tasks selected, the existing ones in bold, the numbers right aligned */
	private class PersonRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int index, int column) {
			JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, index, column);
			AssignmentBoard.Person person = persons.get(index);
			int c = table.convertColumnIndexToModel(column);
			label.setHorizontalAlignment(c >= P_TASKS ? SwingConstants.RIGHT : SwingConstants.LEFT);
			label.setFont(c == P_NAME && person.isExisting() ? table.getFont().deriveFont(Font.BOLD) : table.getFont());
			if (!isSelected)
				label.setForeground(canDoSelected(person) ? table.getForeground() : DIMMED);
			return label;
		}
	}

	/**
	 * The load of a person as a bar: blue within the warning threshold, orange over it, red over the limit. The effort
	 * suggested is a lighter part after it, and its load is shown after an arrow
	 */
	private class LoadRenderer extends JComponent implements TableCellRenderer {
		private static final long serialVersionUID = 1L;
		private double load, loadWithSuggestions;
		private int loadStatus;
		private String text;
		private boolean selected, dimmed;
		private Color background, selectionBackground, selectionForeground, foreground;
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int index, int column) {
			AssignmentBoard.Person person = persons.get(index);
			load = person.getLoad();
			loadWithSuggestions = person.getLoadWithSuggestions();
			loadStatus = person.getCapacityHours() <= 0 && person.getAssignedHours() > 0 ? AssignmentBoard.FAILED : board.getStatus(person);
			text = value == null ? "" : value.toString(); //$NON-NLS-1$
			if (person.getSuggestedHours() > 0)
				text += " → " + percent(loadWithSuggestions); //$NON-NLS-1$
			selected = isSelected;
			dimmed = !canDoSelected(person);
			background = table.getBackground();
			foreground = table.getForeground();
			selectionBackground = table.getSelectionBackground();
			selectionForeground = table.getSelectionForeground();
			setFont(table.getFont());
			return this;
		}
		protected void paintComponent(Graphics g) {
			int w = getWidth(), h = getHeight();
			g.setColor(selected ? selectionBackground : background);
			g.fillRect(0, 0, w, h);
			g.setFont(getFont());
			int textWidth = g.getFontMetrics().stringWidth(text);
			int barWidth = w - textWidth - 12;
			if (barWidth > 10) {
				double ratio = ratio(load), ratioWith = ratio(loadWithSuggestions);
				g.setColor(new Color(0xe6, 0xe6, 0xe6));
				g.fillRect(3, 4, barWidth, h - 8);
				g.setColor(SUGGESTED_BAR);
				g.fillRect(3, 4, (int) Math.round(barWidth * ratioWith), h - 8);
				g.setColor(loadStatus == AssignmentBoard.FAILED ? BAR_FAILED : loadStatus == AssignmentBoard.WARNING ? BAR_WARNING : BAR_OK);
				g.fillRect(3, 4, (int) Math.round(barWidth * ratio), h - 8);
			}
			g.setColor(selected ? selectionForeground : loadStatus == AssignmentBoard.FAILED ? FAILED_COLOR
					: loadStatus == AssignmentBoard.WARNING ? WARNING_COLOR : dimmed ? DIMMED : foreground);
			g.drawString(text, w - textWidth - 4, (h + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
		}
	}

	private static double ratio(double load) {
		return Double.isInfinite(load) ? 1 : Math.min(1, load / 100);
	}

	/** A padlock, gray when only some tasks below a summary are locked */
	private static class LockIcon implements Icon {
		private final boolean partial;
		LockIcon(boolean partial) {
			this.partial = partial;
		}
		public int getIconWidth() {
			return 12;
		}
		public int getIconHeight() {
			return 13;
		}
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Color color = partial ? new Color(0xa0, 0xa0, 0xa0) : new Color(0x55, 0x55, 0x55);
			g.setColor(color);
			g.drawArc(x + 3, y + 1, 6, 8, 0, 180); // the shackle
			g.drawLine(x + 3, y + 5, x + 3, y + 6);
			g.drawLine(x + 9, y + 5, x + 9, y + 6);
			g.fillRect(x + 1, y + 6, 11, 7); // the body
			g.setColor(Color.WHITE);
			g.fillRect(x + 6, y + 8, 1, 3); // the keyhole
		}
	}
}
