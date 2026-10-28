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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.projectlibre1.grouping.core.transform.filtering.TaskNameFilter;
import com.projectlibre1.pm.task.NormalTask;

/**
 * What Auto Link links. The activities are the activity codes of the tasks, like GP, PT, KT.
 * <ul>
 * <li>Activities: rules say which activity is linked after which one. Each task of the target activity of a rule
 * is linked after the tasks of its source activity found in the same group: below the parent of the task (level N)
 * or below the parent of its parent (level N-1), at any depth, so that a WBS with more levels is linked the same
 * way. When there is none, the activities linked before the source by the other rules are used, and so on</li>
 * <li>Sequences (docs/relink-design.md): the tasks of each person are linked one after the other, whatever their
 * activity, in the order they can start or in the order of the WBS. The tasks not assigned make one sequence per
 * activity, or per activity and lane for the lane activities (the development tasks of BE, FE, Mobile...), or
 * none. The first task of a person arriving late starts no earlier than the person, and the leveling delays are
 * removed: the dates come from the links</li>
 * </ul>
 * Cancelled tasks and tasks without work are skipped: the tasks around them are linked together.
 * The activity of a task is its activity code, or found from its name, see activityIndex().
 */
public class AutoLinkOptions {
	/** Groups of the tasks linked between activities: number of levels above the target task */
	public static final int PARENT = 1; // level N
	public static final int GRANDPARENT = 2; // level N-1

	/** The tasks of an activity linked after the tasks of another one in the same group */
	public static class Rule {
		private static final String ARROW = ">", LEVELS = ":", SEPARATOR = ","; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		private final String from;
		private final String to;
		private final int levels;

		public Rule(String from, String to, int levels) {
			this.from = from;
			this.to = to;
			this.levels = levels;
		}
		public String getFrom() {
			return from;
		}
		public String getTo() {
			return to;
		}
		/** PARENT or GRANDPARENT */
		public int getLevels() {
			return levels;
		}
		/** The rules as text, like GP>PT:1,PT>KT:2 */
		public static String format(List<Rule> rules) {
			StringBuilder text = new StringBuilder();
			for (Rule rule : rules) {
				if (text.length() > 0)
					text.append(SEPARATOR);
				text.append(rule.from).append(ARROW).append(rule.to).append(LEVELS).append(rule.levels);
			}
			return text.toString();
		}
		/** The rules written by format(), null if there is no text or it is not valid */
		public static List<Rule> parse(String text) {
			if (text == null)
				return null;
			List<Rule> rules = new ArrayList<Rule>();
			if (text.trim().length() == 0)
				return rules;
			for (String item : text.split(SEPARATOR)) {
				int arrow = item.indexOf(ARROW);
				int levels = item.lastIndexOf(LEVELS);
				if (arrow <= 0 || levels <= arrow + 1)
					return null;
				try {
					int value = Integer.parseInt(item.substring(levels + 1).trim());
					rules.add(new Rule(item.substring(0, arrow).trim(), item.substring(arrow + 1, levels).trim(),
							value == GRANDPARENT ? GRANDPARENT : PARENT));
				} catch (NumberFormatException e) {
					return null;
				}
			}
			return rules;
		}
	}

	private final String[] activities;
	private final List<Rule> rules;
	private final Set<String> laneActivities;
	private boolean linkActivities = true;
	private boolean linkSequences = true;
	private boolean linkUnassigned = false;
	private boolean readyOrder = true;
	private boolean joinConstraints = true;
	private String[] normalizedNames = null;

	/**
	 * @param activities the activity codes linked; the codes of the rules are added
	 * @param rules the rules between activities; the ones from an activity to itself are ignored
	 */
	public AutoLinkOptions(String[] activities, List<Rule> rules, String[] laneActivities) {
		List<String> codes = new ArrayList<String>(Arrays.asList(activities));
		this.rules = new ArrayList<Rule>();
		for (Rule rule : rules) {
			if (rule.getFrom().equals(rule.getTo()))
				continue;
			this.rules.add(rule);
			for (String code : new String[] { rule.getFrom(), rule.getTo() })
				if (!codes.contains(code))
					codes.add(code);
		}
		this.activities = codes.toArray(new String[codes.size()]);
		this.laneActivities = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(laneActivities)));
	}

	/** The rules of before: each activity after the previous one, in the same row first, then in the same function */
	public static List<Rule> defaultRules(String[] activities) {
		List<Rule> rules = new ArrayList<Rule>();
		for (int i = 1; i < activities.length; i++)
			rules.add(new Rule(activities[i - 1], activities[i], i == 1 ? PARENT : GRANDPARENT));
		return rules;
	}

	public String[] getActivities() {
		return activities;
	}
	public List<Rule> getRules() {
		return Collections.unmodifiableList(rules);
	}
	/** Index of an activity code, -1 if it is not linked */
	public int indexOf(String activity) {
		for (int i = 0; i < activities.length; i++)
			if (activities[i].equals(activity))
				return i;
		return -1;
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
	/** Sequences of the tasks not assigned: by activity and lane (true), or none */
	public boolean isLinkUnassigned() {
		return linkUnassigned;
	}
	public void setLinkUnassigned(boolean linkUnassigned) {
		this.linkUnassigned = linkUnassigned;
	}
	/** Sequences: the tasks in the order they can start (true), or in the order of the WBS */
	public boolean isReadyOrder() {
		return readyOrder;
	}
	public void setReadyOrder(boolean readyOrder) {
		this.readyOrder = readyOrder;
	}
	/** Sequences: the first task of a person starts no earlier than the day the person is available */
	public boolean isJoinConstraints() {
		return joinConstraints;
	}
	public void setJoinConstraints(boolean joinConstraints) {
		this.joinConstraints = joinConstraints;
	}
}
