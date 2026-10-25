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
package com.projectlibre1.pm.assignment.board;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projectlibre1.pm.assignment.Assignment;
import com.projectlibre1.pm.resource.Resource;
import com.projectlibre1.pm.resource.ResourceImpl;
import com.projectlibre1.pm.resource.labor.SkillCatalog;
import com.projectlibre1.pm.resource.plan.ResourcePlan;
import com.projectlibre1.pm.resource.plan.ResourcePlanOptions;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.pm.task.Task;
import com.projectlibre1.strings.Messages;

/**
 * Assignment board: who does each task of the activities of the ULNL, see docs/assignment-design.md. Only the
 * people are chosen here, not the dates: they are calculated by ProjectLibre from the links (step 3, Re-link).
 * <p>
 * A <b>row</b> is a summary task of the WBS, or a task of an activity (GP, PT, KT) to assign. A <b>person</b> is a
 * labor resource with a role, existing or TBD, with the capacity of its line in the Resource Plan, so that the
 * load of a person is calculated as the load of a group there and compared to the same thresholds.
 * <p>
 * The changes are kept in the board until AssignmentBoardWriter writes them in the project.
 */
public class AssignmentBoard {
	public static final int OK = 0, WARNING = 1, FAILED = 2;
	private static final double EPSILON = 1e-6;

	/** A summary task of the WBS, or a task of an activity of the ULNL */
	public static class Row {
		private final NormalTask task;
		private final Row parent;
		private final int level;
		private final List<Row> children = new ArrayList<Row>();
		/** Role and required skill of an activity, null role for a summary */
		private final String role, skill;
		/** The labor resources of the task in the project, and the ones chosen in the board */
		private final List<Resource> original = new ArrayList<Resource>();
		private final List<Resource> resources = new ArrayList<Resource>();

		Row(NormalTask task, Row parent, String role, String skill) {
			this.task = task;
			this.parent = parent;
			this.level = parent == null ? 0 : parent.level + 1;
			this.role = role;
			this.skill = skill;
			if (parent != null)
				parent.children.add(this);
		}
		public NormalTask getTask() { return task; }
		public Row getParent() { return parent; }
		/** Depth in the WBS of the board: 0 for the top rows */
		public int getLevel() { return level; }
		public List<Row> getChildren() { return children; }
		/** True for a task to assign, false for a summary */
		public boolean isActivity() { return role != null; }
		public String getRole() { return role; }
		/** Required skill, "" for none */
		public String getSkill() { return skill; }
		/** People chosen for the task, usually one */
		public List<Resource> getResources() { return resources; }
		/** True if the people chosen are not the ones of the project */
		public boolean isChanged() { return !new LinkedHashSet<Resource>(original).equals(new LinkedHashSet<Resource>(resources)); }
		/** Effort at point 1.0, hours: the work of the task, or of the tasks below a summary */
		public double getHours() {
			if (isActivity())
				return task.getWorkHours();
			double hours = 0;
			for (Row child : children)
				hours += child.getHours();
			return hours;
		}
	}

	/** A labor resource with a role, and its load */
	public static class Person {
		private final ResourceImpl resource;
		private final ResourcePlan.Line line;
		private double assignedHours;
		private int tasks;

		Person(ResourceImpl resource, ResourcePlan.Line line) {
			this.resource = resource;
			this.line = line;
		}
		public ResourceImpl getResource() { return resource; }
		public String getName() { return resource.getName(); }
		public String getRole() { return line.getRole(); }
		/** Skills, "" for none: a developer without skill does any development, as in the Resource Plan */
		public String getSkill() { return line.getSkill(); }
		public int getLevel() { return line.getLevel(); }
		public boolean isExisting() { return line.isExisting(); }
		/** Period of the person in the plan */
		public long getFrom() { return line.getFrom(); }
		public long getTo() { return line.getTo(); }
		/** Max Units of the period: point × allocation */
		public double getUnits() { return line.getPoint() * line.getAllocation(); }
		/** Effort the person can do, hours at point 1.0: point × % × working hours × utilization */
		public double getCapacityHours() { return line.getOutputHoursPerPerson(); }
		/** Effort of the tasks chosen for the person, hours at point 1.0. A task of several people is shared */
		public double getAssignedHours() { return assignedHours; }
		public int getTasks() { return tasks; }
		/** Assigned / capacity, %. Infinite if the person has work and no capacity */
		public double getLoad() {
			if (getCapacityHours() <= EPSILON)
				return assignedHours <= EPSILON ? 0 : Double.POSITIVE_INFINITY;
			return assignedHours / getCapacityHours() * 100;
		}
	}

	private final Project project;
	private final ResourcePlanOptions options;
	private final List<Row> roots = new ArrayList<Row>();
	private final List<Row> activities = new ArrayList<Row>();
	private final List<Person> persons = new ArrayList<Person>();
	private final Map<Resource, Person> personOf = new IdentityHashMap<Resource, Person>();

	private AssignmentBoard(Project project) {
		this.project = project;
		this.options = ResourcePlanOptions.load(project);
	}

	/** The board of a project: its tasks to assign with their people, and the people with a role */
	public static AssignmentBoard create(Project project) {
		AssignmentBoard board = new AssignmentBoard(project);
		board.readPersons();
		board.readTasks();
		board.calculate();
		return board;
	}

	/** The people with a role, in the order of the Resource Plan: existing first, then by role, skill and level */
	private void readPersons() {
		ResourcePlan plan = ResourcePlan.create(project, options);
		plan.sortLines();
		for (ResourcePlan.Line line : plan.getLines())
			for (ResourceImpl resource : line.getResources()) {
				Person person = new Person(resource, line);
				persons.add(person);
				personOf.put(resource, person);
			}
	}

	/**
	 * The tasks of the activities, not cancelled nor completed, in the order of the WBS, with the summaries above
	 * them. The summaries without such tasks are not shown
	 */
	private void readTasks() {
		Map<Task, Row> summaries = new IdentityHashMap<Task, Row>();
		for (Iterator i = project.getTaskOutlineIterator(); i.hasNext();) {
			Object impl = i.next();
			if (!(impl instanceof NormalTask))
				continue;
			NormalTask task = (NormalTask) impl;
			String role = ResourcePlan.role(task.getActivity());
			if (role == null || task.isWbsParent() || task.isCancelled() || task.isComplete())
				continue;
			String skill = ResourcePlan.DEV.equalsIgnoreCase(role) && task.getLane() != null ? task.getLane() : ""; //$NON-NLS-1$
			Row row = new Row(task, summary(task.getWbsParentTask(), summaries), role, skill);
			if (row.parent == null)
				roots.add(row);
			for (Iterator a = task.getAssignments().iterator(); a.hasNext();) {
				Assignment assignment = (Assignment) a.next();
				if (!assignment.isDefault() && assignment.isLabor())
					row.original.add(assignment.getResource());
			}
			row.resources.addAll(row.original);
			activities.add(row);
		}
	}

	/** Row of a summary task, added with the ones above it the first time. Null for none */
	private Row summary(Task task, Map<Task, Row> summaries) {
		if (!(task instanceof NormalTask))
			return null;
		Row row = summaries.get(task);
		if (row == null) {
			row = new Row((NormalTask) task, summary(task.getWbsParentTask(), summaries), null, null);
			if (row.parent == null)
				roots.add(row);
			summaries.put(task, row);
		}
		return row;
	}

	/** Calculates the load of the people from the tasks chosen for them */
	public void calculate() {
		for (Person person : persons) {
			person.assignedHours = 0;
			person.tasks = 0;
		}
		for (Row row : activities) {
			int count = row.resources.size();
			for (Resource resource : row.resources) {
				Person person = personOf.get(resource);
				if (person == null)
					continue;
				person.assignedHours += row.getHours() / count;
				person.tasks++;
			}
		}
	}

	/** Gives the tasks to a person, instead of the people they had. Null removes their people */
	public void assign(Collection<Row> rows, Resource resource) {
		for (Row row : rows) {
			if (!row.isActivity())
				continue;
			row.resources.clear();
			if (resource != null)
				row.resources.add(resource);
		}
		calculate();
	}

	/** True if a person has the role of a task, and its skill when it needs one */
	public static boolean canDo(Person person, Row row) {
		if (!row.isActivity() || !person.getRole().equalsIgnoreCase(row.role))
			return false;
		return row.skill.length() == 0 || person.getSkill().length() == 0 || SkillCatalog.getInstance().canDo(person.getSkill(), row.skill);
	}

	/** The tasks to assign of rows: the rows of tasks, and the tasks below the summaries, once each */
	public static List<Row> activities(Collection<Row> rows) {
		Set<Row> result = Collections.newSetFromMap(new IdentityHashMap<Row, Boolean>());
		List<Row> list = new ArrayList<Row>();
		for (Row row : rows)
			addActivities(row, result, list);
		return list;
	}

	private static void addActivities(Row row, Set<Row> done, List<Row> list) {
		if (row.isActivity()) {
			if (done.add(row))
				list.add(row);
			return;
		}
		for (Row child : row.children)
			addActivities(child, done, list);
	}

	/** What is wrong with the people of a task: none, several, a wrong role, a missing skill. Empty for a summary */
	public List<String> getIssues(Row row) {
		List<String> issues = new ArrayList<String>();
		if (!row.isActivity())
			return issues;
		if (row.resources.isEmpty())
			issues.add(Messages.getString("AssignmentBoard.Unassigned")); //$NON-NLS-1$
		else if (row.resources.size() > 1)
			issues.add(MessageFormat.format(Messages.getString("AssignmentBoard.Several"), names(row.resources))); //$NON-NLS-1$
		for (Resource resource : row.resources) {
			Person person = personOf.get(resource);
			if (person == null)
				continue; // a resource without role is not checked
			if (!person.getRole().equalsIgnoreCase(row.role))
				issues.add(MessageFormat.format(Messages.getString("AssignmentBoard.WrongRole"), row.role, person.getName(), person.getRole())); //$NON-NLS-1$
			else if (!canDo(person, row))
				issues.add(MessageFormat.format(Messages.getString("AssignmentBoard.MissingSkill"), row.skill, person.getName(), person.getSkill())); //$NON-NLS-1$
		}
		return issues;
	}

	/** True if the task has no person, or a wrong one */
	public boolean hasIssues(Row row) {
		return !getIssues(row).isEmpty();
	}

	/**
	 * Status of the load of a person, with the thresholds of the shortage of the Resource Plan: OK within the warning,
	 * WARNING over it, FAILED over the limit or with work and no capacity. A low load is not a warning: while the
	 * tasks are being given, everyone has some time left
	 */
	public int getStatus(Person person) {
		double over = person.getLoad() - 100;
		if (over > options.getShortageLimit() + EPSILON)
			return FAILED;
		if (over > options.getShortageWarning() + EPSILON)
			return WARNING;
		return OK;
	}

	/** Why the load of a person is a warning, null if it is not */
	public String getIssue(Person person) {
		if (person.getCapacityHours() <= EPSILON)
			return Messages.getString("AssignmentBoard.NoCapacity"); //$NON-NLS-1$
		int status = getStatus(person);
		if (status == OK)
			return null;
		return MessageFormat.format(Messages.getString(status == FAILED ? "AssignmentBoard.OverLimit" : "AssignmentBoard.OverWarning"), //$NON-NLS-1$ //$NON-NLS-2$
				String.valueOf(Math.round(person.getLoad() - 100)),
				String.valueOf(status == FAILED ? options.getShortageLimit() : options.getShortageWarning()));
	}

	/** Names of resources, separated by commas */
	public static String names(Collection<Resource> resources) {
		StringBuilder names = new StringBuilder();
		for (Resource resource : resources)
			names.append(names.length() == 0 ? "" : ", ").append(resource.getName()); //$NON-NLS-1$ //$NON-NLS-2$
		return names.toString();
	}

	public Person getPerson(Resource resource) { return personOf.get(resource); }
	public Project getProject() { return project; }
	/** The rows of the top of the WBS of the board */
	public List<Row> getRoots() { return roots; }
	/** The tasks to assign, in the order of the WBS */
	public List<Row> getActivities() { return activities; }
	public List<Person> getPersons() { return persons; }

	public int getUnassignedCount() {
		int count = 0;
		for (Row row : activities)
			if (row.resources.isEmpty())
				count++;
		return count;
	}

	/** Tasks with people and an issue: several, a wrong role or a missing skill */
	public int getIssueCount() {
		int count = 0;
		for (Row row : activities)
			if (!row.resources.isEmpty() && hasIssues(row))
				count++;
		return count;
	}

	/** The tasks whose people changed */
	public List<Row> getChangedRows() {
		List<Row> changed = new ArrayList<Row>();
		for (Row row : activities)
			if (row.isChanged())
				changed.add(row);
		return changed;
	}

	public boolean isChanged() {
		return !getChangedRows().isEmpty();
	}
}
