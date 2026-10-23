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

import java.util.ArrayList;
import java.util.List;

import com.projectlibre1.pm.dependency.DependencyType;

/**
 * A task of the AutoScheduler, or a point standing for the links of a summary task. The input is set when it is
 * created, the scheduler fills the results: start, end, resource and units.
 */
public class AutoTask {
	/** To assign and schedule: a task with an activity done by a role */
	public static final int WORK = 0;
	/** Started or completed: its dates and resources are kept */
	public static final int FIXED = 1;
	/** Not scheduled (no activity, cancelled, no work): starts when its links allow, after its leveling delay */
	public static final int PASSIVE = 2;
	/** Links to a summary task: the tasks below it start after this point */
	public static final int SUMMARY_START = 3;
	/** Links from a summary task: starts with the first of the tasks below it, ends with the last */
	public static final int SUMMARY_END = 4;

	/** Results of a WORK task */
	public static final int SCHEDULED = 0;
	public static final int KEPT = 1; // scheduled with the resources it had
	public static final int NO_RESOURCE = 2; // no resource of its role, with its skill, available

	/** Link of a SUMMARY_END point from each task below the summary */
	static final int MEMBER = -1;

	/** A link from a predecessor, see DependencyType */
	public static class Link {
		final AutoTask predecessor;
		final AutoTask successor;
		final int type;
		final long lag; // a duration of ProjectLibre, with its units
		Link(AutoTask predecessor, AutoTask successor, int type, long lag) {
			this.predecessor = predecessor;
			this.successor = successor;
			this.type = type;
			this.lag = lag;
		}
	}

	final int kind;
	final Object source;
	String role;
	String requiredSkill;
	long work; // WORK, milliseconds
	long nominalDuration; // WORK: work at the standard level of the role, for the priorities
	long duration; // PASSIVE, NO_RESOURCE and kept WORK: duration in milliseconds
	long delay; // PASSIVE: its leveling delay, kept
	long fixedStart, fixedEnd; // FIXED
	long earliestStart; // constraint Start No Earlier Than, 0 if none
	List<AutoResource> keptResources; // WORK: the resources kept, null to choose one
	final List<Link> predecessors = new ArrayList<Link>(2);
	final List<Link> successors = new ArrayList<Link>(2);

	// results
	long begin; // start allowed by the links, as ProjectLibre calculates it, before the leveling delay
	long start;
	long end;
	AutoResource resource;
	double units;
	int status;

	// work of the scheduler
	int order; // order of the WBS
	long cpmStart, cpmEnd, latestStart, latestEnd;
	int waiting;

	private AutoTask(int kind, Object source) {
		this.kind = kind;
		this.source = source;
	}

	/**
	 * A task to assign and schedule
	 * @param work work in milliseconds
	 * @param nominalDuration duration at the standard level of the role, for the priorities
	 * @param duration its duration now, kept if no resource can do it
	 */
	public static AutoTask work(Object source, String role, String requiredSkill, long work, long nominalDuration, long duration) {
		AutoTask task = new AutoTask(WORK, source);
		task.role = role;
		task.requiredSkill = requiredSkill;
		task.work = work;
		task.nominalDuration = nominalDuration;
		task.duration = duration;
		return task;
	}
	/** A task keeping its dates, and its resources busy then */
	public static AutoTask fixed(Object source, long start, long end) {
		AutoTask task = new AutoTask(FIXED, source);
		task.fixedStart = start;
		task.fixedEnd = end;
		return task;
	}
	/** A task keeping its duration and leveling delay */
	public static AutoTask passive(Object source, long duration, long delay) {
		AutoTask task = new AutoTask(PASSIVE, source);
		task.duration = duration;
		task.delay = delay;
		return task;
	}
	public static AutoTask summaryStart(Object source) {
		return new AutoTask(SUMMARY_START, source);
	}
	public static AutoTask summaryEnd(Object source) {
		return new AutoTask(SUMMARY_END, source);
	}

	/** Link from a predecessor, a type of DependencyType */
	public void addPredecessor(AutoTask predecessor, int type, long lag) {
		Link link = new Link(predecessor, this, type, lag);
		predecessors.add(link);
		predecessor.successors.add(link);
	}
	/** The task starts after a SUMMARY_START point */
	public void addParentStart(AutoTask summaryStart) {
		addPredecessor(summaryStart, DependencyType.SS, 0);
	}
	/** The task is below the summary of a SUMMARY_END point */
	public void addToSummaryEnd(AutoTask summaryEnd) {
		summaryEnd.addPredecessor(this, MEMBER, 0);
	}
	/** Keeps the resources assigned, the task keeps its duration */
	public void keepResources(List<AutoResource> resources) {
		keptResources = resources;
	}
	/** True for a WORK task keeping its resources */
	public boolean isKept() {
		return keptResources != null;
	}
	public void setEarliestStart(long earliestStart) {
		this.earliestStart = earliestStart;
	}

	public int getKind() {
		return kind;
	}
	public Object getSource() {
		return source;
	}
	public String getRole() {
		return role;
	}
	public String getRequiredSkill() {
		return requiredSkill;
	}
	public long getWork() {
		return work;
	}
	/** Start allowed by the links and constraints, before the leveling delay */
	public long getBegin() {
		return begin;
	}
	public long getStart() {
		return start;
	}
	public long getEnd() {
		return end;
	}
	/** The resource chosen, or the first one kept. Null if none */
	public AutoResource getResource() {
		return resource;
	}
	public double getUnits() {
		return units;
	}
	/** SCHEDULED, KEPT or NO_RESOURCE for a WORK task */
	public int getStatus() {
		return status;
	}
	public List<Link> getPredecessors() {
		return predecessors;
	}

	public String toString() {
		return String.valueOf(source);
	}
}
