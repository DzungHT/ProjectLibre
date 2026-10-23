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

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A resource for the AutoScheduler: its role, the basic skills it has, its periods of availability with their
 * Max Units, its working time, and the times it is busy, filled by the scheduler. A resource works on one task
 * at a time, and only in the periods with Max Units over 0%.
 */
public class AutoResource {
	private final Object source;
	private final String role;
	private final Set<String> skills; // basic skills, upper case
	private final int skillCount; // skills it was given, a combined one (Fullstack) counting for its components
	private final long[] periodStarts; // sorted, the first one is Long.MIN_VALUE
	private final double[] periodUnits;
	private final WorkingTime time;
	private final TreeMap<Long, Long> busy = new TreeMap<Long, Long>(); // start -> end, merged when they touch
	int index;

	/**
	 * @param source the resource of the project
	 * @param role the role of the resource, null if it has none (only kept on the tasks assigned to it)
	 * @param skills its basic skills in upper case, see SkillCatalog.expand()
	 * @param periodStarts starts of the periods of availability, sorted. The first one is the start of time
	 * @param periodUnits Max Units of each period, 1.0 for 100%
	 * @param time the calendar of the resource
	 */
	public AutoResource(Object source, String role, Set<String> skills, long[] periodStarts, double[] periodUnits, WorkingTime time) {
		this.source = source;
		this.role = role;
		this.skills = skills;
		this.skillCount = skills.size();
		this.periodStarts = periodStarts.clone();
		this.periodStarts[0] = Long.MIN_VALUE;
		this.periodUnits = periodUnits;
		this.time = time;
	}

	public Object getSource() {
		return source;
	}
	public String getRole() {
		return role;
	}
	public int getSkillCount() {
		return skillCount;
	}
	public WorkingTime getTime() {
		return time;
	}
	/** True if it can do a task requiring this basic skill, any task of its role for null */
	public boolean canDo(String requiredSkill) {
		return requiredSkill == null || skills.contains(requiredSkill.toUpperCase());
	}
	/** Max Units at a date */
	public double unitsAt(long date) {
		return periodUnits[periodAt(date)];
	}

	/** A place found for a task: start, end and the units of the resource */
	public static class Slot {
		final long start, end;
		final double units;
		Slot(long start, long end, double units) {
			this.start = start;
			this.end = end;
			this.units = units;
		}
	}

	/**
	 * Earliest place for a task from a date, in a period with Max Units over 0% and when the resource is free.
	 * The duration is the work divided by the Max Units of the period of the start, or a fixed duration.
	 * The task does not go over a period of 0%: if it would, it starts in the next period with units.
	 * @param work work in milliseconds, used if duration < 0
	 * @param duration fixed duration, or -1
	 * @param minEnd the earliest end (links finish to finish), 0 if none
	 * @param ignoreBusy true to look only at the availability
	 * @return null if the resource is never available after the date
	 */
	public Slot findSlot(long from, long work, long duration, long minEnd, boolean ignoreBusy) {
		long start = from;
		for (int guard = 0; guard < 100000; guard++) {
			int period = periodAt(start);
			if (periodUnits[period] <= 0) {
				period = nextAvailable(period);
				if (period < 0)
					return null;
				start = periodStarts[period];
			}
			start = time.start(start);
			period = periodAt(start);
			if (periodUnits[period] <= 0)
				continue; // the working time starts in a period of 0%
			double units = periodUnits[period];
			long length = duration >= 0 ? duration : Math.round(work / units);
			long end = time.add(start, length, true);
			if (end < minEnd) {
				start = time.add(start, time.between(minEnd, end), false);
				continue;
			}
			int gap = nextUnavailable(period);
			if (gap >= 0 && periodStarts[gap] < end) {
				int next = nextAvailable(gap);
				if (next < 0)
					return null;
				start = periodStarts[next];
				continue;
			}
			if (!ignoreBusy) {
				long busyEnd = busyEnd(start, end);
				if (busyEnd > start) {
					start = busyEnd;
					continue;
				}
			}
			return new Slot(start, end, units);
		}
		return null;
	}

	/** End of a busy time overlapping [start, end), or Long.MIN_VALUE if the resource is free then */
	private long busyEnd(long start, long end) {
		Map.Entry<Long, Long> before = busy.floorEntry(start);
		if (before != null && before.getValue() > start)
			return before.getValue();
		Map.Entry<Long, Long> after = busy.higherEntry(start);
		if (after != null && after.getKey() < end)
			return after.getValue();
		return Long.MIN_VALUE;
	}

	/** The resource works from start to end */
	public void occupy(long start, long end) {
		if (end <= start)
			return;
		Map.Entry<Long, Long> before = busy.floorEntry(start);
		if (before != null && before.getValue() >= start) {
			start = before.getKey();
			end = Math.max(end, before.getValue());
			busy.remove(before.getKey());
		}
		for (Map.Entry<Long, Long> after = busy.ceilingEntry(start); after != null && after.getKey() <= end; after = busy.ceilingEntry(start)) {
			end = Math.max(end, after.getValue());
			busy.remove(after.getKey());
		}
		busy.put(start, end);
	}
	/** Forgets the busy times, to schedule again */
	public void clearBusy() {
		busy.clear();
	}

	private int periodAt(long date) {
		int low = 0, high = periodStarts.length - 1;
		while (low < high) { // last period starting at or before the date
			int middle = (low + high + 1) >>> 1;
			if (periodStarts[middle] <= date)
				low = middle;
			else
				high = middle - 1;
		}
		return low;
	}
	private int nextAvailable(int period) {
		for (int p = period + 1; p < periodUnits.length; p++)
			if (periodUnits[p] > 0)
				return p;
		return -1;
	}
	private int nextUnavailable(int period) {
		for (int p = period + 1; p < periodUnits.length; p++)
			if (periodUnits[p] <= 0)
				return p;
		return -1;
	}

	public String toString() {
		return String.valueOf(source);
	}
}
