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
import java.util.List;
import java.util.Set;

import com.projectlibre1.pm.dependency.autolink.AutoLinkOptions;
import com.projectlibre1.pm.dependency.autolink.AutoLinkPlan;
import com.projectlibre1.pm.dependency.autolink.AutoLinker;
import com.projectlibre1.pm.resource.Resource;
import com.projectlibre1.pm.task.Project;

/**
 * Assigns the tasks selected in the Gantt to a person, with the rules of the Assignment board: a task selected goes
 * to the person, below a summary selected only the tasks of the role and skill of the person. Then the tasks of each
 * person are linked again by Auto Link, in the same undo step, so that the Gantt shows at once when the module is
 * done. See docs/assignment-design.md section 9.
 */
public class GanttAssigner {
	private final AssignmentBoard board;
	/** The tasks of the activities selected or below a summary selected, in the order of the WBS */
	private final List<AssignmentBoard.Row> selected = new ArrayList<AssignmentBoard.Row>();
	private final Set<AssignmentBoard.Row> belowSummary = Collections.newSetFromMap(new IdentityHashMap<AssignmentBoard.Row, Boolean>());

	/** What a call of apply() did */
	public static class Result {
		int assigned, kept, circular;
		boolean linked;
		long finishBefore, finishAfter;
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
	}

	public AssignmentBoard getBoard() {
		return board;
	}

	/** The tasks of the activities selected, and below the summaries selected */
	public List<AssignmentBoard.Row> getSelected() {
		return selected;
	}

	/** The tasks selected that go to a person: the tasks selected themselves, and below a summary the ones it can do */
	public List<AssignmentBoard.Row> tasksFor(AssignmentBoard.Person person) {
		List<AssignmentBoard.Row> result = new ArrayList<AssignmentBoard.Row>();
		for (AssignmentBoard.Row row : selected)
			if (!belowSummary.contains(row) || AssignmentBoard.canDo(person, row))
				result.add(row);
		return result;
	}

	/** True if a person can do all the tasks selected that would go to it, and at least one */
	public boolean canDo(AssignmentBoard.Person person) {
		List<AssignmentBoard.Row> tasks = tasksFor(person);
		for (AssignmentBoard.Row row : tasks)
			if (!AssignmentBoard.canDo(person, row))
				return false;
		return !tasks.isEmpty();
	}

	/**
	 * Gives tasks to a person, null removes their people, and writes them in the project. Then, if relink is not null,
	 * links again the tasks of the people of the whole project with these options. A single undo step.
	 * @param rows tasks of the board
	 */
	public Result apply(List<AssignmentBoard.Row> rows, Resource resource, AutoLinkOptions relink, Object eventSource) {
		Result result = new Result();
		Project project = board.getProject();
		result.finishBefore = project.getEnd();
		result.kept = board.assign(rows, resource);
		result.assigned = board.getChangedRows().size();
		project.beginUndoUpdate();
		try {
			AssignmentBoardWriter.apply(board, eventSource); // its schedule is calculated: the plan reads the new durations
			if (relink != null) {
				AutoLinkPlan plan = AutoLinkPlan.create(project, null, relink);
				result.circular = plan.getCircularTasks().size();
				if (result.circular == 0) {
					if (!plan.isEmpty())
						AutoLinker.apply(project, plan, eventSource);
					result.linked = true;
				}
			}
		} finally {
			project.endUndoUpdate();
		}
		result.finishAfter = project.getEnd();
		return result;
	}
}
