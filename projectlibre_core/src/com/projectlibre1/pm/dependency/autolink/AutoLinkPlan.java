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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projectlibre1.datatype.Duration;
import com.projectlibre1.pm.assignment.Assignment;
import com.projectlibre1.pm.dependency.Dependency;
import com.projectlibre1.pm.dependency.DependencyType;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.pm.task.Task;

/**
 * The links Auto Link creates and removes, see AutoLinkOptions. Only the tasks with an activity are linked, in the
 * whole project or below some tasks. The links Auto Link created before between these tasks are removed, unless
 * they are created again. A link is not created if the same link already exists.
 * Before applying, the plan is checked for circular links, which can come from links made by hand.
 */
public class AutoLinkPlan {
	/** A link to create, finish to start */
	public static class Link {
		private final NormalTask predecessor;
		private final NormalTask successor;
		Link(NormalTask predecessor, NormalTask successor) {
			this.predecessor = predecessor;
			this.successor = successor;
		}
		public NormalTask getPredecessor() {
			return predecessor;
		}
		public NormalTask getSuccessor() {
			return successor;
		}
	}

	private final List<Link> toCreate = new ArrayList<Link>();
	private final List<Dependency> toRemove = new ArrayList<Dependency>();
	private int kept = 0; // links of Auto Link created again
	private int existing = 0; // links made by hand that Auto Link would create
	private int tasks = 0; // linked tasks
	private List<Task> circularTasks = Collections.emptyList();

	/**
	 * @param roots the tasks below which (included) tasks are linked, null for the whole project
	 */
	public static AutoLinkPlan create(Project project, Collection<? extends Task> roots, AutoLinkOptions options) {
		AutoLinkPlan plan = new AutoLinkPlan();
		plan.build(project, roots, options);
		return plan;
	}

	private void build(Project project, Collection<? extends Task> roots, AutoLinkOptions options) {
		Set<Task> rootSet = roots == null ? null : identitySet(roots);
		Set<NormalTask> scope = identitySet(Collections.<NormalTask> emptyList()); // tasks with an activity in the scope
		List<NormalTask> linked = new ArrayList<NormalTask>(); // the ones that are linked, in the order of the WBS
		for (Iterator i = project.getTaskOutlineIterator(); i.hasNext();) {
			Object impl = i.next();
			if (!(impl instanceof NormalTask))
				continue;
			NormalTask task = (NormalTask) impl;
			if (task.isWbsParent() || options.activityIndex(task) < 0)
				continue;
			if (rootSet != null && !isBelow(task, rootSet))
				continue;
			scope.add(task);
			if (!task.isCancelled() && Duration.millis(task.getWork(null)) > 0)
				linked.add(task);
		}
		tasks = linked.size();

		// the links wanted: predecessors of each successor
		Map<NormalTask, Set<NormalTask>> wanted = new LinkedHashMap<NormalTask, Set<NormalTask>>();
		if (options.isLinkActivities())
			linkActivities(linked, options, wanted);
		if (options.isLinkSequences())
			linkSequences(linked, options, wanted);

		// existing links between the tasks of the scope
		for (NormalTask successor : scope) {
			Set<NormalTask> predecessors = wanted.get(successor);
			for (Iterator j = successor.getPredecessorList().iterator(); j.hasNext();) {
				Dependency dependency = (Dependency) j.next();
				Object predecessor = dependency.getPredecessor();
				boolean isWanted = predecessors != null && predecessors.remove(predecessor);
				if (dependency.isAuto()) {
					if (isWanted)
						kept++;
					else if (scope.contains(predecessor))
						toRemove.add(dependency);
				} else if (isWanted)
					existing++;
			}
		}
		for (Map.Entry<NormalTask, Set<NormalTask>> entry : wanted.entrySet())
			for (NormalTask predecessor : entry.getValue())
				toCreate.add(new Link(predecessor, entry.getKey()));

		circularTasks = findCircularTasks(project);
	}

	/** Each task after the tasks of the previous activity in its group, see AutoLinkOptions */
	private static void linkActivities(List<NormalTask> linked, AutoLinkOptions options, Map<NormalTask, Set<NormalTask>> wanted) {
		int count = options.getActivities().length;
		// tasks of each activity, by group, for each scope used
		Map<Integer, Map<Task, List<NormalTask>[]>> groupsByScope = new HashMap<Integer, Map<Task, List<NormalTask>[]>>();
		for (int a = 1; a < count; a++)
			groupsByScope.put(options.getScope(a), new IdentityHashMap<Task, List<NormalTask>[]>());
		for (Map.Entry<Integer, Map<Task, List<NormalTask>[]>> entry : groupsByScope.entrySet()) {
			for (NormalTask task : linked) {
				Task group = ancestor(task, entry.getKey());
				List<NormalTask>[] byActivity = entry.getValue().get(group);
				if (byActivity == null) {
					byActivity = new List[count];
					entry.getValue().put(group, byActivity);
				}
				int a = options.activityIndex(task);
				if (byActivity[a] == null)
					byActivity[a] = new ArrayList<NormalTask>();
				byActivity[a].add(task);
			}
		}
		for (NormalTask task : linked) {
			int a = options.activityIndex(task);
			if (a == 0)
				continue;
			List<NormalTask>[] byActivity = groupsByScope.get(options.getScope(a)).get(ancestor(task, options.getScope(a)));
			for (int previous = a - 1; previous >= 0; previous--) {
				if (byActivity[previous] == null)
					continue;
				for (NormalTask predecessor : byActivity[previous])
					want(predecessor, task, wanted);
				break;
			}
		}
	}

	/** Tasks of an activity one after the other: by resource for the assigned ones, else by activity or lane */
	private static void linkSequences(List<NormalTask> linked, AutoLinkOptions options, Map<NormalTask, Set<NormalTask>> wanted) {
		Map<List<Object>, NormalTask> last = new HashMap<List<Object>, NormalTask>();
		for (NormalTask task : linked) {
			for (List<Object> key : sequenceKeys(task, options)) {
				NormalTask previous = last.put(key, task);
				if (previous != null)
					want(previous, task, wanted);
			}
		}
	}

	private static List<List<Object>> sequenceKeys(NormalTask task, AutoLinkOptions options) {
		String activity = options.getActivities()[options.activityIndex(task)];
		List<List<Object>> keys = new ArrayList<List<Object>>();
		for (Iterator i = task.getAssignments().iterator(); i.hasNext();) {
			Assignment assignment = (Assignment) i.next();
			if (!assignment.isDefault() && assignment.isLabor())
				keys.add(Arrays.<Object> asList(activity, "resource", assignment.getResource())); //$NON-NLS-1$
		}
		if (keys.isEmpty()) {
			String lane = options.isLaneActivity(activity) ? task.getLane() : null;
			keys.add(Arrays.<Object> asList(activity, "lane", lane)); //$NON-NLS-1$
		}
		return keys;
	}

	private static void want(NormalTask predecessor, NormalTask successor, Map<NormalTask, Set<NormalTask>> wanted) {
		if (predecessor == successor)
			return;
		Set<NormalTask> predecessors = wanted.get(successor);
		if (predecessors == null) {
			predecessors = new LinkedHashSet<NormalTask>();
			wanted.put(successor, predecessors);
		}
		predecessors.add(predecessor);
	}

	/** Group of a task: its parent for ROW, the parent of its parent for FUNCTION. The highest task if there is none */
	private static Task ancestor(Task task, int levels) {
		Task group = task;
		for (int l = 0; l < levels; l++) {
			Task parent = group.getWbsParentTask();
			if (parent == null)
				break;
			group = parent;
		}
		return group;
	}

	private static boolean isBelow(Task task, Set<Task> roots) {
		for (Task t = task; t != null; t = t.getWbsParentTask())
			if (roots.contains(t))
				return true;
		return false;
	}

	private static <T> Set<T> identitySet(Collection<? extends T> items) {
		Set<T> set = Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
		set.addAll(items);
		return set;
	}

	/**
	 * Tasks on circular links once the plan is applied, empty if there are none. Each task has a start and an
	 * end, a summary starts before and ends after its children, links go from the end or the start of their
	 * predecessor to the start or the end of their successor. The graph is sorted: what cannot be sorted is circular
	 */
	private List<Task> findCircularTasks(Project project) {
		Map<Task, Integer> index = new IdentityHashMap<Task, Integer>();
		List<Task> all = new ArrayList<Task>();
		for (Iterator i = project.getTaskOutlineIterator(); i.hasNext();) {
			Object impl = i.next();
			if (impl instanceof Task && !index.containsKey(impl)) {
				index.put((Task) impl, all.size());
				all.add((Task) impl);
			}
		}
		int n = all.size();
		List<int[]> edges = new ArrayList<int[]>(); // node 2 * t is the start of the task t, 2 * t + 1 its end
		Set<Dependency> removed = identitySet(toRemove);
		for (int t = 0; t < n; t++) {
			Task task = all.get(t);
			edges.add(new int[] { 2 * t, 2 * t + 1 });
			Task parent = task.getWbsParentTask();
			Integer p = parent == null ? null : index.get(parent);
			if (p != null) {
				edges.add(new int[] { 2 * p, 2 * t });
				edges.add(new int[] { 2 * t + 1, 2 * p + 1 });
			}
			for (Iterator j = task.getPredecessorList().iterator(); j.hasNext();) {
				Dependency dependency = (Dependency) j.next();
				Integer from = index.get(dependency.getPredecessor());
				if (from == null || removed.contains(dependency) || dependency.isDisabled())
					continue;
				edges.add(link(from, t, dependency.getDependencyType()));
			}
		}
		for (Link link : toCreate)
			edges.add(link(index.get(link.getPredecessor()), index.get(link.getSuccessor()), DependencyType.FS));

		int[] inDegree = new int[2 * n];
		int[] outCount = new int[2 * n];
		for (int[] edge : edges) {
			outCount[edge[0]]++;
			inDegree[edge[1]]++;
		}
		int[][] out = new int[2 * n][];
		for (int v = 0; v < 2 * n; v++)
			out[v] = new int[outCount[v]];
		int[] filled = new int[2 * n];
		for (int[] edge : edges)
			out[edge[0]][filled[edge[0]]++] = edge[1];
		Deque<Integer> ready = new ArrayDeque<Integer>();
		for (int v = 0; v < 2 * n; v++)
			if (inDegree[v] == 0)
				ready.add(v);
		int sorted = 0;
		while (!ready.isEmpty()) {
			int v = ready.poll();
			sorted++;
			for (int w : out[v])
				if (--inDegree[w] == 0)
					ready.add(w);
		}
		if (sorted == 2 * n)
			return Collections.emptyList();
		List<Task> circular = new ArrayList<Task>();
		for (int t = 0; t < n; t++)
			if ((inDegree[2 * t] > 0 || inDegree[2 * t + 1] > 0) && !all.get(t).isWbsParent())
				circular.add(all.get(t));
		return circular;
	}

	private static int[] link(int from, int to, int type) {
		switch (type) {
		case DependencyType.SS:
			return new int[] { 2 * from, 2 * to };
		case DependencyType.FF:
			return new int[] { 2 * from + 1, 2 * to + 1 };
		case DependencyType.SF:
			return new int[] { 2 * from, 2 * to + 1 };
		default:
			return new int[] { 2 * from + 1, 2 * to };
		}
	}

	public List<Link> getToCreate() {
		return toCreate;
	}
	public List<Dependency> getToRemove() {
		return toRemove;
	}
	/** Links of Auto Link that stay */
	public int getKept() {
		return kept;
	}
	/** Links made by hand that Auto Link would create, and that stay */
	public int getExisting() {
		return existing;
	}
	/** Number of tasks linked: with an activity, not cancelled and with work */
	public int getTaskCount() {
		return tasks;
	}
	/** Leaf tasks on circular links if the plan is applied, empty if there are none */
	public List<Task> getCircularTasks() {
		return circularTasks;
	}
	public boolean isEmpty() {
		return toCreate.isEmpty() && toRemove.isEmpty();
	}
}
