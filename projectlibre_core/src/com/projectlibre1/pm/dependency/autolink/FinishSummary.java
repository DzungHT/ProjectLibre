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

import java.text.DateFormat;
import java.text.MessageFormat;

import com.projectlibre1.datatype.Duration;
import com.projectlibre1.options.CalendarOption;
import com.projectlibre1.options.EditOption;
import com.projectlibre1.pm.calendar.WorkCalendar;
import com.projectlibre1.pm.resource.plan.ResourcePlanOptions;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.strings.Messages;

/**
 * Finish of the schedule after Auto Link compared to the deadline of the Resource Plan, see docs/relink-design.md:
 * on time, on time but in the buffer kept before the deadline, or late, in working days.
 */
public final class FinishSummary {
	private static final long MILLIS_PER_MINUTE = 60L * 1000;
	private static final long MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;

	private FinishSummary() {
	}

	/** A sentence for a finish (estimated or calculated), with the deadline if the Resource Plan has one */
	public static String finishText(Project project, long finish, String key) {
		DateFormat format = EditOption.getInstance().getShortDateFormat();
		String text = MessageFormat.format(Messages.getString(key), format.format(finish));
		ResourcePlanOptions options = ResourcePlanOptions.load(project);
		if (options.getDeadline() == 0)
			return text + " " + Messages.getString("AutoLink.NoDeadline"); //$NON-NLS-1$ //$NON-NLS-2$
		WorkCalendar calendar = project.getEffectiveWorkCalendar();
		long deadline = calendar.adjustInsideCalendar(options.getDeadline() + 24 * MILLIS_PER_HOUR, true); // end of the day
		String day = format.format(options.getDeadline());
		if (finish > deadline)
			return text + " " + MessageFormat.format(Messages.getString("AutoLink.Late"), day, days(calendar, finish, deadline)); //$NON-NLS-1$ //$NON-NLS-2$
		String left = days(calendar, deadline, finish);
		boolean beforeBuffer = workingDays(calendar, deadline, finish) >= options.getBuffer() - 1e-6;
		return text + " " + MessageFormat.format(Messages.getString(beforeBuffer ? "AutoLink.OnTime" : "AutoLink.InBuffer"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				day, left, String.valueOf(options.getBuffer()));
	}

	/** Working days from a date to a later one, rounded to a tenth */
	private static String days(WorkCalendar calendar, long later, long earlier) {
		double days = workingDays(calendar, later, earlier);
		return days == Math.rint(days) ? String.valueOf((long) days) : String.valueOf(Math.round(days * 10) / 10.0);
	}

	private static double workingDays(WorkCalendar calendar, long later, long earlier) {
		later -= later % MILLIS_PER_MINUTE; // compare() counts a working day too many for some dates within a minute
		earlier -= earlier % MILLIS_PER_MINUTE;
		if (later <= earlier)
			return 0;
		double hours = Duration.millis(calendar.compare(later, earlier, false)) / (double) MILLIS_PER_HOUR;
		return hours / CalendarOption.getInstance().getHoursPerDay();
	}
}
