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
package com.projectlibre1.pm.assignment.board;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projectlibre1.pm.calendar.WorkCalendar;
import com.projectlibre1.pm.dependency.autolink.FinishSummary;
import com.projectlibre1.pm.resource.Resource;

/**
 * When the tasks selected in the Gantt are done, from the dates of the project: in all, by activity (and by skill of
 * the development), by person, and by cluster below the summaries selected. See docs/assignment-design.md section 9.
 */
public class SelectionTimeline {
	public static final int TOTAL = 0, SECTION = 1, ACTIVITY = 2, SKILL = 3, PERSON = 4, CLUSTER = 5;
	/** Sections, the name of their line */
	public static final String ACTIVITIES = "activities", PEOPLE = "people", CLUSTERS = "clusters"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	/** A line: what is done from its first start to its last finish */
	public static class Line {
		final int kind;
		final String name;
		String activity, role, skill;
		Resource resource;
		long start = Long.MAX_VALUE, finish = 0;
		double hours, days;
		int tasks, unassigned;

		Line(int kind, String name) {
			this.kind = kind;
			this.name = name;
		}
		void add(AssignmentBoard.Row row, double share) {
			start = Math.min(start, row.getTask().getStart());
			finish = Math.max(finish, row.getTask().getEnd());
			hours += row.getHours() * share;
			tasks++;
			if (row.getResources().isEmpty())
				unassigned++;
		}
		public int getKind() { return kind; }
		/** Name of the task, of the person or of the section; the code for an activity, the skill for a skill */
		public String getName() { return name; }
		/** Code of the activity of an ACTIVITY or SKILL line */
		public String getActivity() { return activity; }
		/** Role of an ACTIVITY, SKILL or PERSON line */
		public String getRole() { return role; }
		public String getSkill() { return skill; }
		public Resource getResource() { return resource; }
		/** 0 if the line has no task */
		public long getStart() { return tasks == 0 ? 0 : start; }
		public long getFinish() { return finish; }
		/** Working days from the start to the finish */
		public double getDays() { return days; }
		/** Effort, hours at point 1.0; of a person, its share of the tasks */
		public double getHours() { return hours; }
		public int getTasks() { return tasks; }
		/** Tasks without people, whose dates do not follow any person */
		public int getUnassigned() { return unassigned; }
	}

	private final List<Line> lines = new ArrayList<Line>();
	private Line total;

	/**
	 * @param activityCodes the codes of the activities in their order (GP, PT, KT); the other activities come after
	 */
	public static SelectionTimeline create(GanttAssigner assigner, String[] activityCodes, WorkCalendar calendar) {
		SelectionTimeline timeline = new SelectionTimeline();
		timeline.read(assigner, activityCodes);
		for (Line line : timeline.lines)
			if (line.tasks > 0)
				line.days = FinishSummary.workingDays(calendar, line.finish, line.start);
		return timeline;
	}

	private void read(GanttAssigner assigner, String[] activityCodes) {
		AssignmentBoard board = assigner.getBoard();
		List<AssignmentBoard.Row> rows = assigner.getSelected();
		if (rows.isEmpty())
			return;
		total = new Line(TOTAL, names(assigner));
		for (AssignmentBoard.Row row : rows)
			total.add(row, 1);
		lines.add(total);

		// by activity, in the order of the codes, and by skill when the tasks of an activity need several
		final List<String> order = Arrays.asList(activityCodes);
		Map<String, Line> activities = new LinkedHashMap<String, Line>();
		Map<String, Map<String, Line>> skills = new LinkedHashMap<String, Map<String, Line>>();
		for (AssignmentBoard.Row row : rows) {
			String code = row.getTask().getActivity();
			Line line = activities.get(code);
			if (line == null) {
				activities.put(code, line = new Line(ACTIVITY, code));
				line.activity = code;
				line.role = row.getRole();
				skills.put(code, new LinkedHashMap<String, Line>());
			}
			line.add(row, 1);
			Line skill = skills.get(code).get(row.getSkill());
			if (skill == null) {
				skills.get(code).put(row.getSkill(), skill = new Line(SKILL, row.getSkill()));
				skill.activity = code;
				skill.role = row.getRole();
				skill.skill = row.getSkill();
			}
			skill.add(row, 1);
		}
		List<String> codes = new ArrayList<String>(activities.keySet());
		Collections.sort(codes, (a, b) -> Integer.compare(rank(order, a), rank(order, b)));
		lines.add(new Line(SECTION, ACTIVITIES));
		for (String code : codes) {
			lines.add(activities.get(code));
			if (skills.get(code).size() > 1)
				lines.addAll(skills.get(code).values());
		}

		// by person, in the order of the people of the board, then the resources without role
		Map<Resource, Line> people = new IdentityHashMap<Resource, Line>();
		Set<Resource> resources = new LinkedHashSet<Resource>();
		for (AssignmentBoard.Row row : rows)
			for (Resource resource : row.getResources()) {
				Line line = people.get(resource);
				if (line == null) {
					people.put(resource, line = new Line(PERSON, resource.getName()));
					line.resource = resource;
					AssignmentBoard.Person person = board.getPerson(resource);
					line.role = person == null ? "" : person.getRole(); //$NON-NLS-1$
					line.skill = person == null ? "" : person.getSkill(); //$NON-NLS-1$
					resources.add(resource);
				}
				line.add(row, board.share(row, resource));
			}
		if (!people.isEmpty()) {
			lines.add(new Line(SECTION, PEOPLE));
			for (AssignmentBoard.Person person : board.getPersons())
				if (people.containsKey(person.getResource())) {
					lines.add(people.get(person.getResource()));
					resources.remove(person.getResource());
				}
			for (Resource resource : resources)
				lines.add(people.get(resource));
		}

		// by cluster: the summaries below the summaries selected, or the summaries selected if several are
		List<AssignmentBoard.Row> clusters = new ArrayList<AssignmentBoard.Row>();
		for (AssignmentBoard.Row summary : assigner.getSummaries())
			for (AssignmentBoard.Row child : summary.getChildren())
				if (!child.isActivity())
					clusters.add(child);
		if (clusters.isEmpty() && assigner.getSummaries().size() > 1)
			clusters.addAll(assigner.getSummaries());
		if (!clusters.isEmpty()) {
			lines.add(new Line(SECTION, CLUSTERS));
			for (AssignmentBoard.Row cluster : clusters) {
				Line line = new Line(CLUSTER, cluster.getTask().getName());
				for (AssignmentBoard.Row row : AssignmentBoard.activities(Collections.singletonList(cluster)))
					line.add(row, 1);
				if (line.tasks > 0)
					lines.add(line);
			}
		}
	}

	private static int rank(List<String> order, String code) {
		int index = order.indexOf(code);
		return index < 0 ? order.size() : index;
	}

	/** Names of the summaries selected, or empty if only tasks are */
	private static String names(GanttAssigner assigner) {
		StringBuilder names = new StringBuilder();
		for (AssignmentBoard.Row summary : assigner.getSummaries())
			names.append(names.length() == 0 ? "" : ", ").append(summary.getTask().getName()); //$NON-NLS-1$ //$NON-NLS-2$
		return names.toString();
	}

	/** The lines, the total first; empty if no task is selected */
	public List<Line> getLines() {
		return lines;
	}

	/** The total, null if no task is selected */
	public Line getTotal() {
		return total;
	}
}
