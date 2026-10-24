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
package com.projectlibre1.pm.scheduling.auto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projectlibre1.datatype.Duration;
import com.projectlibre1.pm.assignment.Assignment;
import com.projectlibre1.pm.availability.Availability;
import com.projectlibre1.pm.dependency.Dependency;
import com.projectlibre1.pm.resource.ResourceImpl;
import com.projectlibre1.pm.resource.labor.LaborCatalog;
import com.projectlibre1.pm.resource.labor.SkillCatalog;
import com.projectlibre1.pm.scheduling.ConstraintType;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.pm.task.Task;

/**
 * The tasks and resources of a project for the AutoScheduler:
 * <ul>
 * <li>A leaf task with an activity done by a role, not cancelled, with work and not started is scheduled (WORK).
 * Its required skill is the one of the task for the development activities. Started and completed tasks keep
 * their dates (FIXED), the other tasks their duration and leveling delay (PASSIVE)</li>
 * <li>The resources are the work resources of the project; only the ones with a role are given tasks</li>
 * <li>The links are the enabled links of the tasks. A link to or from a summary task goes through the points of
 * the summary (see AutoTask)</li>
 * </ul>
 */
public class AutoScheduleModel {
	private final Project project;
	private final AutoScheduleOptions options;
	private final WorkingTime time;
	private final List<AutoTask> tasks = new ArrayList<AutoTask>();
	private final List<AutoTask> workTasks = new ArrayList<AutoTask>();
	private final List<AutoResource> resources = new ArrayList<AutoResource>();
	private final Map<Object, AutoResource> byResource = new IdentityHashMap<Object, AutoResource>();
	private final Map<Task, AutoTask> byTask = new IdentityHashMap<Task, AutoTask>();
	private final Map<Task, AutoTask> summaryStarts = new IdentityHashMap<Task, AutoTask>();
	private final Map<Task, AutoTask> summaryEnds = new IdentityHashMap<Task, AutoTask>();
	private final Map<String, Double> standardPoints = new HashMap<String, Double>();
	private AutoScheduler scheduler = null;

	public static AutoScheduleModel create(Project project, AutoScheduleOptions options) {
		AutoScheduleModel model = new AutoScheduleModel(project, options);
		model.build();
		return model;
	}

	private AutoScheduleModel(Project project, AutoScheduleOptions options) {
		this.project = project;
		this.options = options;
		this.time = WorkingTime.of(project.getEffectiveWorkCalendar());
	}

	/**
	 * Runs the scheduler: the results are in the tasks
	 * @throws AutoScheduler.CircularLinksException if the links are circular
	 */
	public void schedule() throws AutoScheduler.CircularLinksException {
		long projectStart = project.getStartConstraint();
		scheduler = new AutoScheduler(time, projectStart, options.getStart() == 0 ? projectStart : options.getStart());
		scheduler.setCriticalFirst(options.isCriticalFirst());
		scheduler.schedule(tasks, resources);
	}

	private void build() {
		for (Iterator i = project.getResourcePool().getResourceList().iterator(); i.hasNext();) {
			Object impl = i.next();
			if (impl instanceof ResourceImpl && ((ResourceImpl) impl).isLabor())
				addResource((ResourceImpl) impl);
		}
		List<NormalTask> all = new ArrayList<NormalTask>();
		Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
		for (Iterator i = project.getTaskOutlineIterator(); i.hasNext();) {
			Object impl = i.next();
			if (impl instanceof NormalTask && seen.add(impl))
				all.add((NormalTask) impl);
		}
		for (NormalTask task : all)
			if (!task.isWbsParent())
				addTask(task);
		// summary tasks with links or a constraint to start: the tasks below start after them
		for (NormalTask task : all)
			if (task.isWbsParent() && (hasLinks(task) || earliestStart(task) != 0)) {
				AutoTask point = AutoTask.summaryStart(task);
				point.setEarliestStart(earliestStart(task));
				summaryStarts.put(task, point);
				tasks.add(point);
			}
		for (NormalTask task : all) {
			AutoTask successor = task.isWbsParent() ? summaryStarts.get(task) : byTask.get(task);
			if (successor != null)
				addLinks(task, successor);
		}
		for (NormalTask task : all) {
			if (task.isWbsParent())
				continue;
			AutoTask node = byTask.get(task);
			for (Task parent = task.getWbsParentTask(); parent != null; parent = parent.getWbsParentTask()) {
				AutoTask start = summaryStarts.get(parent);
				if (start != null)
					node.addParentStart(start);
				AutoTask end = summaryEnds.get(parent);
				if (end != null)
					node.addToSummaryEnd(end);
			}
		}
	}

	private void addResource(ResourceImpl resource) {
		List periods = resource.getAvailabilityTable() == null ? null : resource.getAvailabilityTable().getList();
		long[] starts;
		double[] units;
		if (periods == null || periods.isEmpty()) {
			starts = new long[] { Long.MIN_VALUE };
			units = new double[] { resource.getMaximumUnits() };
		} else {
			starts = new long[periods.size()];
			units = new double[periods.size()];
			for (int p = 0; p < periods.size(); p++) {
				Availability availability = (Availability) periods.get(p);
				starts[p] = availability.getStart();
				units[p] = availability.getMaximumUnits();
			}
		}
		WorkingTime resourceTime = resource.getEffectiveWorkCalendar() == null ? time : WorkingTime.of(resource.getEffectiveWorkCalendar());
		AutoResource result = new AutoResource(resource, resource.getLaborRole(), SkillCatalog.getInstance().expand(resource.getSkills()), starts, units, resourceTime);
		resources.add(result);
		byResource.put(resource, result);
	}

	private void addTask(NormalTask task) {
		String activity = options.activityOf(task);
		String role = options.getRole(activity);
		long work = Duration.millis(task.getWork(null));
		if (task.isEffortDriven() && task.isWorkOriginalEstimate(work))
			work = task.getOriginalEstimate(); // what the work becomes when the task is assigned again
		long duration = Math.max(0, Duration.millis(task.getDuration()));
		AutoTask node;
		if (task.getActualStart() != 0 || task.isComplete()) {
			node = AutoTask.fixed(task, task.getStart(), task.getEnd());
			node.keepResources(assignedResources(task));
		} else if (role != null && !task.isCancelled() && work > 0) {
			String skill = options.hasRequiredSkill(activity) ? task.getLane() : null;
			node = AutoTask.work(task, role, skill, work, Math.round(work / standardPoint(role)), duration);
			List<AutoResource> assigned = assignedResources(task);
			if (options.isKeepAssignments() && !assigned.isEmpty())
				node.keepResources(assigned);
			workTasks.add(node);
		} else
			node = AutoTask.passive(task, duration, task.getLevelingDelay());
		node.setEarliestStart(earliestStart(task));
		byTask.put(task, node);
		tasks.add(node);
	}

	/** Work resources assigned to a task */
	private List<AutoResource> assignedResources(NormalTask task) {
		List<AutoResource> list = new ArrayList<AutoResource>();
		for (Iterator i = task.getAssignments().iterator(); i.hasNext();) {
			Assignment assignment = (Assignment) i.next();
			AutoResource resource = assignment.isDefault() ? null : byResource.get(assignment.getResource());
			if (resource != null && !list.contains(resource))
				list.add(resource);
		}
		return list;
	}

	private void addLinks(NormalTask task, AutoTask successor) {
		for (Iterator i = task.getPredecessorList().iterator(); i.hasNext();) {
			Dependency dependency = (Dependency) i.next();
			if (dependency.isDisabled() || !(dependency.getPredecessor() instanceof Task))
				continue;
			successor.addPredecessor(predecessorPoint((Task) dependency.getPredecessor()), dependency.getDependencyType(), dependency.getLeadValue());
		}
	}

	/** The task, the end point of a summary task, or a task of another project with its dates */
	private AutoTask predecessorPoint(Task task) {
		AutoTask point = task.isWbsParent() ? summaryEnds.get(task) : byTask.get(task);
		if (point != null)
			return point;
		if (task.isWbsParent() && task.getProject() == project) {
			point = AutoTask.summaryEnd(task);
			summaryEnds.put(task, point);
		} else {
			point = AutoTask.fixed(task, task.getStart(), task.getEnd()); // another project
			byTask.put(task, point);
		}
		tasks.add(point);
		return point;
	}

	private static boolean hasLinks(NormalTask task) {
		for (Iterator i = task.getPredecessorList().iterator(); i.hasNext();)
			if (!((Dependency) i.next()).isDisabled())
				return true;
		return false;
	}

	/**
	 * Date of a constraint Start No Earlier Than or Must Start On, 0 if none. A constraint set by a previous Auto
	 * Schedule is not one: it is calculated again
	 */
	private static long earliestStart(NormalTask task) {
		if (AutoScheduleJob.hasOwnConstraint(task))
			return 0;
		int type = task.getConstraintType();
		return type == ConstraintType.SNET || type == ConstraintType.MSO ? task.getConstraintDate() : 0;
	}

	/**
	 * Point of the standard level of a role, for the priorities: the level whose point is the closest to 1.0
	 * (the effort of the ULNL is the one of a point 1.0). 1.0 if the role is not in the labor categories
	 */
	private double standardPoint(String role) {
		Double point = standardPoints.get(role);
		if (point == null) {
			double best = 1.0;
			for (LaborCatalog.Category category : LaborCatalog.getInstance().getCategories())
				if (category.getRole().equalsIgnoreCase(role) && category.getPoint() > 0
						&& (best == 1.0 || Math.abs(category.getPoint() - 1.0) < Math.abs(best - 1.0)))
					best = category.getPoint();
			point = best;
			standardPoints.put(role, point);
		}
		return point;
	}

	public Project getProject() {
		return project;
	}
	public AutoScheduleOptions getOptions() {
		return options;
	}
	/** Working time of the project */
	public WorkingTime getTime() {
		return time;
	}
	/** All the tasks and points, results included after schedule() */
	public List<AutoTask> getTasks() {
		return tasks;
	}
	/** The tasks scheduled (WORK), in the order of the WBS */
	public List<AutoTask> getWorkTasks() {
		return workTasks;
	}
	public List<AutoResource> getResources() {
		return resources;
	}
	/** The task of the scheduler for a leaf task of the project, null if none */
	public AutoTask find(NormalTask task) {
		return byTask.get(task);
	}
	/** End of the last task, after schedule() */
	public long getEnd() {
		return scheduler == null ? 0 : scheduler.getEnd();
	}
}
