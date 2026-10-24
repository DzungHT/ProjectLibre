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
package com.projectlibre1.pm.criticalpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.projectlibre1.datatype.Duration;
import com.projectlibre1.options.ScheduleOption;
import com.projectlibre1.pm.calendar.WorkCalendar;
import com.projectlibre1.pm.dependency.Dependency;
import com.projectlibre1.pm.dependency.DependencyType;
import com.projectlibre1.pm.scheduling.ConstraintType;
import com.projectlibre1.pm.task.Project;
import com.projectlibre1.pm.task.Task;

/**
 * Checks that the dates of the tasks follow their links and constraints, independently of the order in which the
 * schedule calculated them: for a link, the start (finish) of the successor is not before the date the
 * predecessor allows, the lag being added in the calendar of the predecessor like the schedule does. For a
 * constraint, the start or finish is on the right side of its date. A missed deadline is reported too.
 * The tasks with an actual start are not checked: their dates come from their progress.
 * A link not followed is an error, unless the successor has a constraint the schedule honors before the links
 * (SNLT, FNLT, MSO, MFO): it is then a conflict, like a constraint not followed because of the links.
 */
public class ScheduleValidator {
	public static final int ERROR = 0;
	public static final int CONFLICT = 1;
	public static final int WARNING = 2;

	public static final int LINK = 0;
	public static final int CONSTRAINT = 1;
	public static final int DEADLINE = 2;

	/** Differences of up to a minute are rounding */
	private static final long TOLERANCE = 60 * 1000L;

	/** A date of a task not following a rule */
	public static class Issue {
		private final Task task;
		private final int severity;
		private final int kind;
		private final Dependency dependency; // for a link
		private final boolean finish; // the date checked is the finish of the task, else its start
		private final long date; // the date of the task
		private final long limit; // the earliest date the rule allows, the latest for SNLT, FNLT and a deadline
		Issue(Task task, int severity, int kind, Dependency dependency, boolean finish, long date, long limit) {
			this.task = task;
			this.severity = severity;
			this.kind = kind;
			this.dependency = dependency;
			this.finish = finish;
			this.date = date;
			this.limit = limit;
		}
		public Task getTask() {
			return task;
		}
		public int getSeverity() {
			return severity;
		}
		public int getKind() {
			return kind;
		}
		/** The link not followed, null for a constraint or a deadline */
		public Dependency getDependency() {
			return dependency;
		}
		public boolean isFinish() {
			return finish;
		}
		public long getDate() {
			return date;
		}
		public long getLimit() {
			return limit;
		}
	}

	private final List<Issue> issues = new ArrayList<Issue>();
	private int links = 0;
	private int constraints = 0;
	private int started = 0;

	public static ScheduleValidator validate(Project project) {
		ScheduleValidator validator = new ScheduleValidator();
		validator.check(project, ScheduleOption.getInstance().isHonorRequiredDates());
		return validator;
	}

	private void check(Project project, boolean honorRequiredDates) {
		List<Issue> conflicts = new ArrayList<Issue>(), warnings = new ArrayList<Issue>();
		for (Iterator i = project.getTaskOutlineIterator(); i.hasNext();) {
			Object impl = i.next();
			if (!(impl instanceof Task))
				continue;
			Task task = (Task) impl;
			if (task.isExternal())
				continue;
			if (task.getActualStart() != 0) {
				started++;
				continue;
			}
			int constraintType = task.getConstraintType();
			boolean hardConstraint = honorRequiredDates && isHard(constraintType);
			for (Iterator j = task.getPredecessorList().iterator(); j.hasNext();) {
				Dependency dependency = (Dependency) j.next();
				if (dependency.isDisabled() || !(dependency.getPredecessor() instanceof Task))
					continue;
				links++;
				add(checkLink(task, dependency, hardConstraint), conflicts);
			}
			if (constraintType != ConstraintType.ASAP && constraintType != ConstraintType.ALAP && task.getConstraintDate() != 0) {
				constraints++;
				add(checkConstraint(task, constraintType), conflicts);
			}
			long deadline = task.getDeadline();
			if (deadline != 0 && task.getEnd() > deadline + TOLERANCE)
				warnings.add(new Issue(task, WARNING, DEADLINE, null, true, task.getEnd(), deadline));
		}
		issues.addAll(conflicts);
		issues.addAll(warnings);
	}

	/** Adds an error at once, a conflict later: the errors come first */
	private void add(Issue issue, List<Issue> conflicts) {
		if (issue != null)
			(issue.getSeverity() == ERROR ? issues : conflicts).add(issue);
	}

	private static boolean isHard(int constraintType) {
		return constraintType == ConstraintType.SNLT || constraintType == ConstraintType.FNLT
				|| constraintType == ConstraintType.MSO || constraintType == ConstraintType.MFO;
	}

	/** The successor is not before the date of the link, see Dependency.calcForwardDependencyDate */
	private static Issue checkLink(Task successor, Dependency dependency, boolean hardConstraint) {
		Task predecessor = (Task) dependency.getPredecessor();
		int type = dependency.getDependencyType();
		boolean fromFinish = type == DependencyType.FS || type == DependencyType.FF;
		boolean toFinish = type == DependencyType.FF || type == DependencyType.SF;
		long from = fromFinish ? predecessor.getEnd() : predecessor.getStart();
		long date = toFinish ? successor.getEnd() : successor.getStart();
		if (from == 0 || date == 0)
			return null;
		long lag = dependency.getLeadValue();
		WorkCalendar calendar = dependency.getEffectiveWorkCalendar();
		long limit = Duration.millis(lag) == 0 ? from : calendar.add(from, lag, !successor.hasDuration());
		if (date + TOLERANCE >= limit)
			return null;
		return new Issue(successor, hardConstraint ? CONFLICT : ERROR, LINK, dependency, toFinish, date, limit);
	}

	/**
	 * SNET, FNET and the early side of MSO, MFO are always honored: before their date is an error. After the date
	 * of SNLT, FNLT, MSO, MFO is a conflict: the links win when the schedule does not honor the constraints
	 */
	private static Issue checkConstraint(Task task, int constraintType) {
		long limit = task.getConstraintDate();
		boolean finish = constraintType == ConstraintType.FNET || constraintType == ConstraintType.FNLT || constraintType == ConstraintType.MFO;
		long date = finish ? task.getEnd() : task.getStart();
		boolean notBefore = constraintType != ConstraintType.SNLT && constraintType != ConstraintType.FNLT;
		boolean notAfter = constraintType != ConstraintType.SNET && constraintType != ConstraintType.FNET;
		if (notBefore && date + TOLERANCE < limit)
			return new Issue(task, ERROR, CONSTRAINT, null, finish, date, limit);
		if (notAfter && date > limit + TOLERANCE)
			return new Issue(task, CONFLICT, CONSTRAINT, null, finish, date, limit);
		return null;
	}

	/** Errors, then conflicts, then warnings, each in the order of the WBS */
	public List<Issue> getIssues() {
		return Collections.unmodifiableList(issues);
	}
	public int count(int severity) {
		int count = 0;
		for (Issue issue : issues)
			if (issue.getSeverity() == severity)
				count++;
		return count;
	}
	/** Number of links checked */
	public int getLinkCount() {
		return links;
	}
	/** Number of constraints with a date checked */
	public int getConstraintCount() {
		return constraints;
	}
	/** Number of tasks not checked because they have started */
	public int getStartedCount() {
		return started;
	}
}
