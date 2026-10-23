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
package com.projectlibre1.exchange.wbsexcel;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projectlibre1.field.Field;
import com.projectlibre1.field.FieldParseException;
import com.projectlibre1.grouping.core.Node;
import com.projectlibre1.grouping.core.NodeFactory;
import com.projectlibre1.grouping.core.model.NodeModel;
import com.projectlibre1.options.TaskDefaultOption;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.pm.task.Task;
import com.projectlibre1.strings.Messages;

/**
 * Updates a project from a workbook read by {@link WbsExcelParser}.
 * Rows are matched to tasks by Unique ID; rows without one become new tasks.
 * Only the columns present in the file are set, and only the cells whose text differs from the
 * current value, so everything else keeps its value or what the scheduling computes.
 * The outline follows the Outline Level column when the file has it.
 * The whole import is a single undo step.
 */
public class WbsExcelImporter {
	/** Fields set first, in this order, as their value changes how the next ones are applied */
	private static final String[] FIRST_FIELDS = {
		"Field.schedulingType", "Field.effortDriven", "Field.estimated", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		"Field.duration", "Field.work", "Field.start", "Field.finish" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	};

	private final Project project;
	private final NodeModel model;
	private final WbsExcelParser.Sheet sheet;
	private final List<String> warnings = new ArrayList<String>();
	private final Map<WbsExcelParser.Row, Node> existing = new HashMap<WbsExcelParser.Row, Node>();
	private final List<Node> missing = new ArrayList<Node>(); // tasks not in the file, in outline order
	private int updated;
	private int moved;

	public WbsExcelImporter(Project project, WbsExcelParser.Sheet sheet) {
		this.project = project;
		this.model = project.getTaskOutline();
		this.sheet = sheet;
		warnings.addAll(sheet.warnings);
		match();
		countChanges();
	}

	public int getRowCount() {
		return sheet.rows.size();
	}
	public int getNewCount() {
		return sheet.rows.size() - existing.size();
	}
	/** Existing tasks with at least one value to change */
	public int getUpdatedCount() {
		return updated;
	}
	/** Existing tasks that change parent or position */
	public int getMovedCount() {
		return moved;
	}
	/** Tasks of the project that are not in the file */
	public int getMissingCount() {
		return missing.size();
	}
	public List<String> getWarnings() {
		return warnings;
	}

	private void match() {
		Map<Long, Node> byUniqueId = new HashMap<Long, Node>();
		List<Node> all = new ArrayList<Node>();
		collect(null, all);
		for (Node node : all)
			byUniqueId.put(((Task) node.getImpl()).getUniqueId(), node);
		Set<Node> used = new HashSet<Node>();
		for (WbsExcelParser.Row row : sheet.rows) {
			if (row.uniqueId == null)
				continue;
			Node node = byUniqueId.get(row.uniqueId);
			if (node == null) {
				warnings.add(MessageFormat.format(Messages.getString("WbsExcel.UnknownUniqueId"), String.valueOf(row.rowNumber), String.valueOf(row.uniqueId))); //$NON-NLS-1$
			} else if (!used.add(node)) { // a row copied in Excel
				warnings.add(MessageFormat.format(Messages.getString("WbsExcel.DuplicateUniqueId"), String.valueOf(row.rowNumber), String.valueOf(row.uniqueId))); //$NON-NLS-1$
			} else {
				existing.put(row, node);
			}
		}
		for (Node node : all)
			if (!used.contains(node))
				missing.add(node);
	}

	/** Tasks in outline order */
	private void collect(Node parent, List<Node> result) {
		for (Iterator i = WbsExcelExporter.children(model, parent).iterator(); i.hasNext();) {
			Node node = (Node) i.next();
			if (node.isVoid() || !(node.getImpl() instanceof NormalTask))
				continue;
			result.add(node);
			collect(node, result);
		}
	}

	private void countChanges() {
		for (WbsExcelParser.Row row : sheet.rows) {
			Node node = existing.get(row);
			if (node != null && !changedFields(row, node).isEmpty())
				updated++;
		}
		if (!sheet.hasOutlineLevel)
			return;
		// a task moves when its parent or the previous task of the file under the same parent changes
		Map<WbsExcelParser.Row, WbsExcelParser.Row> parents = targetParents();
		Map<Node, WbsExcelParser.Row> rowOf = new HashMap<Node, WbsExcelParser.Row>();
		for (Map.Entry<WbsExcelParser.Row, Node> e : existing.entrySet())
			rowOf.put(e.getValue(), e.getKey());
		Object root = new Object();
		Map<Object, Node> previousInFile = new HashMap<Object, Node>(); // parent row (or root) -> last row placed under it
		for (WbsExcelParser.Row row : sheet.rows) {
			WbsExcelParser.Row parentRow = parents.get(row);
			Object key = parentRow == null ? root : parentRow;
			Node node = existing.get(row);
			Node previous = previousInFile.get(key);
			previousInFile.put(key, node);
			if (node == null)
				continue;
			Node parentNode = parentRow == null ? null : existing.get(parentRow);
			Task currentParent = ((Task) node.getImpl()).getWbsParentTask();
			if (parentRow != null && (parentNode == null || parentNode.getImpl() != currentParent)
					|| parentRow == null && currentParent != null) {
				moved++;
				continue;
			}
			Node currentPrevious = null; // previous sibling that is in the file
			for (Iterator i = WbsExcelExporter.children(model, parentNode).iterator(); i.hasNext();) {
				Node sibling = (Node) i.next();
				if (sibling == node)
					break;
				if (rowOf.containsKey(sibling))
					currentPrevious = sibling;
			}
			if (currentPrevious != previous)
				moved++;
		}
	}

	/** Row -> parent row (absent for the top level), from the Outline Level column */
	private Map<WbsExcelParser.Row, WbsExcelParser.Row> targetParents() {
		Map<WbsExcelParser.Row, WbsExcelParser.Row> parents = new HashMap<WbsExcelParser.Row, WbsExcelParser.Row>();
		List<WbsExcelParser.Row> stack = new ArrayList<WbsExcelParser.Row>(); // stack.get(i) is the last row of level i + 1
		for (WbsExcelParser.Row row : sheet.rows) {
			while (stack.size() >= row.level)
				stack.remove(stack.size() - 1);
			if (!stack.isEmpty())
				parents.put(row, stack.get(stack.size() - 1));
			stack.add(row);
		}
		return parents;
	}

	/** The fields of the row whose text differs from the task, in the order they must be set */
	private List<Field> changedFields(WbsExcelParser.Row row, Node node) {
		List<Field> changed = new ArrayList<Field>();
		for (Field field : orderedFields()) {
			String text = row.values.get(field);
			if (text == null || isReadOnly(field, node))
				continue;
			String current = currentText(field, node);
			if (!text.equals(current))
				changed.add(field);
		}
		return changed;
	}

	private List<Field> ordered;
	private List<Field> orderedFields() {
		if (ordered == null) {
			ordered = new ArrayList<Field>();
			for (String id : FIRST_FIELDS)
				for (Field field : sheet.fields)
					if (id.equals(field.getId()))
						ordered.add(field);
			for (Field field : sheet.fields)
				if (!ordered.contains(field))
					ordered.add(field);
		}
		return ordered;
	}

	private boolean isReadOnly(Field field, Node node) {
		try {
			return field.isReadOnly(node, model, null);
		} catch (RuntimeException e) {
			return true;
		}
	}

	private String currentText(Field field, Node node) {
		try {
			String text = field.getText(node, model, null);
			return text == null || Field.NOT_APPLICABLE.equals(text) ? "" : text.trim(); //$NON-NLS-1$
		} catch (RuntimeException e) {
			return ""; //$NON-NLS-1$
		}
	}

	/**
	 * @param deleteMissing delete the tasks that are not in the file
	 * @return the values that could not be set, one message each
	 */
	public List<String> apply(boolean deleteMissing) {
		List<String> errors = new ArrayList<String>();
		// the cells edited in Excel are found before anything changes: once a duration is set,
		// the finish of the file no longer matches the task but must not be applied
		Map<WbsExcelParser.Row, Node> nodes = new LinkedHashMap<WbsExcelParser.Row, Node>();
		Map<WbsExcelParser.Row, List<Field>> changes = new HashMap<WbsExcelParser.Row, List<Field>>();
		Set<Node> created = new HashSet<Node>();
		for (WbsExcelParser.Row row : sheet.rows) {
			Node node = existing.get(row);
			if (node == null) {
				node = NodeFactory.getInstance().createTask(project);
				TaskDefaultOption.getInstance().applyTo((NormalTask) node.getImpl());
				created.add(node);
				List<Field> fields = new ArrayList<Field>();
				for (Field field : orderedFields())
					if (row.values.get(field).length() > 0) // empty keeps the default of a new task
						fields.add(field);
				changes.put(row, fields);
			} else {
				changes.put(row, changedFields(row, node));
			}
			nodes.put(row, node);
		}

		project.beginUndoUpdate();
		try {
			int transactionId = project.fireMultipleTransaction(0, true);
			try {
				if (sheet.hasOutlineLevel)
					placeByLevel(nodes, created);
				else
					insertNewTasks(nodes, created);

				for (Map.Entry<WbsExcelParser.Row, Node> entry : nodes.entrySet()) {
					WbsExcelParser.Row row = entry.getKey();
					Node node = entry.getValue();
					for (Field field : changes.get(row)) {
						String text = row.values.get(field);
						try {
							setText(field, node, text);
						} catch (Exception e) {
							String message = e instanceof FieldParseException ? Messages.getString("WbsExcel.InvalidValue") : e.getMessage(); //$NON-NLS-1$
							errors.add(MessageFormat.format(Messages.getString("WbsExcel.SetError"), //$NON-NLS-1$
									String.valueOf(row.rowNumber), field.getName(), text, message == null ? e.getClass().getSimpleName() : message));
						}
					}
					((NormalTask) node.getImpl()).markTaskAsNeedingRecalculation();
				}

				if (deleteMissing)
					deleteMissing();
			} finally {
				project.fireMultipleTransaction(transactionId, false);
			}
		} finally {
			project.endUndoUpdate();
		}
		project.recalculate();
		return errors;
	}

	/**
	 * Dates are formatted like "10/26/26, 8:00 AM" with a narrow no-break space before AM, which nobody types:
	 * when the text does not parse, the same date is tried with the comma and that space,
	 * and a date without time keeps the time of the current value.
	 */
	private void setText(Field field, Node node, String text) throws FieldParseException {
		try {
			field.setText(node, model, text, null);
		} catch (FieldParseException e) {
			if (!field.isDate())
				throw e;
			String t = text.replace(' ', ' ').replace(' ', ' ').trim().replaceAll("\\s+", " "); //$NON-NLS-1$ //$NON-NLS-2$
			t = t.replaceFirst("^([^ ,]+),? (\\d)", "$1, $2"); // comma between date and time //$NON-NLS-1$ //$NON-NLS-2$
			if (t.indexOf(' ') < 0) { // date only
				String current = currentText(field, node);
				int comma = current.indexOf(", "); //$NON-NLS-1$
				if (comma > 0)
					t += current.substring(comma).replace(' ', ' ');
			}
			String[] variants = {t.replaceFirst(" ([AaPp][Mm])$", " $1"), t}; //$NON-NLS-1$ //$NON-NLS-2$
			for (String variant : variants) {
				try {
					field.setText(node, model, variant, null);
					return;
				} catch (FieldParseException ignored) {
				}
			}
			throw e;
		}
	}

	/**
	 * Puts every row under the parent given by the outline levels, top down and in file order.
	 * When a row is placed at index i of its parent, indexes 0 to i - 1 hold the rows placed before,
	 * so moving it never shifts them. Tasks not in the file end up after those of the file.
	 */
	private void placeByLevel(Map<WbsExcelParser.Row, Node> nodes, Set<Node> created) {
		Map<WbsExcelParser.Row, WbsExcelParser.Row> parents = targetParents();
		Map<Node, Integer> nextPosition = new HashMap<Node, Integer>();
		int rootPosition = 0;
		for (Map.Entry<WbsExcelParser.Row, Node> entry : nodes.entrySet()) {
			WbsExcelParser.Row parentRow = parents.get(entry.getKey());
			Node parent = parentRow == null ? null : nodes.get(parentRow);
			int position;
			if (parent == null) {
				position = rootPosition++;
			} else {
				Integer p = nextPosition.get(parent);
				position = p == null ? 0 : p.intValue();
				nextPosition.put(parent, Integer.valueOf(position + 1));
			}
			Node node = entry.getValue();
			place(node, parent, position, created.contains(node));
		}
	}

	/** Without outline levels: existing tasks stay, new ones go after the task of the previous row */
	private void insertNewTasks(Map<WbsExcelParser.Row, Node> nodes, Set<Node> created) {
		Node previous = null;
		for (Node node : nodes.values()) {
			if (created.contains(node)) {
				Node parent = previous == null ? null : parentOf(previous);
				int position = previous == null ? 0 : WbsExcelExporter.children(model, parent).indexOf(previous) + 1;
				place(node, parent, position, true);
			}
			previous = node;
		}
	}

	private Node parentOf(Node node) {
		Task parent = ((Task) node.getImpl()).getWbsParentTask();
		return parent == null ? null : model.search(parent);
	}

	private void place(Node node, Node parent, int position, boolean isNew) {
		List<Node> list = new ArrayList<Node>();
		list.add(node);
		if (isNew) {
			model.paste(parent, list, position, NodeModel.NORMAL);
			return;
		}
		List children = WbsExcelExporter.children(model, parent);
		if (position < children.size() && children.get(position) == node)
			return;
		model.move(parent, list, position, NodeModel.NORMAL);
	}

	/** Bottom up, so that the tasks of the file under a deleted task are first moved up to its place */
	private void deleteMissing() {
		Set<Node> toDelete = new HashSet<Node>(missing);
		for (int i = missing.size() - 1; i >= 0; i--) {
			Node node = missing.get(i);
			Node parent = parentOf(node);
			List kept = new ArrayList();
			for (Iterator j = WbsExcelExporter.children(model, node).iterator(); j.hasNext();) {
				Node child = (Node) j.next();
				if (!child.isVoid() && !toDelete.contains(child))
					kept.add(child);
			}
			if (!kept.isEmpty())
				model.move(parent, kept, WbsExcelExporter.children(model, parent).indexOf(node) + 1, NodeModel.NORMAL);
			model.remove(node, NodeModel.NORMAL, true);
		}
	}
}
