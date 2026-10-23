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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import com.projectlibre1.datatype.Duration;
import com.projectlibre1.pm.dependency.DependencyType;

/**
 * Assigns the tasks to the resources and schedules them (list scheduling), see docs/auto-schedule-design.md:
 * <ol>
 * <li>Critical path without the limits of the resources, the work of the tasks done at the standard level of
 * their role: latest start of each task</li>
 * <li>The tasks whose predecessors are scheduled wait in a queue, by latest start</li>
 * <li>For the first task of the queue, each resource of its role having its required skill gives the earliest
 * end it can reach: in a period of its availability with Max Units over 0%, when it is free (between its tasks
 * if there is room), lasting work / Max Units. The earliest end is chosen; for the same end, the resource with
 * fewer skills, keeping the others for the tasks only they can do</li>
 * </ol>
 * A task kept on its resources only gets dates. The dates are calculated with the working time of the
 * calendars. The links are those of ProjectLibre, a summary task being represented by points (see AutoTask).
 * About n log n for n tasks, times the resources of a role.
 */
public class AutoScheduler {
	private static final int MAX_KEPT_ROUNDS = 1000;

	/** Circular links: the tasks cannot be ordered */
	public static class CircularLinksException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final List<AutoTask> tasks;
		CircularLinksException(List<AutoTask> tasks) {
			super("Circular links"); //$NON-NLS-1$
			this.tasks = tasks;
		}
		/** Tasks on the circular links or after them */
		public List<AutoTask> getTasks() {
			return tasks;
		}
	}

	private static final Comparator<AutoTask> PRIORITY = new Comparator<AutoTask>() {
		public int compare(AutoTask a, AutoTask b) {
			if (a.latestStart != b.latestStart)
				return a.latestStart < b.latestStart ? -1 : 1;
			return a.order - b.order;
		}
	};

	private final WorkingTime time;
	private final long projectStart;
	private final long earliest;
	private long end = 0;

	/**
	 * @param time working time of the project, for the links and the tasks without resource
	 * @param projectStart start of the tasks without links, as in ProjectLibre
	 * @param earliest no task assigned by the scheduler starts before this date
	 */
	public AutoScheduler(WorkingTime time, long projectStart, long earliest) {
		this.time = time;
		this.projectStart = projectStart;
		this.earliest = Math.max(earliest, projectStart);
	}

	/**
	 * Schedules the tasks, in the order of the WBS, and fills their results. The resources are only the ones
	 * given: none is added
	 * @throws CircularLinksException if the links are circular
	 */
	public void schedule(List<AutoTask> tasks, List<AutoResource> resources) throws CircularLinksException {
		Map<String, List<AutoResource>> byRole = new HashMap<String, List<AutoResource>>();
		for (int i = 0; i < resources.size(); i++) {
			AutoResource resource = resources.get(i);
			resource.index = i;
			resource.clearBusy();
			if (resource.getRole() == null)
				continue;
			String role = resource.getRole().toUpperCase();
			List<AutoResource> list = byRole.get(role);
			if (list == null) {
				list = new ArrayList<AutoResource>();
				byRole.put(role, list);
			}
			list.add(resource);
		}
		for (int i = 0; i < tasks.size(); i++)
			tasks.get(i).order = i;
		List<AutoTask> sorted = sort(tasks);
		calculateLatestStarts(sorted);
		for (AutoTask task : tasks)
			if (task.kind == AutoTask.FIXED && task.keptResources != null)
				for (AutoResource resource : task.keptResources)
					resource.occupy(task.fixedStart, task.fixedEnd);

		PriorityQueue<AutoTask> queue = new PriorityQueue<AutoTask>(Math.max(1, tasks.size()), PRIORITY);
		Deque<AutoTask> immediate = new ArrayDeque<AutoTask>(); // tasks without choice of resource
		for (AutoTask task : tasks) {
			task.waiting = task.predecessors.size();
			if (task.waiting == 0)
				(task.kind == AutoTask.WORK ? queue : immediate).add(task);
		}
		end = 0;
		while (!immediate.isEmpty() || !queue.isEmpty()) {
			AutoTask task = immediate.isEmpty() ? queue.poll() : immediate.poll();
			place(task, byRole);
			if (task.kind != AutoTask.SUMMARY_START && task.kind != AutoTask.SUMMARY_END)
				end = Math.max(end, task.end);
			for (AutoTask.Link link : task.successors) {
				AutoTask successor = link.successor;
				if (--successor.waiting == 0)
					(successor.kind == AutoTask.WORK ? queue : immediate).add(successor);
			}
		}
	}

	/** End of the last task scheduled */
	public long getEnd() {
		return end;
	}

	/** Tasks in the order of the links */
	private static List<AutoTask> sort(List<AutoTask> tasks) {
		List<AutoTask> sorted = new ArrayList<AutoTask>(tasks.size());
		Deque<AutoTask> ready = new ArrayDeque<AutoTask>();
		for (AutoTask task : tasks) {
			task.waiting = task.predecessors.size();
			if (task.waiting == 0)
				ready.add(task);
		}
		while (!ready.isEmpty()) {
			AutoTask task = ready.poll();
			sorted.add(task);
			for (AutoTask.Link link : task.successors)
				if (--link.successor.waiting == 0)
					ready.add(link.successor);
		}
		if (sorted.size() < tasks.size()) {
			List<AutoTask> circular = new ArrayList<AutoTask>();
			for (AutoTask task : tasks)
				if (task.waiting > 0 && task.kind != AutoTask.SUMMARY_START && task.kind != AutoTask.SUMMARY_END)
					circular.add(task);
			throw new CircularLinksException(circular);
		}
		return sorted;
	}

	/** Critical path in working time from 0, without resources: the latest start of each task is its priority */
	private void calculateLatestStarts(List<AutoTask> sorted) {
		long projectEnd = 0;
		for (AutoTask task : sorted) {
			if (task.kind == AutoTask.SUMMARY_END) {
				task.cpmStart = task.predecessors.isEmpty() ? 0 : Long.MAX_VALUE;
				task.cpmEnd = 0;
				for (AutoTask.Link link : task.predecessors) {
					task.cpmStart = Math.min(task.cpmStart, link.predecessor.cpmStart);
					task.cpmEnd = Math.max(task.cpmEnd, link.predecessor.cpmEnd);
				}
				continue;
			}
			long duration = cpmDuration(task);
			long start = 0;
			for (AutoTask.Link link : task.predecessors) {
				AutoTask predecessor = link.predecessor;
				long lag = Duration.millis(link.lag);
				switch (link.type) {
				case DependencyType.SS:
					start = Math.max(start, predecessor.cpmStart + lag);
					break;
				case DependencyType.FF:
					start = Math.max(start, predecessor.cpmEnd + lag - duration);
					break;
				case DependencyType.SF:
					start = Math.max(start, predecessor.cpmStart + lag - duration);
					break;
				default:
					start = Math.max(start, predecessor.cpmEnd + lag);
				}
			}
			task.cpmStart = start;
			task.cpmEnd = start + duration;
			projectEnd = Math.max(projectEnd, task.cpmEnd);
		}
		for (int i = sorted.size() - 1; i >= 0; i--) {
			AutoTask task = sorted.get(i);
			long duration = task.kind == AutoTask.SUMMARY_END ? task.cpmEnd - task.cpmStart : cpmDuration(task);
			long latestEnd = projectEnd;
			for (AutoTask.Link link : task.successors) {
				AutoTask successor = link.successor;
				long lag = Duration.millis(link.lag);
				switch (link.type) {
				case AutoTask.MEMBER:
					latestEnd = Math.min(latestEnd, successor.latestEnd);
					break;
				case DependencyType.SS:
					latestEnd = Math.min(latestEnd, successor.latestStart - lag + duration);
					break;
				case DependencyType.FF:
					latestEnd = Math.min(latestEnd, successor.latestEnd - lag);
					break;
				case DependencyType.SF:
					latestEnd = Math.min(latestEnd, successor.latestEnd - lag + duration);
					break;
				default:
					latestEnd = Math.min(latestEnd, successor.latestStart - lag);
				}
			}
			task.latestEnd = latestEnd;
			task.latestStart = latestEnd - duration;
		}
	}

	private long cpmDuration(AutoTask task) {
		switch (task.kind) {
		case AutoTask.WORK:
			return task.keptResources != null ? task.duration : task.nominalDuration;
		case AutoTask.FIXED:
			return Math.max(0, time.between(task.fixedEnd, task.fixedStart));
		case AutoTask.PASSIVE:
			return task.duration;
		default:
			return 0;
		}
	}

	/** Dates of a task whose predecessors are placed, and its resource for a WORK task */
	private void place(AutoTask task, Map<String, List<AutoResource>> byRole) {
		if (task.kind == AutoTask.SUMMARY_END) {
			task.start = task.predecessors.isEmpty() ? projectStart : Long.MAX_VALUE;
			task.end = task.predecessors.isEmpty() ? projectStart : 0;
			for (AutoTask.Link link : task.predecessors) {
				task.start = Math.min(task.start, link.predecessor.start);
				task.end = Math.max(task.end, link.predecessor.end);
			}
			task.begin = task.start;
			return;
		}
		long begin = 0, minEnd = 0;
		for (AutoTask.Link link : task.predecessors) {
			AutoTask predecessor = link.predecessor;
			switch (link.type) {
			case DependencyType.SS:
				begin = Math.max(begin, time.add(predecessor.start, link.lag, false));
				break;
			case DependencyType.FF:
				minEnd = Math.max(minEnd, time.add(predecessor.end, link.lag, true));
				break;
			case DependencyType.SF:
				minEnd = Math.max(minEnd, time.add(predecessor.start, link.lag, true));
				break;
			default:
				begin = Math.max(begin, time.add(predecessor.end, link.lag, false));
			}
		}
		if (begin == 0)
			begin = projectStart;
		begin = time.start(Math.max(begin, task.earliestStart));

		switch (task.kind) {
		case AutoTask.FIXED:
			task.begin = task.start = task.fixedStart;
			task.end = task.fixedEnd;
			return;
		case AutoTask.SUMMARY_START:
			task.begin = task.start = task.end = begin;
			return;
		case AutoTask.PASSIVE:
			task.begin = beginForEnd(begin, minEnd, task.duration, time);
			task.start = time.add(task.begin, task.delay, false);
			task.end = time.add(task.start, task.duration, true);
			return;
		default:
			if (task.keptResources != null)
				placeKept(task, begin, minEnd);
			else
				placeWork(task, begin, minEnd, byRole.get(task.role == null ? null : task.role.toUpperCase()));
		}
	}

	/** A WORK task on the resource that ends it first */
	private void placeWork(AutoTask task, long begin, long minEnd, List<AutoResource> candidates) {
		long ready = time.start(Math.max(begin, earliest));
		AutoResource best = null;
		AutoResource.Slot bestSlot = null;
		if (candidates != null)
			for (AutoResource resource : candidates) {
				if (!resource.canDo(task.requiredSkill))
					continue;
				AutoResource.Slot slot = resource.findSlot(ready, task.work, -1, minEnd, false);
				if (slot == null)
					continue;
				if (best == null || slot.end < bestSlot.end
						|| (slot.end == bestSlot.end && resource.getSkillCount() < best.getSkillCount())) {
					best = resource;
					bestSlot = slot;
				}
			}
		if (best == null) {
			task.status = AutoTask.NO_RESOURCE;
			task.resource = null;
			task.units = 0;
			task.begin = beginForEnd(begin, minEnd, task.duration, time);
			task.start = time.start(Math.max(task.begin, earliest));
			task.end = time.add(task.start, task.duration, true);
			return;
		}
		task.status = AutoTask.SCHEDULED;
		task.resource = best;
		task.units = bestSlot.units;
		task.begin = beginForEnd(begin, minEnd, Math.round(task.work / bestSlot.units), best.getTime());
		task.start = bestSlot.start;
		task.end = bestSlot.end;
		best.occupy(task.start, task.end);
	}

	/** A WORK task keeping its resources and its duration: when all of them are available and free */
	private void placeKept(AutoTask task, long begin, long minEnd) {
		task.begin = beginForEnd(begin, minEnd, task.duration, time);
		long start = time.start(Math.max(task.begin, earliest));
		for (int round = 0; round < MAX_KEPT_ROUNDS; round++) {
			boolean moved = false;
			for (AutoResource resource : task.keptResources) {
				AutoResource.Slot slot = resource.findSlot(start, 0, task.duration, 0, false);
				if (slot != null && slot.start > start) {
					start = slot.start;
					moved = true;
				}
			}
			if (!moved)
				break;
		}
		AutoResource first = task.keptResources.isEmpty() ? null : task.keptResources.get(0);
		task.status = AutoTask.KEPT;
		task.resource = first;
		task.units = first == null ? 0 : first.unitsAt(start);
		task.start = start;
		task.end = (first == null ? time : first.getTime()).add(start, task.duration, true);
		for (AutoResource resource : task.keptResources)
			resource.occupy(task.start, task.end);
	}

	/** Start of a task allowed by its links: after begin, and ending after minEnd (0 if none) */
	private static long beginForEnd(long begin, long minEnd, long duration, WorkingTime time) {
		if (minEnd == 0)
			return begin;
		long end = time.add(begin, duration, true);
		return end >= minEnd ? begin : time.add(begin, time.between(minEnd, end), false);
	}
}
