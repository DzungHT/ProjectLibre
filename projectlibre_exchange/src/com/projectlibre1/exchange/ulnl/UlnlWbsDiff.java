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
package com.projectlibre1.exchange.ulnl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projectlibre1.datatype.Duration;
import com.projectlibre1.datatype.TimeUnit;
import com.projectlibre1.grouping.core.Node;
import com.projectlibre1.grouping.core.transform.filtering.TaskNameFilter;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;

/**
 * Differences between the WBS of a project, created from a ULNL, and a new version of the ULNL.
 * <p>
 * The new rows are matched with the tasks: first in the tree, the children of matched parents by name (by activity
 * for the activities), in their order when a name is used more than once. Then the rows left are matched with
 * the tasks left under the same parent with a similar name (renamed rows), and last with the tasks left anywhere
 * that have the same ULNL key (tasks moved by the user). Only tasks created from a ULNL (with a key) are matched,
 * or all the tasks for a project made before the keys.
 * <p>
 * Changes, each can be applied or not:
 * <ul>
 * <li>ESTIMATE: the estimate of an activity changed and its work is still its original estimate: both are updated</li>
 * <li>CONFLICT: the estimate changed but the work was changed by the user: the original estimate is always
 * updated, the work only if the change is applied</li>
 * <li>RENAME, FUNCTION: name, or function code and lane of a row, changed</li>
 * <li>ADDED: new rows, created with the tasks below them</li>
 * <li>REMOVED: tasks no longer in the ULNL, cancelled with a reason</li>
 * <li>RESTORED: cancelled tasks back in the ULNL, un-cancelled</li>
 * </ul>
 * The keys of the matched tasks are always updated, as well as the original estimate of tasks without one whose
 * work is the new estimate.
 */
public class UlnlWbsDiff {
	public static final int ESTIMATE = 0;
	public static final int CONFLICT = 1;
	public static final int RENAME = 2;
	public static final int FUNCTION = 3;
	public static final int ADDED = 4;
	public static final int REMOVED = 5;
	public static final int RESTORED = 6;
	public static final int TYPE_COUNT = 7;

	private static final long ESTIMATE_TOLERANCE = 60 * 1000; // millis
	private static final double SIMILARITY = 0.6; // for renamed rows, 1 for the same names

	public static class Change {
		private final int type;
		private final NormalTask task; // null for ADDED
		private final UlnlWbsParser.WbsItem item; // null for REMOVED
		private boolean apply;
		private int taskCount = 1; // ADDED, REMOVED: tasks of the subtree

		Change(int type, NormalTask task, UlnlWbsParser.WbsItem item, boolean apply) {
			this.type = type;
			this.task = task;
			this.item = item;
			this.apply = apply;
		}
		public int getType() { return type; }
		public NormalTask getTask() { return task; }
		public UlnlWbsParser.WbsItem getItem() { return item; }
		public boolean isApply() { return apply; }
		public void setApply(boolean apply) { this.apply = apply; }
		public int getTaskCount() { return taskCount; }
		/** New estimate of an activity, millis */
		public long getNewEstimate() {
			return item == null ? 0 : Duration.millis(Duration.getInstance(item.getWork(), TimeUnit.HOURS));
		}
	}

	private final List<UlnlWbsParser.WbsItem> roots;
	private final String[] activityCodes;
	private final String[] normalizedActivityNames;
	private final String removedReasonPrefix;
	private final Map<UlnlWbsParser.WbsItem, NormalTask> matches = new IdentityHashMap<UlnlWbsParser.WbsItem, NormalTask>();
	private final Map<UlnlWbsParser.WbsItem, UlnlWbsParser.WbsItem> parents = new IdentityHashMap<UlnlWbsParser.WbsItem, UlnlWbsParser.WbsItem>();
	private final Set<NormalTask> matchedTasks = Collections.newSetFromMap(new IdentityHashMap<NormalTask, Boolean>());
	private final Set<NormalTask> candidates = Collections.newSetFromMap(new IdentityHashMap<NormalTask, Boolean>());
	private final List<Change> changes = new ArrayList<Change>();
	private final List<NormalTask> silentEstimates = new ArrayList<NormalTask>(); // original estimate set without change
	private int unchanged = 0;

	/**
	 * @param activityNames names of the activities, in the order of the codes, to find the activity of the tasks
	 * without code
	 * @param removedReasonPrefix start of the cancel reason of the tasks removed from a ULNL: those are restored
	 * by default when they come back, not the tasks cancelled by the user
	 */
	public static UlnlWbsDiff compare(Project project, List<UlnlWbsParser.WbsItem> roots, String[] activityCodes,
			String[] activityNames, String removedReasonPrefix) {
		UlnlWbsDiff diff = new UlnlWbsDiff(roots, activityCodes, activityNames, removedReasonPrefix);
		diff.compare(project);
		return diff;
	}

	private UlnlWbsDiff(List<UlnlWbsParser.WbsItem> roots, String[] activityCodes, String[] activityNames, String removedReasonPrefix) {
		this.roots = roots;
		this.activityCodes = activityCodes;
		this.removedReasonPrefix = removedReasonPrefix;
		normalizedActivityNames = new String[activityNames.length];
		for (int i = 0; i < activityNames.length; i++)
			normalizedActivityNames[i] = TaskNameFilter.normalize(activityNames[i]);
	}

	private void compare(Project project) {
		List<NormalTask> all = new ArrayList<NormalTask>();
		boolean hasKeys = false;
		for (Iterator i = project.getTaskOutlineIterator(); i.hasNext();) {
			Object impl = i.next();
			if (impl instanceof NormalTask) {
				all.add((NormalTask) impl);
				hasKeys |= ((NormalTask) impl).getUlnlKey() != null;
			}
		}
		for (NormalTask task : all)
			if (!hasKeys || task.getUlnlKey() != null)
				candidates.add(task);
		indexParents(null, roots);

		List<NormalTask> topTasks = new ArrayList<NormalTask>();
		for (NormalTask task : all)
			if (task.getWbsParentTask() == null && candidates.contains(task))
				topTasks.add(task);
		matchChildren(roots, topTasks);
		matchByKey();

		// changes of the matched rows, in the order of the new WBS
		addChanges(roots);
		// tasks no longer in the ULNL: the highest ones
		for (NormalTask task : all) {
			if (!candidates.contains(task) || matchedTasks.contains(task) || task.isCancelled())
				continue;
			NormalTask parent = (NormalTask) task.getWbsParentTask();
			if (parent != null && candidates.contains(parent) && !matchedTasks.contains(parent))
				continue; // removed with its parent
			Change change = new Change(REMOVED, task, null, true);
			change.taskCount = countTasks(task);
			changes.add(change);
		}
	}

	private void indexParents(UlnlWbsParser.WbsItem parent, List<UlnlWbsParser.WbsItem> items) {
		for (UlnlWbsParser.WbsItem item : items) {
			if (parent != null)
				parents.put(item, parent);
			indexParents(item, item.getChildren());
		}
	}

	/** Matches the items with the tasks, same parent: by name or activity, then by similar names */
	private void matchChildren(List<UlnlWbsParser.WbsItem> items, List<NormalTask> tasks) {
		Map<String, Deque<NormalTask>> byName = new HashMap<String, Deque<NormalTask>>();
		for (NormalTask task : tasks) {
			if (matchedTasks.contains(task))
				continue;
			String key = taskMatchKey(task);
			Deque<NormalTask> same = byName.get(key);
			if (same == null) {
				same = new ArrayDeque<NormalTask>();
				byName.put(key, same);
			}
			same.add(task);
		}
		List<UlnlWbsParser.WbsItem> left = new ArrayList<UlnlWbsParser.WbsItem>();
		for (UlnlWbsParser.WbsItem item : items) {
			Deque<NormalTask> same = byName.get(itemMatchKey(item));
			NormalTask task = same == null ? null : same.poll();
			if (task == null)
				left.add(item);
			else
				match(item, task);
		}
		for (UlnlWbsParser.WbsItem item : left) { // renamed rows
			if (item.isActivity())
				continue;
			NormalTask best = null;
			double bestSimilarity = SIMILARITY;
			String name = normalize(item.getName());
			for (NormalTask task : tasks) {
				if (matchedTasks.contains(task) || activityIndex(task) >= 0)
					continue;
				if (item.getLevel() == 4 && item.getFunctionCode() != null && task.getFunctionCode() != null
						&& !item.getFunctionCode().equals(task.getFunctionCode()))
					continue;
				double similarity = similarity(name, normalize(task.getName()));
				if (similarity >= bestSimilarity) {
					best = task;
					bestSimilarity = similarity;
				}
			}
			if (best != null)
				match(item, best);
		}
		for (UlnlWbsParser.WbsItem item : items) {
			NormalTask task = matches.get(item);
			if (task != null && !item.getChildren().isEmpty())
				matchChildren(item.getChildren(), children(task));
		}
	}

	/** Rows left matched with the tasks left that have their key: tasks moved by the user */
	private void matchByKey() {
		Map<String, NormalTask> byKey = new HashMap<String, NormalTask>();
		for (NormalTask task : candidates)
			if (!matchedTasks.contains(task) && task.getUlnlKey() != null)
				byKey.put(task.getUlnlKey(), task);
		if (byKey.isEmpty())
			return;
		List<UlnlWbsParser.WbsItem> items = new ArrayList<UlnlWbsParser.WbsItem>();
		collect(roots, items);
		for (UlnlWbsParser.WbsItem item : items) {
			if (matches.containsKey(item))
				continue;
			NormalTask task = byKey.remove(item.getKey());
			if (task != null && !matchedTasks.contains(task)) {
				match(item, task);
				matchChildren(item.getChildren(), children(task));
			}
		}
	}

	private void match(UlnlWbsParser.WbsItem item, NormalTask task) {
		matches.put(item, task);
		matchedTasks.add(task);
	}

	private void addChanges(List<UlnlWbsParser.WbsItem> items) {
		for (UlnlWbsParser.WbsItem item : items) {
			NormalTask task = matches.get(item);
			if (task == null) {
				Change change = new Change(ADDED, null, item, true);
				change.taskCount = countItems(item);
				changes.add(change);
				continue; // with the items below it
			}
			boolean changed = false;
			if (task.isCancelled()) {
				UlnlWbsParser.WbsItem parent = parents.get(item);
				NormalTask parentTask = parent == null ? null : matches.get(parent);
				if (parentTask == null || !parentTask.isCancelled()) { // else restored with its parent
					String reason = task.getCancelReason();
					changes.add(new Change(RESTORED, task, item, reason != null && removedReasonPrefix != null && reason.startsWith(removedReasonPrefix)));
				}
				changed = true;
			}
			if (!item.isActivity() && !item.getName().equals(task.getName() == null ? null : task.getName().trim())) {
				changes.add(new Change(RENAME, task, item, true));
				changed = true;
			}
			if (item.getLevel() == 4 && (!same(item.getFunctionCode(), task.getFunctionCode()) || !same(item.getLane(), task.getLane()))) {
				changes.add(new Change(FUNCTION, task, item, true));
				changed = true;
			}
			if (item.isActivity()) {
				long estimate = Duration.millis(Duration.getInstance(item.getWork(), TimeUnit.HOURS));
				long work = Duration.millis(task.getWork(null));
				if (task.getOriginalEstimate() == 0) { // project made before the original estimates
					if (Math.abs(work - estimate) < ESTIMATE_TOLERANCE)
						silentEstimates.add(task);
					else {
						changes.add(new Change(task.isCancelled() ? ESTIMATE : CONFLICT, task, item, task.isCancelled()));
						changed = true;
					}
				} else if (Math.abs(task.getOriginalEstimate() - estimate) >= ESTIMATE_TOLERANCE) {
					boolean conflict = !task.isCancelled() && !task.isWorkOriginalEstimate(work);
					changes.add(new Change(conflict ? CONFLICT : ESTIMATE, task, item, !conflict));
					changed = true;
				}
			}
			if (!changed)
				unchanged++;
			addChanges(item.getChildren());
		}
	}

	private String taskMatchKey(NormalTask task) {
		int activity = activityIndex(task);
		return activity >= 0 ? "A:" + activityCodes[activity] : "N:" + (task.getName() == null ? "" : task.getName().trim()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
	private static String itemMatchKey(UlnlWbsParser.WbsItem item) {
		return item.isActivity() ? "A:" + item.getActivityCode() : "N:" + item.getName(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Activity of a leaf task: its code, or found from its name. -1 if none */
	private int activityIndex(NormalTask task) {
		if (task.isWbsParent())
			return -1;
		if (task.getActivity() != null) {
			for (int i = 0; i < activityCodes.length; i++)
				if (activityCodes[i].equals(task.getActivity()))
					return i;
			return -1;
		}
		String name = normalize(task.getName());
		for (int i = 0; i < normalizedActivityNames.length; i++)
			if (name.startsWith(normalizedActivityNames[i]))
				return i;
		return -1;
	}

	private List<NormalTask> children(NormalTask task) {
		List<NormalTask> list = new ArrayList<NormalTask>();
		Collection nodes = task.getWbsChildrenNodes();
		if (nodes != null)
			for (Iterator i = nodes.iterator(); i.hasNext();) {
				Object impl = ((Node) i.next()).getImpl();
				if (impl instanceof NormalTask && candidates.contains(impl))
					list.add((NormalTask) impl);
			}
		return list;
	}

	private static int countTasks(NormalTask task) {
		int n = 1;
		Collection nodes = task.getWbsChildrenNodes();
		if (nodes != null)
			for (Iterator i = nodes.iterator(); i.hasNext();) {
				Object impl = ((Node) i.next()).getImpl();
				if (impl instanceof NormalTask)
					n += countTasks((NormalTask) impl);
			}
		return n;
	}
	private static int countItems(UlnlWbsParser.WbsItem item) {
		int n = 1;
		for (UlnlWbsParser.WbsItem child : item.getChildren())
			n += countItems(child);
		return n;
	}
	private static void collect(List<UlnlWbsParser.WbsItem> items, List<UlnlWbsParser.WbsItem> all) {
		for (UlnlWbsParser.WbsItem item : items) {
			all.add(item);
			collect(item.getChildren(), all);
		}
	}

	private static boolean same(String a, String b) {
		return a == null ? b == null : a.equals(b);
	}
	private static String normalize(String s) {
		return s == null ? "" : TaskNameFilter.normalize(s.trim()).replaceAll("\\s+", " "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	/** 1 - edit distance / length of the longest */
	static double similarity(String a, String b) {
		int max = Math.max(a.length(), b.length());
		if (max == 0)
			return 1;
		int[] previous = new int[b.length() + 1];
		int[] current = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++)
			previous[j] = j;
		for (int i = 1; i <= a.length(); i++) {
			current[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		return 1 - ((double) previous[b.length()]) / max;
	}

	public List<Change> getChanges() {
		return changes;
	}
	/** Number of changes of each type */
	public int[] countByType() {
		int[] counts = new int[TYPE_COUNT];
		for (Change change : changes)
			counts[change.getType()]++;
		return counts;
	}
	/** Matched rows without change */
	public int getUnchanged() {
		return unchanged;
	}
	public List<UlnlWbsParser.WbsItem> getRoots() {
		return roots;
	}
	/** Task matched with a row, null if the row is new */
	public NormalTask getTask(UlnlWbsParser.WbsItem item) {
		return matches.get(item);
	}
	/** Parent of a row, null at the top */
	public UlnlWbsParser.WbsItem getParent(UlnlWbsParser.WbsItem item) {
		return parents.get(item);
	}
	/** All the matched rows and their tasks */
	public Map<UlnlWbsParser.WbsItem, NormalTask> getMatches() {
		return Collections.unmodifiableMap(matches);
	}
	/** Tasks without original estimate whose work is the new estimate: their original estimate is set */
	public List<NormalTask> getSilentEstimates() {
		return silentEstimates;
	}
	/** True if nothing needs to be done, not even updating the keys */
	public boolean isEmpty() {
		if (!changes.isEmpty() || !silentEstimates.isEmpty())
			return false;
		for (Map.Entry<UlnlWbsParser.WbsItem, NormalTask> entry : matches.entrySet())
			if (!same(entry.getKey().getKey(), entry.getValue().getUlnlKey()))
				return false;
		return true;
	}
	/** The changes of a type, in their order */
	public List<Change> getChanges(int type) {
		List<Change> list = new ArrayList<Change>();
		for (Change change : changes)
			if (change.getType() == type)
				list.add(change);
		return list;
	}
}
