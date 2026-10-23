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
package com.projectlibre1.pm.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import com.projectlibre1.configuration.FieldDictionary;
import com.projectlibre1.field.Field;
import com.projectlibre1.grouping.core.Node;

/**
 * Cancels or un-cancels tasks, as one operation:
 * <ul>
 * <li>Cancelling a summary cancels the tasks below it that are not cancelled yet, with the same cancel date</li>
 * <li>Un-cancelling a cancelled summary un-cancels the tasks below it that were cancelled with it, the tasks
 * cancelled before keep their state. Un-cancelling a summary that is not cancelled un-cancels all the tasks below it</li>
 * <li>Afterwards, a summary is cancelled if all its children are</li>
 * </ul>
 * The state of each changed task is kept, so that the whole operation can be undone in one step.
 */
public class TaskCancellation {
	private final Project project;
	private final long date;
	private final Map<NormalTask, State> before = new LinkedHashMap<NormalTask, State>();

	public TaskCancellation(Project project, long date) {
		this.project = project;
		this.date = date;
	}

	/** Cancels the task and the tasks below it. The reason, if not null, is set to the tasks cancelled now */
	public void cancel(NormalTask task, String reason) {
		if (task.isWbsParent())
			for (NormalTask child : children(task))
				cancel(child, reason);
		if (task.isCancelled())
			return;
		touch(task);
		task.cancel(date);
		if (reason != null)
			task.setCancelReason(reason);
	}

	/** Un-cancels the task and the tasks below it cancelled with it (all of them if the task is not cancelled) */
	public void uncancel(NormalTask task) {
		if (task.isWbsParent())
			uncancelBelow(task, task.isCancelled() ? task.getCancelDate() : -1);
		if (!task.isCancelled())
			return;
		touch(task);
		task.uncancel();
	}

	/** batch: cancel date of the tasks to un-cancel, -1 for all */
	private void uncancelBelow(NormalTask summary, long batch) {
		for (NormalTask child : children(summary)) {
			if (child.isCancelled() && batch >= 0 && child.getCancelDate() != batch)
				continue; // cancelled before, with the tasks below it
			if (child.isWbsParent())
				uncancelBelow(child, batch);
			if (child.isCancelled()) {
				touch(child);
				child.uncancel();
			}
		}
	}

	/** Updates the summaries above the changed tasks: a summary is cancelled when all its children are */
	public void finish() {
		for (NormalTask task : new ArrayList<NormalTask>(before.keySet()))
			updateAncestors(task);
	}

	private void updateAncestors(NormalTask task) {
		for (Task parent = task.getWbsParentTask(); parent instanceof NormalTask; parent = parent.getWbsParentTask()) {
			NormalTask summary = (NormalTask) parent;
			boolean all = allChildrenCancelled(summary);
			if (all == summary.isCancelled())
				return; // nothing changes above
			touch(summary);
			if (all)
				summary.cancel(date);
			else
				summary.uncancel();
		}
	}

	private static boolean allChildrenCancelled(NormalTask summary) {
		Collection<NormalTask> children = children(summary);
		if (children.isEmpty())
			return false;
		for (NormalTask child : children)
			if (!child.isCancelled())
				return false;
		return true;
	}

	private static Collection<NormalTask> children(NormalTask task) {
		List<NormalTask> list = new ArrayList<NormalTask>();
		Collection nodes = task.getWbsChildrenNodes();
		if (nodes != null)
			for (Iterator i = nodes.iterator(); i.hasNext();) {
				Object impl = ((Node) i.next()).getImpl();
				if (impl instanceof NormalTask)
					list.add((NormalTask) impl);
			}
		return list;
	}

	private void touch(NormalTask task) {
		if (!before.containsKey(task))
			before.put(task, new State(task));
	}

	public Collection<NormalTask> getChangedTasks() {
		return before.keySet();
	}

	/** Edit that undoes the whole operation. To call after finish() */
	public UndoableEdit createEdit() {
		Map<NormalTask, State> after = new LinkedHashMap<NormalTask, State>();
		for (NormalTask task : before.keySet())
			after.put(task, new State(task));
		return new TaskCancelEdit(project, before, after);
	}

	/**
	 * Lets the views and the schedule know that the tasks changed: the schedule is calculated once at the end
	 */
	public static void refresh(Project project, Collection<NormalTask> tasks, Object source) {
		int transactionId = project.fireMultipleTransaction(0, true);
		try {
			markChanged(tasks, source);
		} finally {
			project.fireMultipleTransaction(transactionId, false);
		}
		project.recalculate();
	}

	/** Marks the tasks to be scheduled again and lets the views know, without calculating the schedule */
	public static void markChanged(Collection<NormalTask> tasks, Object source) {
		Field workField = FieldDictionary.getInstance().getFieldFromId("Field.work"); //$NON-NLS-1$
		for (NormalTask task : tasks) {
			task.markTaskAsNeedingRecalculation();
			workField.fireEvent(task, source, null);
		}
	}

	private static class State {
		final boolean cancelled;
		final long date;
		final String reason;
		final long workBeforeCancel;

		State(NormalTask task) {
			cancelled = task.isCancelled();
			date = task.getCancelDate();
			reason = task.getCancelReason();
			workBeforeCancel = task.getWorkBeforeCancel();
		}
		void apply(NormalTask task) {
			if (cancelled != task.isCancelled()) {
				if (cancelled)
					task.cancel(date);
				else
					task.uncancel();
			}
			task.setCancelState(date, reason, workBeforeCancel);
		}
	}

	private static class TaskCancelEdit extends AbstractUndoableEdit {
		private static final long serialVersionUID = 1L;
		private final Project project;
		private final Map<NormalTask, State> before, after;

		TaskCancelEdit(Project project, Map<NormalTask, State> before, Map<NormalTask, State> after) {
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
			refresh(project, states.keySet(), this);
		}
		public String getPresentationName() {
			return "Cancel tasks"; //$NON-NLS-1$
		}
	}
}
