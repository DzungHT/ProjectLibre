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
package com.projectlibre1.pm.resource.plan;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projectlibre1.grouping.core.Node;
import com.projectlibre1.grouping.core.NodeFactory;
import com.projectlibre1.grouping.core.model.NodeModel;
import com.projectlibre1.pm.assignment.Assignment;
import com.projectlibre1.pm.availability.Availability;
import com.projectlibre1.pm.availability.AvailabilityTable;
import com.projectlibre1.pm.resource.ResourceImpl;
import com.projectlibre1.pm.resource.labor.LaborCatalog;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.strings.Messages;

/**
 * Writes a Resource Plan in the project. The people of the lines that are not existing resources become Generic
 * (TBD) resources named like "DEV BE J #2 (TBD)", with the role, level and skill of their line, and Max Units of
 * point × allocation in the period of the line, 0% before and after. The TBD resources of the previous plan
 * without assignments are replaced; the ones with assignments are kept and counted in their line. The existing
 * resources only get the period and allocation of their line when they were changed in the plan (this change
 * of their availability cannot be undone). The options are saved in the project. A single undo step.
 */
public class ResourcePlanWriter {
	private static final String TBD = " (TBD)"; //$NON-NLS-1$

	private ResourcePlanWriter() {
	}

	/** @return warnings about the TBD resources kept because they have assignments */
	public static List<String> apply(ResourcePlan plan) throws Exception {
		Project project = plan.getProject();
		NodeModel model = project.getResourceModel();
		List<String> warnings = new ArrayList<String>();

		// the TBD resources of the previous plan, and the lines that keep the assigned ones
		Set<ResourceImpl> remove = newIdentitySet();
		Set<ResourceImpl> assigned = newIdentitySet();
		for (Object object : project.getResourcePool().getResourceList()) {
			if (!(object instanceof ResourceImpl))
				continue;
			ResourceImpl resource = (ResourceImpl) object;
			if (!resource.isGeneric() || !resource.isLabor() || resource.getLaborRole() == null)
				continue;
			if (hasAssignments(resource))
				assigned.add(resource);
			else
				remove.add(resource);
		}
		Map<ResourcePlan.Line, Integer> kept = new IdentityHashMap<ResourcePlan.Line, Integer>();
		Set<String> names = new HashSet<String>();
		for (ResourcePlan.Line line : plan.getLines()) {
			if (line.isExisting())
				continue;
			int count = 0;
			for (ResourceImpl resource : line.getResources())
				if (assigned.remove(resource)) {
					count++;
					names.add(resource.getName());
				}
			kept.put(line, count);
			if (count > line.getPeople())
				warnings.add(MessageFormat.format(Messages.getString("ResourcePlan.KeptAssigned"), name(line, 0), String.valueOf(count))); //$NON-NLS-1$
		}
		for (ResourceImpl resource : assigned) {
			names.add(resource.getName());
			warnings.add(MessageFormat.format(Messages.getString("ResourcePlan.KeptResource"), resource.getName())); //$NON-NLS-1$
		}

		project.beginUndoUpdate();
		try {
			List<Node> oldNodes = new ArrayList<Node>();
			findNodes(model, null, remove, oldNodes);
			if (!oldNodes.isEmpty())
				model.remove(oldNodes, NodeModel.NORMAL);

			List<Node> newNodes = new ArrayList<Node>();
			for (ResourcePlan.Line line : plan.getLines()) {
				if (line.isExisting())
					continue;
				int number = 0;
				for (int i = kept.get(line); i < line.getPeople(); i++) {
					String name;
					do {
						name = name(line, ++number);
					} while (!names.add(name));
					newNodes.add(createResource(project, line, name));
				}
			}
			if (!newNodes.isEmpty())
				model.paste(null, newNodes, lastResourcePosition(model), NodeModel.NORMAL);
			for (ResourcePlan.Line line : plan.getLines())
				if (line.isExisting() && line.isEdited())
					for (ResourceImpl resource : line.getResources()) {
						setAvailability(resource, line);
						project.getResourcePool().fireUpdateEvent(ResourcePlanWriter.class, resource);
					}
			plan.getOptions().getAutoLines().clear();
			for (ResourcePlan.Line line : plan.getLines())
				if (line.isAuto() && !line.isExisting())
					plan.getOptions().getAutoLines().add(ResourcePlan.lineKey(line));
			plan.getOptions().save(project);
		} finally {
			project.endUndoUpdate();
		}
		return warnings;
	}

	private static Set<ResourceImpl> newIdentitySet() {
		return Collections.newSetFromMap(new IdentityHashMap<ResourceImpl, Boolean>());
	}

	private static boolean hasAssignments(ResourceImpl resource) {
		for (Iterator i = resource.getAssignments().iterator(); i.hasNext();)
			if (!((Assignment) i.next()).isDefault())
				return true;
		return false;
	}

	/** Nodes of the resources to remove. A resource with resources below it in the RBS is kept */
	private static void findNodes(NodeModel model, Node parent, Set<ResourceImpl> resources, List<Node> nodes) {
		List children = model.getChildren(parent);
		if (children == null)
			return;
		for (Iterator i = children.iterator(); i.hasNext();) {
			Node node = (Node) i.next();
			if (node.isVoid())
				continue;
			if (model.hasChildren(node))
				findNodes(model, node, resources, nodes);
			else if (resources.contains(node.getImpl()))
				nodes.add(node);
		}
	}

	/** Position after the last resource, before the blank rows */
	private static int lastResourcePosition(NodeModel model) {
		List children = model.getChildren(null);
		int position = 0;
		if (children != null)
			for (int i = 0; i < children.size(); i++)
				if (!((Node) children.get(i)).isVoid())
					position = i + 1;
		return position;
	}

	/** "DEV BE J #2 (TBD)", without the number if it is 0 */
	static String name(ResourcePlan.Line line, int number) {
		StringBuilder name = new StringBuilder(line.getRole());
		if (line.getSkill().length() > 0)
			name.append(' ').append(line.getSkill().replace("; ", "+")); //$NON-NLS-1$ //$NON-NLS-2$
		if (line.getLevel() >= 0) {
			LaborCatalog.Category category = LaborCatalog.getInstance().find(line.getRole(), line.getLevel());
			name.append(' ').append(category == null ? LaborCatalog.levelAbbreviation(line.getLevel()) : category.getLevelCode());
		}
		if (number > 0)
			name.append(" #").append(number); //$NON-NLS-1$
		return name.append(TBD).toString();
	}

	private static Node createResource(Project project, ResourcePlan.Line line, String name) throws Exception {
		Node node = NodeFactory.getInstance().createResource(project.getResourcePool());
		ResourceImpl resource = (ResourceImpl) node.getImpl();
		resource.setName(name);
		resource.setGeneric(true);
		resource.setLaborRole(line.getRole());
		if (line.getLevel() >= 0)
			resource.setLaborLevel(String.valueOf(line.getLevel()));
		if (line.getSkill().length() > 0)
			resource.setSkills(line.getSkill());
		setAvailability(resource, line); // after the role and level, which scale the default period by the point
		return node;
	}

	/**
	 * Replaces the availability of a resource: 0%, point × allocation in the period of the line, then 0%. An open
	 * start or end of the line (an existing resource available since always or for ever) stays open
	 */
	private static void setAvailability(ResourceImpl resource, ResourcePlan.Line line) throws Exception {
		AvailabilityTable table = resource.getAvailabilityTable();
		List periods = new ArrayList(table.getList());
		for (int i = periods.size() - 1; i > 0; i--)
			table.remove((Availability) periods.get(i));
		double units = line.getPoint() * line.getAllocation();
		((Availability) periods.get(0)).setMaximumUnits(line.isOpenFrom() ? units : 0);
		if (line.getTo() <= line.getFrom())
			return;
		if (!line.isOpenFrom())
			((Availability) table.newValueObject(line.getFrom())).setMaximumUnits(units);
		if (!line.isOpenTo())
			((Availability) table.newValueObject(line.getTo())).setMaximumUnits(0);
	}
}
