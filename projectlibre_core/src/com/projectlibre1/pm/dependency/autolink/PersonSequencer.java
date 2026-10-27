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
package com.projectlibre1.pm.dependency.autolink;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.projectlibre1.pm.availability.AvailabilityWarning;
import com.projectlibre1.pm.calendar.WorkCalendar;
import com.projectlibre1.pm.dependency.Dependency;
import com.projectlibre1.pm.resource.Resource;
import com.projectlibre1.pm.scheduling.ConstraintType;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.pm.task.Task;

/**
 * Order of the tasks of each sequence of Auto Link, see docs/relink-design.md: of each person, or of a lane for the
 * tasks not assigned. A quick simulation of the schedule gives the order only: the dates are calculated by
 * ProjectLibre from the links. A task can start when its logic predecessors are finished and its people are free
 * and available. In the ready order, the task that can start
 * first goes next, the first in the WBS on a tie; in the WBS order, each person does its tasks in the order of the
 * WBS, unless a link made by hand goes up the WBS. The order comes from a schedule that follows the logic links, so
 * its links make no circle with them.
 * <p>
 * Simplifications, as it is only an order: the links are finish to start without lag, a predecessor outside the
 * tasks ordered ends at its current date, a started task keeps its dates.
 */
class PersonSequencer {
	private final Project project;
	private final List<NormalTask> tasks; // in the order of the WBS
	private final Map<NormalTask, Set<NormalTask>> logic; // logic predecessors among the tasks
	private final boolean readyOrder;
	private final Map<NormalTask, Integer> index = new IdentityHashMap<NormalTask, Integer>();
	private final Map<NormalTask, List<Object>> people = new IdentityHashMap<NormalTask, List<Object>>();
	private final Map<Object, List<NormalTask>> orders = new LinkedHashMap<Object, List<NormalTask>>();
	private final Map<Object, Long> availableFrom = new HashMap<Object, Long>();
	private final Map<Object, Long> free = new HashMap<Object, Long>();
	private final Map<NormalTask, Long> finishes = new IdentityHashMap<NormalTask, Long>();
	private final Set<Task> summaries = Collections.newSetFromMap(new IdentityHashMap<Task, Boolean>()); // above the tasks
	private long finish = 0;
	private final Map<WorkCalendar, Map<Long, Long>> adjusted = new IdentityHashMap<WorkCalendar, Map<Long, Long>>();

	/**
	 * @param tasks the tasks to order, in the order of the WBS
	 * @param logic the logic predecessors of the tasks, among them: links of the activities and links made by hand
	 * @param sequences the sequences of each task: its people (Resource), or another key for a task not assigned
	 * (its lane), none if it is in no sequence
	 */
	PersonSequencer(Project project, List<NormalTask> tasks, Map<NormalTask, Set<NormalTask>> logic, Map<NormalTask, List<Object>> sequences,
			boolean readyOrder) {
		this.project = project;
		this.tasks = tasks;
		this.logic = logic;
		this.readyOrder = readyOrder;
		people.putAll(sequences);
		run();
	}

	private void run() {
		long projectStart = project.getStartConstraint();
		Map<Object, Deque<NormalTask>> queues = new HashMap<Object, Deque<NormalTask>>(); // tasks left, WBS order
		Map<NormalTask, List<NormalTask>> successors = new IdentityHashMap<NormalTask, List<NormalTask>>();
		Map<NormalTask, Integer> waiting = new IdentityHashMap<NormalTask, Integer>();
		for (NormalTask task : tasks) {
			index.put(task, index.size());
			List<Object> resources = people.get(task);
			if (resources == null)
				people.put(task, resources = Collections.emptyList());
			for (Object resource : resources) {
				if (!orders.containsKey(resource)) {
					orders.put(resource, new ArrayList<NormalTask>());
					queues.put(resource, new ArrayDeque<NormalTask>());
					long from = resource instanceof Resource ? AvailabilityWarning.availableFrom((Resource) resource, projectStart) : projectStart;
					if (from == AvailabilityWarning.NEVER)
						from = projectStart; // never available: warned by the indicator of its tasks
					availableFrom.put(resource, Math.max(from, projectStart));
					free.put(resource, Math.max(from, projectStart));
				}
				queues.get(resource).add(task);
			}
		}
		Set<NormalTask> ready = Collections.newSetFromMap(new IdentityHashMap<NormalTask, Boolean>()); // not done yet
		for (NormalTask task : tasks) {
			int count = 0;
			Set<NormalTask> predecessors = logic.get(task);
			if (predecessors != null)
				for (NormalTask predecessor : predecessors) {
					if (!index.containsKey(predecessor) || predecessor == task)
						continue;
					count++;
					List<NormalTask> next = successors.get(predecessor);
					if (next == null) {
						next = new ArrayList<NormalTask>();
						successors.put(predecessor, next);
					}
					next.add(task);
				}
			waiting.put(task, count);
			if (count == 0)
				ready.add(task);
		}

		for (NormalTask task : tasks)
			for (Task parent = task.getWbsParentTask(); parent != null && summaries.add(parent); parent = parent.getWbsParentTask());
		Map<NormalTask, Long> readyAt = new IdentityHashMap<NormalTask, Long>(); // once the logic predecessors are finished
		// the tasks that can be chosen (ready, and in the WBS order next in their sequences), by start then WBS. A start
		// only grows when a person gets busy, so it is calculated again only when the task comes first (lazily)
		PriorityQueue<long[]> candidates = new PriorityQueue<long[]>(Math.max(1, tasks.size()), new Comparator<long[]>() {
			public int compare(long[] a, long[] b) {
				return a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]);
			}
		});
		for (NormalTask task : ready) {
			readyAt.put(task, readyAt(task, projectStart));
			offer(task, readyAt, queues, candidates);
		}
		for (int done = 0; done < tasks.size(); done++) {
			NormalTask best = null;
			long bestStart = 0;
			while (best == null && !candidates.isEmpty()) {
				long[] candidate = candidates.poll();
				NormalTask task = tasks.get((int) candidate[1]);
				if (finishes.containsKey(task))
					continue;
				long start = start(task, readyAt.get(task));
				if (start > candidate[0])
					candidates.add(new long[] { start, candidate[1] });
				else {
					best = task;
					bestStart = start;
				}
			}
			// in the WBS order, if all the next tasks wait for links going up the WBS: any task ready
			if (best == null)
				for (NormalTask task : ready) {
					long start = start(task, readyAt.get(task));
					if (best == null || start < bestStart || (start == bestStart && index.get(task) < index.get(best))) {
						best = task;
						bestStart = start;
					}
				}
			if (best == null) { // circular links: the first task left in the WBS, Auto Link reports the circle
				for (NormalTask task : tasks)
					if (!finishes.containsKey(task)) {
						best = task;
						break;
					}
				bestStart = start(best, readyAt(best, projectStart));
			}
			ready.remove(best);
			long end = best.getActualStart() != 0 ? best.getEnd()
					: best.getEffectiveWorkCalendar().add(bestStart, best.getDuration(), false);
			finishes.put(best, end);
			finish = Math.max(finish, end);
			for (Object resource : people.get(best)) {
				free.put(resource, Math.max(free.get(resource), end));
				orders.get(resource).add(best);
				Deque<NormalTask> queue = queues.get(resource);
				queue.remove(best);
				if (!readyOrder && ready.contains(queue.peekFirst())) // next in the WBS of the person
					offer(queue.peekFirst(), readyAt, queues, candidates);
			}
			List<NormalTask> next = successors.get(best);
			if (next != null)
				for (NormalTask successor : next) {
					int count = waiting.get(successor) - 1;
					waiting.put(successor, count);
					if (count == 0 && !finishes.containsKey(successor)) {
						readyAt.put(successor, readyAt(successor, projectStart));
						ready.add(successor);
						offer(successor, readyAt, queues, candidates);
					}
				}
		}
	}

	/** Adds a task ready to the ones that can be chosen: in the WBS order, only if it is next in its sequences */
	private void offer(NormalTask task, Map<NormalTask, Long> readyAt, Map<Object, Deque<NormalTask>> queues, PriorityQueue<long[]> candidates) {
		if (readyOrder || isNext(task, queues))
			candidates.add(new long[] { start(task, readyAt.get(task)), index.get(task) });
	}

	/** True if the task is the next one in the WBS of each of its people */
	private boolean isNext(NormalTask task, Map<Object, Deque<NormalTask>> queues) {
		for (Object resource : people.get(task))
			if (queues.get(resource).peekFirst() != task)
				return false;
		return true;
	}

	/** Start of a task ready at a date, once its people are free */
	private long start(NormalTask task, long readyAt) {
		if (task.getActualStart() != 0)
			return task.getStart();
		long start = readyAt;
		for (Object resource : people.get(task))
			start = Math.max(start, free.get(resource));
		return adjust(task.getEffectiveWorkCalendar(), start);
	}

	/** First working time of a calendar from a date. Remembered: the same dates come back often, as the ends of tasks */
	private long adjust(WorkCalendar calendar, long date) {
		Map<Long, Long> dates = adjusted.get(calendar);
		if (dates == null)
			adjusted.put(calendar, dates = new HashMap<Long, Long>());
		Long result = dates.get(date);
		if (result == null)
			dates.put(date, result = calendar.adjustInsideCalendar(date, false));
		return result;
	}

	/**
	 * Earliest start of a task without its people: after its logic predecessors finished in the simulation, and
	 * after what is not simulated: the start of the project, the links (of the task and its summaries) from the
	 * tasks not ordered here, a constraint set by the user. The links from the tasks ordered here, or from a summary
	 * above some of them, are logic predecessors
	 */
	private long readyAt(NormalTask task, long projectStart) {
		long start = projectStart;
		Set<NormalTask> predecessors = logic.get(task);
		if (predecessors != null)
			for (NormalTask predecessor : predecessors) {
				Long end = finishes.get(predecessor);
				if (end != null)
					start = Math.max(start, end);
			}
		for (Task t = task; t != null; t = t.getWbsParentTask()) {
			for (Iterator i = t.getPredecessorList().iterator(); i.hasNext();) {
				Dependency dependency = (Dependency) i.next();
				Object predecessor = dependency.getPredecessor();
				if (dependency.isDisabled() || !(predecessor instanceof Task) || index.containsKey(predecessor) || summaries.contains(predecessor))
					continue;
				start = Math.max(start, ((Task) predecessor).getEnd());
			}
		}
		int type = task.getConstraintType();
		if ((type == ConstraintType.SNET || type == ConstraintType.MSO) && !task.isJoinConstraint())
			start = Math.max(start, task.getConstraintDate());
		return start;
	}

	/** The tasks of each sequence, in the order found */
	Map<Object, List<NormalTask>> getOrders() {
		return orders;
	}
	/** First day of a person: the start of the project, or the start of its first period of Max Units over 0% after it */
	long getAvailableFrom(Object resource) {
		Long from = availableFrom.get(resource);
		return from == null ? project.getStartConstraint() : from;
	}
	/** End of the last task in the simulation, 0 if there are none */
	long getFinish() {
		return finish;
	}
}
