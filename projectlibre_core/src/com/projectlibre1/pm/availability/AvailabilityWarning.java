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
package com.projectlibre1.pm.availability;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.projectlibre1.options.EditOption;
import com.projectlibre1.pm.assignment.Assignment;
import com.projectlibre1.pm.resource.Resource;
import com.projectlibre1.pm.task.NormalTask;
import com.projectlibre1.strings.Messages;

/**
 * Resources assigned while they are not available: a period of their availability with Max Units of 0%, like
 * before joining the project, overlaps their assignment. The task keeps its dates (ProjectLibre does not level
 * the resources) and shows an indicator: Auto Schedule places the tasks in the periods in which the resources
 * are available.
 */
public final class AvailabilityWarning {
	/** availableFrom() of a resource never available after a date */
	public static final long NEVER = Long.MAX_VALUE;

	private AvailabilityWarning() {
	}

	/**
	 * First date, from a date, when a work resource is available: the date itself if its Max Units are over 0%
	 * then, the start of the next period with Max Units over 0%, or NEVER
	 */
	public static long availableFrom(Resource resource, long date) {
		AvailabilityTable table = resource.getAvailabilityTable();
		if (table == null || !resource.isLabor())
			return date;
		Availability active = (Availability) table.findActive(date);
		if (active.getMaximumUnits() > 0)
			return date;
		for (Iterator i = table.getList().iterator(); i.hasNext();) {
			Availability availability = (Availability) i.next();
			if (availability.getStart() > active.getStart() && availability.getMaximumUnits() > 0)
				return availability.getStart();
		}
		return NEVER;
	}

	/**
	 * For the indicator of a task: one line per assigned resource and period of 0% overlapping its assignment,
	 * empty when all are available. Completed and cancelled tasks have none
	 */
	public static List<String> unavailablePeriods(NormalTask task) {
		List<String> lines = new ArrayList<String>();
		if (task.isComplete() || task.isCancelled())
			return lines;
		DateFormat format = null;
		for (Iterator i = task.getAssignments().iterator(); i.hasNext();) {
			Assignment assignment = (Assignment) i.next();
			Resource resource = assignment.getResource();
			if (assignment.isDefault() || resource == null || !resource.isLabor() || resource.getAvailabilityTable() == null)
				continue;
			long start = assignment.getStart(), end = assignment.getEnd();
			List periods = resource.getAvailabilityTable().getList();
			for (int p = 0; p < periods.size(); p++) {
				Availability period = (Availability) periods.get(p);
				boolean first = period.isFirst(), last = p == periods.size() - 1;
				long periodEnd = last ? NEVER : ((Availability) periods.get(p + 1)).getStart();
				if (period.getMaximumUnits() > 0 || (!first && period.getStart() >= end) || periodEnd <= start)
					continue;
				if (format == null)
					format = EditOption.getInstance().getShortDateFormat(); // the format of the dates of the sheets
				String key = first && last ? "AvailabilityWarning.Always" : first ? "AvailabilityWarning.Before" //$NON-NLS-1$ //$NON-NLS-2$
						: last ? "AvailabilityWarning.From" : "AvailabilityWarning.Between"; //$NON-NLS-1$ //$NON-NLS-2$
				lines.add(MessageFormat.format(Messages.getString(key), resource.getName(),
						first ? "" : format.format(period.getStart()), last ? "" : format.format(periodEnd))); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return lines;
	}
}
