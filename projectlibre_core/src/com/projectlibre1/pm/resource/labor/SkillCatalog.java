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
package com.projectlibre1.pm.resource.labor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.projectlibre1.strings.Messages;

/**
 * Skills of the resources and required skills of the tasks (BE, FE, Mobile...).
 * A basic skill can be the required skill of a task. A combined skill, like Fullstack = BE + FE, is only given to
 * resources and means all its basic skills. A task with a required skill can be done by a resource having this
 * skill, a task without required skill by any resource of its role.
 * The catalog is kept in the configuration directory of the user and shared by all the projects.
 */
public class SkillCatalog {
	public static final String FILE_NAME = "skills.csv"; //$NON-NLS-1$
	private static final String CODE = "code", NAME = "name", ROLE = "role", COMPONENTS = "components"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	/** Separator of the skills of a resource, and of the components in the file */
	public static final String SEPARATOR = "; ", COMPONENT_SEPARATOR = "+"; //$NON-NLS-1$ //$NON-NLS-2$

	public static class Skill {
		private final String code;
		private final String name;
		private final String role;
		private final List<String> components;

		public Skill(String code, String name, String role, List<String> components) {
			this.code = code.trim();
			this.name = name == null || name.trim().length() == 0 ? this.code : name.trim();
			this.role = role == null ? "" : role.trim(); //$NON-NLS-1$
			this.components = components == null ? Collections.<String> emptyList()
					: Collections.unmodifiableList(new ArrayList<String>(components));
		}
		public String getCode() { return code; }
		public String getName() { return name; }
		/** Role whose resources have this skill (DEV...), empty for any role */
		public String getRole() { return role; }
		/** Basic skills of a combined skill, empty for a basic skill */
		public List<String> getComponents() { return components; }
		public boolean isBasic() { return components.isEmpty(); }
		public String toString() { return code; }
	}

	private static SkillCatalog instance = null;
	private List<Skill> skills = Collections.emptyList();

	public static synchronized SkillCatalog getInstance() {
		if (instance == null) {
			instance = new SkillCatalog();
			instance.skills = defaults();
			File file = CatalogFile.get(FILE_NAME);
			if (file.isFile()) {
				try {
					instance.skills = parse(CatalogFile.read(file));
				} catch (Exception e) {
					e.printStackTrace(); // the default skills
				}
			}
		}
		return instance;
	}
	public static List<Skill> defaults() {
		String dev = "DEV"; //$NON-NLS-1$
		return Collections.unmodifiableList(Arrays.asList(
				new Skill("BE", "Back-end", dev, null), //$NON-NLS-1$ //$NON-NLS-2$
				new Skill("FE", "Front-end", dev, null), //$NON-NLS-1$ //$NON-NLS-2$
				new Skill("Mobile", "Mobile", dev, null), //$NON-NLS-1$ //$NON-NLS-2$
				new Skill("Fullstack", "Fullstack", dev, Arrays.asList("BE", "FE")))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	public List<Skill> getSkills() {
		return skills;
	}
	public List<Skill> getBasicSkills() {
		List<Skill> basic = new ArrayList<Skill>();
		for (Skill skill : skills)
			if (skill.isBasic())
				basic.add(skill);
		return basic;
	}
	/** Skill with this code, ignoring case, null if there is none */
	public Skill find(String code) {
		if (code == null)
			return null;
		code = code.trim();
		for (Skill skill : skills)
			if (skill.getCode().equalsIgnoreCase(code))
				return skill;
		return null;
	}

	/**
	 * Codes of the catalog for a list of skills separated by ";" or ",", in the order of the list and without
	 * duplicates. Throws an exception naming the first unknown skill
	 */
	public List<String> normalize(String list) throws IllegalArgumentException {
		Set<String> codes = new LinkedHashSet<String>();
		for (String code : split(list)) {
			Skill skill = find(code);
			if (skill == null)
				throw new IllegalArgumentException(code);
			codes.add(skill.getCode());
		}
		return new ArrayList<String>(codes);
	}
	/** Basic skills, upper case, of a list of skills of a resource. Unknown skills are taken as basic ones */
	public Set<String> expand(String list) {
		Set<String> basic = new LinkedHashSet<String>();
		for (String code : split(list)) {
			Skill skill = find(code);
			if (skill == null || skill.isBasic())
				basic.add(code.toUpperCase());
			else
				for (String component : skill.getComponents())
					basic.add(component.toUpperCase());
		}
		return basic;
	}
	/** True if a resource with these skills can do a task with this required skill (none: any resource) */
	public boolean canDo(String resourceSkills, String requiredSkill) {
		return requiredSkill == null || requiredSkill.trim().length() == 0
				|| expand(resourceSkills).contains(requiredSkill.trim().toUpperCase());
	}
	public static List<String> split(String list) {
		List<String> codes = new ArrayList<String>();
		if (list != null)
			for (String code : list.split("[;,]")) { //$NON-NLS-1$
				code = code.trim();
				if (code.length() > 0)
					codes.add(code);
			}
		return codes;
	}

	/** Error message for a list of skills, null if it can be saved */
	public static String validate(List<Skill> list) {
		Set<String> codes = new LinkedHashSet<String>();
		for (Skill skill : list) {
			String code = skill.getCode();
			if (code.length() == 0)
				return Messages.getString("SkillCatalog.EmptyCode"); //$NON-NLS-1$
			if (code.indexOf(';') >= 0 || code.indexOf(',') >= 0 || code.indexOf('+') >= 0)
				return Messages.getString("SkillCatalog.InvalidCode") + " " + code; //$NON-NLS-1$ //$NON-NLS-2$
			if (!codes.add(code.toUpperCase()))
				return Messages.getString("SkillCatalog.DuplicateCode") + " " + code; //$NON-NLS-1$ //$NON-NLS-2$
		}
		for (Skill skill : list) {
			if (skill.isBasic())
				continue;
			if (skill.getComponents().size() < 2)
				return Messages.getString("SkillCatalog.TwoComponents") + " " + skill.getCode(); //$NON-NLS-1$ //$NON-NLS-2$
			for (String component : skill.getComponents()) {
				Skill basic = null;
				for (Skill other : list)
					if (other.getCode().equalsIgnoreCase(component))
						basic = other;
				if (basic == null || !basic.isBasic())
					return Messages.getString("SkillCatalog.InvalidComponent") + " " + skill.getCode() + ": " + component; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		}
		return null;
	}

	/** Replaces the skills and saves the catalog, after validate() */
	public synchronized void replace(List<Skill> newSkills) throws IOException {
		String error = validate(newSkills);
		if (error != null)
			throw new IllegalArgumentException(error);
		List<String[]> rows = new ArrayList<String[]>();
		rows.add(new String[] { CODE, NAME, ROLE, COMPONENTS });
		for (Skill skill : newSkills) {
			StringBuilder components = new StringBuilder();
			for (String component : skill.getComponents())
				components.append(components.length() == 0 ? "" : COMPONENT_SEPARATOR).append(component); //$NON-NLS-1$
			rows.add(new String[] { skill.getCode(), skill.getName(), skill.getRole(), components.toString() });
		}
		CatalogFile.write(CatalogFile.get(FILE_NAME), rows);
		skills = Collections.unmodifiableList(new ArrayList<Skill>(newSkills));
	}
	/** Adds basic skills that are not in the catalog, like the ones typed for the function codes, and saves it */
	public synchronized void addBasic(Iterable<String> codes, String role) throws IOException {
		List<Skill> list = new ArrayList<Skill>(skills);
		for (String code : codes)
			if (code != null && code.trim().length() > 0 && find(code) == null && !contains(list, code))
				list.add(new Skill(code, null, role, null));
		if (list.size() > skills.size())
			replace(list);
	}
	private static boolean contains(List<Skill> list, String code) {
		for (Skill skill : list)
			if (skill.getCode().equalsIgnoreCase(code.trim()))
				return true;
		return false;
	}

	private static List<Skill> parse(List<String[]> rows) throws IOException {
		if (rows.isEmpty())
			return new ArrayList<Skill>();
		String[] header = rows.get(0);
		int code = CatalogFile.column(header, CODE), name = CatalogFile.column(header, NAME);
		int role = CatalogFile.column(header, ROLE), components = CatalogFile.column(header, COMPONENTS);
		if (code < 0)
			throw new IOException("Missing column: " + CODE); //$NON-NLS-1$
		List<Skill> list = new ArrayList<Skill>();
		for (int r = 1; r < rows.size(); r++) {
			String[] row = rows.get(r);
			String c = CatalogFile.value(row, code).trim();
			if (c.length() == 0)
				continue;
			List<String> parts = new ArrayList<String>();
			for (String part : CatalogFile.value(row, components).split("\\+")) //$NON-NLS-1$
				if (part.trim().length() > 0)
					parts.add(part.trim());
			list.add(new Skill(c, CatalogFile.value(row, name), CatalogFile.value(row, role), parts));
		}
		return Collections.unmodifiableList(list);
	}

	/** Codes of a list of skills of the catalog joined with "; ", null for an empty list */
	public String join(List<String> codes) {
		StringBuilder list = new StringBuilder();
		for (String code : codes)
			list.append(list.length() == 0 ? "" : SEPARATOR).append(code); //$NON-NLS-1$
		return list.length() == 0 ? null : list.toString();
	}

	/**
	 * All the skills, basic and combined, for the choice of the skills of a resource. Several skills can also be
	 * typed, separated by ";"
	 */
	public static Object[] allSkills() {
		List<String> codes = new ArrayList<String>();
		for (Skill skill : getInstance().getSkills())
			codes.add(skill.getCode());
		return codes.toArray();
	}
	/** Skills chosen or typed, with the codes of the catalog, null if one is not in the catalog */
	public static String findSkills(String list) {
		try {
			return getInstance().join(getInstance().normalize(list));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Basic skills for the choice of the required skill of a task */
	public static Object[] allBasicSkills() {
		List<String> codes = new ArrayList<String>();
		for (Skill skill : getInstance().getBasicSkills())
			codes.add(skill.getCode());
		return codes.toArray();
	}
	public static String findBasicSkill(String code) {
		Skill skill = getInstance().find(code);
		return skill == null || !skill.isBasic() ? null : skill.getCode();
	}
}
