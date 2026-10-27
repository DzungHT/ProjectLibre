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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projectlibre1.pm.assignment.Assignment;
import com.projectlibre1.pm.resource.Resource;
import com.projectlibre1.pm.resource.ResourceImpl;
import com.projectlibre1.pm.resource.labor.LaborCatalog;
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
		/** Person suggested, null for none; true if no person can do the task (see suggest()) */
		private Resource suggestion = null;
		private boolean noCandidate = false;
		/** Fixed people: kept by the suggestions and the bulk changes. In the project, and in the board */
		private final boolean originalLocked;
		private boolean locked;

		Row(NormalTask task, Row parent, String role, String skill) {
			this.task = task;
			this.parent = parent;
			this.level = parent == null ? 0 : parent.level + 1;
			this.role = role;
			this.skill = skill;
			this.originalLocked = this.locked = role != null && task.isAssignmentLocked();
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
		public boolean isPeopleChanged() { return !new LinkedHashSet<Resource>(original).equals(new LinkedHashSet<Resource>(resources)); }
		/** True if the people or the lock are not the ones of the project */
		public boolean isChanged() { return isPeopleChanged() || locked != originalLocked; }
		public Resource getSuggestion() { return suggestion; }
		/** True if the last suggestions found no person of the role and skill of the task */
		public boolean hasNoCandidate() { return noCandidate; }
		public boolean isLocked() { return locked; }
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
		private double assignedHours, suggestedHours;
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
		/** Code of the level in the labor catalog, "" if not set */
		public String getLevelCode() {
			if (getLevel() < 0)
				return ""; //$NON-NLS-1$
			LaborCatalog.Category category = LaborCatalog.getInstance().find(getRole(), getLevel());
			return category == null ? LaborCatalog.levelAbbreviation(getLevel()) : category.getLevelCode();
		}
		public boolean isExisting() { return line.isExisting(); }
		/** Period of the person in the plan */
		public long getFrom() { return line.getFrom(); }
		public long getTo() { return line.getTo(); }
		/** Max Units of the period: point × allocation */
		public double getUnits() { return line.getPoint() * line.getAllocation(); }
		/** Effort the person can do, hours at point 1.0: point × % × working hours × utilization */
		public double getCapacityHours() { return line.getOutputHoursPerPerson(); }
		/** Effort of the tasks chosen for the person, hours at point 1.0. A task of several people is shared by their Max Units, see share() */
		public double getAssignedHours() { return assignedHours; }
		public int getTasks() { return tasks; }
		/** Assigned / capacity, %. Infinite if the person has work and no capacity */
		public double getLoad() {
			if (getCapacityHours() <= EPSILON)
				return assignedHours <= EPSILON ? 0 : Double.POSITIVE_INFINITY;
			return assignedHours / getCapacityHours() * 100;
		}
		/** Effort of the tasks suggested for the person, hours at point 1.0 */
		public double getSuggestedHours() { return suggestedHours; }
		/** Load once the suggestions are accepted, % */
		public double getLoadWithSuggestions() {
			double hours = assignedHours + suggestedHours;
			if (getCapacityHours() <= EPSILON)
				return hours <= EPSILON ? 0 : Double.POSITIVE_INFINITY;
			return hours / getCapacityHours() * 100;
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

	/** Calculates the load of the people from the tasks chosen for them, and the effort suggested to them */
	public void calculate() {
		for (Person person : persons) {
			person.assignedHours = 0;
			person.suggestedHours = 0;
			person.tasks = 0;
		}
		for (Row row : activities) {
			Person suggested = row.suggestion == null ? null : personOf.get(row.suggestion);
			if (suggested != null)
				suggested.suggestedHours += row.getHours();
			for (Resource resource : row.resources) {
				Person person = personOf.get(resource);
				if (person == null)
					continue;
				person.assignedHours += row.getHours() * share(row, resource);
				person.tasks++;
			}
		}
	}

	/**
	 * Part of the work of a task done by one of its people: its Max Units (point × allocation) over the ones of all the
	 * people, as ProjectLibre shares the work of a task between its assignments. Equal parts if no person has units
	 */
	public double share(Row row, Resource resource) {
		double total = 0;
		for (Resource r : row.resources)
			total += units(r);
		return total <= EPSILON ? 1.0 / row.resources.size() : units(resource) / total;
	}

	/** Max Units of a person in the plan, 1 for a resource without role */
	private double units(Resource resource) {
		Person person = personOf.get(resource);
		return person == null ? 1 : person.getUnits();
	}

	/**
	 * Gives a task to a group of people, instead of the people it had, and drops its suggestion. Empty removes its
	 * people. Call calculate() after.
	 * @return false if the task is locked and was kept
	 */
	public boolean setPeople(Row row, Collection<? extends Resource> resources) {
		if (!row.isActivity())
			return true;
		if (row.locked)
			return false;
		row.resources.clear();
		row.resources.addAll(resources);
		row.suggestion = null;
		row.noCandidate = false;
		return true;
	}

	/**
	 * Gives the tasks to a person, instead of the people they had, and drops their suggestions. Null removes their
	 * people. The locked tasks are kept
	 * @return the number of locked tasks kept
	 */
	public int assign(Collection<Row> rows, Resource resource) {
		int kept = 0;
		for (Row row : rows) {
			if (!row.isActivity())
				continue;
			if (row.locked) {
				kept++;
				continue;
			}
			row.resources.clear();
			if (resource != null)
				row.resources.add(resource);
			row.suggestion = null;
			row.noCandidate = false;
		}
		calculate();
		return kept;
	}

	public void setLocked(Collection<Row> rows, boolean locked) {
		for (Row row : rows)
			if (row.isActivity())
				row.locked = locked;
	}

	/**
	 * Suggests a person for the tasks without people and not locked, by function (the summary above the tasks), in the
	 * order of the WBS. The tasks of a role and skill in a function go to a single person, chosen in this order:
	 * <ol>
	 * <li>the person already doing tasks of the role in the function, if the load stays within the shortage warning
	 * of the Resource Plan once the tasks are added (see SuggestOptions.isKeepFunction())</li>
	 * <li>the person already doing tasks of the role in the cluster (the summary above the function), same condition</li>
	 * <li>the existing person with time left: the lowest load once the tasks are added, within the warning</li>
	 * <li>the TBD person with the lowest load once the tasks are added; all the people if there is no TBD one</li>
	 * </ol>
	 * A tie goes to the person whose skill is the required one (BE before Fullstack), then to the first in the list.
	 * The suggestions only fill the suggestion of the tasks: acceptSuggestions() makes them the people.
	 * @return the number of tasks with a suggestion
	 */
	public int suggest(SuggestOptions options) {
		clearSuggestions();
		Map<Person, Double> hours = new IdentityHashMap<Person, Double>();
		for (Person person : persons)
			hours.put(person, person.assignedHours);
		// the functions in the order of the WBS; a task without summary is a function of its own
		Map<Object, List<Row>> functions = new LinkedHashMap<Object, List<Row>>();
		for (Row row : activities) {
			Object function = row.parent == null ? row : row.parent;
			List<Row> rows = functions.get(function);
			if (rows == null)
				functions.put(function, rows = new ArrayList<Row>());
			rows.add(row);
		}
		int count = 0;
		for (List<Row> function : functions.values()) {
			Map<Object, List<Row>> groups = new LinkedHashMap<Object, List<Row>>();
			for (Row row : function) {
				if (row.locked || !row.resources.isEmpty())
					continue;
				Object key = options.isKeepFunction() ? ResourcePlan.key(row.role, row.skill) : row;
				List<Row> group = groups.get(key);
				if (group == null)
					groups.put(key, group = new ArrayList<Row>());
				group.add(row);
			}
			for (List<Row> group : groups.values()) {
				Person person = choose(group, function, options, hours);
				for (Row row : group) {
					row.suggestion = person == null ? null : person.resource;
					row.noCandidate = person == null;
				}
				if (person == null)
					continue;
				hours.put(person, hours.get(person) + hours(group));
				count += group.size();
			}
		}
		calculate();
		return count;
	}

	/** The person for a group of tasks of a function, see suggest(). Null if no one can do them */
	private Person choose(List<Row> group, List<Row> function, SuggestOptions options, Map<Person, Double> hours) {
		Row first = group.get(0);
		double added = hours(group);
		List<Person> candidates = new ArrayList<Person>();
		for (Person person : persons)
			if (canDo(person, first) && person.getCapacityHours() > EPSILON)
				candidates.add(person);
		if (candidates.isEmpty())
			return null;
		Person person = null;
		if (options.isKeepFunction())
			person = lowest(withTime(doing(candidates, function, first.role), added, hours), first, added, hours);
		if (person == null && options.isKeepCluster() && first.parent != null && first.parent.parent != null)
			person = lowest(withTime(doing(candidates, activities(Collections.singletonList(first.parent.parent)), first.role), added, hours),
					first, added, hours);
		if (person == null && options.isPreferExisting()) {
			List<Person> existing = new ArrayList<Person>();
			for (Person candidate : candidates)
				if (candidate.isExisting())
					existing.add(candidate);
			person = lowest(withTime(existing, added, hours), first, added, hours);
		}
		if (person == null && options.isPreferExisting()) {
			List<Person> tbd = new ArrayList<Person>();
			for (Person candidate : candidates)
				if (!candidate.isExisting())
					tbd.add(candidate);
			person = lowest(tbd, first, added, hours);
		}
		return person == null ? lowest(candidates, first, added, hours) : person;
	}

	/** The candidates doing (or suggested for) tasks of a role among rows */
	private List<Person> doing(List<Person> candidates, List<Row> rows, String role) {
		Set<Resource> resources = Collections.newSetFromMap(new IdentityHashMap<Resource, Boolean>());
		for (Row row : rows)
			if (row.isActivity() && row.role.equalsIgnoreCase(role)) {
				resources.addAll(row.resources);
				if (row.suggestion != null)
					resources.add(row.suggestion);
			}
		List<Person> result = new ArrayList<Person>();
		for (Person candidate : candidates)
			if (resources.contains(candidate.resource))
				result.add(candidate);
		return result;
	}

	/** The candidates whose load stays within the shortage warning once the effort is added */
	private List<Person> withTime(List<Person> candidates, double added, Map<Person, Double> hours) {
		List<Person> result = new ArrayList<Person>();
		for (Person candidate : candidates)
			if (loadAfter(candidate, added, hours) <= 100 + options.getShortageWarning() + EPSILON)
				result.add(candidate);
		return result;
	}

	/** The candidate with the lowest load once the effort is added, the one of the required skill on a tie. Null if none */
	private Person lowest(List<Person> candidates, Row row, double added, Map<Person, Double> hours) {
		Person best = null;
		double bestLoad = 0;
		for (Person candidate : candidates) {
			double load = loadAfter(candidate, added, hours);
			if (best == null || load < bestLoad - EPSILON
					|| (load < bestLoad + EPSILON && exactSkill(candidate, row) && !exactSkill(best, row))) {
				best = candidate;
				bestLoad = load;
			}
		}
		return best;
	}

	private static double loadAfter(Person person, double added, Map<Person, Double> hours) {
		return (hours.get(person) + added) / person.getCapacityHours() * 100;
	}

	private static boolean exactSkill(Person person, Row row) {
		return person.getSkill().equalsIgnoreCase(row.skill);
	}

	private static double hours(List<Row> rows) {
		double hours = 0;
		for (Row row : rows)
			hours += row.getHours();
		return hours;
	}

	/**
	 * The suggestions of rows become their people, for the tasks not locked
	 * @return the number of tasks changed
	 */
	public int acceptSuggestions(Collection<Row> rows) {
		int count = 0;
		for (Row row : rows)
			if (row.isActivity() && !row.locked && row.suggestion != null) {
				row.resources.clear();
				row.resources.add(row.suggestion);
				row.suggestion = null;
				count++;
			}
		calculate();
		return count;
	}

	/**
	 * Gives the tasks of a person to another one, as when a real person is found for a TBD one. A task of several
	 * people keeps the others. The suggestions of the person go to the other one too
	 * @param withLocked true to move the locked tasks too, which stay locked
	 * @return the number of tasks moved, and of locked tasks kept
	 */
	public int[] replace(Person from, Person to, boolean withLocked) {
		int moved = 0, kept = 0;
		for (Row row : activities) {
			if (row.suggestion == from.resource)
				row.suggestion = to.resource;
			int index = row.resources.indexOf(from.resource);
			if (index < 0)
				continue;
			if (row.locked && !withLocked) {
				kept++;
				continue;
			}
			if (row.resources.contains(to.resource))
				row.resources.remove(index);
			else
				row.resources.set(index, to.resource);
			moved++;
		}
		calculate();
		return new int[] { moved, kept };
	}

	/** A task to give from a person to another one, see planOffload() */
	public static class Move {
		private final Row row;
		private final Person from, to;
		Move(Row row, Person from, Person to) {
			this.row = row;
			this.from = from;
			this.to = to;
		}
		public Row getRow() { return row; }
		public Person getFrom() { return from; }
		public Person getTo() { return to; }
	}

	/** True if the load of a person is over the shortage warning of the Resource Plan */
	public boolean isOverloaded(Person person) {
		return getStatus(person) != OK;
	}

	/**
	 * The tasks to move from the people over the shortage warning to the people of their role and skill with the
	 * lowest load, until they are within it. The tasks of a person (alone, not locked) are taken by function from
	 * the end of the WBS, and the tasks of a role and skill in a function go to a single person whose load stays
	 * within the warning once they are added. A function no one can take is moved task by task. Nothing is changed:
	 * applyMoves() does it
	 */
	public List<Move> planOffload(Collection<Person> people) {
		Map<Person, Double> hours = new IdentityHashMap<Person, Double>();
		for (Person person : persons)
			hours.put(person, person.assignedHours);
		List<Move> moves = new ArrayList<Move>();
		for (Person from : people) {
			if (!over(from, hours))
				continue;
			Map<Object, List<Row>> groups = new LinkedHashMap<Object, List<Row>>();
			for (Row row : activities) {
				if (row.locked || row.resources.size() != 1 || row.resources.get(0) != from.resource)
					continue;
				List<Object> key = new ArrayList<Object>();
				key.add(row.parent == null ? row : row.parent);
				key.add(ResourcePlan.key(row.role, row.skill));
				List<Row> group = groups.get(key);
				if (group == null)
					groups.put(key, group = new ArrayList<Row>());
				group.add(row);
			}
			List<List<Row>> functions = new ArrayList<List<Row>>(groups.values());
			Collections.reverse(functions);
			for (List<Row> group : functions) {
				if (!over(from, hours))
					break;
				if (move(group, from, hours, moves))
					continue;
				for (int i = group.size() - 1; i >= 0 && over(from, hours); i--)
					move(Collections.singletonList(group.get(i)), from, hours, moves);
			}
		}
		return moves;
	}

	/** Moves tasks of a person to the one of their role and skill with the lowest load, if it stays within the warning */
	private boolean move(List<Row> group, Person from, Map<Person, Double> hours, List<Move> moves) {
		Row first = group.get(0);
		double added = hours(group);
		List<Person> candidates = new ArrayList<Person>();
		for (Person person : persons)
			if (person != from && canDo(person, first) && person.getCapacityHours() > EPSILON)
				candidates.add(person);
		Person to = lowest(withTime(candidates, added, hours), first, added, hours);
		if (to == null)
			return false;
		for (Row row : group)
			moves.add(new Move(row, from, to));
		hours.put(from, hours.get(from) - added);
		hours.put(to, hours.get(to) + added);
		return true;
	}

	private boolean over(Person person, Map<Person, Double> hours) {
		double assigned = hours.get(person);
		if (person.getCapacityHours() <= EPSILON)
			return assigned > EPSILON;
		return assigned / person.getCapacityHours() * 100 > 100 + options.getShortageWarning() + EPSILON;
	}

	/** Gives the tasks of moves to their new people, see planOffload() */
	public void applyMoves(List<Move> moves) {
		for (Move move : moves) {
			int index = move.row.resources.indexOf(move.from.resource);
			if (index >= 0)
				move.row.resources.set(index, move.to.resource);
			move.row.suggestion = null;
			move.row.noCandidate = false;
		}
		calculate();
	}

	public void clearSuggestions() {
		for (Row row : activities) {
			row.suggestion = null;
			row.noCandidate = false;
		}
	}

	public int getSuggestionCount() {
		int count = 0;
		for (Row row : activities)
			if (row.suggestion != null)
				count++;
		return count;
	}

	public int getLockedCount() {
		int count = 0;
		for (Row row : activities)
			if (row.locked)
				count++;
		return count;
	}

	/** True if a person has the role of a task, and its skill when it needs one */
	public static boolean canDo(Person person, Row row) {
		return row.isActivity() && canDo(person.getRole(), person.getSkill(), row.role, row.skill);
	}

	private static boolean canDo(String personRole, String personSkill, String role, String skill) {
		if (!personRole.equalsIgnoreCase(role))
			return false;
		return skill.length() == 0 || personSkill.length() == 0 || SkillCatalog.getInstance().canDo(personSkill, skill);
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
			issues.add(Messages.getString(row.noCandidate ? "AssignmentBoard.NoCandidate" : "AssignmentBoard.Unassigned")); //$NON-NLS-1$ //$NON-NLS-2$
		addIssues(row.role, row.skill, row.resources, issues);
		return issues;
	}

	/**
	 * For the indicator of the task sheet: what is wrong with the people of a task of an activity, see getIssues(),
	 * without the task not assigned. Empty for the other tasks, and for the completed and cancelled ones
	 */
	public static List<String> taskIssues(NormalTask task) {
		List<String> issues = new ArrayList<String>();
		String role = ResourcePlan.role(task.getActivity());
		if (role == null || task.isWbsParent() || task.isCancelled() || task.isComplete())
			return issues;
		List<Resource> resources = new ArrayList<Resource>();
		for (Iterator i = task.getAssignments().iterator(); i.hasNext();) {
			Assignment assignment = (Assignment) i.next();
			if (!assignment.isDefault() && assignment.isLabor())
				resources.add(assignment.getResource());
		}
		String skill = ResourcePlan.DEV.equalsIgnoreCase(role) && task.getLane() != null ? task.getLane() : ""; //$NON-NLS-1$
		addIssues(role, skill, resources, issues);
		return issues;
	}

	/** People of another role or without the skill of a task: a group of people of the task is not a warning. A resource without role is not checked */
	private static void addIssues(String role, String skill, List<Resource> resources, List<String> issues) {
		for (Resource resource : resources) {
			if (!(resource instanceof ResourceImpl) || ((ResourceImpl) resource).getLaborRole() == null)
				continue;
			String personRole = ((ResourceImpl) resource).getLaborRole(), personSkill = skills((ResourceImpl) resource);
			if (!personRole.equalsIgnoreCase(role))
				issues.add(MessageFormat.format(Messages.getString("AssignmentBoard.WrongRole"), role, resource.getName(), personRole)); //$NON-NLS-1$
			else if (!canDo(personRole, personSkill, role, skill))
				issues.add(MessageFormat.format(Messages.getString("AssignmentBoard.MissingSkill"), skill, resource.getName(), personSkill)); //$NON-NLS-1$
		}
	}

	/** Skills of a resource, "" for none, as in the lines of the Resource Plan */
	private static String skills(ResourceImpl resource) {
		return resource.getSkills() == null ? "" : resource.getSkills(); //$NON-NLS-1$
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
