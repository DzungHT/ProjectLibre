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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;
import com.projectlibre1.help.HelpUtil;
import com.projectlibre1.menu.MenuManager;
import com.projectlibre1.pm.graphic.IconManager;
import com.projectlibre1.strings.Messages;
import com.projectlibre1.util.Alert;
import com.projectlibre1.util.Environment;

public final class WelcomeDialog extends AbstractDialog {
	private static final long serialVersionUID = 1L;

	public static class Form {
		boolean createProject = true;
		boolean openProject = false;
		boolean manageResources = false;
		String recentFile = null;
		public final boolean isCreateProject() {
			return createProject;
		}
		public final void setCreateProject(boolean createProject) {
			this.createProject = createProject;
		}
		public final boolean isManageResources() {
			return manageResources;
		}
		public final void setManageResources(boolean manageResources) {
			this.manageResources = manageResources;
		}
		public final boolean isOpenProject() {
			return openProject;
		}
		public final void setOpenProject(boolean openProject) {
			this.openProject = openProject;
		}
		/** the file chosen in the recent projects, null if a button was chosen */
		public final String getRecentFile() {
			return recentFile;
		}
		public final void setRecentFile(String recentFile) {
			this.recentFile = recentFile;
		}
	}
	private Form form;
	private MenuManager menuManager;
	// use property utils to copy to project like struts

	ButtonGroup radioGroup;
	JButton createProject;
	JButton openProject;
	JButton manageResources;
	JList recentList;

	protected boolean bind(boolean get) {
		if (form == null)
			return false;
		if (!get) {
			form.setCreateProject(createProject.isSelected());
			form.setOpenProject(openProject.isSelected());
			form.setManageResources(manageResources.isSelected());
		}
		return true;
	}
	public static WelcomeDialog getInstance(Frame owner, MenuManager menuManager) {
		return new WelcomeDialog(owner,menuManager);
	}

	private WelcomeDialog(Frame owner, MenuManager menuManager) {
		super(owner, Messages.getContextString("Text.welcomeToPod"), true); //$NON-NLS-1$
		this.menuManager = menuManager;
		form = new Form();
	}

	// Component Creation and Initialization **********************************

	/**
	 * Creates, intializes and configures the UI components. Real applications
	 * may further bind the components to underlying models.
	 */
	protected void initControls() {
		createProject = new JButton(Messages.getString("Text.createProject"),IconManager.getIcon("menu24.new"));
		openProject = new JButton(Messages.getString("Text.openProject"),IconManager.getIcon("menu24.open"));
		manageResources = new JButton(Messages.getString("Text.manageResources"),IconManager.getIcon("view.resources"));
		
		HelpUtil.addDocHelp(createProject,"Creating_a_Project");
		HelpUtil.addDocHelp(manageResources,"Managing_your_resource_pool");
		
		createProject.setSelected(true);
		
		ActionListener buttonListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				createProject.setSelected(false);
				openProject.setSelected(false);
				manageResources.setSelected(false);
				((JButton)e.getSource()).setSelected(true);
				onOk();
			}};
			
		createProject.addActionListener(buttonListener);
		openProject.addActionListener(buttonListener);
		manageResources.addActionListener(buttonListener);
		
		initRecentList();
		bind(true);
	}

	private void initRecentList() {
		DefaultListModel model = new DefaultListModel();
		for (RecentProjects.Entry entry : RecentProjects.getEntries())
			model.addElement(entry);
		recentList = new JList(model);
		recentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		recentList.setVisibleRowCount(RecentProjects.MAX);
		recentList.setCellRenderer(new RecentRenderer());
		recentList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				int index = recentList.locationToIndex(e.getPoint());
				if (index >= 0 && recentList.getCellBounds(index, index).contains(e.getPoint()))
					openRecent(index);
			}
		});
		// Enter on the list opens the selected project instead of the default Create Project
		recentList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openRecent"); //$NON-NLS-1$
		recentList.getActionMap().put("openRecent", new AbstractAction() { //$NON-NLS-1$
			private static final long serialVersionUID = 1L;
			public void actionPerformed(ActionEvent e) {
				if (recentList.getSelectedIndex() >= 0)
					openRecent(recentList.getSelectedIndex());
			}
		});
	}

	private void openRecent(int index) {
		RecentProjects.Entry entry = (RecentProjects.Entry) recentList.getModel().getElementAt(index);
		if (!entry.exists()) {
			Alert.warn(MessageFormat.format(Messages.getString("WelcomeDialog.FileNotFound"), entry.getPath()), this); //$NON-NLS-1$
			RecentProjects.remove(entry.getPath());
			((DefaultListModel) recentList.getModel()).remove(index);
			return;
		}
		form.setRecentFile(entry.getPath());
		onOk();
	}

	private static final class RecentRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			RecentProjects.Entry entry = (RecentProjects.Entry) value;
			String pathColor = isSelected ? "" : " color='#808080'"; //$NON-NLS-1$ //$NON-NLS-2$
			String text = "<html><b>" + escape(entry.getName()) + "</b>&nbsp;&nbsp;<font" + pathColor + ">" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ escape(entry.getPath()) + "</font></html>"; //$NON-NLS-1$
			JLabel label = (JLabel) super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
			label.setIcon(IconManager.getIcon("menu.open")); //$NON-NLS-1$
			label.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
			label.setToolTipText(entry.getPath());
			return label;
		}
		private static String escape(String s) {
			return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		}
	}

	// Building *************************************************************

	/**
	 * Builds the panel. Initializes and configures components first, then
	 * creates a FormLayout, configures the layout, creates a builder, sets a
	 * border, and finally adds the components.
	 * 
	 * @return the built panel
	 */

	public JComponent createContentPanel() {
		// Separating the component initialization and configuration
		// from the layout code makes both parts easier to read.
		initControls();
		//TODO set minimum size
		FormLayout layout = new FormLayout("default, 3dlu, default, 3dlu, default", // cols //$NON-NLS-1$
				"p, 8dlu, p, 3dlu, p, 3dlu, p, 3dlu"); // rows //$NON-NLS-1$

		// Create a builder that assists in adding components to the container.
		// Wrap the panel with a standardized border.
		DefaultFormBuilder builder = new DefaultFormBuilder(layout);
		builder.setBorder(BorderFactory.createEmptyBorder(20,20,20,20)); // use bigger border to fit title bar text
		builder.append(Messages.getString("WelcomeDialog.WhatWouldYouLikeToDo") + "      "); // adding spaces to widen dialog //$NON-NLS-1$ //$NON-NLS-2$
		builder.nextLine(2);
		builder.append(createProject);
		builder.nextLine(2);
		builder.append(openProject);
		if (Environment.isAdministrator()) {
			builder.nextLine(2);
			builder.append(manageResources);
		}
		requestFocusInWindow();

		JPanel panel = new JPanel(new BorderLayout());
		panel.add(builder.getPanel(), BorderLayout.WEST);
		panel.add(createRecentPanel(), BorderLayout.CENTER);
		return panel;
	}

	private JComponent createRecentPanel() {
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 20));
		panel.add(new JLabel(Messages.getString("WelcomeDialog.RecentProjects")), BorderLayout.NORTH); //$NON-NLS-1$
		if (recentList.getModel().getSize() == 0) {
			JLabel empty = new JLabel(Messages.getString("WelcomeDialog.NoRecentProjects")); //$NON-NLS-1$
			empty.setForeground(Color.GRAY);
			empty.setVerticalAlignment(JLabel.TOP);
			panel.add(empty, BorderLayout.CENTER);
		} else {
			JScrollPane scroll = new JScrollPane(recentList);
			scroll.setPreferredSize(new Dimension(560, 270));
			panel.add(scroll, BorderLayout.CENTER);
		}
		return panel;
	}

	/**
	 * @return Returns the form.
	 */
	public Form getForm() {
		return form;
	}
	
	public Object getBean() {
		return form;
	}
	@Override
	protected boolean hasOkAndCancelButtons() {
		return false;
	}
}
