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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.MessageFormat;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import com.projectlibre1.job.ProgressReporter;
import com.projectlibre1.strings.Messages;

/**
 * Modal progress dialog for long operations, showing the time spent and, when the work is counted, the time left.
 * Work that changes the project is done on the event thread, since the model is not thread safe, but in slices:
 * between two slices the dialog repaints and the Cancel button can be pressed, while the modal dialog keeps the user
 * from touching the project. Work that only reads files or the project can instead run on a worker thread, see
 * runInBackground, so that the dialog stays alive during a single long step.
 */
public class BusyOperationDialog extends JDialog implements ProgressReporter {
	private static final long serialVersionUID = 1L;
	private static final long SLICE_MILLIS = 40;
	private static final long POPUP_MILLIS = 300; // quick background work ends before the dialog opens
	private static final int REFRESH_MILLIS = 200;
	private static final long ESTIMATE_AFTER_MILLIS = 1500; // the rate of the first steps says little

	/** One small piece of the work, called until it returns true */
	public interface Step {
		boolean next(BusyOperationDialog dialog) throws Exception;
	}

	/** Work run on a worker thread: it must not change the project, and can report its progress to the dialog */
	public interface Background<T> {
		T run(BusyOperationDialog dialog) throws Exception;
	}

	private final Step step;
	private final JLabel messageLabel = new JLabel(" ");
	private final JLabel countLabel = new JLabel(" ");
	private final JLabel timeLabel = new JLabel(" ");
	private final JProgressBar progressBar = new JProgressBar(0, 1000);
	private final JButton cancelButton = new JButton(Messages.getString("Text.Cancel"));
	private final Timer timer;
	private final long start = System.currentTimeMillis();
	private volatile boolean canceled = false;
	private boolean sliceEnded = false;
	private Throwable exception = null;

	// progress shown, for the time left: the rate is measured since the message, so the phase, changed
	private String shownMessage = null;
	private int shownDone = 0;
	private int shownTotal = 0;
	private long phaseStart = 0;
	private int phaseStartDone = 0;

	// progress reported by a worker thread, shown by the timer
	private Thread worker = null;
	private Object result = null;
	private volatile String pendingMessage = null;
	private volatile int pendingDone = 0;
	private volatile int pendingTotal = 0;
	private volatile boolean pending = false;

	/**
	 * Run step until it is done or canceled. Returns false if canceled. An exception thrown by the step stops the work
	 * and is thrown again once the dialog is closed.
	 */
	public static boolean run(Component owner, String title, Step step) throws Exception {
		BusyOperationDialog dialog = new BusyOperationDialog(window(owner), title, step);
		dialog.setVisible(true); // returns when the dialog is disposed at the end of the work
		dialog.rethrow();
		return !dialog.canceled;
	}

	/**
	 * Run a single long operation on the event thread, after showing the message: the bar cannot move during the work,
	 * but the user sees what is being done instead of a frozen window. Runtime exceptions are thrown again.
	 */
	public static void run(Component owner, String title, final String message, final Runnable work) {
		try {
			run(owner, title, new Step() {
				boolean started = false;
				public boolean next(BusyOperationDialog dialog) {
					if (!started) {
						started = true;
						dialog.setCancelable(false);
						dialog.setProgress(message, 0, 0);
						dialog.endSlice(); // show the message before the work
						return false;
					}
					work.run();
					return true;
				}
			});
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Run work on a worker thread, showing the dialog if it takes more than a moment. Returns what the work returns,
	 * or null if it was canceled. An exception thrown by the work is thrown again.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T runInBackground(Component owner, String title, String message, final Background<T> work) throws Exception {
		final BusyOperationDialog dialog = new BusyOperationDialog(window(owner), title, null);
		dialog.showProgress(message, 0, 0);
		dialog.worker = new Thread("BusyOperation: " + title) { //$NON-NLS-1$
			public void run() {
				try {
					dialog.result = work.run(dialog);
				} catch (Throwable e) {
					dialog.exception = e;
				}
			}
		};
		dialog.worker.start();
		try {
			dialog.worker.join(POPUP_MILLIS);
		} catch (InterruptedException e) {}
		if (dialog.worker.isAlive())
			dialog.setVisible(true); // returns when the timer sees that the worker ended
		else
			dialog.dispose();
		dialog.rethrow();
		return dialog.canceled ? null : (T) dialog.result;
	}

	private static Window window(Component owner) {
		return owner == null || owner instanceof Window ? (Window)owner : SwingUtilities.getWindowAncestor(owner);
	}

	private BusyOperationDialog(Window owner, String title, Step step) {
		super(owner, title, ModalityType.APPLICATION_MODAL);
		this.step = step;
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		setResizable(false);

		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		JPanel labels = new JPanel(new BorderLayout(12, 0));
		labels.add(messageLabel, BorderLayout.CENTER);
		labels.add(countLabel, BorderLayout.EAST);
		panel.add(labels, BorderLayout.NORTH);
		progressBar.setPreferredSize(new Dimension(420, progressBar.getPreferredSize().height));
		panel.add(progressBar, BorderLayout.CENTER);
		JPanel bottom = new JPanel(new BorderLayout(12, 0));
		bottom.add(timeLabel, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		buttons.add(cancelButton);
		bottom.add(buttons, BorderLayout.EAST);
		panel.add(bottom, BorderLayout.SOUTH);
		setContentPane(panel);
		updateTime();
		pack();
		setLocationRelativeTo(owner);
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				canceled = true;
				cancelButton.setEnabled(false);
			}
		});
		timer = new Timer(REFRESH_MILLIS, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refresh();
			}
		});
		addWindowListener(new WindowAdapter() {
			public void windowOpened(WindowEvent e) {
				timer.start();
				if (BusyOperationDialog.this.step != null)
					SwingUtilities.invokeLater(slice);
			}
			public void windowClosing(WindowEvent e) {
				if (cancelButton.isEnabled())
					canceled = true;
			}
			public void windowClosed(WindowEvent e) {
				timer.stop();
			}
		});
	}

	private final Runnable slice = new Runnable() {
		public void run() {
			long end = System.currentTimeMillis() + SLICE_MILLIS;
			sliceEnded = false;
			try {
				while (!canceled) {
					if (step.next(BusyOperationDialog.this)) {
						dispose();
						return;
					}
					if (sliceEnded || System.currentTimeMillis() >= end)
						break;
				}
			} catch (Exception e) {
				exception = e;
			}
			if (canceled || exception != null)
				dispose();
			else {
				updateTime(); // the timer cannot run during a long step
				SwingUtilities.invokeLater(this); // queued after the repaint of the progress
			}
		}
	};

	/** Called by the timer: shows the progress of the worker, the time, and closes the dialog once the worker ended */
	private void refresh() {
		if (worker != null) {
			if (!worker.isAlive()) {
				dispose();
				return;
			}
			if (pending) {
				pending = false;
				showProgress(pendingMessage, pendingDone, pendingTotal);
			}
		}
		updateTime();
	}

	private void rethrow() throws Exception {
		if (exception instanceof Exception)
			throw (Exception)exception;
		if (exception instanceof Error)
			throw (Error)exception;
	}

	/** Show a message and the progress done/total, or an animated bar if total is 0. Can be called from any thread */
	public void setProgress(String message, int done, int total) {
		if (SwingUtilities.isEventDispatchThread()) {
			showProgress(message, done, total);
		} else {
			pendingMessage = message;
			pendingDone = done;
			pendingTotal = total;
			pending = true;
		}
	}

	private void showProgress(String message, int done, int total) {
		long now = System.currentTimeMillis();
		if (!Objects.equals(message, shownMessage) || total != shownTotal || done < shownDone) {
			phaseStart = now; // a new phase: its rate is measured from now
			phaseStartDone = done;
		}
		shownMessage = message;
		shownDone = done;
		shownTotal = total;
		messageLabel.setText(message == null || message.length() == 0 ? " " : message); //$NON-NLS-1$
		if (total > 0) {
			progressBar.setIndeterminate(false);
			progressBar.setValue((int)(1000L * Math.min(done, total) / total));
			countLabel.setText(done + "/" + total); //$NON-NLS-1$
		} else {
			progressBar.setIndeterminate(true);
			countLabel.setText(" "); //$NON-NLS-1$
		}
	}

	/** The time spent and, once the rate of the current phase is known, the time it still needs */
	private void updateTime() {
		long now = System.currentTimeMillis();
		String elapsed = formatTime(now - start);
		long phaseMillis = now - phaseStart;
		int phaseDone = shownDone - phaseStartDone;
		if (shownTotal > 0 && phaseDone > 0 && phaseMillis >= ESTIMATE_AFTER_MILLIS && shownDone < shownTotal) {
			long left = phaseMillis * (shownTotal - shownDone) / phaseDone;
			timeLabel.setText(MessageFormat.format(Messages.getString("BusyOperation.TimeLeft"), elapsed, formatTime(left))); //$NON-NLS-1$
		} else {
			timeLabel.setText(MessageFormat.format(Messages.getString("BusyOperation.Elapsed"), elapsed)); //$NON-NLS-1$
		}
	}

	private static String formatTime(long millis) {
		long seconds = (millis + 999) / 1000;
		if (seconds < 60)
			return MessageFormat.format(Messages.getString("BusyOperation.Seconds"), String.valueOf(seconds)); //$NON-NLS-1$
		return MessageFormat.format(Messages.getString("BusyOperation.Minutes"), String.valueOf(seconds / 60), String.valueOf(seconds % 60)); //$NON-NLS-1$
	}

	/** Once the work starts changing the project it can no longer be canceled */
	public void setCancelable(final boolean cancelable) {
		if (SwingUtilities.isEventDispatchThread())
			cancelButton.setEnabled(cancelable && !canceled);
		else
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					setCancelable(cancelable);
				}
			});
	}

	public boolean isCanceled() {
		return canceled;
	}

	/** End the current slice after this step, so that the dialog repaints before a long step */
	public void endSlice() {
		sliceEnded = true;
	}
}
