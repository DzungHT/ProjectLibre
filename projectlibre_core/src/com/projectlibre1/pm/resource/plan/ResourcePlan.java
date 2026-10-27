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
package com.projectlibre1.pm.resource.plan;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import com.projectlibre1.datatype.Duration;
import com.projectlibre1.options.CalendarOption;
import com.projectlibre1.pm.availability.Availability;
import com.projectlibre1.pm.availability.AvailabilityTable;
import com.projectlibre1.pm.calendar.WorkCalendar;
import com.projectlibre1.pm.resource.ResourceImpl;
import com.projectlibre1.pm.resource.labor.LaborCatalog;
import com.projectlibre1.pm.resource.labor.SkillCatalog;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.pm.task.Task;
import com.projectlibre1.strings.Messages;

/**
 * Resource Plan for a deadline: how many people of each role, level and skill, and when, to do the effort of the
 * WBS (and the effort added for PM, QA...) before the deadline. See docs/resource-plan-design.md.
 * <p>
 * A <b>group</b> is a role and a required skill (DEV BE, DEV FE, BA...) with its effort: the work of the tasks of
 * the activities done by the role, at point 1.0. A <b>line</b> is a number of people of the same role, skill,
 * level, period and allocation. Its quantity is typed, or <b>auto</b>: what the group still needs. The lines are
 * read from the resources of the project (existing ones, and the TBD ones of a previous plan, see
 * ResourcePlanWriter), so that the resources are the only place where the plan is kept.
 * <p>
 * The plan is a capacity check, not a schedule: the capacity of a person in a period is
 * point × allocation × working hours × utilization. The dates of the tasks are only calculated by ProjectLibre
 * from the links, once the people are assigned.
 */
public class ResourcePlan {
	public static final String BA = "BA", DEV = "DEV", TESTER = "TESTER"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	/** Role doing the tasks of each activity of the ULNL. The developers also need the required skill of the task */
	private static final String[][] ACTIVITY_ROLES = { { "GP", BA }, { "PT", DEV }, { "KT", TESTER } }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	private static final double MILLIS_PER_HOUR = 60 * 60 * 1000;
	private static final double EPSILON = 1e-6;
	private static final int MAX_FUNCTIONS_SHOWN = 5;

	public static class Group {
		private final String role, skill;
		private int tasks = 0;
		private double wbsHours = 0;
		private double extraDays = 0;
		private double capacityHours = 0;
		private double people = 0;
		private double existingPeople = 0; // the part of the people who are existing resources
		/** How the capacity meets the effort, see checkBalance() */
		public static final int OK = 0, WARNING = 1, FAILED = 2;
		private int status = OK;

		Group(String role, String skill) {
			this.role = role;
			this.skill = skill;
		}
		public String getRole() { return role; }
		/** Required skill, "" for none */
		public String getSkill() { return skill; }
		public String getKey() { return key(role, skill); }
		/** Leaf tasks of the WBS, not cancelled */
		public int getTasks() { return tasks; }
		/** Work of the tasks, man-hours at point 1.0: the estimate of the ULNL */
		public double getWbsHours() { return wbsHours; }
		/** Effort added to the WBS (PM, QA...), man-days */
		public double getExtraDays() { return extraDays; }
		public double getExtraHours() { return extraDays * NormalTask.HOURS_PER_MAN_DAY; }
		/** WBS + added, man-hours */
		public double getEffortHours() { return wbsHours + getExtraHours(); }
		/** Capacity of the people of the lines, man-hours at point 1.0 */
		public double getCapacityHours() { return capacityHours; }
		/** People of the lines, with a part of the people shared with other skills (Fullstack...) */
		public double getPeople() { return people; }
		/** People of the lines who are existing resources */
		public double getExistingPeople() { return existingPeople; }
		/** OK, WARNING or FAILED: the gap between capacity and effort against the thresholds of the options */
		public int getStatus() { return status; }
		/** People of the lines to add (TBD) */
		public double getNewPeople() { return people - existingPeople; }
		/** Effort / capacity, 0 if there is neither */
		public double getLoad() {
			if (capacityHours <= EPSILON)
				return getEffortHours() > EPSILON ? Double.POSITIVE_INFINITY : 0;
			return getEffortHours() / capacityHours;
		}
	}

	public static class Line {
		private String role;
		private String skill = ""; //$NON-NLS-1$
		private int level = -1;
		private long from = 0, to = 0; // working time, set by calculate() when the dates are not custom
		private boolean customDates = false;
		private boolean edited = false; // period or allocation changed in the plan
		private boolean openFrom = false, openTo = false; // a resource available since always or for ever: the start or end of the plan
		private double allocation = 1;
		private int quantity = 0;
		private boolean auto = false;
		private final boolean existing;
		private final List<ResourceImpl> resources = new ArrayList<ResourceImpl>();
		// results of calculate()
		private double point, price, hours, perPerson, fte;
		private int people;

		/** @param existing true for the resources already in the team, which are not changed by the plan */
		public Line(String role, int level, boolean existing) {
			this.role = role;
			this.level = level;
			this.existing = existing;
		}
		public String getRole() { return role; }
		public void setRole(String role) { this.role = role; }
		public String getSkill() { return skill; }
		public void setSkill(String skill) { this.skill = skill == null ? "" : skill.trim(); } //$NON-NLS-1$
		/** Level of the labor category, -1 if not set */
		public int getLevel() { return level; }
		public void setLevel(int level) { this.level = level; }
		/** Start of the period, working time */
		public long getFrom() { return from; }
		/** End of the period, working time */
		public long getTo() { return to; }
		/** True if the period starts with the plan: the resource is available since always */
		public boolean isOpenFrom() { return openFrom; }
		/** True if the period ends with the plan: the resource is available for ever */
		public boolean isOpenTo() { return openTo; }
		/** Sets the period. It is no longer the default period of the role */
		public void setPeriod(long from, long to) {
			openFrom &= from == this.from;
			openTo &= to == this.to;
			this.from = from;
			this.to = to;
			customDates = true;
			edited = true;
		}
		/** True if the dates are typed, false if they follow the default period of the role */
		public boolean isCustomDates() { return customDates; }
		public void setCustomDates(boolean customDates) {
			this.customDates = customDates;
			edited = true;
		}
		/** Part of the time spent on the project, 1 for full time */
		public double getAllocation() { return allocation; }
		public void setAllocation(double allocation) {
			this.allocation = Math.max(0, allocation);
			edited = true;
		}
		/** True if the period or the allocation were changed: the existing resources of the line get them */
		public boolean isEdited() { return edited; }
		public int getQuantity() { return quantity; }
		public void setQuantity(int quantity) { this.quantity = Math.max(0, quantity); }
		/** True if the quantity is what the group still needs */
		public boolean isAuto() { return auto; }
		public void setAuto(boolean auto) { this.auto = auto; }
		/** True for resources already in the team: kept as they are, only counted */
		public boolean isExisting() { return existing; }
		/** Resources read for this line, empty for a new line */
		public List<ResourceImpl> getResources() { return resources; }

		/** Point of the labor category, 1 if it is not in the catalog */
		public double getPoint() { return point; }
		/** Price of a man-month, 0 if unknown */
		public double getPrice() { return price; }
		/** People: the quantity, or the one calculated for an auto line */
		public int getPeople() { return people; }
		/** People needed, not rounded, for an auto line. The quantity otherwise */
		public double getFte() { return fte; }
		/** Working days of the period inside the plan */
		public double getDays() { return hours / hoursPerDay(); }
		/** Resource allocation (RA): man-hours taken by the people in the period inside the plan, people × % × working hours */
		public double getAllocatedHours() { return people * allocation * hours; }
		/** Resource allocation in man-months: 1 MM = 22 MD of 8 hours */
		public double getManMonths() { return getAllocatedHours() / NormalTask.HOURS_PER_MAN_DAY / NormalTask.MAN_DAYS_PER_MAN_MONTH; }
		/** Expected production (SLSX), man-hours of ULNL effort the people can do: RA × point × utilization */
		public double getOutputHours() { return people * perPerson; }
		/** Expected production of one person of the line: point × % × working hours × utilization */
		public double getOutputHoursPerPerson() { return perPerson; }
		public double getCost() { return getManMonths() * price; }
	}

	private final Project project;
	private final ResourcePlanOptions options;
	private final WorkCalendar calendar;
	private final List<Group> groups = new ArrayList<Group>();
	private final List<Line> lines = new ArrayList<Line>();
	/** Effort at point 1.0 of each role, hours, for each parent of activities (a function of the ULNL) */
	private final Map<Task, Map<String, Double>> functions = new LinkedHashMap<Task, Map<String, Double>>();
	// results of calculate()
	private long start, end;
	private final List<String> errors = new ArrayList<String>();
	private final List<String> warnings = new ArrayList<String>();
	private long earliestEnd;

	private ResourcePlan(Project project, ResourcePlanOptions options) {
		this.project = project;
		this.options = options;
		this.calendar = project.getEffectiveWorkCalendar();
	}

	/** The plan of a project: the effort of its WBS and the lines of its resources, calculated */
	public static ResourcePlan create(Project project, ResourcePlanOptions options) {
		ResourcePlan plan = new ResourcePlan(project, options);
		plan.readTasks();
		plan.readExtraEffort();
		plan.calculateDates();
		plan.readResources();
		plan.calculate();
		return plan;
	}

	/**
	 * The plan of a version, see snapshot(): its options and its TBD lines. The existing resources are read from the
	 * project, with the dates and % the version changed. The TBD resources of the project are given to the TBD lines of
	 * the same role, skill and level, so that the ones with assignments are kept when the version is applied
	 */
	public static ResourcePlan create(Project project, String snapshot) {
		Map<String, String> values = parse(snapshot);
		Properties properties = new Properties();
		for (Map.Entry<String, String> entry : values.entrySet())
			if (entry.getKey().startsWith(OPTION))
				properties.setProperty(entry.getKey().substring(OPTION.length()), entry.getValue());
		ResourcePlan plan = new ResourcePlan(project, ResourcePlanOptions.fromProperties(properties));
		plan.readTasks();
		plan.readExtraEffort();
		plan.calculateDates();
		plan.readResources();
		plan.restoreLines(values);
		plan.calculate();
		return plan;
	}

	private static final String OPTION = "option.", LINE = "line."; //$NON-NLS-1$ //$NON-NLS-2$

	/**
	 * Text of the plan to keep it as a version: its options and its lines, one "key=value" per line, sorted, so that
	 * two snapshots of the same plan are equal
	 */
	public String snapshot() {
		Map<String, String> values = new TreeMap<String, String>();
		Properties properties = options.toProperties();
		for (String name : properties.stringPropertyNames())
			values.put(OPTION + name, properties.getProperty(name));
		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			String prefix = LINE + String.format("%03d", i) + "."; //$NON-NLS-1$ //$NON-NLS-2$
			values.put(prefix + "role", line.role); //$NON-NLS-1$
			values.put(prefix + "skill", line.skill); //$NON-NLS-1$
			values.put(prefix + "level", String.valueOf(line.level)); //$NON-NLS-1$
			values.put(prefix + "existing", String.valueOf(line.existing)); //$NON-NLS-1$
			values.put(prefix + "allocation", String.valueOf(line.allocation)); //$NON-NLS-1$
			values.put(prefix + "quantity", String.valueOf(line.quantity)); //$NON-NLS-1$
			values.put(prefix + "auto", String.valueOf(line.auto)); //$NON-NLS-1$
			values.put(prefix + "customDates", String.valueOf(line.customDates)); //$NON-NLS-1$
			values.put(prefix + "edited", String.valueOf(line.edited)); //$NON-NLS-1$
			values.put(prefix + "openFrom", String.valueOf(line.openFrom)); //$NON-NLS-1$
			values.put(prefix + "openTo", String.valueOf(line.openTo)); //$NON-NLS-1$
			if (line.customDates) {
				values.put(prefix + "from", String.valueOf(line.from)); //$NON-NLS-1$
				values.put(prefix + "to", String.valueOf(line.to)); //$NON-NLS-1$
			}
			if (line.existing) {
				StringBuilder ids = new StringBuilder();
				for (ResourceImpl resource : line.resources)
					ids.append(ids.length() == 0 ? "" : ",").append(resource.getUniqueId()); //$NON-NLS-1$ //$NON-NLS-2$
				values.put(prefix + "resources", ids.toString()); //$NON-NLS-1$
			}
		}
		StringBuilder text = new StringBuilder();
		for (Map.Entry<String, String> entry : values.entrySet())
			text.append(entry.getKey()).append('=').append(entry.getValue().replace('\n', ' ')).append('\n');
		return text.toString();
	}

	private static Map<String, String> parse(String snapshot) {
		Map<String, String> values = new TreeMap<String, String>();
		if (snapshot != null)
			for (String line : snapshot.split("\n")) { //$NON-NLS-1$
				int equal = line.indexOf('=');
				if (equal > 0)
					values.put(line.substring(0, equal), line.substring(equal + 1).trim());
			}
		return values;
	}

	/** Replaces the lines read from the resources with the ones of a version, see create(Project, String) */
	private void restoreLines(Map<String, String> values) {
		List<Line> existing = new ArrayList<Line>();
		List<ResourceImpl> tbd = new ArrayList<ResourceImpl>();
		for (Line line : lines)
			if (line.existing)
				existing.add(line);
			else
				tbd.addAll(line.resources);
		lines.clear();
		lines.addAll(existing);
		Map<String, Map<String, String>> saved = new TreeMap<String, Map<String, String>>();
		for (Map.Entry<String, String> entry : values.entrySet()) {
			if (!entry.getKey().startsWith(LINE))
				continue;
			String rest = entry.getKey().substring(LINE.length());
			int dot = rest.indexOf('.');
			if (dot < 0)
				continue;
			Map<String, String> fields = saved.get(rest.substring(0, dot));
			if (fields == null)
				saved.put(rest.substring(0, dot), fields = new HashMap<String, String>());
			fields.put(rest.substring(dot + 1), entry.getValue());
		}
		for (Map<String, String> fields : saved.values()) {
			Line line;
			if (Boolean.parseBoolean(fields.get("existing"))) { //$NON-NLS-1$
				line = findExisting(existing, fields.get("resources")); //$NON-NLS-1$
				if (line == null)
					continue; // its resources are no longer in the project
			} else {
				line = new Line(fields.get("role"), intValue(fields.get("level"), -1), false); //$NON-NLS-1$ //$NON-NLS-2$
				line.skill = fields.get("skill") == null ? "" : fields.get("skill"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				line.quantity = intValue(fields.get("quantity"), 0); //$NON-NLS-1$
				line.auto = Boolean.parseBoolean(fields.get("auto")); //$NON-NLS-1$
				lines.add(line);
			}
			line.allocation = doubleValue(fields.get("allocation"), line.allocation); //$NON-NLS-1$
			line.customDates = Boolean.parseBoolean(fields.get("customDates")); //$NON-NLS-1$
			line.edited = Boolean.parseBoolean(fields.get("edited")); //$NON-NLS-1$
			line.openFrom = Boolean.parseBoolean(fields.get("openFrom")); //$NON-NLS-1$
			line.openTo = Boolean.parseBoolean(fields.get("openTo")); //$NON-NLS-1$
			if (line.customDates) {
				line.from = longValue(fields.get("from"), line.from); //$NON-NLS-1$
				line.to = longValue(fields.get("to"), line.to); //$NON-NLS-1$
			}
		}
		// the TBD resources already in the project go to the lines like them
		for (ResourceImpl resource : tbd) {
			int level = LaborCatalog.parseLevel(resource.getLaborLevel());
			String skill = resource.getSkills() == null ? "" : resource.getSkills(); //$NON-NLS-1$
			for (Line line : lines)
				if (!line.existing && line.role.equals(resource.getLaborRole()) && line.skill.equals(skill) && line.level == level) {
					line.resources.add(resource);
					break;
				}
		}
	}

	/** The existing line of exactly these resources (unique ids separated by commas), null if there is none */
	private static Line findExisting(List<Line> existing, String ids) {
		if (ids == null)
			return null;
		Set<String> wanted = new HashSet<String>(Arrays.asList(ids.split(","))); //$NON-NLS-1$
		for (Line line : existing) {
			Set<String> have = new HashSet<String>();
			for (ResourceImpl resource : line.resources)
				have.add(String.valueOf(resource.getUniqueId()));
			if (have.equals(wanted))
				return line;
		}
		return null;
	}

	private static int intValue(String text, int value) {
		try {
			return text == null ? value : Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return value;
		}
	}

	private static long longValue(String text, long value) {
		try {
			return text == null ? value : Long.parseLong(text.trim());
		} catch (NumberFormatException e) {
			return value;
		}
	}

	private static double doubleValue(String text, double value) {
		try {
			return text == null ? value : Double.parseDouble(text.trim());
		} catch (NumberFormatException e) {
			return value;
		}
	}

	public static String key(String role, String skill) {
		return skill == null || skill.length() == 0 ? role : role + " " + skill; //$NON-NLS-1$
	}

	/** Key of a line in ResourcePlanOptions.getAutoLines(): role, skill and level */
	public static String lineKey(Line line) {
		return key(line.role, line.skill) + " " + line.level; //$NON-NLS-1$
	}

	/** Codes of the activities of the ULNL done by a role (GP, PT, KT), in the order of the work */
	public static String[] activities() {
		String[] activities = new String[ACTIVITY_ROLES.length];
		for (int i = 0; i < activities.length; i++)
			activities[i] = ACTIVITY_ROLES[i][0];
		return activities;
	}

	/** Role of the tasks of an activity, null if it is not an activity of the ULNL */
	public static String role(String activity) {
		if (activity != null)
			for (String[] activityRole : ACTIVITY_ROLES)
				if (activityRole[0].equalsIgnoreCase(activity))
					return roleName(activityRole[1]);
		return null;
	}

	/** Name of a role in the labor catalog, the name itself if it is not in it */
	private static String roleName(String role) {
		String name = LaborCatalog.getInstance().findRoleName(role);
		return name == null ? role : name;
	}

	private void readTasks() {
		for (Iterator i = project.getTaskOutlineIterator(); i.hasNext();) {
			Object impl = i.next();
			if (!(impl instanceof NormalTask))
				continue;
			NormalTask task = (NormalTask) impl;
			String role = role(task.getActivity());
			if (role == null || task.isWbsParent() || task.isCancelled())
				continue;
			String skill = DEV.equalsIgnoreCase(role) && task.getLane() != null ? task.getLane() : ""; //$NON-NLS-1$
			double hours = task.getWorkHours();
			Group group = group(role, skill);
			group.tasks++;
			group.wbsHours += hours;
			Task parent = task.getWbsParentTask();
			if (parent == null)
				continue;
			Map<String, Double> effort = functions.get(parent);
			if (effort == null)
				functions.put(parent, effort = new HashMap<String, Double>());
			Double total = effort.get(role);
			effort.put(role, (total == null ? 0 : total) + hours);
		}
	}

	private void readExtraEffort() {
		for (Map.Entry<String, Double> entry : options.getExtraDays().entrySet()) {
			String[] roleAndSkill = entry.getKey().split(" ", 2); //$NON-NLS-1$
			group(roleAndSkill[0], roleAndSkill.length > 1 ? roleAndSkill[1] : "").extraDays = entry.getValue(); //$NON-NLS-1$
		}
	}

	/** Group of a role and skill, added if there is none */
	public Group group(String role, String skill) {
		Group group = findGroup(role, skill);
		if (group == null) {
			group = new Group(role, skill == null ? "" : skill); //$NON-NLS-1$
			groups.add(group);
			sortGroups();
		}
		return group;
	}

	/** Sets the effort added to the WBS for a group, man-days. Kept in the options */
	public void setExtraDays(Group group, double days) {
		group.extraDays = days;
		options.setExtraDays(group.getKey(), days);
	}

	private Group findGroup(String role, String skill) {
		for (Group group : groups)
			if (group.role.equalsIgnoreCase(role) && group.skill.equalsIgnoreCase(skill == null ? "" : skill)) //$NON-NLS-1$
				return group;
		return null;
	}

	/** In the order of the roles of the catalog, then by skill */
	private void sortGroups() {
		final List<String> roles = LaborCatalog.getInstance().getRoles();
		Collections.sort(groups, new Comparator<Group>() {
			public int compare(Group a, Group b) {
				return compareRoles(roles, a.role, a.skill, b.role, b.skill);
			}
		});
	}

	/** By role in the order of the catalog (roles not in it at the end), then by skill */
	private static int compareRoles(List<String> roles, String roleA, String skillA, String roleB, String skillB) {
		int ra = roles.indexOf(roleA), rb = roles.indexOf(roleB);
		if (ra != rb)
			return (ra < 0 ? Integer.MAX_VALUE : ra) < (rb < 0 ? Integer.MAX_VALUE : rb) ? -1 : 1;
		int c = roleA.compareToIgnoreCase(roleB);
		return c != 0 ? c : skillA.compareToIgnoreCase(skillB);
	}

	/**
	 * The default order of the lines: the existing resources first, then by role (in the order of the catalog), skill,
	 * level from the highest, and start. The user can change the order, it is kept in the versions
	 */
	public void sortLines() {
		final List<String> roles = LaborCatalog.getInstance().getRoles();
		Collections.sort(lines, new Comparator<Line>() {
			public int compare(Line a, Line b) {
				if (a.existing != b.existing)
					return a.existing ? -1 : 1;
				int c = compareRoles(roles, a.role, a.skill, b.role, b.skill);
				if (c != 0)
					return c;
				if (a.level != b.level)
					return a.level > b.level ? -1 : 1;
				return a.from < b.from ? -1 : a.from > b.from ? 1 : 0;
			}
		});
	}

	/** One line per role, skill, level, period, allocation and kind (existing or TBD) of the labor resources with a role */
	private void readResources() {
		for (Object object : project.getResourcePool().getResourceList()) {
			if (!(object instanceof ResourceImpl))
				continue;
			ResourceImpl resource = (ResourceImpl) object;
			if (!resource.isLabor() || resource.getLaborRole() == null)
				continue;
			int level = LaborCatalog.parseLevel(resource.getLaborLevel());
			String skill = resource.getSkills() == null ? "" : resource.getSkills(); //$NON-NLS-1$
			double point = point(resource.getLaborRole(), level);
			long[] period = new long[2];
			double units = availablePeriod(resource, period);
			double allocation = Math.round(units / point * 100) / 100.0;
			long from = period[0] == 0 ? start : period[0];
			long to = period[1] == 0 ? end : period[1];
			boolean existing = !resource.isGeneric();
			Line line = null;
			for (Line l : lines)
				if (l.existing == existing && l.role.equals(resource.getLaborRole()) && l.skill.equals(skill) && l.level == level
						&& l.from == from && l.to == to && l.allocation == allocation) {
					line = l;
					break;
				}
			if (line == null) {
				line = new Line(resource.getLaborRole(), level, existing);
				line.skill = skill;
				line.allocation = allocation;
				line.from = from;
				line.to = to;
				line.openFrom = period[0] == 0;
				line.openTo = period[1] == 0;
				long[] window = window(line.role);
				line.customDates = existing || from != window[0] || to != window[1];
				line.auto = !line.customDates && !existing && options.getAutoLines().contains(lineKey(line));
				lines.add(line);
			}
			line.quantity++;
			line.resources.add(resource);
		}
	}

	/**
	 * Period of the availability of a resource: the first one with Max Units over 0%, until the next one at 0%.
	 * @param period from and to, 0 when open
	 * @return the Max Units of the period, 0 if the resource is never available
	 */
	static double availablePeriod(ResourceImpl resource, long[] period) {
		AvailabilityTable table = resource.getAvailabilityTable();
		period[0] = period[1] = 0;
		if (table == null)
			return resource.getMaximumUnits();
		List list = table.getList();
		for (int i = 0; i < list.size(); i++) {
			Availability availability = (Availability) list.get(i);
			if (availability.getMaximumUnits() <= 0)
				continue;
			period[0] = availability.isFirst() ? 0 : availability.getStart();
			for (int j = i + 1; j < list.size(); j++)
				if (((Availability) list.get(j)).getMaximumUnits() <= 0) {
					period[1] = ((Availability) list.get(j)).getStart();
					break;
				}
			return availability.getMaximumUnits();
		}
		return 0;
	}

	/**
	 * An auto line at the standard level of the role for each group with effort and no line able to do it. Done at
	 * each calculation, so that the effort of a group always has people: a line not wanted is given a quantity of 0
	 */
	private void addDefaultLines() {
		for (Group group : groups) {
			if (group.getEffortHours() <= EPSILON)
				continue;
			boolean covered = false;
			for (Line line : lines)
				covered |= covers(line, group);
			if (covered)
				continue;
			Line line = new Line(group.role, standardLevel(group.role), false);
			line.skill = group.skill;
			line.auto = true;
			lines.add(line);
		}
	}

	/** True if the people of a line can do the tasks of a group: same role, and the skill if the group needs one */
	private static boolean covers(Line line, Group group) {
		if (!line.role.equalsIgnoreCase(group.role))
			return false;
		return line.skill.length() == 0 || SkillCatalog.getInstance().canDo(line.skill, group.skill);
	}

	/** Level of the role whose point is the nearest to 1.0: the effort of the ULNL is for it. -1 if the role has none */
	public static int standardLevel(String role) {
		int best = -1;
		double distance = Double.MAX_VALUE;
		for (Integer level : LaborCatalog.getInstance().getLevels(role)) {
			double d = Math.abs(LaborCatalog.getInstance().find(role, level).getPoint() - 1);
			if (d < distance) {
				distance = d;
				best = level;
			}
		}
		return best;
	}

	/** Point of a role and level, 1 if it is not in the catalog */
	public static double point(String role, int level) {
		LaborCatalog.Category category = level < 0 ? null : LaborCatalog.getInstance().find(role, level);
		return category == null || category.getPoint() <= 0 ? 1 : category.getPoint();
	}

	private static double price(String role, int level) {
		LaborCatalog.Category category = level < 0 ? null : LaborCatalog.getInstance().find(role, level);
		return category == null ? 0 : category.getPrice();
	}

	/** Start and end of the plan, working time: from the start to the end of the deadline, less the buffer */
	private void calculateDates() {
		start = calendar.adjustInsideCalendar(options.getStart() == 0 ? project.getStart() : options.getStart(), false);
		long deadline = options.getDeadline() == 0 ? project.getEnd() : endOfDay(options.getDeadline());
		end = addDays(deadline, -options.getBuffer());
	}

	/** First working time of a day */
	public long startOfDay(long day) {
		return calendar.adjustInsideCalendar(day, false);
	}

	/** Last working time of a day, or of the last working day before it */
	public long endOfDay(long day) {
		return calendar.adjustInsideCalendar(day + 24L * 60 * 60 * 1000, true);
	}

	private long addDays(long date, int days) {
		if (days == 0)
			return date;
		return calendar.add(date, (long) (days * hoursPerDay() * MILLIS_PER_HOUR), days < 0);
	}

	private static double hoursPerDay() {
		return CalendarOption.getInstance().getHoursPerDay();
	}

	/** Working hours between two dates, 0 if the second is not after the first */
	private double workingHours(long from, long to) {
		return to <= from ? 0 : Duration.millis(calendar.compare(to, from, false)) / MILLIS_PER_HOUR;
	}

	/** Default period of a role: the BAs come first, then the developers, then the testers; the others are there all along */
	public long[] window(String role) {
		long from = start, to = end;
		if (BA.equalsIgnoreCase(role))
			to = addDays(end, -options.getBaTail());
		else if (DEV.equalsIgnoreCase(role)) {
			from = addDays(start, options.getDevLag());
			to = addDays(end, -options.getTestTail());
		} else if (TESTER.equalsIgnoreCase(role))
			from = addDays(start, options.getTesterLag());
		return new long[] { from, to };
	}

	/** Calculates the people of the auto lines, the capacity of the groups and the issues */
	public void calculate() {
		calculateDates();
		errors.clear();
		warnings.clear();
		for (Line line : lines)
			if (!covered(line))
				group(line.role, line.skill); // a group without effort shows that the line is not needed
		addDefaultLines();
		for (Group group : groups) {
			group.capacityHours = 0;
			group.people = 0;
			group.existingPeople = 0;
		}
		for (Line line : lines) {
			if (!line.customDates) {
				long[] window = window(line.role);
				line.from = window[0];
				line.to = window[1];
			} else {
				if (line.openFrom)
					line.from = start;
				if (line.openTo)
					line.to = end;
			}
			line.point = point(line.role, line.level);
			line.price = price(line.role, line.level);
			line.hours = workingHours(Math.max(line.from, start), Math.min(line.to, end));
			line.perPerson = line.point * line.allocation * line.hours * options.getUtilization();
			line.people = line.auto ? 0 : line.quantity;
			line.fte = line.people;
			if (line.to <= line.from)
				errors.add(MessageFormat.format(Messages.getString("ResourcePlan.EmptyPeriod"), lineName(line))); //$NON-NLS-1$
		}
		// the people of the typed lines: lines of a single group first, then the ones shared by several groups
		for (Line line : lines) {
			Group group = exactGroup(line);
			if (!line.auto && group != null) {
				group.capacityHours += line.people * line.perPerson;
				group.people += line.people;
				if (line.existing)
					group.existingPeople += line.people;
			}
		}
		for (Line line : lines)
			if (!line.auto && exactGroup(line) == null)
				share(line);
		// then the auto lines take what is missing
		for (Group group : groups) {
			Line auto = null;
			for (Line line : lines)
				if (line.auto && exactGroup(line) == group) {
					if (auto == null)
						auto = line;
					else
						warnings.add(MessageFormat.format(Messages.getString("ResourcePlan.SeveralAuto"), group.getKey())); //$NON-NLS-1$
				}
			double missing = group.getEffortHours() - group.capacityHours;
			if (auto != null && missing > EPSILON) {
				if (auto.perPerson <= EPSILON)
					errors.add(MessageFormat.format(Messages.getString("ResourcePlan.NoCapacity"), lineName(auto))); //$NON-NLS-1$
				else {
					auto.fte = missing / auto.perPerson;
					// the fewest people giving a load not over 100% + the shortage accepted without a warning
					double needed = group.getEffortHours() / (1 + options.getShortageWarning() / 100.0) - group.capacityHours;
					auto.people = needed <= EPSILON ? 0 : (int) Math.ceil(needed / auto.perPerson - EPSILON);
					group.capacityHours += auto.people * auto.perPerson;
					group.people += auto.people;
				}
			}
			checkBalance(group);
			if (group.people > options.getMaxPeople() + EPSILON)
				errors.add(MessageFormat.format(Messages.getString("ResourcePlan.TooManyPeople"), group.getKey(), //$NON-NLS-1$
						format(group.people), String.valueOf(options.getMaxPeople())));
		}
		for (Line line : lines)
			if (line.auto && exactGroup(line) == null)
				warnings.add(MessageFormat.format(Messages.getString("ResourcePlan.AutoShared"), lineName(line))); //$NON-NLS-1$
		calculateEarliestEnd();
	}

	/**
	 * Compares the load of a group (effort / capacity) to the thresholds of the options: a load of 117% is a shortage of
	 * 17%, a load of 80% a surplus of 20%. Within the warnings the gap is accepted, over a limit the plan fails. A surplus of a single person is only a warning: there
	 * cannot be less than one person, but the % workload can be lowered
	 */
	private void checkBalance(Group group) {
		group.status = Group.OK;
		double effort = group.getEffortHours(), capacity = group.capacityHours;
		if (effort <= EPSILON) {
			if (capacity > EPSILON) {
				group.status = Group.WARNING;
				warnings.add(MessageFormat.format(Messages.getString("ResourcePlan.NoEffort"), group.getKey())); //$NON-NLS-1$
			}
			return;
		}
		double load = capacity <= EPSILON ? Double.POSITIVE_INFINITY : effort / capacity * 100;
		double gap = 100 - load; // negative: shortage
		String percent = Double.isInfinite(gap) ? "221e" : String.valueOf(Math.round(Math.abs(gap))); //$NON-NLS-1$
		if (gap < 0) {
			if (-gap > options.getShortageLimit() + EPSILON) {
				group.status = Group.FAILED;
				errors.add(MessageFormat.format(Messages.getString("ResourcePlan.Shortage"), group.getKey(), percent, //$NON-NLS-1$
						String.valueOf(options.getShortageLimit())));
			} else if (-gap > options.getShortageWarning() + EPSILON) {
				group.status = Group.WARNING;
				warnings.add(MessageFormat.format(Messages.getString("ResourcePlan.ShortageWarning"), group.getKey(), percent, //$NON-NLS-1$
						String.valueOf(options.getShortageWarning())));
			}
		} else if (gap > options.getSurplusLimit() + EPSILON) {
			if (group.people <= 1 + EPSILON) {
				group.status = Group.WARNING;
				warnings.add(MessageFormat.format(Messages.getString("ResourcePlan.SurplusOnePerson"), group.getKey(), percent)); //$NON-NLS-1$
			} else {
				group.status = Group.FAILED;
				errors.add(MessageFormat.format(Messages.getString("ResourcePlan.Surplus"), group.getKey(), percent, //$NON-NLS-1$
						String.valueOf(options.getSurplusLimit())));
			}
		} else if (gap > options.getSurplusWarning() + EPSILON) {
			group.status = Group.WARNING;
			warnings.add(MessageFormat.format(Messages.getString("ResourcePlan.SurplusWarning"), group.getKey(), percent, //$NON-NLS-1$
					String.valueOf(options.getSurplusWarning())));
		}
	}

	/** Group of the role and skill of a line, null if the line can do the tasks of several groups (or none) */
	private Group exactGroup(Line line) {
		Group group = findGroup(line.role, line.skill);
		if (group == null)
			return null;
		for (Group other : groups)
			if (other != group && covers(line, other) && line.skill.length() == 0)
				return null; // a developer without skill can do any development
		return group;
	}

	private boolean covered(Line line) {
		for (Group group : groups)
			if (covers(line, group))
				return true;
		return false;
	}

	/** Shares the capacity of a line between the groups it can do, in proportion of what they miss */
	private void share(Line line) {
		List<Group> covered = new ArrayList<Group>();
		double missing = 0;
		for (Group group : groups)
			if (covers(line, group)) {
				covered.add(group);
				missing += Math.max(0, group.getEffortHours() - group.capacityHours);
			}
		if (covered.isEmpty())
			return;
		double capacity = line.people * line.perPerson;
		for (Group group : covered) {
			double part = missing <= EPSILON ? 1.0 / covered.size() : Math.max(0, group.getEffortHours() - group.capacityHours) / missing;
			group.capacityHours += capacity * part;
			group.people += line.people * part;
			if (line.existing)
				group.existingPeople += line.people * part;
		}
	}

	/**
	 * Lower bound of the end: a task is done by a single person, so each function takes at least the effort of its
	 * activities (GP, then PT, then KT) done one after the other by the best level of each role in the plan
	 */
	private void calculateEarliestEnd() {
		Map<String, Double> bestPoints = new HashMap<String, Double>();
		for (Line line : lines)
			if (line.people > 0) {
				Double best = bestPoints.get(line.role);
				bestPoints.put(line.role, best == null ? line.point : Math.max(best, line.point));
			}
		earliestEnd = start;
		List<Object[]> late = new ArrayList<Object[]>();
		for (Map.Entry<Task, Map<String, Double>> function : functions.entrySet()) {
			double hours = 0;
			for (Map.Entry<String, Double> effort : function.getValue().entrySet()) {
				Double best = bestPoints.get(effort.getKey());
				hours += effort.getValue() / (best == null ? point(effort.getKey(), standardLevel(effort.getKey())) : best);
			}
			long functionEnd = calendar.add(start, (long) (hours * MILLIS_PER_HOUR), false);
			earliestEnd = Math.max(earliestEnd, functionEnd);
			if (functionEnd > end)
				late.add(new Object[] { function.getKey(), hours });
		}
		if (late.isEmpty())
			return;
		Collections.sort(late, new Comparator<Object[]>() {
			public int compare(Object[] a, Object[] b) {
				return Double.compare((Double) b[1], (Double) a[1]);
			}
		});
		StringBuilder names = new StringBuilder();
		for (int i = 0; i < Math.min(MAX_FUNCTIONS_SHOWN, late.size()); i++)
			names.append(i == 0 ? "" : ", ").append(((Task) late.get(i)[0]).getName()) //$NON-NLS-1$ //$NON-NLS-2$
					.append(" (").append(format((Double) late.get(i)[1] / hoursPerDay())).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
		errors.add(MessageFormat.format(Messages.getString("ResourcePlan.FunctionTooLong"), String.valueOf(late.size()), names)); //$NON-NLS-1$
	}

	private static String lineName(Line line) {
		String level = line.level < 0 ? "" : " " + LaborCatalog.levelAbbreviation(line.level); //$NON-NLS-1$ //$NON-NLS-2$
		return key(line.role, line.skill) + level;
	}

	static String format(double value) {
		return String.valueOf(Math.round(value * 10) / 10.0);
	}

	public List<Group> getGroups() { return groups; }
	public List<Line> getLines() { return lines; }
	public ResourcePlanOptions getOptions() { return options; }
	public Project getProject() { return project; }
	/** Start of the plan, working time */
	public long getStart() { return start; }
	/** End of the plan: the deadline less the buffer, working time */
	public long getEnd() { return end; }
	/** Lower bound of the end, see calculateEarliestEnd() */
	public long getEarliestEnd() { return earliestEnd; }
	/** What makes the deadline not reachable */
	public List<String> getErrors() { return errors; }
	public List<String> getWarnings() { return warnings; }
	public boolean isFeasible() { return errors.isEmpty(); }

	public int getTotalPeople() {
		int people = 0;
		for (Line line : lines)
			people += line.people;
		return people;
	}
	public int getNewPeople() {
		int people = 0;
		for (Line line : lines)
			if (!line.existing)
				people += line.people;
		return people;
	}
	public double getTotalManMonths() {
		double mm = 0;
		for (Line line : lines)
			mm += line.getManMonths();
		return mm;
	}
	public double getTotalCost() {
		double cost = 0;
		for (Line line : lines)
			cost += line.getCost();
		return cost;
	}
}
