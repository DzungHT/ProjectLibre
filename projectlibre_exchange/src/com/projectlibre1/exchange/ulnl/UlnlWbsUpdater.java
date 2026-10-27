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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEditSupport;

import com.projectlibre1.configuration.FieldDictionary;
import com.projectlibre1.datatype.Duration;
import com.projectlibre1.datatype.TimeUnit;
import com.projectlibre1.datatype.Work;
import com.projectlibre1.field.Field;
import com.projectlibre1.field.FieldParseException;
import com.projectlibre1.grouping.core.Node;
import com.projectlibre1.grouping.core.NodeFactory;
import com.projectlibre1.grouping.core.model.NodeModel;
import com.projectlibre1.pm.scheduling.SchedulingType;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.pm.task.TaskCancellation;
import com.projectlibre1.undo.FieldEdit;

/**
 * Applies the changes of a UlnlWbsDiff that are to apply, keeping the tasks, their links and assignments.
 * New rows are inserted after the row before them, removed rows are cancelled with a reason. The schedule is
 * calculated once at the end and the whole update is a single undo step.
 * The work is done in small steps by next(), so that a progress dialog can repaint between them.
 */
public class UlnlWbsUpdater {
	public static final int UPDATING = 0;
	public static final int ADDING = 1;
	public static final int SETTING_WORK = 2;
	public static final int CANCELLING = 3;
	public static final int RECALCULATING = 4;
	public static final int DONE = 5;
	private static final int CHANGES_PER_STEP = 100;

	private final Project project;
	private final NodeModel model;
	private final UlnlWbsDiff diff;
	private final String removedReason;
	private final Object eventSource;
	private final List<UlnlWbsDiff.Change> values = new ArrayList<UlnlWbsDiff.Change>();
	private final List<UlnlWbsDiff.Change> added = new ArrayList<UlnlWbsDiff.Change>();
	private final List<UlnlWbsDiff.Change> cancellations = new ArrayList<UlnlWbsDiff.Change>();
	private final Map<UlnlWbsParser.WbsItem, NormalTask> created = new LinkedHashMap<UlnlWbsParser.WbsItem, NormalTask>();
	private final List<NormalTask> newActivities = new ArrayList<NormalTask>();
	private final Map<NormalTask, String[]> infoBefore = new LinkedHashMap<NormalTask, String[]>(); // key, function code, lane, activity
	private final Field nameField, workField, estimateField;
	private UndoableEditSupport undo;
	private int phase = UPDATING;
	private int done = 0;
	private int transactionId = 0;
	private boolean started = false;
	private int changed = 0;

	/**
	 * @param removedReason cancel reason of the removed rows
	 * @param eventSource not null, so that the changes can be undone
	 */
	public UlnlWbsUpdater(Project project, UlnlWbsDiff diff, String removedReason, Object eventSource) {
		this.project = project;
		this.model = project.getTaskOutline();
		this.diff = diff;
		this.removedReason = removedReason;
		this.eventSource = eventSource;
		nameField = FieldDictionary.getInstance().getFieldFromId("Field.name"); //$NON-NLS-1$
		workField = FieldDictionary.getInstance().getFieldFromId("Field.work"); //$NON-NLS-1$
		estimateField = FieldDictionary.getInstance().getFieldFromId("Field.originalEstimate"); //$NON-NLS-1$
		for (UlnlWbsDiff.Change change : diff.getChanges()) {
			switch (change.getType()) {
			case UlnlWbsDiff.ADDED:
				if (change.isApply())
					added.add(change);
				break;
			case UlnlWbsDiff.REMOVED:
			case UlnlWbsDiff.RESTORED:
				if (change.isApply())
					cancellations.add(change);
				break;
			default:
				if (change.isApply() || change.getType() == UlnlWbsDiff.CONFLICT) // the estimate of a conflict is always updated
					values.add(change);
			}
		}
	}

	/** Does the whole update at once */
	public static void apply(Project project, UlnlWbsDiff diff, String removedReason, Object eventSource) {
		UlnlWbsUpdater updater = new UlnlWbsUpdater(project, diff, removedReason, eventSource);
		try {
			while (!updater.next());
		} finally {
			updater.close();
		}
	}

	/** Does a small piece of the work. Returns true when done */
	public boolean next() {
		if (!started) {
			started = true;
			project.beginUndoUpdate();
			undo = project.getUndoController() == null ? null : project.getUndoController().getEditSupport();
			transactionId = project.fireMultipleTransaction(0, true);
		}
		switch (phase) {
		case UPDATING:
			for (int n = 0; n < CHANGES_PER_STEP && done < values.size(); n++)
				applyValue(values.get(done++));
			if (done == values.size()) {
				updateSilently();
				phase = ADDING;
				done = 0;
			}
			return false;
		case ADDING:
			if (done < added.size()) {
				add(added.get(done++));
				return false;
			}
			phase = SETTING_WORK;
			done = 0;
			return false;
		case SETTING_WORK:
			for (int n = 0; n < CHANGES_PER_STEP && done < newActivities.size(); n++) {
				NormalTask task = newActivities.get(done++);
				task.setWork(task.getPlannedEstimate());
				task.markTaskAsNeedingRecalculation();
			}
			if (done == newActivities.size()) {
				phase = CANCELLING;
				done = 0;
			}
			return false;
		case CANCELLING:
			cancel();
			phase = RECALCULATING;
			return false;
		case RECALCULATING:
			close();
			project.recalculate();
			phase = DONE;
			return true;
		default:
			return true;
		}
	}

	private void applyValue(UlnlWbsDiff.Change change) {
		NormalTask task = change.getTask();
		UlnlWbsParser.WbsItem item = change.getItem();
		switch (change.getType()) {
		case UlnlWbsDiff.RENAME:
			setField(nameField, task, item.getName());
			break;
		case UlnlWbsDiff.FUNCTION:
			setInfo(task, task.getUlnlKey(), item.getFunctionCode(), item.getLane());
			for (UlnlWbsParser.WbsItem child : item.getChildren()) {
				NormalTask activity = diff.getTask(child);
				if (activity != null)
					setInfo(activity, activity.getUlnlKey(), item.getFunctionCode(), item.getLane());
			}
			break;
		case UlnlWbsDiff.ESTIMATE:
		case UlnlWbsDiff.CONFLICT:
			setField(estimateField, task, new Work(change.getNewEstimate()));
			if (change.isApply() && !task.isCancelled()) {
				Object oldWork = workField.getValue(task, null);
				if (task.resetWorkToPlannedEstimate()) {
					task.markTaskAsNeedingRecalculation();
					workField.fireEvent(task, eventSource, null);
					if (undo != null)
						undo.postEdit(new FieldEdit(workField, task, workField.getValue(task, null), oldWork, eventSource, null));
				}
			}
			break;
		}
		changed++;
	}

	/** Keys of all the matched tasks, original estimates of the tasks without one */
	private void updateSilently() {
		for (Map.Entry<UlnlWbsParser.WbsItem, NormalTask> entry : diff.getMatches().entrySet()) {
			NormalTask task = entry.getValue();
			String key = entry.getKey().getKey();
			if (key != null && !key.equals(task.getUlnlKey())) {
				String[] info = infoBefore.containsKey(task) ? null : info(task);
				if (info != null)
					infoBefore.put(task, info);
				task.setUlnlKey(key);
			}
			if (entry.getKey().isActivity() && task.getActivity() == null) { // projects made before the activity codes
				if (!infoBefore.containsKey(task))
					infoBefore.put(task, info(task));
				task.setActivity(entry.getKey().getActivityCode());
			}
		}
		for (NormalTask task : diff.getSilentEstimates())
			task.setOriginalEstimate(task.getWork(null));
		if (!infoBefore.isEmpty() && undo != null)
			undo.postEdit(new InfoEdit(infoBefore));
	}

	/** Key, function code, lane and activity of a task, for undo */
	private static String[] info(NormalTask task) {
		return new String[] { task.getUlnlKey(), task.getFunctionCode(), task.getLane(), task.getActivity() };
	}

	private void setInfo(NormalTask task, String key, String functionCode, String lane) {
		if (!infoBefore.containsKey(task))
			infoBefore.put(task, info(task));
		task.setUlnlKey(key);
		task.setFunctionCode(functionCode);
		task.setLane(lane);
	}

	private void setField(Field field, NormalTask task, Object value) {
		Object oldValue = field.getValue(task, null);
		try {
			field.setValue(task, eventSource, value, null);
		} catch (FieldParseException e) {
			return;
		}
		if (undo != null)
			undo.postEdit(new FieldEdit(field, task, value, oldValue, eventSource, null));
	}

	/** Inserts a new row and the rows below it after the row before it, or first */
	private void add(UlnlWbsDiff.Change change) {
		UlnlWbsParser.WbsItem item = change.getItem();
		UlnlWbsParser.WbsItem parentItem = diff.getParent(item);
		NormalTask parent = parentItem == null ? null : taskOf(parentItem);
		Node parentNode = parent == null ? null : nodeOf(parent);
		if (parent != null && parentNode == null)
			return; // parent not found
		Node node = createNode(item, parent);
		List siblings = model.getChildren(parentNode);
		int position = 0;
		NormalTask previous = previousTask(item, parentItem);
		if (siblings != null) {
			int index = indexOf(siblings, previous);
			if (index >= 0)
				position = index + 1;
			else if (previous != null) { // the row before was moved: at the end, before the blank rows
				position = siblings.size();
				while (position > 0 && ((Node) siblings.get(position - 1)).isVoid())
					position--;
			}
		}
		List<Node> nodes = new ArrayList<Node>();
		nodes.add(node);
		model.paste(parentNode, nodes, position, NodeModel.NORMAL);
		changed += change.getTaskCount();
	}

	private NormalTask taskOf(UlnlWbsParser.WbsItem item) {
		NormalTask task = diff.getTask(item);
		return task != null ? task : created.get(item);
	}

	/** Task of the closest row before the item, under the same parent, that is in the project */
	private NormalTask previousTask(UlnlWbsParser.WbsItem item, UlnlWbsParser.WbsItem parentItem) {
		List<UlnlWbsParser.WbsItem> siblings = parentItem == null ? diff.getRoots() : parentItem.getChildren();
		NormalTask previous = null;
		for (UlnlWbsParser.WbsItem sibling : siblings) {
			if (sibling == item)
				break;
			NormalTask task = taskOf(sibling);
			if (task != null)
				previous = task;
		}
		return previous;
	}

	private Node createNode(UlnlWbsParser.WbsItem item, NormalTask parent) {
		Node node = NodeFactory.getInstance().createTask(project);
		NormalTask task = (NormalTask) node.getImpl();
		task.setName(item.getName());
		task.setWbsParent(parent);
		task.setUlnlKey(item.getKey());
		task.setFunctionCode(item.getFunctionCode());
		task.setLane(item.getLane());
		if (item.isActivity()) {
			task.setSchedulingType(SchedulingType.FIXED_WORK);
			task.setEstimated(false);
			task.setActivity(item.getActivityCode());
			task.setOriginalEstimate(Duration.getInstance(item.getWork(), TimeUnit.HOURS));
			newActivities.add(task); // the work is set once the task is in the project
		}
		created.put(item, task);
		for (UlnlWbsParser.WbsItem child : item.getChildren())
			node.add(createNode(child, task));
		return node;
	}

	/** Node of a task, from the children of its parent */
	private Node nodeOf(NormalTask task) {
		NormalTask parent = (NormalTask) task.getWbsParentTask();
		Collection nodes = parent == null ? model.getChildren(null) : parent.getWbsChildrenNodes();
		if (nodes != null)
			for (Iterator i = nodes.iterator(); i.hasNext();) {
				Node node = (Node) i.next();
				if (node.getImpl() == task)
					return node;
			}
		return null;
	}

	private static int indexOf(List nodes, NormalTask task) {
		if (task == null)
			return -1;
		for (int i = 0; i < nodes.size(); i++)
			if (((Node) nodes.get(i)).getImpl() == task)
				return i;
		return -1;
	}

	private void cancel() {
		if (cancellations.isEmpty())
			return;
		TaskCancellation cancellation = new TaskCancellation(project, System.currentTimeMillis());
		for (UlnlWbsDiff.Change change : cancellations) {
			if (change.getType() == UlnlWbsDiff.REMOVED)
				cancellation.cancel(change.getTask(), removedReason);
			else
				cancellation.uncancel(change.getTask());
			changed += change.getTaskCount();
		}
		cancellation.finish();
		TaskCancellation.markChanged(cancellation.getChangedTasks(), eventSource);
		if (undo != null)
			undo.postEdit(cancellation.createEdit());
	}

	/** Ends the transaction and the undo step. Called at the end, or if the update stops on an error */
	public void close() {
		try {
			if (transactionId != 0) {
				int id = transactionId;
				transactionId = 0;
				project.fireMultipleTransaction(id, false);
			}
		} finally {
			if (started) {
				started = false;
				project.endUndoUpdate();
			}
		}
	}

	/** One of UPDATING, ADDING, SETTING_WORK, CANCELLING, RECALCULATING, DONE: what the next call to next() does */
	public int getPhase() {
		return phase;
	}
	/** True if the next call to next() is a single long operation */
	public boolean isLongStep() {
		return phase == CANCELLING || phase == RECALCULATING;
	}
	/** Changes done in the current phase */
	public int getDone() {
		return done;
	}
	/** Changes to do in the current phase, 0 if it is a single operation */
	public int getTotal() {
		switch (phase) {
		case UPDATING: return values.size();
		case ADDING: return added.size();
		case SETTING_WORK: return newActivities.size();
		default: return 0;
		}
	}
	/** Tasks changed, created, cancelled or un-cancelled */
	public int getChanged() {
		return changed;
	}

	/** Undoes the changes of key, function code, lane and activity, which are not all fields that can be set */
	private static class InfoEdit extends AbstractUndoableEdit {
		private static final long serialVersionUID = 1L;
		private final Map<NormalTask, String[]> before;
		private final Map<NormalTask, String[]> after = new LinkedHashMap<NormalTask, String[]>();

		InfoEdit(Map<NormalTask, String[]> before) {
			this.before = new LinkedHashMap<NormalTask, String[]>(before);
			for (NormalTask task : before.keySet())
				after.put(task, info(task));
		}
		public void undo() throws CannotUndoException {
			super.undo();
			apply(before);
		}
		public void redo() throws CannotRedoException {
			super.redo();
			apply(after);
		}
		private static void apply(Map<NormalTask, String[]> values) {
			for (Map.Entry<NormalTask, String[]> entry : values.entrySet()) {
				entry.getKey().setUlnlKey(entry.getValue()[0]);
				entry.getKey().setFunctionCode(entry.getValue()[1]);
				entry.getKey().setLane(entry.getValue()[2]);
				entry.getKey().setActivity(entry.getValue()[3]);
			}
		}
	}
}
