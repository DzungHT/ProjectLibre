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
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.projectlibre1.strings.Messages;

/**
 * Modal progress dialog for long operations on the project. The work is done on the event thread, since the model
 * is not thread safe, but in slices: between two slices the dialog repaints and the Cancel button can be pressed,
 * while the modal dialog keeps the user from touching the project.
 */
public class BusyOperationDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static final long SLICE_MILLIS = 40;

	/** One small piece of the work, called until it returns true */
	public interface Step {
		boolean next(BusyOperationDialog dialog) throws Exception;
	}

	private final Step step;
	private final JLabel messageLabel = new JLabel(" ");
	private final JLabel countLabel = new JLabel(" ");
	private final JProgressBar progressBar = new JProgressBar(0, 1000);
	private final JButton cancelButton = new JButton(Messages.getString("Text.Cancel"));
	private boolean canceled = false;
	private boolean sliceEnded = false;
	private Exception exception = null;

	/**
	 * Run step until it is done or canceled. Returns false if canceled. An exception thrown by the step stops the work
	 * and is thrown again once the dialog is closed.
	 */
	public static boolean run(Component owner, String title, Step step) throws Exception {
		Window window = owner instanceof Window ? (Window)owner : SwingUtilities.getWindowAncestor(owner);
		BusyOperationDialog dialog = new BusyOperationDialog(window, title, step);
		dialog.setVisible(true); // returns when the dialog is disposed at the end of the work
		if (dialog.exception != null)
			throw dialog.exception;
		return !dialog.canceled;
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
		progressBar.setPreferredSize(new java.awt.Dimension(360, progressBar.getPreferredSize().height));
		panel.add(progressBar, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		buttons.add(cancelButton);
		panel.add(buttons, BorderLayout.SOUTH);
		setContentPane(panel);
		pack();
		setLocationRelativeTo(owner);
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				canceled = true;
			}
		});
		addWindowListener(new WindowAdapter() {
			public void windowOpened(WindowEvent e) {
				SwingUtilities.invokeLater(slice);
			}
			public void windowClosing(WindowEvent e) {
				if (cancelButton.isEnabled())
					canceled = true;
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
			else
				SwingUtilities.invokeLater(this); // queued after the repaint of the progress
		}
	};

	/** Show a message and the progress done/total, or an animated bar if total is 0 */
	public void setProgress(String message, int done, int total) {
		messageLabel.setText(message);
		if (total > 0) {
			progressBar.setIndeterminate(false);
			progressBar.setValue((int)(1000L * done / total));
			countLabel.setText(done + "/" + total);
		} else {
			progressBar.setIndeterminate(true);
			countLabel.setText(" ");
		}
	}

	/** Once the work starts changing the project it can no longer be canceled */
	public void setCancelable(boolean cancelable) {
		cancelButton.setEnabled(cancelable);
	}

	public boolean isCanceled() {
		return canceled;
	}

	/** End the current slice after this step, so that the dialog repaints before a long step */
	public void endSlice() {
		sliceEnded = true;
	}
}
