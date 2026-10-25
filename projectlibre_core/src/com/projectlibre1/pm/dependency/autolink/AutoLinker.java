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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import com.projectlibre1.configuration.Configuration;
import com.projectlibre1.pm.assignment.Assignment;
import com.projectlibre1.pm.dependency.Dependency;
import com.projectlibre1.pm.dependency.DependencyService;
import com.projectlibre1.pm.dependency.DependencyType;
import com.projectlibre1.pm.scheduling.ConstraintType;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;

/**
 * Applies an AutoLinkPlan: removes the old links of Auto Link, then creates the new ones, in a single transaction
 * so that the schedule is calculated once at the end, and a single undo step.
 * The work is done in small steps by next(), so that a progress dialog can repaint between them.
 */
public class AutoLinker {
	public static final int REMOVING = 0;
	public static final int CREATING = 1;
	public static final int RECALCULATING = 2;
	public static final int DONE = 3;
	private static final int LINKS_PER_STEP = 50;

	private final Project project;
	private final AutoLinkPlan plan;
	private final Object eventSource;
	private int phase = REMOVING;
	private int done = 0;
	private int transactionId = 0;
	private boolean undoStarted = false;

	/** Applies the whole plan at once */
	public static void apply(Project project, AutoLinkPlan plan, Object eventSource) {
		AutoLinker linker = new AutoLinker(project, plan, eventSource);
		try {
			while (!linker.next());
		} finally {
			linker.close();
		}
	}

	/** @param eventSource not null, so that the links can be undone */
	public AutoLinker(Project project, AutoLinkPlan plan, Object eventSource) {
		this.project = project;
		this.plan = plan;
		this.eventSource = eventSource;
	}

	/** Does a small piece of the work. Returns true when done */
	public boolean next() {
		if (!undoStarted) {
			project.beginUndoUpdate();
			undoStarted = true;
			transactionId = project.fireMultipleTransaction(0, true);
		}
		switch (phase) {
		case REMOVING:
			for (int n = 0; n < LINKS_PER_STEP && done < plan.getToRemove().size(); n++)
				DependencyService.getInstance().remove(plan.getToRemove().get(done++), eventSource, true);
			if (done == plan.getToRemove().size()) {
				phase = CREATING;
				done = 0;
			}
			return false;
		case CREATING:
			for (int n = 0; n < LINKS_PER_STEP && done < plan.getToCreate().size(); n++) {
				AutoLinkPlan.Link link = plan.getToCreate().get(done++);
				Dependency dependency = Dependency.getInstance(link.getPredecessor(), link.getSuccessor(), DependencyType.FS, 0);
				dependency.setAuto(true);
				// no test of each link: the plan was checked for circular links, and links between leaf tasks are valid
				DependencyService.getInstance().connect(dependency, eventSource);
			}
			if (done == plan.getToCreate().size()) {
				changeTasks();
				phase = RECALCULATING;
				done = 0;
			}
			return false;
		case RECALCULATING:
			close(); // the end of the transaction calculates the schedule
			phase = DONE;
			return true;
		default:
			return true;
		}
	}

	/** Sequences: the constraints of the first day of the people and the leveling delays, undone with the links */
	private void changeTasks() {
		if (plan.getConstraints().isEmpty() && plan.getDelayed().isEmpty())
			return;
		TaskEdit edit = new TaskEdit(plan);
		edit.redo();
		project.getUndoController().getEditSupport().postEdit(edit);
	}

	/** State of a task changed by Auto Link: its constraint, and the leveling delays of the task and its assignments */
	private static class TaskState {
		int type;
		long date;
		boolean joinConstraint;
		long delay;
		long[] assignmentDelays;

		TaskState(NormalTask task, List<Assignment> assignments) {
			type = task.getConstraintType();
			date = task.getConstraintDate();
			joinConstraint = task.isJoinConstraint();
			delay = task.getLevelingDelay();
			assignmentDelays = new long[assignments.size()];
			for (int i = 0; i < assignmentDelays.length; i++)
				assignmentDelays[i] = assignments.get(i).getLevelingDelay();
		}
		TaskState(TaskState from) {
			type = from.type;
			date = from.date;
			joinConstraint = from.joinConstraint;
			delay = from.delay;
			assignmentDelays = from.assignmentDelays.clone();
		}
	}

	private static class TaskEdit extends AbstractUndoableEdit {
		private static final long serialVersionUID = 1L;
		private final List<NormalTask> tasks = new ArrayList<NormalTask>();
		private final List<List<Assignment>> assignments = new ArrayList<List<Assignment>>();
		private final List<TaskState> before = new ArrayList<TaskState>();
		private final List<TaskState> after = new ArrayList<TaskState>();

		TaskEdit(AutoLinkPlan plan) {
			Map<NormalTask, TaskState> changes = new IdentityHashMap<NormalTask, TaskState>();
			for (Map.Entry<NormalTask, Long> entry : plan.getConstraints().entrySet()) {
				NormalTask task = entry.getKey();
				TaskState state = change(task, changes);
				long date = entry.getValue();
				state.type = date == 0 ? ConstraintType.ASAP : ConstraintType.SNET;
				state.date = date;
				state.joinConstraint = date != 0;
			}
			for (NormalTask task : plan.getDelayed()) {
				TaskState state = change(task, changes);
				state.delay = 0;
				Arrays.fill(state.assignmentDelays, 0);
			}
		}
		private TaskState change(NormalTask task, Map<NormalTask, TaskState> changes) {
			TaskState state = changes.get(task);
			if (state == null) {
				List<Assignment> list = new ArrayList<Assignment>();
				for (Iterator i = task.getAssignments().iterator(); i.hasNext();)
					list.add((Assignment) i.next());
				TaskState old = new TaskState(task, list);
				state = new TaskState(old);
				tasks.add(task);
				assignments.add(list);
				before.add(old);
				after.add(state);
				changes.put(task, state);
			}
			return state;
		}
		public void undo() throws CannotUndoException {
			super.undo();
			set(before);
		}
		public void redo() throws CannotRedoException {
			if (canRedo())
				super.redo();
			set(after);
		}
		private void set(List<TaskState> states) {
			for (int t = 0; t < tasks.size(); t++) {
				NormalTask task = tasks.get(t);
				TaskState state = states.get(t);
				task.setScheduleConstraint(state.type, state.date);
				task.setJoinConstraint(state.joinConstraint);
				task.setLevelingDelay(state.delay);
				List<Assignment> list = assignments.get(t);
				for (int i = 0; i < list.size(); i++)
					list.get(i).setLevelingDelay(state.assignmentDelays[i]);
				task.setDirty(true);
				task.getDocument().getObjectEventManager().fireUpdateEvent(task, task, Configuration.getFieldFromId("Field.constraintType")); //$NON-NLS-1$
				task.getDocument().getObjectEventManager().fireUpdateEvent(task, task, Configuration.getFieldFromId("Field.levelingDelay")); //$NON-NLS-1$
			}
		}
	}

	/** Ends the transaction and the undo step. Called at the end, or if the work stops on an error */
	public void close() {
		try {
			if (transactionId != 0) {
				int id = transactionId;
				transactionId = 0;
				project.fireMultipleTransaction(id, false);
			}
		} finally {
			if (undoStarted) {
				undoStarted = false;
				project.endUndoUpdate();
			}
		}
	}

	/** One of REMOVING, CREATING, RECALCULATING, DONE: what the next call to next() does */
	public int getPhase() {
		return phase;
	}
	/** True if the next call to next() is a single long operation */
	public boolean isLongStep() {
		return phase == RECALCULATING;
	}
	/** Links processed in the current phase */
	public int getDone() {
		return done;
	}
	/** Links to process in the current phase, 0 if it is a single operation */
	public int getTotal() {
		return phase == REMOVING ? plan.getToRemove().size() : phase == CREATING ? plan.getToCreate().size() : 0;
	}
}
