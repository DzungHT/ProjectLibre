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
package com.projectlibre1.options;

import java.util.prefs.Preferences;

import com.projectlibre1.pm.availability.Availability;
import com.projectlibre1.pm.availability.AvailabilityTable;
import com.projectlibre1.pm.availability.AvailabilityWarning;
import com.projectlibre1.pm.resource.Resource;
import com.projectlibre1.pm.scheduling.SchedulingType;
import com.projectlibre1.pm.task.NormalTask;

/**
 * Default values applied to tasks and assignments created by the user (not to those read from files).
 * Values are persisted in the user preferences.
 */
public class TaskDefaultOption {
	private static final String SCHEDULING_TYPE_KEY = "taskDefault.schedulingType";
	private static final String ASSIGN_MAX_UNITS_KEY = "taskDefault.assignMaxUnits";
	private static TaskDefaultOption instance = null;

	public static TaskDefaultOption getInstance() {
		if (instance == null)
			instance = new TaskDefaultOption();
		return instance;
	}

	int schedulingType = SchedulingType.FIXED_WORK;
	boolean assignMaxUnits = true;

	private TaskDefaultOption() {
		load();
	}

	public int getSchedulingType() {
		return schedulingType;
	}

	public void setSchedulingType(int schedulingType) {
		this.schedulingType = schedulingType;
	}

	/**
	 * Apply the defaults to a newly created task
	 */
	public void applyTo(NormalTask task) {
		task.setSchedulingType(schedulingType);
	}

	public boolean isAssignMaxUnits() {
		return assignMaxUnits;
	}

	public void setAssignMaxUnits(boolean assignMaxUnits) {
		this.assignMaxUnits = assignMaxUnits;
	}

	/**
	 * Units used when the user assigns a resource without entering units:
	 * the resource's max units for work resources if enabled, otherwise 100%
	 */
	public double getAssignmentUnits(Resource resource) {
		return getAssignmentUnits(resource, null);
	}
	/**
	 * Same for an assignment to a task: the max units of the resource when the task starts, not today, as the
	 * availability can have several periods. If the resource is not available then (0%, like before joining the
	 * project), the max units of the first period after the start in which it is available
	 */
	public double getAssignmentUnits(Resource resource, NormalTask task) {
		if (assignMaxUnits && resource != null && resource.isLabor()) {
			double maxUnits = task == null ? resource.getMaximumUnits() : maximumUnitsFrom(resource, task.getStart());
			if (maxUnits > 0)
				return maxUnits;
		}
		return 1.0D;
	}
	private static double maximumUnitsFrom(Resource resource, long date) {
		AvailabilityTable table = resource.getAvailabilityTable();
		if (table == null)
			return resource.getMaximumUnits();
		long from = AvailabilityWarning.availableFrom(resource, date);
		return from == AvailabilityWarning.NEVER ? 0 : ((Availability) table.findActive(from)).getMaximumUnits();
	}

	private static Preferences getPreferences() {
		return Preferences.userNodeForPackage(TaskDefaultOption.class);
	}

	private void load() {
		try {
			int value = getPreferences().getInt(SCHEDULING_TYPE_KEY, schedulingType);
			if (value == SchedulingType.FIXED_UNITS || value == SchedulingType.FIXED_DURATION || value == SchedulingType.FIXED_WORK)
				schedulingType = value;
			assignMaxUnits = getPreferences().getBoolean(ASSIGN_MAX_UNITS_KEY, assignMaxUnits);
		} catch (Exception e) { // preferences not available, keep defaults
		}
	}

	public void save() {
		try {
			getPreferences().putInt(SCHEDULING_TYPE_KEY, schedulingType);
			getPreferences().putBoolean(ASSIGN_MAX_UNITS_KEY, assignMaxUnits);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
