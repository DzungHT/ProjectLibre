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
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEditSupport;

import com.projectlibre1.pm.assignment.Assignment;
import com.projectlibre1.pm.assignment.AssignmentService;
import com.projectlibre1.pm.availability.AvailabilityWarning;
import com.projectlibre1.pm.dependency.Dependency;
import com.projectlibre1.pm.dependency.DependencyService;
import com.projectlibre1.pm.dependency.DependencyType;
import com.projectlibre1.pm.dependency.autolink.AutoLinkPlan;
import com.projectlibre1.pm.dependency.autolink.AutoLinker;
import com.projectlibre1.pm.resource.Resource;
import com.projectlibre1.pm.scheduling.ConstraintType;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.pm.task.TaskCancellation;

/**
 * Auto Schedule of a project, as a single undo step:
 * <ol>
 * <li>Auto Link without the sequences, so that only the logical links stay (see docs/auto-schedule-design.md).
 * The links of the resources made by a previous Auto Schedule are links of Auto Link: they are removed too</li>
 * <li>The AutoScheduler assigns and schedules the tasks</li>
 * <li>Each task scheduled gets its resource, with units = the Max Units of the period of its start (the work of
 * the task is not changed: a task of fixed work lasts work / units). The tasks without resource get a warning</li>
 * <li>The order of the tasks of each resource is written as finish to start links (links of Auto Link), so that
 * all the tasks are as soon as possible, after their links: no leveling delay</li>
 * <li>The schedule is calculated. A task whose resource is not available then (Max Units of 0%, like before
 * joining the project) gets a constraint Start No Earlier Than the date calculated</li>
 * </ol>
 * The work is done in small steps by next(), so that a progress dialog can repaint between them.
 */
public class AutoScheduleJob {
	public static final int LINKING = 0;
	public static final int SCHEDULING = 1;
	public static final int ASSIGNING = 2;
	public static final int SEQUENCING = 3;
	public static final int RECALCULATING = 4;
	public static final int CHECKING = 5;
	public static final int DONE = 6;
	private static final int TASKS_PER_STEP = 50;
	private static final int MAX_CORRECTIONS = 5;
	private static final long TOLERANCE = 60 * 1000L; // a start this close to the one calculated is right

	private final Project project;
	private final AutoScheduleOptions options;
	private final Object eventSource;
	private AutoLinker linker;
	private AutoScheduleModel model = null;
	private final Map<NormalTask, State> before = new LinkedHashMap<NormalTask, State>();
	private final List<AutoTask[]> sequences = new ArrayList<AutoTask[]>(); // links to create: predecessor, successor
	private final Map<AutoTask, List<AutoTask>> sequenceSuccessors = new IdentityHashMap<AutoTask, List<AutoTask>>();
	private int phase = LINKING;
	private int done = 0;
	private int corrections = 0;
	private int constraints = 0;
	private int wrongStarts = 0;
	private int transactionId = 0;
	private boolean undoStarted = false;
	private boolean editPosted = false;

	/** Runs the whole job at once */
	public static AutoScheduleJob run(Project project, AutoLinkPlan linkPlan, AutoScheduleOptions options, Object eventSource) {
		AutoScheduleJob job = new AutoScheduleJob(project, linkPlan, options, eventSource);
		try {
			while (!job.next());
		} finally {
			job.close();
		}
		return job;
	}

	/**
	 * @param linkPlan the plan of Auto Link without the sequences, null or empty if the links stay
	 * @param eventSource not null, so that the job can be undone
	 */
	public AutoScheduleJob(Project project, AutoLinkPlan linkPlan, AutoScheduleOptions options, Object eventSource) {
		this.project = project;
		this.options = options;
		this.eventSource = eventSource;
		this.linker = linkPlan == null || linkPlan.isEmpty() ? null : new AutoLinker(project, linkPlan, eventSource);
	}

	/** True if the task has the constraint Start No Earlier Than set by Auto Schedule, unchanged */
	public static boolean hasOwnConstraint(NormalTask task) {
		return task.getAutoScheduleStart() != 0 && task.getConstraintType() == ConstraintType.SNET
				&& task.getConstraintDate() == task.getAutoScheduleStart();
	}

	/**
	 * Does a small piece of the work. Returns true when done
	 * @throws AutoScheduler.CircularLinksException if the links are circular: nothing is assigned
	 */
	public boolean next() throws AutoScheduler.CircularLinksException {
		if (!undoStarted) {
			project.beginUndoUpdate();
			undoStarted = true;
		}
		switch (phase) {
		case LINKING:
			if (linker != null && !linker.next())
				return false;
			linker = null;
			phase = SCHEDULING;
			return false;
		case SCHEDULING:
			model = AutoScheduleModel.create(project, options);
			model.schedule();
			for (AutoTask task : model.getTasks())
				if (task.getSource() instanceof NormalTask) {
					NormalTask normalTask = (NormalTask) task.getSource();
					if (task.getKind() == AutoTask.WORK)
						touch(normalTask);
					else if (normalTask.getAutoScheduleRole() != null) { // a warning of a previous run
						touch(normalTask);
						normalTask.setAutoScheduleMissing(null, null);
					}
				}
			findSequences();
			transactionId = project.fireMultipleTransaction(0, true);
			phase = ASSIGNING;
			done = 0;
			return false;
		case ASSIGNING:
			List<AutoTask> tasks = model.getWorkTasks();
			for (int n = 0; n < TASKS_PER_STEP && done < tasks.size(); n++)
				write(tasks.get(done++));
			if (done == tasks.size()) {
				phase = SEQUENCING;
				done = 0;
			}
			return false;
		case SEQUENCING:
			for (int n = 0; n < TASKS_PER_STEP && done < sequences.size(); n++) {
				AutoTask[] pair = sequences.get(done++);
				Dependency dependency = Dependency.getInstance((NormalTask) pair[0].getSource(), (NormalTask) pair[1].getSource(), DependencyType.FS, 0);
				dependency.setAuto(true);
				DependencyService.getInstance().connect(dependency, eventSource);
			}
			if (done == sequences.size())
				phase = RECALCULATING;
			return false;
		case RECALCULATING:
			TaskCancellation.markChanged(before.keySet(), eventSource);
			endTransaction();
			project.recalculate();
			phase = CHECKING;
			return false;
		case CHECKING:
			if (corrections < MAX_CORRECTIONS && correct()) {
				corrections++;
				return false;
			}
			wrongStarts = wrongStarts().size();
			postEdit();
			phase = DONE;
			return true;
		default:
			return true;
		}
	}

	/** Ends the transaction and the undo step. Called at the end, or if the work stops on an error */
	public void close() {
		try {
			if (linker != null)
				linker.close();
			endTransaction();
			if (undoStarted && phase > SCHEDULING)
				postEdit();
		} finally {
			if (undoStarted) {
				undoStarted = false;
				project.endUndoUpdate();
			}
		}
	}

	/**
	 * The links between the tasks of each resource, in the order of their starts. A started task can be a
	 * predecessor, not a successor. Links already there are not created again
	 */
	private void findSequences() {
		Map<AutoResource, List<AutoTask>> byResource = new LinkedHashMap<AutoResource, List<AutoTask>>();
		for (AutoTask task : model.getTasks()) {
			List<AutoResource> resources;
			if (task.getKind() == AutoTask.WORK && task.getStatus() == AutoTask.SCHEDULED)
				resources = Collections.singletonList(task.getResource());
			else if ((task.getKind() == AutoTask.WORK || task.getKind() == AutoTask.FIXED) && task.keptResources != null && task.getSource() instanceof NormalTask)
				resources = task.keptResources;
			else
				continue;
			for (AutoResource resource : resources) {
				List<AutoTask> list = byResource.get(resource);
				if (list == null) {
					list = new ArrayList<AutoTask>();
					byResource.put(resource, list);
				}
				list.add(task);
			}
		}
		Comparator<AutoTask> byStart = new Comparator<AutoTask>() {
			public int compare(AutoTask a, AutoTask b) {
				return a.start != b.start ? (a.start < b.start ? -1 : 1) : a.order - b.order;
			}
		};
		for (List<AutoTask> list : byResource.values()) {
			Collections.sort(list, byStart);
			for (int i = 1; i < list.size(); i++) {
				AutoTask predecessor = list.get(i - 1), successor = list.get(i);
				if (successor.getKind() != AutoTask.WORK || isLinked((NormalTask) predecessor.getSource(), (NormalTask) successor.getSource()))
					continue;
				sequences.add(new AutoTask[] { predecessor, successor });
				List<AutoTask> successors = sequenceSuccessors.get(predecessor);
				if (successors == null) {
					successors = new ArrayList<AutoTask>(1);
					sequenceSuccessors.put(predecessor, successors);
				}
				successors.add(successor);
			}
		}
	}

	private static boolean isLinked(NormalTask predecessor, NormalTask successor) {
		for (Iterator i = successor.getPredecessorList().iterator(); i.hasNext();)
			if (((Dependency) i.next()).getPredecessor() == predecessor)
				return true;
		return false;
	}

	/**
	 * The resource and the warning of a task scheduled. Its leveling delay and the constraint set by a previous
	 * Auto Schedule are removed: the links place it
	 */
	private void write(AutoTask node) {
		NormalTask task = (NormalTask) node.getSource();
		if (node.getStatus() == AutoTask.SCHEDULED)
			assign(task, (Resource) node.getResource().getSource(), node.getUnits());
		task.setAutoScheduleMissing(node.getStatus() == AutoTask.NO_RESOURCE ? node.getRole() : null, node.getRequiredSkill());
		task.setLevelingDelay(0);
		if (hasOwnConstraint(task))
			task.setScheduleConstraint(ConstraintType.ASAP, 0);
		task.setAutoScheduleStart(0);
		task.markTaskAsNeedingRecalculation();
	}

	/** The task done by a resource only, with these units. The work of the task does not change */
	private void assign(NormalTask task, Resource resource, double units) {
		List<Assignment> old = new ArrayList<Assignment>();
		for (Iterator i = task.getAssignments().iterator(); i.hasNext();) {
			Assignment assignment = (Assignment) i.next();
			if (!assignment.isDefault() && assignment.isLabor())
				old.add(assignment);
		}
		if (old.size() == 1 && old.get(0).getResource() == resource && Math.abs(old.get(0).getUnits() - units) < 1e-9)
			return;
		for (Assignment assignment : old)
			AssignmentService.getInstance().remove(assignment, eventSource, true);
		AssignmentService.getInstance().newAssignment(task, resource, units, 0, eventSource, true);
	}

	/**
	 * Tasks scheduled starting later than calculated (should be none). The links place the tasks: a task can
	 * start a little earlier than calculated, as ProjectLibre rounds the work and durations to the millisecond
	 */
	private List<AutoTask> wrongStarts() {
		List<AutoTask> wrong = new ArrayList<AutoTask>();
		for (AutoTask node : model.getWorkTasks())
			if (((NormalTask) node.getSource()).getStart() > node.getStart() + TOLERANCE)
				wrong.add(node);
		return wrong;
	}

	/**
	 * A task whose resource is not available during it (Max Units of 0%, like before joining the project) gets a
	 * constraint Start No Earlier Than the date calculated, in a period when the resource is available. A task
	 * after another one to correct waits for the next time, as its start changes with the other one.
	 * Returns false if nothing changes
	 */
	private boolean correct() {
		List<AutoTask> wrong = new ArrayList<AutoTask>();
		for (AutoTask node : model.getWorkTasks())
			if (node.getStatus() != AutoTask.NO_RESOURCE && !AvailabilityWarning.unavailablePeriods((NormalTask) node.getSource()).isEmpty())
				wrong.add(node);
		if (wrong.isEmpty())
			return false;
		Set<AutoTask> after = Collections.newSetFromMap(new IdentityHashMap<AutoTask, Boolean>());
		Deque<AutoTask> stack = new ArrayDeque<AutoTask>();
		for (AutoTask node : wrong)
			stack.push(node);
		while (!stack.isEmpty()) {
			AutoTask node = stack.pop();
			for (AutoTask.Link link : node.successors)
				if (after.add(link.successor))
					stack.push(link.successor);
			List<AutoTask> successors = sequenceSuccessors.get(node);
			if (successors != null)
				for (AutoTask successor : successors)
					if (after.add(successor))
						stack.push(successor);
		}
		List<NormalTask> changed = new ArrayList<NormalTask>();
		for (AutoTask node : wrong) {
			NormalTask task = (NormalTask) node.getSource();
			if (after.contains(node) || hasOwnConstraint(task))
				continue;
			task.setScheduleConstraint(ConstraintType.SNET, node.getStart());
			task.setAutoScheduleStart(node.getStart());
			task.markTaskAsNeedingRecalculation();
			changed.add(task);
			constraints++;
		}
		if (changed.isEmpty())
			return false;
		TaskCancellation.refresh(project, changed, eventSource);
		return true;
	}

	private void endTransaction() {
		if (transactionId != 0) {
			int id = transactionId;
			transactionId = 0;
			project.fireMultipleTransaction(id, false);
		}
	}

	private void touch(NormalTask task) {
		if (!before.containsKey(task))
			before.put(task, new State(task));
	}

	/** The edit undoing the changes of the tasks, after the ones of the links and assignments */
	private void postEdit() {
		if (editPosted || before.isEmpty())
			return;
		editPosted = true;
		Map<NormalTask, State> after = new LinkedHashMap<NormalTask, State>();
		for (NormalTask task : before.keySet())
			after.put(task, new State(task));
		UndoableEditSupport undo = project.getUndoController() == null ? null : project.getUndoController().getEditSupport();
		if (undo != null)
			undo.postEdit(new ScheduleEdit(project, before, after));
	}

	/** One of LINKING, SCHEDULING, ASSIGNING, SEQUENCING, RECALCULATING, CHECKING, DONE: what next() does */
	public int getPhase() {
		return phase;
	}
	/** The Auto Link running, null if none */
	public AutoLinker getLinker() {
		return linker;
	}
	/** True if the next call to next() is a single long operation */
	public boolean isLongStep() {
		return phase == SCHEDULING || phase == RECALCULATING || phase == CHECKING || (phase == LINKING && linker != null && linker.isLongStep());
	}
	/** Tasks or links processed in the current phase */
	public int getDone() {
		return phase == LINKING && linker != null ? linker.getDone() : done;
	}
	/** Tasks or links to process in the current phase, 0 if it is a single operation */
	public int getTotal() {
		if (phase == LINKING)
			return linker == null ? 0 : linker.getTotal();
		return phase == ASSIGNING ? model.getWorkTasks().size() : phase == SEQUENCING ? sequences.size() : 0;
	}
	/** The tasks and resources with the results, null before the scheduling */
	public AutoScheduleModel getModel() {
		return model;
	}
	/** Links created between the tasks of each resource */
	public int getSequenceCount() {
		return sequences.size();
	}
	/** Constraints Start No Earlier Than set, for resources not available before */
	public int getConstraintCount() {
		return constraints;
	}
	/** Tasks starting later than calculated at the end (should be 0) */
	public int getWrongStarts() {
		return wrongStarts;
	}
	/** Number of tasks scheduled with a status of AutoTask (SCHEDULED, KEPT, NO_RESOURCE) */
	public int count(int status) {
		int count = 0;
		if (model != null)
			for (AutoTask task : model.getWorkTasks())
				if (task.getStatus() == status)
					count++;
		return count;
	}

	/** What the job changes on a task besides its links and assignments */
	private static class State {
		final long delay;
		final String role;
		final String skill;
		final int constraintType;
		final long constraintDate;
		final long autoScheduleStart;

		State(NormalTask task) {
			delay = task.getLevelingDelay();
			role = task.getAutoScheduleRole();
			skill = task.getAutoScheduleSkill();
			constraintType = task.getConstraintType();
			constraintDate = task.getConstraintDate();
			autoScheduleStart = task.getAutoScheduleStart();
		}
		void apply(NormalTask task) {
			task.setLevelingDelay(delay);
			task.setAutoScheduleMissing(role, skill);
			task.setScheduleConstraint(constraintType, constraintDate);
			task.setAutoScheduleStart(autoScheduleStart);
			task.markTaskAsNeedingRecalculation();
		}
	}

	private static class ScheduleEdit extends AbstractUndoableEdit {
		private static final long serialVersionUID = 1L;
		private final Project project;
		private final Map<NormalTask, State> before, after;

		ScheduleEdit(Project project, Map<NormalTask, State> before, Map<NormalTask, State> after) {
			this.project = project;
			this.before = before;
			this.after = after;
		}
		public void undo() throws CannotUndoException {
			super.undo();
			apply(before);
		}
		public void redo() throws CannotRedoException {
			super.redo();
			apply(after);
		}
		private void apply(Map<NormalTask, State> states) {
			for (Map.Entry<NormalTask, State> entry : states.entrySet())
				entry.getValue().apply(entry.getKey());
			TaskCancellation.refresh(project, states.keySet(), this);
		}
		public String getPresentationName() {
			return "Auto Schedule"; //$NON-NLS-1$
		}
	}
}
