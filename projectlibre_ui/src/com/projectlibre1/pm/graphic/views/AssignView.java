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
package com.projectlibre1.pm.graphic.views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import com.projectlibre1.dialog.AutoLinkDialog;
import com.projectlibre1.dialog.WrappedText;
import com.projectlibre1.menu.MenuActionConstants;
import com.projectlibre1.options.EditOption;
import com.projectlibre1.pm.assignment.board.AssignmentBoard;
import com.projectlibre1.pm.assignment.board.GanttAssigner;
import com.projectlibre1.pm.dependency.autolink.FinishSummary;
import com.projectlibre1.pm.graphic.frames.DocumentFrame;
import com.projectlibre1.pm.graphic.model.cache.NodeModelCache;
import com.projectlibre1.pm.graphic.spreadsheet.SpreadSheet;
import com.projectlibre1.pm.graphic.spreadsheet.selection.event.SelectionNodeEvent;
import com.projectlibre1.pm.graphic.spreadsheet.selection.event.SelectionNodeListener;
import com.projectlibre1.pm.resource.Resource;
import com.projectlibre1.pm.scheduling.ScheduleEvent;
import com.projectlibre1.pm.scheduling.ScheduleEventListener;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.strings.Messages;
import com.projectlibre1.undo.UndoController;
import com.projectlibre1.workspace.WorkspaceSetting;

/**
 * Sub-window of the Gantt to give the tasks selected in it to a person, with the load of the people as in the
 * Assignment board. Each change is written at once and the tasks of each person are linked again by Auto Link, so
 * that the Gantt shows when a module is done. See docs/assignment-design.md section 9.
 */
public class AssignView extends JPanel implements BaseView, SelectionNodeListener, ScheduleEventListener {
	private static final long serialVersionUID = 1L;
	private static final int P_NAME = 0, P_ROLE = 1, P_SKILL = 2, P_LEVEL = 3, P_LOAD = 4, P_ASSIGNED = 5, P_TASKS = 6, P_SELECTED = 7, P_START = 8, P_FINISH = 9;
	private static final String[] P_HEADERS = { "AssignmentBoard.Person", "AssignmentBoard.Role", "AssignmentBoard.Skill", "AssignmentBoard.Level", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"AssignmentBoard.Load", "AssignView.AssignedMd", "AssignmentBoard.TaskCount", "AssignView.InSelection", "AssignView.Start", "AssignView.Finish" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	private static final int[] P_WIDTHS = { 150, 60, 50, 45, 110, 75, 45, 90, 80, 80 };
	private static final Color BAR_OK = new Color(0x4a, 0x90, 0xd9), BAR_WARNING = new Color(0xf0, 0xa0, 0x30), BAR_FAILED = new Color(0xd9, 0x40, 0x40);
	private static final Color WARNING_COLOR = new Color(0xb2, 0x6a, 0x00), FAILED_COLOR = new Color(0xc0, 0x00, 0x00);
	private static final Color HIGHLIGHT = new Color(0xff, 0xf2, 0xc4), DIMMED = new Color(0xa0, 0xa0, 0xa0);
	private static final String PREF_RELINK = "assignViewRelink"; //$NON-NLS-1$
	private static final int CONTROLS_WIDTH = 230; // pixels of the column of the buttons, the check box wraps
	private static final int REFRESH_DELAY = 400; // milliseconds after the last change of the schedule

	private final DocumentFrame documentFrame;
	private final Project project;
	private final Preferences preferences = Preferences.userNodeForPackage(AssignView.class);
	private final DateFormat dateFormat = EditOption.getInstance().getShortDateFormat();
	private final NumberFormat decimal = NumberFormat.getNumberInstance();
	private final List<AssignmentBoard.Person> persons = new ArrayList<AssignmentBoard.Person>();
	/** First start and last finish of the tasks of each person, in the project */
	private final Map<Resource, long[]> dates = new IdentityHashMap<Resource, long[]>();
	/** Hours of the tasks selected of each person */
	private final Map<Resource, Double> selectedHours = new IdentityHashMap<Resource, Double>();
	private AssignmentBoard board;
	private GanttAssigner assigner;
	private List<?> selection = new ArrayList<Object>();
	private String note = ""; //$NON-NLS-1$
	private boolean dirty = true;
	private boolean applying = false;

	private final JTextArea info = new JTextArea();
	private final JComboBox roleFilter = new JComboBox();
	private final JCheckBox relink;
	private AbstractTableModel personModel;
	private JTable personTable;
	private final Timer refreshTimer;

	public AssignView(DocumentFrame documentFrame) {
		super(new BorderLayout(6, 6));
		this.documentFrame = documentFrame;
		this.project = documentFrame.getProject();
		decimal.setMaximumFractionDigits(1);
		relink = WrappedText.wrap(new JCheckBox(Messages.getString("AssignView.Relink"), preferences.getBoolean(PREF_RELINK, true)), CONTROLS_WIDTH); //$NON-NLS-1$
		relink.setToolTipText(WrappedText.text(Messages.getString("AssignView.RelinkTooltip"), 300)); //$NON-NLS-1$
		relink.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				preferences.putBoolean(PREF_RELINK, relink.isSelected());
			}
		});
		refreshTimer = new Timer(REFRESH_DELAY, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (isShowing())
					refresh();
				else
					dirty = true;
			}
		});
		refreshTimer.setRepeats(false);
		setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		info.setEditable(false);
		info.setLineWrap(true);
		info.setWrapStyleWord(true);
		info.setOpaque(false);
		info.setFont(new JLabel().getFont());
		add(info, BorderLayout.NORTH);
		add(createControls(), BorderLayout.WEST);
		createPersonTable();
		add(new JScrollPane(personTable), BorderLayout.CENTER);
		roleFilter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showPersons();
			}
		});
		project.addScheduleListener(this);
	}

	private JComponent createControls() {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTHWEST;
		c.insets = new Insets(0, 0, 4, 0);
		JPanel filter = new JPanel(new BorderLayout(4, 0));
		filter.add(new JLabel(Messages.getString("AssignmentBoard.Role")), BorderLayout.WEST); //$NON-NLS-1$
		filter.add(roleFilter, BorderLayout.CENTER);
		panel.add(filter, c);
		panel.add(button("AssignView.Assign", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				assignSelected();
			}
		}), c);
		panel.add(button("AssignView.Unassign", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				unassign();
			}
		}), c);
		panel.add(relink, c);
		panel.add(button("AssignView.Board", new ActionListener() { //$NON-NLS-1$
			public void actionPerformed(ActionEvent e) {
				Action action = documentFrame.getGraphicManager().getMenuManager().getActionFromId(MenuActionConstants.ACTION_ASSIGNMENT_BOARD);
				if (action != null)
					action.actionPerformed(e);
			}
		}), c);
		c.weighty = 1;
		panel.add(new JPanel(), c); // the rest of the height
		return panel;
	}

	private static JButton button(String key, ActionListener listener) {
		JButton button = new JButton(Messages.getString(key));
		button.setToolTipText(WrappedText.text(Messages.getString(key + "Tooltip"), 300)); //$NON-NLS-1$
		button.addActionListener(listener);
		return button;
	}

	// ---------------------------------------------------------------- data

	/** Reads the people and their tasks from the project again */
	private void refresh() {
		dirty = false;
		board = AssignmentBoard.create(project);
		dates.clear();
		for (AssignmentBoard.Row row : board.getActivities())
			for (Resource resource : row.getResources()) {
				long[] range = dates.get(resource);
				if (range == null)
					dates.put(resource, range = new long[] { Long.MAX_VALUE, 0 });
				range[0] = Math.min(range[0], row.getTask().getStart());
				range[1] = Math.max(range[1], row.getTask().getEnd());
			}
		Object role = roleFilter.getSelectedItem();
		Set<String> roles = new LinkedHashSet<String>();
		for (AssignmentBoard.Person person : board.getPersons())
			roles.add(person.getRole());
		roleFilter.removeAllItems();
		roleFilter.addItem(Messages.getString("AssignmentBoard.All")); //$NON-NLS-1$
		for (String r : roles)
			roleFilter.addItem(r);
		if (role != null && roles.contains(role))
			roleFilter.setSelectedItem(role);
		readSelection();
	}

	private void readSelection() {
		List<?> impls = documentFrame.getSelectedImpls(true);
		selection = impls == null ? new ArrayList<Object>() : impls;
		assigner = new GanttAssigner(board, selection);
		selectedHours.clear();
		for (AssignmentBoard.Row row : assigner.getSelected())
			for (Resource resource : row.getResources()) {
				Double hours = selectedHours.get(resource);
				selectedHours.put(resource, (hours == null ? 0 : hours) + row.getHours() / row.getResources().size());
			}
		showPersons();
		showInfo();
	}

	private void showPersons() {
		if (board == null)
			return;
		AssignmentBoard.Person selected = selectedPerson();
		persons.clear();
		for (AssignmentBoard.Person person : board.getPersons())
			if (roleFilter.getSelectedIndex() <= 0 || person.getRole().equals(roleFilter.getSelectedItem()))
				persons.add(person);
		personModel.fireTableDataChanged();
		int index = persons.indexOf(selected);
		if (index >= 0)
			personTable.setRowSelectionInterval(index, index);
	}

	/** What is selected in the Gantt: the number of tasks, their effort, their dates and their people */
	private void showInfo() {
		StringBuilder text = new StringBuilder();
		List<AssignmentBoard.Row> rows = assigner.getSelected();
		if (board.getPersons().isEmpty())
			text.append(Messages.getString("AssignView.NoPeople")); //$NON-NLS-1$
		else if (rows.isEmpty())
			text.append(Messages.getString(selection.isEmpty() ? "AssignView.Help" : "AssignView.NoActivity")); //$NON-NLS-1$ //$NON-NLS-2$
		else {
			double hours = 0;
			int unassigned = 0;
			long start = Long.MAX_VALUE, finish = 0;
			Set<Resource> people = new LinkedHashSet<Resource>();
			for (AssignmentBoard.Row row : rows) {
				hours += row.getHours();
				if (row.getResources().isEmpty())
					unassigned++;
				people.addAll(row.getResources());
				start = Math.min(start, row.getTask().getStart());
				finish = Math.max(finish, row.getTask().getEnd());
			}
			text.append(MessageFormat.format(Messages.getString("AssignView.Selection"), String.valueOf(rows.size()), //$NON-NLS-1$
					decimal.format(hours / NormalTask.HOURS_PER_MAN_DAY), String.valueOf(unassigned), dateFormat.format(new Date(start)),
					dateFormat.format(new Date(finish - 1)), people.isEmpty() ? "-" : AssignmentBoard.names(people))); //$NON-NLS-1$
		}
		if (note.length() > 0)
			text.append('\n').append(note);
		info.setText(text.toString());
		revalidate();
	}

	private AssignmentBoard.Person selectedPerson() {
		int index = personTable == null ? -1 : personTable.getSelectedRow();
		return index < 0 || index >= persons.size() ? null : persons.get(index);
	}

	// ---------------------------------------------------------------- actions

	/** Gives the tasks selected to the person selected, after asking if some are not of its role or skill */
	private void assignSelected() {
		AssignmentBoard.Person person = selectedPerson();
		if (assigner == null || assigner.getSelected().isEmpty()) {
			message(Messages.getString("AssignView.ChooseTasks")); //$NON-NLS-1$
			return;
		}
		if (person == null) {
			message(Messages.getString("AssignView.ChoosePerson")); //$NON-NLS-1$
			return;
		}
		List<AssignmentBoard.Row> tasks = assigner.tasksFor(person);
		if (tasks.isEmpty()) {
			message(MessageFormat.format(Messages.getString("AssignmentBoard.NoTaskFits"), person.getName())); //$NON-NLS-1$
			return;
		}
		int wrong = 0;
		for (AssignmentBoard.Row row : tasks)
			if (!AssignmentBoard.canDo(person, row))
				wrong++;
		if (wrong > 0 && JOptionPane.showConfirmDialog(this, MessageFormat.format(Messages.getString("AssignmentBoard.ConfirmMismatch"), //$NON-NLS-1$
				String.valueOf(wrong), person.getName()), Messages.getString("AssignView.Title"), JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) //$NON-NLS-1$
			return;
		int others = assigner.getSelected().size() - tasks.size();
		apply(tasks, person, others);
	}

	private void unassign() {
		if (assigner == null || assigner.getSelected().isEmpty()) {
			message(Messages.getString("AssignView.ChooseTasks")); //$NON-NLS-1$
			return;
		}
		apply(assigner.getSelected(), null, 0);
	}

	/**
	 * Writes the people of tasks, links again the tasks of the people, and tells what changed
	 * @param person null to remove the people of the tasks
	 * @param others tasks selected kept by their people, not of the role or skill of the person
	 */
	private void apply(List<AssignmentBoard.Row> tasks, AssignmentBoard.Person person, int others) {
		Resource resource = person == null ? null : person.getResource();
		if (!documentFrame.getGraphicManager().isDocumentWritable())
			return;
		documentFrame.finishAnyOperations();
		GanttAssigner.Result result;
		applying = true;
		documentFrame.showWaitCursor(true);
		try {
			result = assigner.apply(tasks, resource, relink.isSelected() ? AutoLinkDialog.savedSequenceOptions() : null, this);
		} catch (Exception e) {
			e.printStackTrace();
			message(e.getMessage());
			return;
		} finally {
			documentFrame.showWaitCursor(false);
			applying = false;
		}
		String count = String.valueOf(result.getAssigned());
		StringBuilder text = new StringBuilder(person == null ? MessageFormat.format(Messages.getString("AssignView.Unassigned"), count) //$NON-NLS-1$
				: MessageFormat.format(Messages.getString("AssignView.Assigned"), count, person.getName())); //$NON-NLS-1$
		if (others > 0)
			text.append(MessageFormat.format(Messages.getString("AssignmentBoard.OthersKept"), String.valueOf(others), person.getName())); //$NON-NLS-1$
		if (result.getKept() > 0)
			text.append(MessageFormat.format(Messages.getString("AssignmentBoard.LockedKept"), String.valueOf(result.getKept()))); //$NON-NLS-1$
		text.append(". "); //$NON-NLS-1$
		if (result.getCircular() > 0)
			text.append(MessageFormat.format(Messages.getString("AssignView.Circular"), String.valueOf(result.getCircular()))); //$NON-NLS-1$
		else
			text.append(Messages.getString(result.isLinked() ? "AssignView.Linked" : "AssignView.NotLinked")); //$NON-NLS-1$ //$NON-NLS-2$
		text.append(' ').append(MessageFormat.format(Messages.getString("AssignView.FinishChange"), //$NON-NLS-1$
				dateFormat.format(new Date(result.getFinishBefore())), dateFormat.format(new Date(result.getFinishAfter()))));
		text.append(' ').append(FinishSummary.deadlineText(project, result.getFinishAfter()));
		note = text.toString();
		refreshTimer.stop();
		refresh();
	}

	private void message(String text) {
		JOptionPane.showMessageDialog(this, WrappedText.text(text, 400), Messages.getString("AssignView.Title"), JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$
	}

	// ---------------------------------------------------------------- events

	public void selectionChanged(SelectionNodeEvent e) {
		if (board == null || dirty)
			refresh();
		else
			readSelection();
	}

	public void scheduleChanged(ScheduleEvent evt) {
		if (!applying)
			refreshTimer.restart(); // after an undo, an edit in the Gantt, Auto Link...
	}

	public void onActivate(boolean activate) {
		if (activate)
			refresh();
	}

	// ---------------------------------------------------------------- the people

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
				return Messages.getString(P_HEADERS[column]);
			}
			public Object getValueAt(int index, int column) {
				AssignmentBoard.Person person = persons.get(index);
				long[] range = dates.get(person.getResource());
				Double hours = selectedHours.get(person.getResource());
				switch (column) {
				case P_NAME: return person.getName();
				case P_ROLE: return person.getRole();
				case P_SKILL: return person.getSkill();
				case P_LEVEL: return person.getLevelCode();
				case P_LOAD: return percent(person.getLoad());
				case P_ASSIGNED: return decimal.format(person.getAssignedHours() / NormalTask.HOURS_PER_MAN_DAY);
				case P_TASKS: return String.valueOf(person.getTasks());
				case P_START: return range == null ? "" : dateFormat.format(new Date(range[0])); //$NON-NLS-1$
				case P_FINISH: return range == null ? "" : dateFormat.format(new Date(range[1] - 1)); //$NON-NLS-1$ the end of the working time of a day
				case P_SELECTED: return hours == null ? "" : decimal.format(hours / NormalTask.HOURS_PER_MAN_DAY); //$NON-NLS-1$
				default: return null;
				}
			}
		};
		personTable = new JTable(personModel) {
			private static final long serialVersionUID = 1L;
			public String getToolTipText(MouseEvent e) {
				int index = rowAtPoint(e.getPoint());
				if (index < 0 || board == null)
					return null;
				AssignmentBoard.Person person = persons.get(index);
				StringBuilder text = new StringBuilder(person.getName());
				if (!person.isExisting())
					text.append(" — ").append(Messages.getString("AssignmentBoard.Tbd")); //$NON-NLS-1$ //$NON-NLS-2$
				String issue = board.getIssue(person);
				if (issue != null)
					text.append("<br>").append(issue); //$NON-NLS-1$
				if (dimmed(person))
					text.append("<br>").append(Messages.getString("AssignmentBoard.CannotDo")); //$NON-NLS-1$ //$NON-NLS-2$
				return "<html>" + text + "</html>"; //$NON-NLS-1$ //$NON-NLS-2$
			}
		};
		personTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		personTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		personTable.getTableHeader().setReorderingAllowed(false);
		PersonRenderer renderer = new PersonRenderer();
		for (int i = 0; i < P_HEADERS.length; i++) {
			personTable.getColumnModel().getColumn(i).setPreferredWidth(Math.max(P_WIDTHS[i], headerWidth(i)));
			personTable.getColumnModel().getColumn(i).setCellRenderer(i == P_LOAD ? new LoadRenderer() : renderer);
		}
		personTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && personTable.rowAtPoint(e.getPoint()) >= 0)
					assignSelected();
			}
		});
	}

	/** Width of the title of a column, so that it is never cut */
	private static int headerWidth(int column) {
		return new JLabel(Messages.getString(P_HEADERS[column])).getPreferredSize().width + 16;
	}

	/** True if tasks are selected and the person cannot do the ones that would go to it */
	private boolean dimmed(AssignmentBoard.Person person) {
		return assigner != null && !assigner.getSelected().isEmpty() && !assigner.canDo(person);
	}

	private static String percent(double load) {
		return Double.isInfinite(load) ? "∞ %" : Math.round(load) + "%"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** People: dimmed if they cannot do the tasks selected, highlighted if they do some of them, existing ones in bold */
	private class PersonRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int index, int column) {
			JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, index, column);
			AssignmentBoard.Person person = persons.get(index);
			int c = table.convertColumnIndexToModel(column);
			label.setHorizontalAlignment(c == P_ASSIGNED || c == P_TASKS || c == P_SELECTED ? SwingConstants.RIGHT : SwingConstants.LEFT);
			label.setFont(c == P_NAME && person.isExisting() ? table.getFont().deriveFont(Font.BOLD) : table.getFont());
			if (!isSelected) {
				label.setForeground(dimmed(person) ? DIMMED : table.getForeground());
				label.setBackground(selectedHours.containsKey(person.getResource()) ? HIGHLIGHT : table.getBackground());
			}
			return label;
		}
	}

	/** The load of a person as a bar: blue within the warning threshold, orange over it, red over the limit */
	private class LoadRenderer extends JComponent implements TableCellRenderer {
		private static final long serialVersionUID = 1L;
		private double load;
		private int loadStatus;
		private String text;
		private boolean selected, dim;
		private Color background, foreground;
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int index, int column) {
			AssignmentBoard.Person person = persons.get(index);
			load = person.getLoad();
			loadStatus = person.getCapacityHours() <= 0 && person.getAssignedHours() > 0 ? AssignmentBoard.FAILED : board.getStatus(person);
			text = value == null ? "" : value.toString(); //$NON-NLS-1$
			selected = isSelected;
			dim = dimmed(person);
			background = isSelected ? table.getSelectionBackground() : selectedHours.containsKey(person.getResource()) ? HIGHLIGHT : table.getBackground();
			foreground = isSelected ? table.getSelectionForeground() : table.getForeground();
			setFont(table.getFont());
			return this;
		}
		protected void paintComponent(Graphics g) {
			int w = getWidth(), h = getHeight();
			g.setColor(background);
			g.fillRect(0, 0, w, h);
			g.setFont(getFont());
			int textWidth = g.getFontMetrics().stringWidth(text);
			int barWidth = w - textWidth - 12;
			if (barWidth > 10) {
				g.setColor(new Color(0xe6, 0xe6, 0xe6));
				g.fillRect(3, 4, barWidth, h - 8);
				g.setColor(loadStatus == AssignmentBoard.FAILED ? BAR_FAILED : loadStatus == AssignmentBoard.WARNING ? BAR_WARNING : BAR_OK);
				g.fillRect(3, 4, (int) Math.round(barWidth * (Double.isInfinite(load) ? 1 : Math.min(1, load / 100))), h - 8);
			}
			g.setColor(selected ? foreground : loadStatus == AssignmentBoard.FAILED ? FAILED_COLOR
					: loadStatus == AssignmentBoard.WARNING ? WARNING_COLOR : dim ? DIMMED : foreground);
			g.drawString(text, w - textWidth - 4, (h + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
		}
	}

	// ---------------------------------------------------------------- BaseView

	public void cleanUp() {
		refreshTimer.stop();
		project.removeScheduleListener(this);
	}
	public UndoController getUndoController() {
		return null; // the changes are undone in the Gantt
	}
	public void zoomIn() {
	}
	public void zoomOut() {
	}
	public void scrollToTask() {
	}
	public boolean canZoomIn() {
		return false;
	}
	public boolean canZoomOut() {
		return false;
	}
	public boolean canScrollToTask() {
		return false;
	}
	public int getScale() {
		return 0;
	}
	public SpreadSheet getSpreadSheet() {
		return null;
	}
	public boolean hasNormalMinWidth() {
		return true;
	}
	public String getViewName() {
		return MenuActionConstants.ACTION_ASSIGN_VIEW;
	}
	public boolean showsTasks() {
		return false;
	}
	public boolean showsResources() {
		return false;
	}
	public boolean isPrintable() {
		return false;
	}
	public NodeModelCache getCache() {
		return null;
	}
	public void restoreWorkspace(WorkspaceSetting setting, int context) {
	}
	public WorkspaceSetting createWorkspace(int context) {
		return null;
	}
}
