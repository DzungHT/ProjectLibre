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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Labor categories, from the NLTC table: for each role (position: BA, DEV, TESTER...) and level, the point and
 * the price of a man-month. The point is the performance: 1 is 100% unit, the effort estimated in the ULNL, so a
 * resource of point p works effort / p hours for an effort of the ULNL.
 * The catalog is imported from the CSV of the NLTC table (columns positionName, level, point, defaultPrice, and
 * optionally levelName and levelCode, in any order) and kept in the configuration directory of the user. The
 * level, its name (Middle) and its code (M) are separate fields, to export them as they are. Rows with a point of
 * 0 are skipped: there is nobody at this level.
 */
public class LaborCatalog {
	public static final String FILE_NAME = "labor-categories.csv"; //$NON-NLS-1$
	private static final String ROLE = "positionName", LEVEL = "level", LEVEL_NAME = "levelName", LEVEL_CODE = "levelCode", POINT = "point", PRICE = "defaultPrice"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$

	public static class Category {
		private final String role;
		private final int level;
		private final String levelName;
		private final String levelCode;
		private final double point;
		private final double price;

		/** The name and the code of the level are the default ones (see levelName()) when null or empty */
		public Category(String role, int level, String levelName, String levelCode, double point, double price) {
			this.role = role;
			this.level = level;
			this.levelName = levelName == null || levelName.trim().length() == 0 ? levelName(level)
					: levelName.trim().replaceAll("\\s+\\+", "+"); // Middle+, also in the files saved as Middle + //$NON-NLS-1$ //$NON-NLS-2$
			this.levelCode = levelCode == null || levelCode.trim().length() == 0 ? levelAbbreviation(level) : levelCode.trim();
			this.point = point;
			this.price = price;
		}
		public String getRole() { return role; }
		public int getLevel() { return level; }
		/** Name of the level: Intern, Fresher, Junior... */
		public String getLevelName() { return levelName; }
		/** Abbreviation of the level: Intern, F, J, J+, M, M+, S */
		public String getLevelCode() { return levelCode; }
		/** Performance, 1 = 100% unit */
		public double getPoint() { return point; }
		/** Price of a man-month */
		public double getPrice() { return price; }
	}

	private static LaborCatalog instance = null;
	private List<Category> categories = Collections.emptyList();
	private Map<String, Category> byKey = new HashMap<String, Category>();

	public static synchronized LaborCatalog getInstance() {
		if (instance == null) {
			instance = new LaborCatalog();
			File file = CatalogFile.get(FILE_NAME);
			if (file.isFile()) {
				try {
					instance.set(parse(CatalogFile.read(file)));
				} catch (Exception e) {
					e.printStackTrace(); // an empty catalog: the points are 1
				}
			}
		}
		return instance;
	}

	/** Categories sorted by role, in the order of the file, then by level */
	public List<Category> getCategories() {
		return categories;
	}
	public boolean isEmpty() {
		return categories.isEmpty();
	}
	/** Roles in the order of the file */
	public List<String> getRoles() {
		Set<String> roles = new LinkedHashSet<String>();
		for (Category category : categories)
			roles.add(category.getRole());
		return new ArrayList<String>(roles);
	}
	/** Levels of a role, ascending */
	public List<Integer> getLevels(String role) {
		List<Integer> levels = new ArrayList<Integer>();
		for (Category category : categories)
			if (category.getRole().equalsIgnoreCase(role))
				levels.add(category.getLevel());
		return levels;
	}
	/** Category of a role and level, null if there is none */
	public Category find(String role, int level) {
		return role == null ? null : byKey.get(key(role, level));
	}
	/** Role of the catalog with this name, ignoring case, null if there is none */
	public String findRoleName(String name) {
		if (name == null)
			return null;
		name = name.trim();
		for (Category category : categories)
			if (category.getRole().equalsIgnoreCase(name))
				return category.getRole();
		return null;
	}

	/** Replaces the categories and saves the catalog */
	public synchronized void replace(List<Category> newCategories) throws IOException {
		List<String[]> rows = new ArrayList<String[]>();
		rows.add(new String[] { ROLE, LEVEL, LEVEL_NAME, LEVEL_CODE, POINT, PRICE });
		for (Category category : newCategories)
			rows.add(new String[] { category.getRole(), Integer.toString(category.getLevel()), category.getLevelName(),
					category.getLevelCode(), Double.toString(category.getPoint()), format(category.getPrice()) });
		CatalogFile.write(CatalogFile.get(FILE_NAME), rows);
		set(newCategories);
	}
	/** Reads the categories of a CSV file of the NLTC table, without changing the catalog */
	public static List<Category> importFile(File file) throws IOException {
		return parse(CatalogFile.read(file));
	}

	private void set(List<Category> newCategories) {
		final List<String> roles = new ArrayList<String>();
		Map<String, Category> keys = new HashMap<String, Category>();
		for (Category category : newCategories) {
			if (!roles.contains(category.getRole()))
				roles.add(category.getRole());
			keys.put(key(category.getRole(), category.getLevel()), category);
		}
		List<Category> sorted = new ArrayList<Category>(keys.values()); // the last row of a role and level wins
		Collections.sort(sorted, new Comparator<Category>() {
			public int compare(Category a, Category b) {
				int c = roles.indexOf(a.getRole()) - roles.indexOf(b.getRole());
				return c != 0 ? c : a.getLevel() - b.getLevel();
			}
		});
		categories = Collections.unmodifiableList(sorted);
		byKey = keys;
	}

	private static List<Category> parse(List<String[]> rows) throws IOException {
		if (rows.isEmpty())
			throw new IOException("The file is empty"); //$NON-NLS-1$
		String[] header = rows.get(0);
		int role = CatalogFile.column(header, ROLE), level = CatalogFile.column(header, LEVEL);
		int point = CatalogFile.column(header, POINT), price = CatalogFile.column(header, PRICE);
		int levelName = CatalogFile.column(header, LEVEL_NAME), levelCode = CatalogFile.column(header, LEVEL_CODE);
		if (role < 0 || level < 0 || point < 0)
			throw new IOException("Missing column: " + ROLE + ", " + LEVEL + " or " + POINT); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		List<Category> categories = new ArrayList<Category>();
		for (int r = 1; r < rows.size(); r++) {
			String[] row = rows.get(r);
			String name = CatalogFile.value(row, role).trim();
			try {
				int l = Integer.parseInt(CatalogFile.value(row, level).trim());
				double p = Double.parseDouble(CatalogFile.value(row, point).trim());
				String priceValue = CatalogFile.value(row, price).trim();
				double c = priceValue.length() == 0 ? 0 : Double.parseDouble(priceValue);
				if (name.length() > 0 && p > 0)
					categories.add(new Category(name, l, CatalogFile.value(row, levelName), CatalogFile.value(row, levelCode), p, c));
			} catch (NumberFormatException e) {
				throw new IOException("Invalid number on line " + (r + 1)); //$NON-NLS-1$
			}
		}
		return categories;
	}

	/** Names and abbreviations of the levels 0 to 6 */
	private static final String[] LEVEL_NAMES = { "Intern", "Fresher", "Junior", "Junior+", "Middle", "Middle+", "Senior" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
	private static final String[] LEVEL_ABBREVIATIONS = { "Intern", "F", "J", "J+", "M", "M+", "S" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$

	/** Default name of a level (Middle for 4), the number for an unknown level */
	public static String levelName(int level) {
		return level >= 0 && level < LEVEL_NAMES.length ? LEVEL_NAMES[level] : Integer.toString(level);
	}
	/** Default abbreviation of a level (M for 4), the number for an unknown level */
	public static String levelAbbreviation(int level) {
		return level >= 0 && level < LEVEL_ABBREVIATIONS.length ? LEVEL_ABBREVIATIONS[level] : Integer.toString(level);
	}
	/** Text of a level: number, name and abbreviation, like "4 - Middle (M)" */
	public static String levelText(int level) {
		if (level < 0 || level >= LEVEL_NAMES.length)
			return Integer.toString(level);
		String text = level + " - " + LEVEL_NAMES[level]; //$NON-NLS-1$
		return LEVEL_NAMES[level].equals(LEVEL_ABBREVIATIONS[level]) ? text : text + " (" + LEVEL_ABBREVIATIONS[level] + ")"; //$NON-NLS-1$ //$NON-NLS-2$
	}
	/**
	 * Level of a text: a number (or a text starting with it, like "4 - Middle (M)"), a name (Middle) or an
	 * abbreviation (M), ignoring case and spaces. -1 if it is none
	 */
	public static int parseLevel(String text) {
		if (text == null)
			return -1;
		text = text.trim();
		int digits = 0;
		while (digits < text.length() && Character.isDigit(text.charAt(digits)))
			digits++;
		if (digits > 0)
			return Integer.parseInt(text.substring(0, digits));
		String compact = text.replace(" ", "").toUpperCase(); //$NON-NLS-1$ //$NON-NLS-2$
		for (int level = 0; level < LEVEL_NAMES.length; level++)
			if (compact.equals(LEVEL_NAMES[level].replace(" ", "").toUpperCase()) || compact.equals(LEVEL_ABBREVIATIONS[level].toUpperCase())) //$NON-NLS-1$ //$NON-NLS-2$
				return level;
		return -1;
	}

	private static String key(String role, int level) {
		return role.trim().toUpperCase() + '|' + level;
	}
	private static String format(double price) {
		return price == Math.rint(price) ? Long.toString((long) price) : Double.toString(price);
	}

	/** Roles for the choice of the role of a resource */
	public static Object[] allRoles() {
		return getInstance().getRoles().toArray();
	}
	public static String findRole(String name) {
		return getInstance().findRoleName(name);
	}
	/** Levels of all the roles for the choice of the level of a resource */
	public static Object[] allLevels() {
		Set<Integer> levels = new TreeSet<Integer>();
		for (Category category : getInstance().getCategories())
			levels.add(category.getLevel());
		List<String> names = new ArrayList<String>();
		for (Integer level : levels)
			names.add(levelText(level));
		return names.toArray();
	}
	/** Text of the level typed or chosen, see parseLevel(), null if it is not a level */
	public static String findLevel(String name) {
		int level = parseLevel(name);
		return level < 0 ? null : levelText(level);
	}
}
