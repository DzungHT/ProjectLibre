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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.AbstractCellEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import net.sf.nachocalendar.CalendarFactory;
import net.sf.nachocalendar.components.DateField;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.options.EditOption;
import com.projectlibre1.pm.graphic.frames.GraphicManager;
import com.projectlibre1.pm.resource.labor.LaborCatalog;
import com.projectlibre1.pm.resource.labor.SkillCatalog;
import com.projectlibre1.pm.resource.plan.ResourcePlan;
import com.projectlibre1.pm.resource.plan.ResourcePlanOptions;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.strings.Messages;
import com.projectlibre1.util.Alert;
import com.projectlibre1.util.DateTime;

/**
 * Resource Plan for a deadline, see ResourcePlan: the parameters, the effort by role and skill (the WBS and the
 * effort added for PM, QA...), the lines of people and the verdict, calculated again at each change.
 * OK returns the plan, which the caller writes in the project with ResourcePlanWriter.
 */
public final class ResourcePlanDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;
	private static final int MAX_DAYS = 999;
	// columns of the groups
	private static final int G_ROLE = 0, G_SKILL = 1, G_TASKS = 2, G_WBS = 3, G_EXTRA = 4, G_EFFORT = 5, G_CAPACITY = 6, G_PEOPLE = 7, G_LOAD = 8;
	private static final String[] G_HEADERS = { "Role", "Skill", "Tasks", "WbsDays", "ExtraDays", "EffortDays", "CapacityDays", "People", "Load" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
	// columns of the lines, then a total row
	private static final int L_SOURCE = 0, L_ROLE = 1, L_SKILL = 2, L_LEVEL = 3, L_FROM = 4, L_TO = 5, L_ALLOCATION = 6, L_QUANTITY = 7,
			L_HEADCOUNT = 8, L_FTE = 9, L_NLTC = 10, L_RA_DAYS = 11, L_RA_MONTHS = 12, L_OUTPUT = 13, L_COST = 14;
	private static final String[] L_HEADERS = { "LineSource", "LineRole", "LineSkill", "LineLevel", "LineFrom", "LineTo", "LineAllocation", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
			"LineQuantity", "LineHeadcount", "LineFte", "LineNltc", "LineRaDays", "LineRaMonths", "LineOutput", "LineCost" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
	private static final int[] L_WIDTHS = { 70, 60, 60, 110, 85, 85, 40, 60, 65, 45, 45, 60, 60, 80, 95 };
	/** Columns of the lines hidden by the user, see chooseColumns() */
	private static final String HIDDEN_COLUMNS = "resourcePlan.hiddenColumns", DEFAULT_HIDDEN_COLUMNS = "LineCost"; //$NON-NLS-1$ //$NON-NLS-2$

	private static final long DAY = 24L * 60 * 60 * 1000;
	private final ResourcePlanOptions options;
	private final ResourcePlan plan;
	private final DateFormat dateFormat = EditOption.getInstance().getShortDateFormat();
	private final NumberFormat decimal = NumberFormat.getNumberInstance();
	private final NumberFormat money = NumberFormat.getIntegerInstance();

	DateField start, deadline;
	JSpinner buffer, utilization, devLag, testerLag, testTail, baTail, maxPeople;
	JTable groupTable, lineTable;
	AbstractTableModel groupModel, lineModel;
	TableColumn[] lineColumns; // all the columns of the lines, shown or not
	JLabel verdict;
	private boolean updating = false;

	/** @return the plan to write in the project, or null if cancelled */
	public static ResourcePlan doDialog(Project project) {
		ResourcePlanDialog dialog = new ResourcePlanDialog(project);
		return dialog.doModal() ? dialog.plan : null;
	}

	private ResourcePlanDialog(Project project) {
		super(GraphicManager.getFrameInstance(), Messages.getString("ResourcePlan.Title"), true); //$NON-NLS-1$
		this.options = ResourcePlanOptions.load(project);
		this.plan = ResourcePlan.create(project, options);
		decimal.setMaximumFractionDigits(1);
		decimal.setMinimumFractionDigits(0);

		start = CalendarFactory.createDateField();
		deadline = CalendarFactory.createDateField();
		buffer = spinner(0, MAX_DAYS);
		utilization = spinner(1, 100);
		devLag = spinner(0, MAX_DAYS);
		testerLag = spinner(0, MAX_DAYS);
		testTail = spinner(0, MAX_DAYS);
		baTail = spinner(0, MAX_DAYS);
		maxPeople = spinner(1, MAX_DAYS);
		verdict = new JLabel();
		verdict.setVerticalAlignment(SwingConstants.TOP);
		showParameters();

		ChangeListener listener = new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				if (!updating)
					update();
			}
		};
		start.addChangeListener(listener);
		deadline.addChangeListener(listener);
		for (JSpinner spinner : spinners())
			spinner.addChangeListener(listener);
	}

	private static JSpinner spinner(int min, int max) {
		return new JSpinner(new SpinnerNumberModel(min, min, max, 1));
	}

	private JSpinner[] spinners() {
		return new JSpinner[] { buffer, utilization, devLag, testerLag, testTail, baTail, maxPeople };
	}

	/** Shows the options. A date not set is the one of the plan: the start or the end of the project */
	private void showParameters() {
		updating = true;
		try {
			start.setValue(date(options.getStart() == 0 ? plan.getStart() : options.getStart()));
			deadline.setValue(date(options.getDeadline() == 0 ? plan.getEnd() : options.getDeadline()));
			buffer.setValue(options.getBuffer());
			utilization.setValue((int) Math.round(options.getUtilization() * 100));
			devLag.setValue(options.getDevLag());
			testerLag.setValue(options.getTesterLag());
			testTail.setValue(options.getTestTail());
			baTail.setValue(options.getBaTail());
			maxPeople.setValue(options.getMaxPeople());
		} finally {
			updating = false;
		}
	}

	private void readParameters() {
		options.setStart(day(start));
		options.setDeadline(day(deadline));
		options.setBuffer(intValue(buffer));
		options.setUtilization(intValue(utilization) / 100.0);
		options.setDevLag(intValue(devLag));
		options.setTesterLag(intValue(testerLag));
		options.setTestTail(intValue(testTail));
		options.setBaTail(intValue(baTail));
		options.setMaxPeople(intValue(maxPeople));
	}

	/** The day chosen in a date field, as the dates of the project: midnight GMT. 0 if none */
	private static long day(DateField field) {
		Object value = field.getValue();
		if (!(value instanceof Date))
			return 0;
		long day = DateTime.gmt((Date) value);
		return day - Math.floorMod(day, DAY);
	}

	/** Value of a date field showing the day of a date of the project */
	private static Date date(long date) {
		return new Date(DateTime.fromGmt(date - Math.floorMod(date, DAY)));
	}

	private static int intValue(JSpinner spinner) {
		return ((Number) spinner.getValue()).intValue();
	}

	/** Reads the parameters, calculates and shows the plan */
	private void update() {
		stopEditing();
		readParameters();
		plan.calculate();
		groupModel.fireTableDataChanged();
		lineModel.fireTableDataChanged();
		showVerdict();
	}

	/** Calculates once the table has ended the edit that calls it: ending the edit again would call it again */
	private void updateLater() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				update();
			}
		});
	}

	private void stopEditing() {
		if (groupTable != null && groupTable.isEditing())
			groupTable.getCellEditor().stopCellEditing();
		if (lineTable != null && lineTable.isEditing())
			lineTable.getCellEditor().stopCellEditing();
	}

	protected boolean bind(boolean get) {
		if (get)
			return true;
		update();
		if (!plan.isFeasible() && !Alert.okCancel(Messages.getString("ResourcePlan.ApplyAnyway"))) //$NON-NLS-1$
			return false;
		return true;
	}

	public JComponent createContentPanel() {
		createGroupTable();
		createLineTable();
		JButton defaults = new JButton(Messages.getString("ResourcePlan.Defaults")); //$NON-NLS-1$
		defaults.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				options.resetDefaults();
				showParameters();
				update();
			}
		});
		JButton addRole = new JButton(Messages.getString("ResourcePlan.AddRole")); //$NON-NLS-1$
		addRole.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				addRole();
			}
		});
		JButton addLine = new JButton(Messages.getString("ResourcePlan.AddLine")); //$NON-NLS-1$
		addLine.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				addLine();
			}
		});
		JButton defaultDates = new JButton(Messages.getString("ResourcePlan.DefaultDates")); //$NON-NLS-1$
		defaultDates.setToolTipText(Messages.getString("ResourcePlan.DefaultDatesTooltip")); //$NON-NLS-1$
		defaultDates.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				defaultDates();
			}
		});
		JButton removeLine = new JButton(Messages.getString("ResourcePlan.RemoveLine")); //$NON-NLS-1$
		removeLine.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				removeLines();
			}
		});
		final JButton columns = new JButton(Messages.getString("ResourcePlan.Columns")); //$NON-NLS-1$
		columns.setToolTipText(Messages.getString("ResourcePlan.ColumnsTooltip")); //$NON-NLS-1$
		columns.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseColumns(columns);
			}
		});

		FormLayout parameterLayout = new FormLayout("right:pref, 3dlu, 70dlu, 15dlu, right:pref, 3dlu, 40dlu, 15dlu, right:pref, 3dlu, 40dlu", ""); //$NON-NLS-1$ //$NON-NLS-2$
		DefaultFormBuilder parameters = new DefaultFormBuilder(parameterLayout);
		parameters.append(Messages.getString("ResourcePlan.Start"), start); //$NON-NLS-1$
		parameters.append(Messages.getString("ResourcePlan.DevLag"), devLag); //$NON-NLS-1$
		parameters.append(Messages.getString("ResourcePlan.Utilization"), utilization); //$NON-NLS-1$
		parameters.nextLine();
		parameters.append(Messages.getString("ResourcePlan.Deadline"), deadline); //$NON-NLS-1$
		parameters.append(Messages.getString("ResourcePlan.TesterLag"), testerLag); //$NON-NLS-1$
		parameters.append(Messages.getString("ResourcePlan.MaxPeople"), maxPeople); //$NON-NLS-1$
		parameters.nextLine();
		parameters.append(Messages.getString("ResourcePlan.Buffer"), buffer); //$NON-NLS-1$
		parameters.append(Messages.getString("ResourcePlan.TestTail"), testTail); //$NON-NLS-1$
		parameters.append("", defaults); //$NON-NLS-1$
		parameters.nextLine();
		parameters.append(""); //$NON-NLS-1$
		parameters.append(""); //$NON-NLS-1$
		parameters.append(Messages.getString("ResourcePlan.BaTail"), baTail); //$NON-NLS-1$

		JScrollPane groupScroll = new JScrollPane(groupTable);
		groupScroll.setPreferredSize(new Dimension(900, 140));
		JScrollPane lineScroll = new JScrollPane(lineTable);
		lineScroll.setPreferredSize(new Dimension(900, 200));
		JScrollPane verdictScroll = new JScrollPane(verdict);
		verdictScroll.setPreferredSize(new Dimension(900, 110));
		verdictScroll.setBorder(null);

		FormLayout layout = new FormLayout("600dlu:grow", ""); //$NON-NLS-1$ //$NON-NLS-2$
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setDefaultDialogBorder();
		builder.appendSeparator(Messages.getString("ResourcePlan.Parameters")); //$NON-NLS-1$
		builder.append(parameters.getPanel());
		builder.append(help("ResourcePlan.ParametersHelp")); //$NON-NLS-1$
		builder.appendSeparator(Messages.getString("ResourcePlan.Effort")); //$NON-NLS-1$
		builder.append(groupScroll);
		builder.append(buttons(addRole));
		builder.appendSeparator(Messages.getString("ResourcePlan.Lines")); //$NON-NLS-1$
		JPanel lineHeader = new JPanel(new BorderLayout());
		lineHeader.add(columns, BorderLayout.EAST);
		builder.append(lineHeader);
		builder.append(lineScroll);
		builder.append(buttons(addLine, removeLine, defaultDates));
		builder.appendSeparator(Messages.getString("ResourcePlan.Verdict")); //$NON-NLS-1$
		builder.append(verdictScroll);
		showVerdict();
		return builder.getPanel();
	}

	private static JLabel help(String key) {
		return new JLabel("<html><div style='width:600px'>" + Messages.getString(key) + "</div></html>"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static JPanel buttons(JButton... buttons) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		for (JButton button : buttons)
			panel.add(button);
		return panel;
	}

	private void createGroupTable() {
		groupModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
			public int getRowCount() {
				return plan.getGroups().size();
			}
			public int getColumnCount() {
				return G_HEADERS.length;
			}
			public String getColumnName(int column) {
				return Messages.getString("ResourcePlan." + G_HEADERS[column]); //$NON-NLS-1$
			}
			public Object getValueAt(int row, int column) {
				ResourcePlan.Group group = plan.getGroups().get(row);
				switch (column) {
				case G_ROLE: return group.getRole();
				case G_SKILL: return group.getSkill();
				case G_TASKS: return group.getTasks() == 0 ? "" : String.valueOf(group.getTasks()); //$NON-NLS-1$
				case G_WBS: return days(group.getWbsDays());
				case G_EXTRA: return group.getExtraDays() == 0 ? "" : decimal.format(group.getExtraDays()); //$NON-NLS-1$
				case G_EFFORT: return days(group.getEffortDays());
				case G_CAPACITY: return days(group.getCapacityDays());
				case G_PEOPLE: return group.getPeople() == 0 ? "" : decimal.format(group.getPeople()); //$NON-NLS-1$
				case G_LOAD: return percent(group.getLoad());
				default: return null;
				}
			}
			public boolean isCellEditable(int row, int column) {
				return column == G_EXTRA;
			}
			public void setValueAt(Object value, int row, int column) {
				double days = parseNumber(value);
				if (days < 0)
					return;
				plan.setExtraDays(plan.getGroups().get(row), days);
				updateLater();
			}
		};
		groupTable = new PlanTable(groupModel, G_HEADERS);
		NumberRenderer renderer = new NumberRenderer();
		for (int column = G_TASKS; column < G_HEADERS.length; column++)
			groupTable.getColumnModel().getColumn(column).setCellRenderer(renderer);
		groupTable.getColumnModel().getColumn(G_LOAD).setCellRenderer(new NumberRenderer() {
			private static final long serialVersionUID = 1L;
			protected boolean isAlert(int row) {
				return plan.getGroups().get(row).getLoad() > 1 + 1e-6;
			}
		});
	}

	private void createLineTable() {
		lineModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
			public int getRowCount() {
				return plan.getLines().size() + 1; // the total
			}
			public int getColumnCount() {
				return L_HEADERS.length;
			}
			public String getColumnName(int column) {
				return Messages.getString("ResourcePlan." + L_HEADERS[column]); //$NON-NLS-1$
			}
			public Object getValueAt(int row, int column) {
				if (isTotalLine(row))
					return getTotal(column);
				ResourcePlan.Line line = plan.getLines().get(row);
				switch (column) {
				case L_SOURCE: return Messages.getString(line.isExisting() ? "ResourcePlan.Existing" : "ResourcePlan.New"); //$NON-NLS-1$ //$NON-NLS-2$
				case L_ROLE: return line.getRole();
				case L_SKILL: return line.getSkill();
				case L_LEVEL: return line.getLevel() < 0 ? "" : LaborCatalog.levelText(line.getLevel()); //$NON-NLS-1$
				case L_FROM: return dateFormat.format(new Date(line.getFrom()));
				case L_TO: return dateFormat.format(new Date(line.getTo() - 1)); // the end of the working time of a day
				case L_ALLOCATION: return String.valueOf(Math.round(line.getAllocation() * 100));
				case L_QUANTITY: return line.isAuto() ? Messages.getString("ResourcePlan.Auto") : String.valueOf(line.getQuantity()); //$NON-NLS-1$
				case L_HEADCOUNT: return String.valueOf(line.getPeople());
				case L_FTE: return line.isAuto() ? decimal.format(line.getFte()) : ""; //$NON-NLS-1$
				case L_NLTC: return String.valueOf(line.getPoint());
				case L_RA_DAYS: return decimal.format(line.getAllocatedDays());
				case L_RA_MONTHS: return decimal.format(line.getManMonths());
				case L_OUTPUT: return decimal.format(line.getOutputManMonths());
				case L_COST: return line.getCost() == 0 ? "" : money.format(line.getCost()); //$NON-NLS-1$
				default: return null;
				}
			}
			private Object getTotal(int column) {
				double total = 0;
				for (ResourcePlan.Line line : plan.getLines()) {
					switch (column) {
					case L_HEADCOUNT: total += line.getPeople(); break;
					case L_RA_DAYS: total += line.getAllocatedDays(); break;
					case L_RA_MONTHS: total += line.getManMonths(); break;
					case L_OUTPUT: total += line.getOutputManMonths(); break;
					case L_COST: total += line.getCost(); break;
					default: break;
					}
				}
				switch (column) {
				case L_SOURCE: return Messages.getString("ResourcePlan.Total"); //$NON-NLS-1$
				case L_HEADCOUNT: return String.valueOf(Math.round(total));
				case L_RA_DAYS:
				case L_RA_MONTHS:
				case L_OUTPUT: return decimal.format(total);
				case L_COST: return money.format(total);
				default: return ""; //$NON-NLS-1$
				}
			}
			public boolean isCellEditable(int row, int column) {
				if (isTotalLine(row))
					return false;
				if (plan.getLines().get(row).isExisting()) // the role, level and skill of a resource are changed in the resource sheet
					return column == L_FROM || column == L_TO || column == L_ALLOCATION;
				return column >= L_ROLE && column <= L_QUANTITY;
			}
			public void setValueAt(Object value, int row, int column) {
				if (isTotalLine(row))
					return;
				ResourcePlan.Line line = plan.getLines().get(row);
				String text = value == null ? "" : value.toString().trim(); //$NON-NLS-1$
				switch (column) {
				case L_ROLE:
					String role = LaborCatalog.getInstance().findRoleName(text);
					if (role == null)
						return;
					line.setRole(role);
					if (LaborCatalog.getInstance().find(role, line.getLevel()) == null)
						line.setLevel(ResourcePlan.standardLevel(role));
					break;
				case L_SKILL:
					if (text.length() > 0 && SkillCatalog.findSkills(text) == null)
						return;
					line.setSkill(text.length() == 0 ? "" : SkillCatalog.findSkills(text)); //$NON-NLS-1$
					break;
				case L_LEVEL:
					line.setLevel(LaborCatalog.parseLevel(text));
					break;
				case L_FROM:
				case L_TO:
					long day = value instanceof Long ? (Long) value : 0; // see DayEditor
					if (day == 0)
						return;
					if (column == L_FROM)
						line.setPeriod(plan.startOfDay(day), line.getTo());
					else
						line.setPeriod(line.getFrom(), plan.endOfDay(day));
					break;
				case L_ALLOCATION:
					double percent = parseNumber(text);
					if (percent <= 0)
						return;
					line.setAllocation(percent / 100);
					break;
				case L_QUANTITY:
					if (text.length() == 0 || text.equalsIgnoreCase(Messages.getString("ResourcePlan.Auto")) || text.equalsIgnoreCase("auto")) { //$NON-NLS-1$ //$NON-NLS-2$
						line.setAuto(true);
						break;
					}
					double quantity = parseNumber(text);
					if (quantity < 0)
						return;
					line.setAuto(false);
					line.setQuantity((int) Math.round(quantity));
					break;
				default:
					return;
				}
				updateLater();
			}
		};
		lineTable = new PlanTable(lineModel, L_HEADERS) {
			private static final long serialVersionUID = 1L;
			protected boolean isTotalRow(int row) {
				return isTotalLine(row);
			}
		};
		Set<String> roles = new LinkedHashSet<String>(LaborCatalog.getInstance().getRoles());
		JComboBox roleEditor = new JComboBox(roles.toArray());
		roleEditor.setEditable(true);
		lineTable.getColumnModel().getColumn(L_ROLE).setCellEditor(new DefaultCellEditor(roleEditor));
		Set<Object> skills = new LinkedHashSet<Object>();
		skills.add(""); //$NON-NLS-1$
		for (Object skill : SkillCatalog.allSkills())
			skills.add(skill.toString());
		JComboBox skillEditor = new JComboBox(skills.toArray());
		skillEditor.setEditable(true);
		lineTable.getColumnModel().getColumn(L_SKILL).setCellEditor(new DefaultCellEditor(skillEditor));
		JComboBox levelEditor = new JComboBox(LaborCatalog.allLevels());
		levelEditor.setEditable(true);
		lineTable.getColumnModel().getColumn(L_LEVEL).setCellEditor(new DefaultCellEditor(levelEditor));
		JComboBox quantityEditor = new JComboBox(new Object[] { Messages.getString("ResourcePlan.Auto") }); //$NON-NLS-1$
		quantityEditor.setEditable(true);
		lineTable.getColumnModel().getColumn(L_QUANTITY).setCellEditor(new DefaultCellEditor(quantityEditor));
		lineTable.getColumnModel().getColumn(L_FROM).setCellEditor(new DayEditor());
		lineTable.getColumnModel().getColumn(L_TO).setCellEditor(new DayEditor());
		lineTable.setRowHeight(Math.max(lineTable.getRowHeight(), roleEditor.getPreferredSize().height));
		NumberRenderer renderer = new NumberRenderer();
		for (int column = L_ALLOCATION; column < L_HEADERS.length; column++)
			lineTable.getColumnModel().getColumn(column).setCellRenderer(renderer);
		lineColumns = new TableColumn[L_HEADERS.length];
		for (int column = 0; column < L_HEADERS.length; column++) {
			lineColumns[column] = lineTable.getColumnModel().getColumn(column);
			lineColumns[column].setPreferredWidth(L_WIDTHS[column]);
		}
		showColumns(hiddenColumns());
	}

	/** The row after the lines, with the totals */
	private boolean isTotalLine(int row) {
		return row >= plan.getLines().size();
	}

	/** Rows of the lines selected, without the total */
	private int[] selectedLines() {
		int count = 0;
		int[] rows = lineTable.getSelectedRows();
		for (int row : rows)
			if (!isTotalLine(row))
				count++;
		int[] lines = new int[count];
		count = 0;
		for (int row : rows)
			if (!isTotalLine(row))
				lines[count++] = row;
		return lines;
	}

	/** Header keys of the columns of the lines hidden by the user */
	private static Set<String> hiddenColumns() {
		Set<String> hidden = new LinkedHashSet<String>();
		String value = DEFAULT_HIDDEN_COLUMNS;
		try {
			value = Preferences.userNodeForPackage(ResourcePlanDialog.class).get(HIDDEN_COLUMNS, DEFAULT_HIDDEN_COLUMNS);
		} catch (Exception e) { // preferences not available
		}
		for (String key : value.split(",")) //$NON-NLS-1$
			if (key.trim().length() > 0)
				hidden.add(key.trim());
		return hidden;
	}

	/** Shows the columns of the lines that are not hidden, in their order */
	private void showColumns(Set<String> hidden) {
		stopEditing();
		TableColumnModel model = lineTable.getColumnModel();
		while (model.getColumnCount() > 0)
			model.removeColumn(model.getColumn(0));
		for (int column = 0; column < L_HEADERS.length; column++)
			if (!hidden.contains(L_HEADERS[column]))
				model.addColumn(lineColumns[column]);
	}

	/** A menu of the columns of the lines to check the ones to show. Remembered for the next times */
	private void chooseColumns(Component button) {
		final Set<String> hidden = hiddenColumns();
		JPopupMenu menu = new JPopupMenu();
		for (int column = 0; column < L_HEADERS.length; column++) {
			final String key = L_HEADERS[column];
			final JCheckBoxMenuItem item = new JCheckBoxMenuItem(Messages.getString("ResourcePlan." + key), !hidden.contains(key)); //$NON-NLS-1$
			item.setToolTipText("<html><div style='width:300px'>" + Messages.getString("ResourcePlan." + key + "Tooltip") + "</div></html>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			item.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if (item.isSelected())
						hidden.remove(key);
					else
						hidden.add(key);
					StringBuilder value = new StringBuilder();
					for (String h : hidden)
						value.append(value.length() == 0 ? "" : ",").append(h); //$NON-NLS-1$ //$NON-NLS-2$
					try {
						Preferences.userNodeForPackage(ResourcePlanDialog.class).put(HIDDEN_COLUMNS, value.toString());
					} catch (Exception ex) { // preferences not available: shown until the dialog is closed
					}
					showColumns(hidden);
				}
			});
			menu.add(item);
		}
		menu.show(button, 0, button.getHeight());
	}

	/** Adds a group to type the effort of a role which has no task in the WBS: PM, QA... */
	private void addRole() {
		Object role = JOptionPane.showInputDialog(this, Messages.getString("ResourcePlan.AddRolePrompt"), //$NON-NLS-1$
				Messages.getString("ResourcePlan.Title"), JOptionPane.QUESTION_MESSAGE, null, //$NON-NLS-1$
				LaborCatalog.getInstance().getRoles().toArray(), null);
		if (role == null)
			return;
		ResourcePlan.Group group = plan.group(role.toString(), ""); //$NON-NLS-1$
		update();
		int row = plan.getGroups().indexOf(group);
		groupTable.changeSelection(row, G_EXTRA, false, false);
		groupTable.editCellAt(row, G_EXTRA);
	}

	/** Adds an auto line for the selected group, or a copy of the selected line */
	private void addLine() {
		stopEditing();
		ResourcePlan.Line line;
		int[] selectedLines = selectedLines();
		int selectedLine = selectedLines.length == 0 ? -1 : selectedLines[0];
		int selectedGroup = groupTable.getSelectedRow();
		if (selectedLine >= 0) {
			ResourcePlan.Line copy = plan.getLines().get(selectedLine);
			line = new ResourcePlan.Line(copy.getRole(), copy.getLevel(), false);
			line.setSkill(copy.getSkill());
			line.setAllocation(copy.getAllocation());
			if (copy.isCustomDates())
				line.setPeriod(copy.getFrom(), copy.getTo());
			line.setQuantity(1);
		} else {
			ResourcePlan.Group group = plan.getGroups().get(Math.max(0, selectedGroup));
			line = new ResourcePlan.Line(group.getRole(), ResourcePlan.standardLevel(group.getRole()), false);
			line.setSkill(group.getSkill());
			line.setAuto(true);
		}
		plan.getLines().add(line);
		update();
		lineTable.changeSelection(plan.getLines().size() - 1, L_LEVEL, false, false);
	}

	/** The selected lines get back the default period of their role */
	private void defaultDates() {
		stopEditing();
		for (int row : selectedLines())
			plan.getLines().get(row).setCustomDates(false);
		update();
	}

	/**
	 * Picks the day of the period of a line with the calendar of the date fields, so that no date is typed in a
	 * format that depends on the computer. The value is the day as the dates of the project, see day()
	 */
	private class DayEditor extends AbstractCellEditor implements TableCellEditor {
		private static final long serialVersionUID = 1L;
		private final DateField field = CalendarFactory.createDateField();
		private boolean starting = false;

		DayEditor() {
			field.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					if (!starting)
						stopCellEditing(); // a day picked in the calendar
				}
			});
		}
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			ResourcePlan.Line line = plan.getLines().get(row);
			starting = true;
			try {
				field.setValue(date(column == L_FROM ? line.getFrom() : line.getTo() - 1)); // the end of the working time of a day
			} finally {
				starting = false;
			}
			return field;
		}
		public Object getCellEditorValue() {
			return day(field);
		}
	}

	/** Removes the selected lines, except the existing resources */
	private void removeLines() {
		if (lineTable.isEditing())
			lineTable.getCellEditor().cancelCellEditing();
		int[] selected = selectedLines();
		for (int i = selected.length - 1; i >= 0; i--)
			if (!plan.getLines().get(selected[i]).isExisting())
				plan.getLines().remove(selected[i]);
		update();
	}

	private void showVerdict() {
		StringBuilder text = new StringBuilder("<html><div style='width:600px'>"); //$NON-NLS-1$
		text.append(plan.isFeasible() ? "<b><font color='#2e7d32'>" + Messages.getString("ResourcePlan.Feasible") //$NON-NLS-1$ //$NON-NLS-2$
				: "<b><font color='red'>" + Messages.getString("ResourcePlan.NotFeasible")).append("</font></b><br>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		text.append(MessageFormat.format(Messages.getString("ResourcePlan.Period"), //$NON-NLS-1$
				dateFormat.format(new Date(plan.getStart())), dateFormat.format(new Date(plan.getEnd() - 1)))).append("<br>"); //$NON-NLS-1$
		text.append(MessageFormat.format(Messages.getString("ResourcePlan.EarliestEnd"), //$NON-NLS-1$
				dateFormat.format(new Date(plan.getEarliestEnd() - 1)))).append("<br>"); //$NON-NLS-1$
		double output = 0;
		for (ResourcePlan.Line line : plan.getLines())
			output += line.getOutputManMonths();
		text.append(MessageFormat.format(Messages.getString("ResourcePlan.Totals"), String.valueOf(plan.getTotalPeople()), //$NON-NLS-1$
				String.valueOf(plan.getNewPeople()), decimal.format(plan.getTotalManMonths()), decimal.format(output)));
		for (String error : plan.getErrors())
			text.append("<br><font color='red'>&#8226; ").append(error).append("</font>"); //$NON-NLS-1$ //$NON-NLS-2$
		for (String warning : plan.getWarnings())
			text.append("<br><font color='#b26a00'>&#8226; ").append(warning).append("</font>"); //$NON-NLS-1$ //$NON-NLS-2$
		verdict.setText(text.append("</div></html>").toString()); //$NON-NLS-1$
	}

	private String days(double days) {
		return days == 0 ? "" : decimal.format(days); //$NON-NLS-1$
	}

	private static String percent(double load) {
		if (Double.isInfinite(load))
			return "∞"; //$NON-NLS-1$
		return load == 0 ? "" : Math.round(load * 100) + "%"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** A number typed with the format of the user or with a dot, -1 if it is not a number */
	private double parseNumber(Object value) {
		String text = value == null ? "" : value.toString().trim(); //$NON-NLS-1$
		if (text.length() == 0)
			return 0;
		try {
			return Math.max(0, decimal.parse(text).doubleValue());
		} catch (ParseException e) {
			try {
				return Math.max(0, Double.parseDouble(text));
			} catch (NumberFormatException e2) {
				return -1;
			}
		}
	}

	/** Gray background for the cells that cannot be changed, and a tooltip on each header telling what the column is */
	private static class PlanTable extends JTable {
		private static final long serialVersionUID = 1L;
		private static final Color READ_ONLY = new Color(242, 242, 242), TOTAL = new Color(225, 232, 242);
		private final String[] headers;
		PlanTable(AbstractTableModel model, String[] headers) {
			super(model);
			this.headers = headers;
		}
		protected JTableHeader createDefaultTableHeader() {
			return new JTableHeader(columnModel) {
				private static final long serialVersionUID = 1L;
				public String getToolTipText(MouseEvent e) {
					int column = columnAtPoint(e.getPoint());
					if (column < 0)
						return null;
					return "<html><div style='width:300px'>" + Messages.getString("ResourcePlan." + headers[convertColumnIndexToModel(column)] + "Tooltip") + "</div></html>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				}
			};
		}
		/** True for a row of totals, in bold */
		protected boolean isTotalRow(int row) {
			return false;
		}
		public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
			Component c = super.prepareRenderer(renderer, row, column);
			boolean total = isTotalRow(row);
			if (!isCellSelected(row, column))
				c.setBackground(total ? TOTAL : isCellEditable(row, column) ? getBackground() : READ_ONLY);
			c.setFont(total ? getFont().deriveFont(Font.BOLD) : getFont());
			return c;
		}
	}

	/** Right aligned, in red when isAlert() */
	private static class NumberRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		NumberRenderer() {
			setHorizontalAlignment(SwingConstants.RIGHT);
		}
		protected boolean isAlert(int row) {
			return false;
		}
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (!isSelected)
				c.setForeground(isAlert(row) ? Color.RED : table.getForeground());
			return c;
		}
	}
}
