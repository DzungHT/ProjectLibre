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
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.projectlibre1.datatype.Duration;
import com.projectlibre1.datatype.TimeUnit;
import com.projectlibre1.grouping.core.Node;
import com.projectlibre1.grouping.core.NodeFactory;
import com.projectlibre1.grouping.core.model.NodeModel;
import com.projectlibre1.pm.scheduling.SchedulingType;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;

/**
 * Replaces the WBS of a project with the tree read from a ULNL workbook.
 * Project information, resources and calendars are kept. The whole import is a single undo step.
 * The tasks keep the key, function code, activity and lane of the ULNL, and the work of the activities as
 * original estimate. Their work is the part of it planned by the effort ratios of the project.
 * The work is done in small steps by next(), so that a progress dialog can repaint between them.
 */
public class UlnlWbsImporter {
	public static final int REMOVING = 0;
	public static final int CREATING = 1;
	public static final int INSERTING = 2;
	public static final int SETTING_WORK = 3;
	public static final int RECALCULATING = 4;
	public static final int DONE = 5;

	private final Project project;
	private final NodeModel model;
	private final List<UlnlWbsParser.WbsItem> roots;
	private int phase = REMOVING;
	private int done = 0;
	private int total = 0;
	private boolean undoStarted = false;
	private int transactionId = 0;

	private final Deque<Pending> pending = new ArrayDeque<Pending>();
	private final List<Node> rootNodes = new ArrayList<Node>();
	private final Map<NormalTask, Double> activities = new LinkedHashMap<NormalTask, Double>();
	private Iterator<Map.Entry<NormalTask, Double>> workIterator;

	/** Does the whole import at once */
	public static void replaceWbs(Project project, List<UlnlWbsParser.WbsItem> roots) {
		UlnlWbsImporter importer = new UlnlWbsImporter(project, roots);
		try {
			while (!importer.next());
		} finally {
			importer.close();
		}
	}

	public UlnlWbsImporter(Project project, List<UlnlWbsParser.WbsItem> roots) {
		this.project = project;
		this.model = project.getTaskOutline();
		this.roots = roots;
	}

	/** Does a small piece of the work. Returns true when the import is done */
	public boolean next() {
		switch (phase) {
		case REMOVING:
			project.beginUndoUpdate();
			undoStarted = true;
			List<Node> oldNodes = new ArrayList<Node>();
			List children = model.getChildren(null); // null for a project that never had rows
			for (Iterator i = children == null ? oldNodes.iterator() : children.iterator(); i.hasNext();) {
				Node node = (Node) i.next();
				if (!node.isVoid())
					oldNodes.add(node);
			}
			if (!oldNodes.isEmpty())
				model.remove(oldNodes, NodeModel.NORMAL, true);
			for (int i = roots.size() - 1; i >= 0; i--)
				pending.push(new Pending(roots.get(i), null));
			total = count(roots);
			done = 0;
			phase = CREATING;
			return false;
		case CREATING:
			if (!pending.isEmpty()) {
				createNode(pending.pop());
				done++;
				return false;
			}
			phase = INSERTING;
			done = total = 0;
			return false;
		case INSERTING:
			model.paste(null, rootNodes, 0, NodeModel.NORMAL); // before the blank rows left by the removal
			// work is set once the tasks are connected to the project and its scheduling
			transactionId = project.fireMultipleTransaction(0, true);
			workIterator = activities.entrySet().iterator();
			total = activities.size();
			done = 0;
			phase = SETTING_WORK;
			return false;
		case SETTING_WORK:
			if (workIterator.hasNext()) {
				Map.Entry<NormalTask, Double> entry = workIterator.next();
				NormalTask task = entry.getKey();
				task.setWork(task.getPlannedEstimate()); // the effort ratio of the project applies
				task.markTaskAsNeedingRecalculation();
				done++;
				return false;
			}
			phase = RECALCULATING;
			done = total = 0;
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

	/** Ends the transaction and the undo step. Called at the end, or if the import stops on an error */
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

	/** One of REMOVING, CREATING, INSERTING, SETTING_WORK, RECALCULATING, DONE: what the next call to next() does */
	public int getPhase() {
		return phase;
	}

	/** True if the next call to next() is a single long operation */
	public boolean isLongStep() {
		return phase == REMOVING || phase == INSERTING || phase == RECALCULATING;
	}

	/** Tasks processed in the current phase */
	public int getDone() {
		return done;
	}

	/** Tasks to process in the current phase, 0 if it is a single operation */
	public int getTotal() {
		return total;
	}

	private void createNode(Pending p) {
		UlnlWbsParser.WbsItem item = p.item;
		Node node = NodeFactory.getInstance().createTask(project);
		NormalTask task = (NormalTask) node.getImpl();
		task.setName(item.getName());
		task.setWbsParent(p.parent == null ? null : (NormalTask) p.parent.getImpl());
		task.setUlnlKey(item.getKey());
		task.setFunctionCode(item.getFunctionCode());
		task.setLane(item.getLane());
		if (item.isActivity()) {
			task.setSchedulingType(SchedulingType.FIXED_WORK);
			task.setEstimated(false);
			task.setActivity(item.getActivityCode());
			task.setOriginalEstimate(Duration.getInstance(item.getWork(), TimeUnit.HOURS));
			activities.put(task, item.getWork());
		}
		if (p.parent == null)
			rootNodes.add(node);
		else
			p.parent.add(node);
		List<UlnlWbsParser.WbsItem> children = item.getChildren();
		for (int i = children.size() - 1; i >= 0; i--) // so that the children are created in order
			pending.push(new Pending(children.get(i), node));
	}

	private static int count(List<UlnlWbsParser.WbsItem> items) {
		int n = items.size();
		for (UlnlWbsParser.WbsItem item : items)
			n += count(item.getChildren());
		return n;
	}

	private static class Pending {
		final UlnlWbsParser.WbsItem item;
		final Node parent;
		Pending(UlnlWbsParser.WbsItem item, Node parent) {
			this.item = item;
			this.parent = parent;
		}
	}
}
