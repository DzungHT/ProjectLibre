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

import java.util.LinkedHashMap;
import java.util.Map;

import com.projectlibre1.pm.dependency.autolink.AutoLinkOptions;
import com.projectlibre1.pm.task.NormalTask;

/**
 * What Auto Schedule does: from which date, keeping the assignments or not, and which role does each activity.
 * The activity of a task is found as Auto Link finds it, see AutoLinkOptions.activityIndex().
 */
public class AutoScheduleOptions {
	/** Default role of each activity of the ULNL: GP (solution) by BA, PT (development) by DEV, KT (test) by TESTER */
	public static final String[][] DEFAULT_ROLES = { { "GP", "BA" }, { "PT", "DEV" }, { "KT", "TESTER" } }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$

	private final AutoLinkOptions linkOptions;
	private final Map<String, String> roles = new LinkedHashMap<String, String>();
	private long start = 0;
	private boolean keepAssignments = true;
	private boolean criticalFirst = false;

	/** @param linkOptions the activities and the activities with a required skill, as for Auto Link */
	public AutoScheduleOptions(AutoLinkOptions linkOptions) {
		this.linkOptions = linkOptions;
		for (String[] role : DEFAULT_ROLES)
			roles.put(role[0], role[1]);
	}

	public AutoLinkOptions getLinkOptions() {
		return linkOptions;
	}
	/** Activity code of a task, null if it has none */
	public String activityOf(NormalTask task) {
		int index = linkOptions.activityIndex(task);
		return index < 0 ? null : linkOptions.getActivities()[index];
	}
	/** Role doing an activity, null if the activity is not scheduled */
	public String getRole(String activity) {
		return activity == null ? null : roles.get(activity);
	}
	public void setRole(String activity, String role) {
		if (role == null)
			roles.remove(activity);
		else
			roles.put(activity, role);
	}
	/** True if the tasks of the activity need their required skill (development) */
	public boolean hasRequiredSkill(String activity) {
		return linkOptions.isLaneActivity(activity);
	}
	/** No task is scheduled before this date. 0 for the start of the project */
	public long getStart() {
		return start;
	}
	public void setStart(long start) {
		this.start = start;
	}
	/** True to keep the resources of the assigned tasks (only their dates change), false to assign all again */
	public boolean isKeepAssignments() {
		return keepAssignments;
	}
	public void setKeepAssignments(boolean keepAssignments) {
		this.keepAssignments = keepAssignments;
	}
	/** True to give the resources to the critical tasks first, false (default) in the order of the WBS */
	public boolean isCriticalFirst() {
		return criticalFirst;
	}
	public void setCriticalFirst(boolean criticalFirst) {
		this.criticalFirst = criticalFirst;
	}
}
