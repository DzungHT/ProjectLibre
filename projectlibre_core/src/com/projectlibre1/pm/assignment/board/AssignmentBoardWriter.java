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
import java.util.Iterator;
import java.util.List;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import com.projectlibre1.options.TaskDefaultOption;
import com.projectlibre1.pm.assignment.Assignment;
import com.projectlibre1.pm.assignment.AssignmentService;
import com.projectlibre1.pm.resource.Resource;
import com.projectlibre1.pm.scheduling.SchedulingType;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.undo.ScheduleBackupEdit;

/**
 * Writes the people of an AssignmentBoard in the project, as a single undo step. Each task whose people changed
 * loses its labor assignments and gets one per person chosen, with units = the Max Units of the person when the task
 * starts (or of its first later period with units). The work of the task is kept: it lasts work / units. No leveling
 * delay, no constraint, no link: the dates are calculated by ProjectLibre. The locks of the tasks are written in the
 * same undo step.
 */
public class AssignmentBoardWriter {
	private AssignmentBoardWriter() {
	}

	/** @return the number of tasks changed */
	public static int apply(AssignmentBoard board, Object eventSource) {
		List<AssignmentBoard.Row> rows = board.getChangedRows();
		if (rows.isEmpty())
			return 0;
		Project project = board.getProject();
		List<NormalTask> tasks = new ArrayList<NormalTask>();
		for (AssignmentBoard.Row row : rows)
			tasks.add(row.getTask());
		project.beginUndoUpdate();
		int transactionId = project.fireMultipleTransaction(0, true);
		try {
			project.getUndoController().getEditSupport().postEdit(new ScheduleBackupEdit(tasks, eventSource));
			LockEdit locks = new LockEdit();
			for (AssignmentBoard.Row row : rows) {
				if (row.isPeopleChanged())
					write(board, row, eventSource);
				if (row.isLocked() != row.getTask().isAssignmentLocked())
					locks.add(row.getTask(), row.isLocked());
			}
			if (!locks.tasks.isEmpty()) {
				locks.redo();
				project.getUndoController().getEditSupport().postEdit(locks);
				project.setGroupDirty(true);
			}
		} finally {
			project.fireMultipleTransaction(transactionId, false);
			project.endUndoUpdate();
		}
		return rows.size();
	}

	/** The people of a task. It is of fixed work while they change, so that its work does not change */
	private static void write(AssignmentBoard board, AssignmentBoard.Row row, Object eventSource) {
		NormalTask task = row.getTask();
		int schedulingType = task.getSchedulingType();
		boolean effortDriven = task.isEffortDriven();
		if (schedulingType != SchedulingType.FIXED_WORK)
			task.setSchedulingType(SchedulingType.FIXED_WORK);
		try {
			List<Assignment> old = new ArrayList<Assignment>();
			for (Iterator i = task.getAssignments().iterator(); i.hasNext();) {
				Assignment assignment = (Assignment) i.next();
				if (!assignment.isDefault() && assignment.isLabor())
					old.add(assignment);
			}
			for (Assignment assignment : old)
				AssignmentService.getInstance().remove(assignment, eventSource, true);
			for (Resource resource : row.getResources())
				AssignmentService.getInstance().newAssignment(task, resource, units(board, resource, task), 0, eventSource, true);
		} finally {
			if (schedulingType != SchedulingType.FIXED_WORK) {
				task.setSchedulingType(schedulingType);
				task.setEffortDriven(effortDriven);
			}
		}
	}

	/** The locks of tasks changed, undone with the assignments */
	private static class LockEdit extends AbstractUndoableEdit {
		private static final long serialVersionUID = 1L;
		final List<NormalTask> tasks = new ArrayList<NormalTask>();
		final List<Boolean> locked = new ArrayList<Boolean>();

		void add(NormalTask task, boolean lock) {
			tasks.add(task);
			locked.add(lock);
		}
		public void undo() throws CannotUndoException {
			super.undo();
			set(true);
		}
		public void redo() throws CannotRedoException {
			if (canRedo())
				super.redo();
			set(false);
		}
		private void set(boolean undo) {
			for (int i = 0; i < tasks.size(); i++)
				tasks.get(i).setAssignmentLocked(undo != locked.get(i));
		}
	}

	/** Max Units of a resource when the task starts, else of its period in the plan, else 100% */
	private static double units(AssignmentBoard board, Resource resource, NormalTask task) {
		double units = TaskDefaultOption.maximumUnitsFrom(resource, task.getStart());
		if (units > 0)
			return units;
		AssignmentBoard.Person person = board.getPerson(resource);
		return person != null && person.getUnits() > 0 ? person.getUnits() : 1.0;
	}
}
