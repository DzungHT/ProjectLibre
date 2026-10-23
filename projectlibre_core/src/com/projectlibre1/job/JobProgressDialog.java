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
package com.projectlibre1.job;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import com.projectlibre1.strings.Messages;

/**
 * Modal dialog showing the progress of a job running in its own thread, until the thread ends. Called on the event
 * thread instead of waiting for the job: the event thread keeps running, so the window repaints and the job can
 * still run its Swing parts, while the modal dialog keeps the user from changing the project.
 */
public class JobProgressDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static final long POPUP_MILLIS = 300; // quick jobs end before the dialog opens
	private static final int REFRESH_MILLIS = 200;

	private final Job job;
	private final String message;
	private final long start = System.currentTimeMillis();
	private final JLabel messageLabel = new JLabel(" ");
	private final JLabel timeLabel = new JLabel(" ");
	private final JProgressBar progressBar = new JProgressBar(0, JobQueue.MAX_PROGRESS);
	private final Timer timer;

	/** Returns once the job, already scheduled, has ended */
	public static void waitFor(Job job, String title, String message) {
		if (isDone(job))
			return;
		try {
			job.join(POPUP_MILLIS);
		} catch (InterruptedException e) {}
		if (isDone(job))
			return;
		Component owner = job.getJobQueue().getComponent();
		Window window = owner == null || owner instanceof Window ? (Window)owner : SwingUtilities.getWindowAncestor(owner);
		new JobProgressDialog(window, job, title, message).setVisible(true); // returns when disposed at the end of the job
	}

	/** Not started (nothing to do, or already running) or ended */
	private static boolean isDone(Job job) {
		Thread.State state = job.getState();
		return state == Thread.State.NEW || state == Thread.State.TERMINATED;
	}

	private JobProgressDialog(Window owner, Job job, String title, String message) {
		super(owner, title, ModalityType.APPLICATION_MODAL);
		this.job = job;
		this.message = message;
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		setResizable(false);

		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		JPanel labels = new JPanel(new BorderLayout(12, 0));
		labels.add(messageLabel, BorderLayout.CENTER);
		labels.add(timeLabel, BorderLayout.EAST);
		panel.add(labels, BorderLayout.NORTH);
		progressBar.setPreferredSize(new Dimension(360, progressBar.getPreferredSize().height));
		panel.add(progressBar, BorderLayout.CENTER);
		setContentPane(panel);
		refresh();
		pack();
		setLocationRelativeTo(owner);
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		timer = new Timer(REFRESH_MILLIS, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (isDone(JobProgressDialog.this.job)) {
					timer.stop();
					dispose();
				} else
					refresh();
			}
		});
		timer.start();
	}

	private void refresh() {
		String note = job.getNote();
		messageLabel.setText(note == null || note.length() == 0 ? message : note);
		float progress = job.getProgress();
		if (progress > 0f && progress < 1f) {
			progressBar.setIndeterminate(false);
			progressBar.setValue(Math.round(progress * JobQueue.MAX_PROGRESS));
		} else
			progressBar.setIndeterminate(true); // nothing reported yet, or the last steps after the file is written
		long seconds = (System.currentTimeMillis() - start) / 1000;
		timeLabel.setText(MessageFormat.format(Messages.getString("JobProgress.Elapsed"), String.valueOf(seconds))); //$NON-NLS-1$
	}
}
