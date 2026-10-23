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
package com.projectlibre1.association;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

/**
 * Association list that finds the associations of an object without going through the list, for the lists that
 * hold an association with each task, like the dependencies of the start and end sentinels of the schedule: with
 * large projects, adding a task or calculating the schedule would otherwise go through all the tasks for each one.
 * <p>
 * The associations are indexed by their left and right objects, in their order. The index is kept up to date by
 * add() and remove(), and dropped by the other changes: it is built again when needed. Associations are compared by
 * identity, as dependencies are.
 */
public class IndexedAssociationList extends AssociationList {
	private Map<Object, Set<Association>> lefts = null;
	private Map<Object, Set<Association>> rights = null;

	private void buildIndex() {
		lefts = new IdentityHashMap<Object, Set<Association>>();
		rights = new IdentityHashMap<Object, Set<Association>>();
		for (Iterator i = list.iterator(); i.hasNext();) {
			Object next = i.next();
			if (next instanceof Association)
				index((Association) next);
		}
	}
	private void index(Association association) {
		index(lefts, association.getLeft(), association);
		index(rights, association.getRight(), association);
	}
	private static void index(Map<Object, Set<Association>> side, Object key, Association association) {
		Set<Association> associations = side.get(key);
		if (associations == null) {
			associations = new LinkedHashSet<Association>();
			side.put(key, associations);
		}
		associations.add(association);
	}
	private static void unindex(Map<Object, Set<Association>> side, Object key, Association association) {
		Set<Association> associations = side.get(key);
		if (associations != null && associations.remove(association) && associations.isEmpty())
			side.remove(key);
	}
	private void dropIndex() {
		lefts = rights = null;
	}
	private Map<Object, Set<Association>> side(boolean leftObject) {
		if (lefts == null)
			buildIndex();
		return leftObject ? lefts : rights;
	}

	public boolean add(Association association) {
		Set<Association> sameLeft = side(true).get(association.getLeft());
		Set<Association> sameRight = side(false).get(association.getRight());
		if (sameLeft != null && sameRight != null) { // already in the list? looks in the smallest of both
			for (Association other : sameLeft.size() < sameRight.size() ? sameLeft : sameRight)
				if (other.getLeft() == association.getLeft() && other.getRight() == association.getRight())
					return false;
		}
		boolean added = list.add(association);
		if (added)
			index(association);
		return added;
	}
	public boolean add(Object object) {
		if (object instanceof Association)
			return add((Association) object);
		dropIndex();
		return list.add(object);
	}

	public Association find(boolean leftObject, Object object) {
		Set<Association> associations = side(leftObject).get(object);
		return associations == null ? null : associations.iterator().next();
	}

	public boolean remove(Object object) {
		boolean removed = list.remove(object);
		if (removed && lefts != null && object instanceof Association) {
			Association association = (Association) object;
			unindex(lefts, association.getLeft(), association);
			unindex(rights, association.getRight(), association);
		}
		return removed;
	}

	// other changes: the index is dropped
	public void addFirst(Object object) {
		dropIndex();
		super.addFirst(object);
	}
	public void addLast(Object object) {
		dropIndex();
		super.addLast(object);
	}
	public void add(int index, Object object) {
		dropIndex();
		super.add(index, object);
	}
	public boolean addAll(Collection collection) {
		dropIndex();
		return super.addAll(collection);
	}
	public boolean addAll(int index, Collection collection) {
		dropIndex();
		return super.addAll(index, collection);
	}
	public void clear() {
		dropIndex();
		super.clear();
	}
	public Object remove(int index) {
		dropIndex();
		return super.remove(index);
	}
	public boolean removeAll(Collection collection) {
		dropIndex();
		return super.removeAll(collection);
	}
	public Object removeFirst() {
		dropIndex();
		return super.removeFirst();
	}
	public Object removeLast() {
		dropIndex();
		return super.removeLast();
	}
	public boolean retainAll(Collection collection) {
		dropIndex();
		return super.retainAll(collection);
	}
	public Object set(int index, Object object) {
		dropIndex();
		return super.set(index, object);
	}
	/** The list can be changed directly: the index is dropped */
	public LinkedList getList() {
		dropIndex();
		return super.getList();
	}
	public Iterator iterator() {
		return listIterator();
	}
	public ListIterator listIterator() {
		return new Changes(super.listIterator());
	}
	public ListIterator listIterator(int index) {
		return new Changes(super.listIterator(index));
	}

	/** Iterator that drops the index when it changes the list */
	private class Changes implements ListIterator {
		private final ListIterator iterator;
		Changes(ListIterator iterator) {
			this.iterator = iterator;
		}
		public boolean hasNext() { return iterator.hasNext(); }
		public Object next() { return iterator.next(); }
		public boolean hasPrevious() { return iterator.hasPrevious(); }
		public Object previous() { return iterator.previous(); }
		public int nextIndex() { return iterator.nextIndex(); }
		public int previousIndex() { return iterator.previousIndex(); }
		public void remove() {
			dropIndex();
			iterator.remove();
		}
		public void set(Object object) {
			dropIndex();
			iterator.set(object);
		}
		public void add(Object object) {
			dropIndex();
			iterator.add(object);
		}
	}
}
