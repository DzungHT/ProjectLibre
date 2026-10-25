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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.projectlibre1.pm.task.Project;

/**
 * Versions of the Resource Plan of a project, kept in the project (see Project.getResourcePlanVersions()) so that a
 * plan can be prepared and changed several times before it is applied. A version is a snapshot of the plan, see
 * ResourcePlan.snapshot(): saving it does not change the resources, only applying a plan does (ResourcePlanWriter).
 * The list also remembers the version last applied and the one last opened.
 */
public class ResourcePlanVersions {
	private static final String COUNT = "count", APPLIED = "applied", APPLIED_SNAPSHOT = "appliedSnapshot", LAST = "last", VERSION = "version.", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			NAME = ".name", SAVED = ".saved", SNAPSHOT = ".snapshot"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	public static class Version {
		private String name;
		private long saved;
		private String snapshot;

		public Version(String name, long saved, String snapshot) {
			this.name = name;
			this.saved = saved;
			this.snapshot = snapshot;
		}
		public String getName() { return name; }
		/** When it was last saved, millis */
		public long getSaved() { return saved; }
		/** The plan, see ResourcePlan.snapshot() */
		public String getSnapshot() { return snapshot; }
		public String toString() { return name; }
	}

	private final List<Version> versions = new ArrayList<Version>();
	private String applied = null;
	private String appliedSnapshot = null; // the plan of the version when it was applied
	private String last = null;

	/** The versions of a project, none if it has never had one */
	public static ResourcePlanVersions load(Project project) {
		ResourcePlanVersions list = new ResourcePlanVersions();
		String text = project.getResourcePlanVersions();
		if (text == null)
			return list;
		Properties p = new Properties();
		try {
			p.load(new StringReader(text));
		} catch (IOException e) { // a string cannot fail
		}
		int count = 0;
		try {
			count = Integer.parseInt(p.getProperty(COUNT, "0")); //$NON-NLS-1$
		} catch (NumberFormatException e) {
		}
		for (int i = 0; i < count; i++) {
			String name = p.getProperty(VERSION + i + NAME);
			if (name == null)
				continue;
			long saved = 0;
			try {
				saved = Long.parseLong(p.getProperty(VERSION + i + SAVED, "0")); //$NON-NLS-1$
			} catch (NumberFormatException e) {
			}
			list.versions.add(new Version(name, saved, p.getProperty(VERSION + i + SNAPSHOT, ""))); //$NON-NLS-1$
		}
		list.applied = p.getProperty(APPLIED);
		list.appliedSnapshot = p.getProperty(APPLIED_SNAPSHOT);
		list.last = p.getProperty(LAST);
		return list;
	}

	/** Keeps the versions in the project, which needs to be saved */
	public void save(Project project) {
		Properties p = new Properties();
		p.setProperty(COUNT, String.valueOf(versions.size()));
		for (int i = 0; i < versions.size(); i++) {
			Version version = versions.get(i);
			p.setProperty(VERSION + i + NAME, version.name);
			p.setProperty(VERSION + i + SAVED, String.valueOf(version.saved));
			p.setProperty(VERSION + i + SNAPSHOT, version.snapshot);
		}
		if (applied != null)
			p.setProperty(APPLIED, applied);
		if (appliedSnapshot != null)
			p.setProperty(APPLIED_SNAPSHOT, appliedSnapshot);
		if (last != null)
			p.setProperty(LAST, last);
		StringWriter writer = new StringWriter();
		try {
			p.store(writer, null);
		} catch (IOException e) { // a string cannot fail
		}
		project.setResourcePlanVersions(writer.toString());
		project.setGroupDirty(true);
	}

	public List<Version> getVersions() {
		return versions;
	}

	/** The version of a name, ignoring case, null if there is none */
	public Version find(String name) {
		if (name != null)
			for (Version version : versions)
				if (version.name.equalsIgnoreCase(name.trim()))
					return version;
		return null;
	}

	/** Saves a plan in the version of this name, added at the end if there is none */
	public Version put(String name, String snapshot) {
		Version version = find(name);
		if (version == null) {
			version = new Version(name.trim(), 0, snapshot);
			versions.add(version);
		}
		version.snapshot = snapshot;
		version.saved = System.currentTimeMillis();
		return version;
	}

	public void rename(Version version, String name) {
		if (version.name.equals(applied))
			applied = name.trim();
		if (version.name.equals(last))
			last = name.trim();
		version.name = name.trim();
	}

	public void remove(Version version) {
		versions.remove(version);
		if (version.name.equals(applied)) {
			applied = null;
			appliedSnapshot = null;
		}
		if (version.name.equals(last))
			last = null;
	}

	/** Name of the version last applied to the resources, null if none or if the plan applied was not a version */
	public String getApplied() { return applied; }
	/** Remembers the version applied and its plan then, see isApplied(). Null when the plan applied was not a version */
	public void setApplied(Version version) {
		applied = version == null ? null : version.name;
		appliedSnapshot = version == null ? null : version.snapshot;
	}
	/** True if the version is the one applied and has not been changed since */
	public boolean isApplied(Version version) {
		return version != null && version.name.equals(applied) && version.snapshot.equals(appliedSnapshot);
	}
	/** Name of the version last opened, to open it again; null for the plan of the resources */
	public String getLast() { return last; }
	public void setLast(String last) { this.last = last; }
}
