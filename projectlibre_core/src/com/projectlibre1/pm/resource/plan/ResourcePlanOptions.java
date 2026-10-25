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
package com.projectlibre1.pm.resource.plan;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.prefs.Preferences;

import com.projectlibre1.pm.task.Project;

/**
 * Parameters of the Resource Plan: start, deadline, buffer, utilization, the periods of the roles and the effort
 * added to the WBS (PM, QA...). Saved in the project, see Project.getResourcePlanSettings(). The parameters other
 * than the dates and the added effort are also remembered for the next projects. Days are working days of the
 * project calendar.
 */
public class ResourcePlanOptions {
	public static final int DEFAULT_BUFFER = 0;
	public static final double DEFAULT_UTILIZATION = 0.85;
	public static final int DEFAULT_DEV_LAG = 10;
	public static final int DEFAULT_TESTER_LAG = 20;
	public static final int DEFAULT_TEST_TAIL = 10;
	public static final int DEFAULT_BA_TAIL = 20;
	public static final int DEFAULT_MAX_PEOPLE = 20;

	private static final String START = "start", DEADLINE = "deadline", BUFFER = "buffer", UTILIZATION = "utilization", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			DEV_LAG = "devLag", TESTER_LAG = "testerLag", TEST_TAIL = "testTail", BA_TAIL = "baTail", MAX_PEOPLE = "maxPeople", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			EXTRA = "extra.", AUTO = "auto."; //$NON-NLS-1$ //$NON-NLS-2$
	private static final String PREFERENCE_PREFIX = "resourcePlan."; //$NON-NLS-1$

	private long start = 0; // 0: the start of the project
	private long deadline = 0; // a day, 0: not set
	private int buffer;
	private double utilization;
	private int devLag, testerLag, testTail, baTail;
	private int maxPeople;
	/** Effort added to the WBS, man-days, by key of group (see ResourcePlan.key()) */
	private final Map<String, Double> extraDays = new LinkedHashMap<String, Double>();
	/** Lines whose quantity is calculated, by role, skill and level (see ResourcePlan.lineKey()), to read them again as auto */
	private final Set<String> autoLines = new LinkedHashSet<String>();

	public ResourcePlanOptions() {
		resetDefaults();
	}

	/** The default parameters. The dates and the added effort are kept */
	public void resetDefaults() {
		buffer = DEFAULT_BUFFER;
		utilization = DEFAULT_UTILIZATION;
		devLag = DEFAULT_DEV_LAG;
		testerLag = DEFAULT_TESTER_LAG;
		testTail = DEFAULT_TEST_TAIL;
		baTail = DEFAULT_BA_TAIL;
		maxPeople = DEFAULT_MAX_PEOPLE;
	}

	/** The options saved in the project, or the ones last used if there are none */
	public static ResourcePlanOptions load(Project project) {
		ResourcePlanOptions options = new ResourcePlanOptions();
		options.readPreferences();
		String settings = project.getResourcePlanSettings();
		if (settings != null) {
			Properties properties = new Properties();
			try {
				properties.load(new StringReader(settings));
				options.read(properties);
			} catch (IOException e) { // a string cannot fail
			}
		}
		return options;
	}

	/** Saves the options in the project and remembers the parameters */
	public void save(Project project) {
		Properties properties = new Properties();
		write(properties, true);
		StringWriter writer = new StringWriter();
		try {
			properties.store(writer, null);
		} catch (IOException e) { // a string cannot fail
		}
		project.setResourcePlanSettings(writer.toString());
		project.setGroupDirty(true);
		writePreferences();
	}

	/** All the options, to keep them in a version of the plan, see ResourcePlan.snapshot() */
	Properties toProperties() {
		Properties properties = new Properties();
		write(properties, true);
		return properties;
	}

	/** The options of a version of the plan. The ones it does not have are the ones last used */
	static ResourcePlanOptions fromProperties(Properties properties) {
		ResourcePlanOptions options = new ResourcePlanOptions();
		options.readPreferences();
		options.read(properties);
		return options;
	}

	private void read(Properties p) {
		start = longValue(p, START, start);
		deadline = longValue(p, DEADLINE, deadline);
		buffer = (int) longValue(p, BUFFER, buffer);
		utilization = doubleValue(p, UTILIZATION, utilization);
		devLag = (int) longValue(p, DEV_LAG, devLag);
		testerLag = (int) longValue(p, TESTER_LAG, testerLag);
		testTail = (int) longValue(p, TEST_TAIL, testTail);
		baTail = (int) longValue(p, BA_TAIL, baTail);
		maxPeople = (int) longValue(p, MAX_PEOPLE, maxPeople);
		extraDays.clear();
		autoLines.clear();
		for (String name : p.stringPropertyNames())
			if (name.startsWith(EXTRA))
				extraDays.put(name.substring(EXTRA.length()), doubleValue(p, name, 0));
			else if (name.startsWith(AUTO))
				autoLines.add(name.substring(AUTO.length()));
	}

	private void write(Properties p, boolean all) {
		if (all) {
			p.setProperty(START, String.valueOf(start));
			p.setProperty(DEADLINE, String.valueOf(deadline));
			for (Map.Entry<String, Double> entry : extraDays.entrySet())
				if (entry.getValue() != 0)
					p.setProperty(EXTRA + entry.getKey(), String.valueOf(entry.getValue()));
			for (String line : autoLines)
				p.setProperty(AUTO + line, "true"); //$NON-NLS-1$
		}
		p.setProperty(BUFFER, String.valueOf(buffer));
		p.setProperty(UTILIZATION, String.valueOf(utilization));
		p.setProperty(DEV_LAG, String.valueOf(devLag));
		p.setProperty(TESTER_LAG, String.valueOf(testerLag));
		p.setProperty(TEST_TAIL, String.valueOf(testTail));
		p.setProperty(BA_TAIL, String.valueOf(baTail));
		p.setProperty(MAX_PEOPLE, String.valueOf(maxPeople));
	}

	private void readPreferences() {
		try {
			Preferences preferences = getPreferences();
			Properties properties = new Properties();
			for (String key : preferences.keys())
				if (key.startsWith(PREFERENCE_PREFIX))
					properties.setProperty(key.substring(PREFERENCE_PREFIX.length()), preferences.get(key, "")); //$NON-NLS-1$
			read(properties);
		} catch (Exception e) { // preferences not available, keep the defaults
		}
	}

	private void writePreferences() {
		try {
			Preferences preferences = getPreferences();
			Properties properties = new Properties();
			write(properties, false);
			for (String name : properties.stringPropertyNames())
				preferences.put(PREFERENCE_PREFIX + name, properties.getProperty(name));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static Preferences getPreferences() {
		return Preferences.userNodeForPackage(ResourcePlanOptions.class);
	}

	private static long longValue(Properties p, String name, long value) {
		try {
			String s = p.getProperty(name);
			return s == null ? value : Long.parseLong(s.trim());
		} catch (NumberFormatException e) {
			return value;
		}
	}

	private static double doubleValue(Properties p, String name, double value) {
		try {
			String s = p.getProperty(name);
			return s == null ? value : Double.parseDouble(s.trim());
		} catch (NumberFormatException e) {
			return value;
		}
	}

	/** First day of the plan, 0 for the start of the project */
	public long getStart() { return start; }
	public void setStart(long start) { this.start = start; }
	/** Last day of the plan (a day: the end of its working time is used), 0 if not set */
	public long getDeadline() { return deadline; }
	public void setDeadline(long deadline) { this.deadline = deadline; }
	/** Working days kept free before the deadline */
	public int getBuffer() { return buffer; }
	public void setBuffer(int buffer) { this.buffer = Math.max(0, buffer); }
	/** Part of the time in which the people really work on the tasks, 0.85 for 85% */
	public double getUtilization() { return utilization; }
	public void setUtilization(double utilization) { this.utilization = Math.max(0.01, Math.min(1, utilization)); }
	/** Working days from the start to the arrival of the developers */
	public int getDevLag() { return devLag; }
	public void setDevLag(int devLag) { this.devLag = Math.max(0, devLag); }
	/** Working days from the start to the arrival of the testers */
	public int getTesterLag() { return testerLag; }
	public void setTesterLag(int testerLag) { this.testerLag = Math.max(0, testerLag); }
	/** Working days before the deadline in which the developers have left: the last tests */
	public int getTestTail() { return testTail; }
	public void setTestTail(int testTail) { this.testTail = Math.max(0, testTail); }
	/** Working days before the deadline in which the BAs have left */
	public int getBaTail() { return baTail; }
	public void setBaTail(int baTail) { this.baTail = Math.max(0, baTail); }
	/** Most people of a role and skill, more is shown as an issue */
	public int getMaxPeople() { return maxPeople; }
	public void setMaxPeople(int maxPeople) { this.maxPeople = Math.max(1, maxPeople); }

	/** Effort added to the WBS for a group, man-days */
	public double getExtraDays(String key) {
		Double days = extraDays.get(key);
		return days == null ? 0 : days;
	}
	public void setExtraDays(String key, double days) {
		if (days == 0)
			extraDays.remove(key);
		else
			extraDays.put(key, days);
	}
	public Map<String, Double> getExtraDays() {
		return extraDays;
	}
	/** Lines whose quantity is calculated, see ResourcePlan.lineKey() */
	public Set<String> getAutoLines() {
		return autoLines;
	}
}
