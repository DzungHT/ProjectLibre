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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projectlibre1.pm.availability.AvailabilityWarning;
import com.projectlibre1.pm.dependency.autolink.AutoLinkOptions;
import com.projectlibre1.pm.dependency.autolink.AutoLinkPlan;
import com.projectlibre1.pm.dependency.autolink.AutoLinker;
import com.projectlibre1.pm.resource.Resource;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;

/**
 * Assigns the tasks selected in the Gantt to a person or a group, with the rules of the Assignment board: a task selected goes
 * to the person, below a summary selected only the tasks of the role and skill of the person. A group shares each task
 * between its people of the role and skill of the task, by their Max Units. Then the tasks of each
 * person are linked again by Auto Link, in the same undo step, so that the Gantt shows at once when the module is
 * done. See docs/assignment-design.md section 9.
 */
public class GanttAssigner {
	private final AssignmentBoard board;
	/** The tasks of the activities selected or below a summary selected, in the order of the WBS */
	private final List<AssignmentBoard.Row> selected = new ArrayList<AssignmentBoard.Row>();
	private final Set<AssignmentBoard.Row> belowSummary = Collections.newSetFromMap(new IdentityHashMap<AssignmentBoard.Row, Boolean>());
	private final List<AssignmentBoard.Row> summaries = new ArrayList<AssignmentBoard.Row>();

	/** Times the people are chosen again from the new dates, see apply() */
	private static final int MAX_ROUNDS = 6;

	/** What a call of apply() did */
	public static class Result {
		int assigned, kept, circular, left, waiting, rounds;
		boolean linked, settled;
		long finishBefore, finishAfter;
		/** Tasks of a group without some of its people, not available during the task */
		public int getLeft() { return left; }
		/** Tasks of a group without anyone available, given to the ones available the soonest */
		public int getWaiting() { return waiting; }
		/** Times the people were written */
		public int getRounds() { return rounds; }
		/** False if the people still changed with the dates after the last time */
		public boolean isSettled() { return settled; }
		/** Tasks whose people were written */
		public int getAssigned() { return assigned; }
		/** Locked tasks kept */
		public int getKept() { return kept; }
		/** True if the tasks of the people were linked again */
		public boolean isLinked() { return linked; }
		/** Tasks of circular links when the links could not be made again, 0 if none */
		public int getCircular() { return circular; }
		public long getFinishBefore() { return finishBefore; }
		public long getFinishAfter() { return finishAfter; }
	}

	/** @param selection the tasks selected in the Gantt, any object else is ignored */
	public GanttAssigner(AssignmentBoard board, Collection<?> selection) {
		this.board = board;
		Set<Object> tasks = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
		if (selection != null)
			tasks.addAll(selection);
		for (AssignmentBoard.Row row : board.getActivities()) {
			boolean below = false;
			for (AssignmentBoard.Row parent = row.getParent(); parent != null && !below; parent = parent.getParent())
				below = tasks.contains(parent.getTask());
			if (below)
				belowSummary.add(row);
			if (below || tasks.contains(row.getTask()))
				selected.add(row);
		}
		List<AssignmentBoard.Row> rows = new ArrayList<AssignmentBoard.Row>(board.getRoots());
		for (int i = 0; i < rows.size(); i++) {
			AssignmentBoard.Row row = rows.get(i);
			if (row.isActivity())
				continue;
			if (tasks.contains(row.getTask()))
				summaries.add(row);
			rows.addAll(row.getChildren());
		}
	}

	public AssignmentBoard getBoard() {
		return board;
	}

	/** The summaries selected, with tasks of activities below them */
	public List<AssignmentBoard.Row> getSummaries() {
		return summaries;
	}

	/** The tasks of the activities selected, and below the summaries selected */
	public List<AssignmentBoard.Row> getSelected() {
		return selected;
	}


	/**
	 * The people of a group that each task selected gets: the ones of its role and skill. A task selected itself, not
	 * below a summary selected, goes to a single person even if it is not of its role (the caller asks first). The
	 * tasks no one of the group can do are not in the result, and keep their people
	 */
	public Map<AssignmentBoard.Row, List<Resource>> tasksFor(List<AssignmentBoard.Person> group) {
		Map<AssignmentBoard.Row, List<Resource>> result = new LinkedHashMap<AssignmentBoard.Row, List<Resource>>();
		for (AssignmentBoard.Row row : selected) {
			List<Resource> people = new ArrayList<Resource>();
			for (AssignmentBoard.Person person : group)
				if (AssignmentBoard.canDo(person, row))
					people.add(person.getResource());
			if (people.isEmpty() && group.size() == 1 && !belowSummary.contains(row))
				people.add(group.get(0).getResource());
			if (!people.isEmpty())
				result.put(row, people);
		}
		return result;
	}

	/** True if a person can do all the tasks selected that would go to it alone, and at least one */
	public boolean canDo(AssignmentBoard.Person person) {
		Set<AssignmentBoard.Row> tasks = tasksFor(Collections.singletonList(person)).keySet();
		for (AssignmentBoard.Row row : tasks)
			if (!AssignmentBoard.canDo(person, row))
				return false;
		return !tasks.isEmpty();
	}

	/**
	 * Gives tasks to their people, an empty list removes them, and writes them in the project. The work of a task is
	 * shared by ProjectLibre between its people by their units. Then, if relink is not null, links again the tasks of
	 * the people of the whole project with these options: a task of a group is in the sequence of each of its people.
	 * <p>
	 * With byAvailability, a task of several people gets only the ones available (Max Units over 0%) from its start to
	 * its finish; if none is, the ones available the soonest after its start, and it waits for them. As the dates
	 * change with the people and the links, this is done again from the new dates until the people stay the same, at
	 * most MAX_ROUNDS times. A single undo step.
	 * @param people tasks of the board and their people
	 */
	public Result apply(Map<AssignmentBoard.Row, List<Resource>> people, AutoLinkOptions relink, boolean byAvailability, Object eventSource) {
		Result result = new Result();
		Project project = board.getProject();
		result.finishBefore = project.getEnd();
		Map<NormalTask, List<Resource>> candidates = new LinkedHashMap<NormalTask, List<Resource>>();
		for (Map.Entry<AssignmentBoard.Row, List<Resource>> entry : people.entrySet())
			candidates.put(entry.getKey().getTask(), entry.getValue());
		Set<NormalTask> changed = Collections.newSetFromMap(new IdentityHashMap<NormalTask, Boolean>());
		project.beginUndoUpdate();
		try {
			AssignmentBoard current = board;
			for (int round = 0; round < MAX_ROUNDS; round++) {
				if (round > 0)
					current = AssignmentBoard.create(project); // the dates and the people written
				Map<NormalTask, AssignmentBoard.Row> rows = new IdentityHashMap<NormalTask, AssignmentBoard.Row>();
				for (AssignmentBoard.Row row : current.getActivities())
					rows.put(row.getTask(), row);
				int left = 0, waiting = 0, kept = 0;
				for (Map.Entry<NormalTask, List<Resource>> entry : candidates.entrySet()) {
					AssignmentBoard.Row row = rows.get(entry.getKey());
					if (row == null)
						continue;
					List<Resource> chosen = entry.getValue();
					if (byAvailability && chosen.size() > 1) {
						List<Resource> available = available(chosen, row.getTask());
						if (available.isEmpty()) {
							available = soonest(chosen, row.getTask().getStart());
							waiting++;
						}
						if (available.size() < chosen.size())
							left++;
						chosen = available;
					}
					if (new LinkedHashSet<Resource>(chosen).equals(new LinkedHashSet<Resource>(row.getResources())))
						continue;
					if (current.setPeople(row, chosen))
						changed.add(row.getTask());
					else
						kept++;
				}
				result.left = left;
				result.waiting = waiting;
				if (round == 0)
					result.kept = kept;
				current.calculate();
				if (round > 0 && current.getChangedRows().isEmpty()) {
					result.settled = true;
					break;
				}
				result.rounds = round + 1;
				AssignmentBoardWriter.apply(current, eventSource); // its schedule is calculated: the plan reads the new durations
				if (relink != null) {
					AutoLinkPlan plan = AutoLinkPlan.create(project, null, relink);
					result.circular = plan.getCircularTasks().size();
					if (result.circular > 0)
						break;
					if (!plan.isEmpty())
						AutoLinker.apply(project, plan, eventSource);
					result.linked = true;
				}
				if (!byAvailability) {
					result.settled = true;
					break;
				}
			}
		} finally {
			project.endUndoUpdate();
		}
		result.assigned = changed.size();
		result.finishAfter = project.getEnd();
		return result;
	}

	/** The people available during a task, from its start to its finish */
	private static List<Resource> available(List<Resource> people, NormalTask task) {
		List<Resource> result = new ArrayList<Resource>();
		for (Resource resource : people)
			if (AvailabilityWarning.isAvailable(resource, task.getStart(), task.getEnd()))
				result.add(resource);
		return result;
	}

	/** The people available the soonest from a date, all of them if none ever is */
	private static List<Resource> soonest(List<Resource> people, long date) {
		long first = AvailabilityWarning.NEVER;
		for (Resource resource : people)
			first = Math.min(first, AvailabilityWarning.availableFrom(resource, date));
		if (first == AvailabilityWarning.NEVER)
			return people;
		List<Resource> result = new ArrayList<Resource>();
		for (Resource resource : people)
			if (AvailabilityWarning.availableFrom(resource, date) == first)
				result.add(resource);
		return result;
	}
}
