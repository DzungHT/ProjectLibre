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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.projectlibre1.grouping.core.transform.filtering.TaskNameFilter;
import com.projectlibre1.pm.task.NormalTask;

/**
 * What Auto Link links. The activities (activity codes of the tasks, like GP, PT, KT) are in their order of work.
 * <ul>
 * <li>Activities: each task of an activity is linked after the tasks of the previous activity found in the same
 * group, a row (the parent of the task) or a function (the parent of the row), chosen for each pair of activities.
 * When there is none, the activity before is used, and so on</li>
 * <li>Sequences: the tasks of an activity are linked one after the other in the order of the WBS. Assigned tasks
 * make one sequence per resource. The other ones make one sequence per activity, or per activity and lane for
 * the lane activities (the development tasks of BE, FE, Mobile...)</li>
 * </ul>
 * Cancelled tasks and tasks without work are skipped: the tasks around them are linked together.
 * The activity of a task is its activity code, or found from its name, see activityIndex().
 */
public class AutoLinkOptions {
	/** Groups of the tasks linked between activities: number of levels above the task */
	public static final int ROW = 1;
	public static final int FUNCTION = 2;

	private final String[] activities;
	private final int[] scopes; // scopes[i]: group of the link from activities[i] to activities[i + 1]
	private final Set<String> laneActivities;
	private boolean linkActivities = true;
	private boolean linkSequences = true;
	private boolean relink = false;
	private boolean readyOrder = true;
	private boolean joinConstraints = true;
	private String[] normalizedNames = null;

	public AutoLinkOptions(String[] activities, int[] scopes, String[] laneActivities) {
		if (scopes.length != activities.length - 1)
			throw new IllegalArgumentException("One scope for each pair of activities"); //$NON-NLS-1$
		this.activities = activities;
		this.scopes = scopes;
		this.laneActivities = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(laneActivities)));
	}

	public String[] getActivities() {
		return activities;
	}
	/** Index of an activity code, -1 if it is not linked */
	public int indexOf(String activity) {
		for (int i = 0; i < activities.length; i++)
			if (activities[i].equals(activity))
				return i;
		return -1;
	}
	/** Group of the link from the activity at index - 1 to the one at index */
	public int getScope(int index) {
		return scopes[index - 1];
	}
	public void setScope(int index, int scope) {
		scopes[index - 1] = scope;
	}
	/**
	 * Index of the activity of a task, -1 if it has none or it is not linked. Without activity code, a leaf task
	 * whose name starts with the name of an activity has this activity, ignoring case and accents (for the projects
	 * created before the activity codes)
	 */
	public int activityIndex(NormalTask task) {
		if (task.getActivity() != null)
			return indexOf(task.getActivity());
		if (normalizedNames == null || task.isWbsParent() || task.getName() == null)
			return -1;
		String name = TaskNameFilter.normalize(task.getName().trim());
		for (int i = 0; i < normalizedNames.length; i++)
			if (name.startsWith(normalizedNames[i]))
				return i;
		return -1;
	}
	/** Names of the activities, in the order of the codes, to find the activity of the tasks without code */
	public void setActivityNames(String[] names) {
		normalizedNames = new String[names.length];
		for (int i = 0; i < names.length; i++)
			normalizedNames[i] = TaskNameFilter.normalize(names[i]);
	}
	public boolean isLaneActivity(String activity) {
		return laneActivities.contains(activity);
	}
	public boolean isLinkActivities() {
		return linkActivities;
	}
	public void setLinkActivities(boolean linkActivities) {
		this.linkActivities = linkActivities;
	}
	public boolean isLinkSequences() {
		return linkSequences;
	}
	public void setLinkSequences(boolean linkSequences) {
		this.linkSequences = linkSequences;
	}
	/**
	 * Re-link (docs/relink-design.md): one sequence per person, whatever the activity, and none for the tasks not
	 * assigned. The leveling delays of the tasks are removed, the dates come from the links
	 */
	public boolean isRelink() {
		return relink;
	}
	public void setRelink(boolean relink) {
		this.relink = relink;
	}
	/** Re-link: the tasks of a person in the order they can start (true), or in the order of the WBS */
	public boolean isReadyOrder() {
		return readyOrder;
	}
	public void setReadyOrder(boolean readyOrder) {
		this.readyOrder = readyOrder;
	}
	/** Re-link: the first task of a person starts no earlier than the day the person is available */
	public boolean isJoinConstraints() {
		return joinConstraints;
	}
	public void setJoinConstraints(boolean joinConstraints) {
		this.joinConstraints = joinConstraints;
	}
}
