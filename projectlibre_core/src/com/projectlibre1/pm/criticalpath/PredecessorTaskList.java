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
package com.projectlibre1.pm.criticalpath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.projectlibre1.configuration.CircularDependencyException;
import com.projectlibre1.grouping.core.Node;
import com.projectlibre1.pm.dependency.Dependency;
import com.projectlibre1.pm.task.SubProj;
import com.projectlibre1.pm.task.Task;
import com.projectlibre1.util.Environment;

/**
* This class implements a task list in predecessor/parent order.  That is, the successors of any given
* task are guaranteed to be after that task in the list. Also wbs children are after their parents.
*  This ordering is needed for the critical path algorithm.
*/
public class PredecessorTaskList {
	private LinkedList list = new LinkedList();
	private int calculationStateCount = 0;
	private boolean markerStatus;
	private int numberOfReverseScheduledTasks = 0;
	public static final int CALCULATION_STATUS_STEP = 3;
	private SchedulingAlgorithm schedulingAlgorithm;
	// while pasting many tasks, they are only added at the end: the list is put in order once afterwards, see CriticalPath
	private boolean appendOnly = false;
	private boolean appended = false;
	void setAppendOnly(boolean appendOnly) {
		this.appendOnly = appendOnly;
	}
	/** True if tasks were added at the end since the last call. Resets the flag */
	boolean takeAppended() {
		boolean result = appended;
		appended = false;
		return result;
	}
	PredecessorTaskList(SchedulingAlgorithm schedulingAlgorithm) {
		this.schedulingAlgorithm = schedulingAlgorithm;
	}
	void removeTask(Task task) {
		if (task.isReverseScheduled())
			numberOfReverseScheduledTasks--;

		Iterator i = list.iterator();
		TaskReference current;
		// the item may be in the list once or twice.  It may be that it is in twice, but the
		// task is no longer a parent
		while (i.hasNext()) {
			current = (TaskReference) i.next();
			if (current.task == task) {
				i.remove();
			}
		}
	}
	ListIterator reverseIterator() {
		return list.listIterator(list.size());
	}

	/** Tasks of references, once each, in their order */
	private static Collection tasksOf(Collection references) {
		Set tasks = new LinkedHashSet();
		for (Iterator i = references.iterator(); i.hasNext();)
			tasks.add(((TaskReference) i.next()).getTask());
		return tasks;
	}

/**
 * Add a subproject. It will convert the existing task into a parent and add all children
 * @param subproject
 */	
	public void addSubproject(final Task subproject) {
		// remove sentinels 
		TaskReference startSentinel = (TaskReference) list.removeFirst();
		TaskReference endSentinel = (TaskReference) list.removeLast();

		// mark tasks to be added as not yet treated
		boolean m = !getMarkerStatus();
		subproject.setMarkerStatus(m);
		subproject.markTaskAsNeedingRecalculation();
		Iterator j = ((SubProj)subproject).getSubproject().getTasks().iterator();
		while (j.hasNext()) {
			Task task = (Task)j.next();
			task.setMarkerStatus(m);
			task.markTaskAsNeedingRecalculation();

		}
		
		// all the tasks are arranged again: the subproject becomes a parent, with the tasks of the subproject
		Collection tasks = tasksOf(list);
		tasks.addAll(((SubProj)subproject).getSubproject().getTasks());
		list = arrange(tasks);

		// put back sentinels
		list.addFirst(startSentinel);
		list.addLast(endSentinel);
	}
    /**
     * Insert a task into the list.  Go thru and insert it after its parent
     * The task being inserted is a new task and as such has no preds/succs. Just insert it after its parent
     *  * @param hasDependencies
     */
    void arrangeTask(Task task) {
        if (task.isReverseScheduled())
			numberOfReverseScheduledTasks++;
    	task.setMarkerStatus(markerStatus);
        TaskReference previousTaskReference;
    	Task previousTask;
        // go thru in reverse order inserting after first predecessor or parent encountered
        ListIterator i = list.listIterator();
        TaskReference taskReference = new TaskReference(task);
        if (appendOnly) { // before the end sentinel, in order later
        	ListIterator end = list.listIterator(list.size());
        	end.previous();
        	end.add(taskReference);
        	appended = true;
        	return;
        }
        while (i.hasNext()) {
        	previousTaskReference = (TaskReference)i.next();
        	previousTask = previousTaskReference.getTask();
            if (task.getWbsParentTask() == previousTask) {
            	i.add(taskReference);
                return;
            }
        }
        i.previous(); // add before end sentinel
        i.add(taskReference);
    }    
	/**
	 * Return a list iterator - delegates to internal list
	 * @return list iterator
	 */
	ListIterator listIterator() {
		return list.listIterator();
	}
	
	LinkedList getList(){
		return list;
	}
	
	public void dump() {
		ListIterator i = list.listIterator();
		Object x;
		while (i.hasNext()) {
			x = i.next();
			System.out.println(x);
		}
	}
	
	synchronized int getFreshCalculationStateCount() {
		while (calculationStateCount % CALCULATION_STATUS_STEP != 0)// go by 3s so we can see what happens during different passes
			calculationStateCount++;
		return calculationStateCount;
	}
	synchronized int getNextCalculationStateCount() {
		calculationStateCount += 1; // just get next one
		return calculationStateCount;
	}
	int getCalculationStateCount() {
		return calculationStateCount;
	}

	boolean addAll(Collection tasks) {
		toggleMarkerStatus();
		list = arrange(tasks);
		return true;
	}

	/** A task to arrange: a summary has a node for its begin and one for its end, another task a single node */
	private static final class Arranged {
		final Task task;
		final int first; // node of the begin of a summary
		final int last; // node of the end of a summary
		Arranged(Task task, int first, int last) {
			this.task = task;
			this.first = first;
			this.last = last;
		}
	}

	/**
	 * Puts tasks in predecessor/parent order: a summary begins before its children and ends after them, and a task
	 * comes after its predecessors, after the end of a summary predecessor. The tasks they depend on that are not
	 * given, like the children of a summary or the predecessors in another project, are added too.
	 * It is a topological sort: a task is put once all the ones that must be before it are, and among the tasks
	 * that can be put, the first given first, so the order stays the one of the WBS when the links allow it.
	 * The former recursive arrangement (Task.arrangeTask) marked a task as treated before its predecessors were
	 * put: through a predecessor in another summary, the children of this summary were put, and among them the
	 * successors of the task, before the task. These successors were then calculated with old dates of the task.
	 * Also counts the reverse scheduled tasks.
	 */
	private LinkedList arrange(Collection tasks) {
		Map<Task, Arranged> arranged = new IdentityHashMap<Task, Arranged>();
		List<Arranged> order = new ArrayList<Arranged>(); // in the order given, then the order found
		int nodeCount = 0;
		for (Iterator i = tasks.iterator(); i.hasNext();)
			nodeCount = add((Task) i.next(), arranged, order, nodeCount);
		for (int a = 0; a < order.size(); a++) { // tasks the given ones depend on
			Task task = order.get(a).task;
			if (task.getWbsParentTask() != null)
				nodeCount = add(task.getWbsParentTask(), arranged, order, nodeCount);
			for (Iterator i = task.getPredecessorList().iterator(); i.hasNext();) {
				Dependency dependency = (Dependency) i.next();
				if (!dependency.isDisabled())
					nodeCount = add((Task) dependency.getPredecessor(), arranged, order, nodeCount);
			}
			Collection children = task.getWbsChildrenNodes();
			if (children != null)
				for (Iterator i = children.iterator(); i.hasNext();) {
					Object child = ((Node) i.next()).getImpl();
					if (child instanceof Task)
						nodeCount = add((Task) child, arranged, order, nodeCount);
				}
		}

		// edges: from a node to the ones that must be after it
		Arranged[] taskOfNode = new Arranged[nodeCount];
		int[][] after = new int[nodeCount][];
		int[] afterCount = new int[nodeCount];
		int[] waiting = new int[nodeCount]; // number of nodes still to put before a node
		List<int[]> edges = new ArrayList<int[]>();
		for (Arranged a : order) {
			taskOfNode[a.first] = taskOfNode[a.last] = a;
			if (a.first != a.last)
				edges.add(new int[] { a.first, a.last });
			Arranged parent = arranged.get(a.task.getWbsParentTask());
			if (parent != null && parent.first != parent.last) {
				edges.add(new int[] { parent.first, a.first });
				edges.add(new int[] { a.last, parent.last });
			}
			for (Iterator i = a.task.getPredecessorList().iterator(); i.hasNext();) {
				Dependency dependency = (Dependency) i.next();
				Arranged predecessor = arranged.get(dependency.getPredecessor());
				if (!dependency.isDisabled() && predecessor != null && predecessor != a)
					edges.add(new int[] { predecessor.last, a.first });
			}
		}
		for (int[] edge : edges) {
			afterCount[edge[0]]++;
			waiting[edge[1]]++;
		}
		for (int n = 0; n < nodeCount; n++)
			after[n] = new int[afterCount[n]];
		int[] filled = new int[nodeCount];
		for (int[] edge : edges)
			after[edge[0]][filled[edge[0]]++] = edge[1];

		LinkedList result = new LinkedList();
		numberOfReverseScheduledTasks = 0;
		boolean[] put = new boolean[nodeCount];
		PriorityQueue<Integer> ready = new PriorityQueue<Integer>();
		for (int n = 0; n < nodeCount; n++)
			if (waiting[n] == 0)
				ready.add(n);
		int firstNotPut = 0;
		for (int count = 0; count < nodeCount; count++) {
			int n;
			if (!ready.isEmpty())
				n = ready.poll();
			else { // circular links: the first node not put is put anyway, so that all the tasks are calculated
				if (Environment.isImporting())
					throw new RuntimeException(CircularDependencyException.RUNTIME_EXCEPTION_TEXT);
				while (put[firstNotPut])
					firstNotPut++;
				n = firstNotPut;
			}
			put[n] = true;
			Arranged a = taskOfNode[n];
			TaskReference reference = new TaskReference(a.task);
			if (a.first != a.last) {
				if (n == a.first)
					reference.setParentBegin();
				else
					reference.setParentEnd();
			}
			result.add(reference);
			if (n == a.first && a.task.isReverseScheduled())
				numberOfReverseScheduledTasks++;
			for (int next : after[n])
				if (--waiting[next] == 0 && !put[next])
					ready.add(next);
		}
		return result;
	}

	private static int add(Task task, Map<Task, Arranged> arranged, List<Arranged> order, int nodeCount) {
		if (arranged.containsKey(task))
			return nodeCount;
		Arranged a = task.isWbsParent() ? new Arranged(task, nodeCount, nodeCount + 1) : new Arranged(task, nodeCount, nodeCount);
		arranged.put(task, a);
		order.add(a);
		return a.last + 1;
	}
	
	private void setDebugDependencyOrder() {
		int count = 0;
		Iterator i = list.iterator();
		Task task;
		TaskReference ref;
		while (i.hasNext()) {
			ref = (TaskReference)i.next();
			if (ref.getType() == TaskReference.PARENT_END)
				continue;
			task = ref.getTask();
			task.setDebugDependencyOrder(count++);
		}
	}
	
	void rearrangeAll() {
		LinkedList oldList = list;
		// store off sentinels to put them back later
		TaskReference startSentinel = (TaskReference) list.removeFirst();
		TaskReference endSentinel = (TaskReference) list.removeLast();
		toggleMarkerStatus();
		list = arrange(tasksOf(oldList));
		list.addFirst(startSentinel);
		list.addLast(endSentinel);
//		setDebugDependencyOrder();
 	}
	
	boolean hasReverseScheduledTasks() {
		return (numberOfReverseScheduledTasks > 0);
	}
	public static final class TaskReference implements Comparable {
		static final int PARENT_BEGIN = -1;
		static final int CHILD = 0;
		static final int PARENT_END = 1;
		
		public TaskReference(Task task) {
			this.task = task;
		}
		Task task;
		int type = CHILD;
		TaskReference opposite = null;
		long calculationStateCount = 0;
		/**
		 * @return Returns the task.
		 */
		public Task getTask() {
			return task;
		}
		/* (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Object arg0) {
			if (arg0 instanceof Task)
				return (getTask() == arg0 ? 0 : -1);
			return (arg0 == this ? 0 : -1);
		}
		
		public void setParentBegin() {
			type = PARENT_BEGIN;
		}
		public void setParentEnd() {
			type = PARENT_END;
		}
		public String toString() {
			String result = task.toString();
			if (type == PARENT_BEGIN)
				result += " begin";
			else if (type == PARENT_END)
				result += " end";
			return result;
		}
		
		/**
		 * @return Returns the type.
		 */
		public int getType() {
			return type;
		}
	}
	/**
	 * Refresh the Reverse schedule count - called in response to change in constraint type field
	 */
	void recalculateReverseScheduledCount() {
		numberOfReverseScheduledTasks = 0;
		
		Iterator i = list.iterator();
		Task task;
		while (i.hasNext()) {
			task = ((TaskReference)i.next()).getTask();
	    	if (task.isReverseScheduled())
				numberOfReverseScheduledTasks++;
		}
	}
	
	
	/**
	 * @return Returns the markerStatus.
	 */
	public final boolean getMarkerStatus() {
		return markerStatus;
	}
	
	final boolean toggleMarkerStatus() {
		markerStatus = !markerStatus;
		return markerStatus;
	}
	
	// for debugging - finds position(s) in pred list of a task
	public int[] findTaskPosition(Task t) {
		int[] result;
		if (t.isWbsParent())
			result = new int[2];
		else
			result = new int[1];
			
		Iterator i = list.iterator();
		Task task;
		int resultIndex = 0;
		int pos = 0;
		while (i.hasNext()) {
			task = ((TaskReference)i.next()).getTask();
			if (task == t) {
				result[resultIndex++]  = pos;
				if (resultIndex == result.length)
					break;
			}
			pos++;
		}
		return result;
	}
}
