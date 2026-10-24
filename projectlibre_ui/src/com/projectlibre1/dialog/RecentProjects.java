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
package com.projectlibre1.dialog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * The projects opened or saved last, most recent first, kept in the user preferences.
 */
public final class RecentProjects {
	public static final int MAX = 10;
	private static final Preferences preferences = Preferences.userNodeForPackage(RecentProjects.class).node("recentProjects"); //$NON-NLS-1$
	private static final String PATH = "path"; //$NON-NLS-1$
	private static final String NAME = "name"; //$NON-NLS-1$

	public static final class Entry {
		private final String name;
		private final String path;
		Entry(String name, String path) {
			this.name = name;
			this.path = path;
		}
		public String getName() {
			return name;
		}
		public String getPath() {
			return path;
		}
		public boolean exists() {
			return new File(path).isFile();
		}
	}

	private RecentProjects() {
	}

	public static synchronized List<Entry> getEntries() {
		List<Entry> entries = new ArrayList<Entry>();
		for (int i = 0; i < MAX; i++) {
			String path = preferences.get(PATH + i, null);
			if (path == null)
				break;
			String name = preferences.get(NAME + i, null);
			entries.add(new Entry(name == null || name.length() == 0 ? defaultName(path) : name, path));
		}
		return entries;
	}

	public static synchronized void add(String name, String path) {
		if (path == null || path.length() == 0)
			return;
		path = new File(path).getAbsolutePath();
		List<Entry> entries = getEntries();
		remove(entries, path);
		entries.add(0, new Entry(name == null || name.length() == 0 ? defaultName(path) : name, path));
		store(entries);
	}

	public static synchronized void remove(String path) {
		List<Entry> entries = getEntries();
		if (remove(entries, path))
			store(entries);
	}

	private static boolean remove(List<Entry> entries, String path) {
		boolean removed = false;
		for (int i = entries.size() - 1; i >= 0; i--) {
			if (entries.get(i).getPath().equalsIgnoreCase(path)) {
				entries.remove(i);
				removed = true;
			}
		}
		return removed;
	}

	private static void store(List<Entry> entries) {
		for (int i = 0; i < MAX; i++) {
			if (i < entries.size()) {
				preferences.put(PATH + i, entries.get(i).getPath());
				preferences.put(NAME + i, entries.get(i).getName());
			} else {
				preferences.remove(PATH + i);
				preferences.remove(NAME + i);
			}
		}
		try {
			preferences.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static String defaultName(String path) {
		String name = new File(path).getName();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}
}
