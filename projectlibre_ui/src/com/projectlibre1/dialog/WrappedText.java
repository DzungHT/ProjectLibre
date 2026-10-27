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

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.UIManager;

/**
 * Texts of the dialogs that never overflow nor get cut, whatever their language (the Vietnamese texts are longer):
 * <ul>
 * <li>a long text wraps at a width in pixels: help, preview, and the text of a check box or radio button</li>
 * <li>a column of a FormLayout is at least a width but grows to its content, see column(), so nothing is cut
 * when a text is wider than planned</li>
 * </ul>
 * Use them for every text that can be longer than a few words.
 */
public final class WrappedText {
	/** Pixels before the options below a check box */
	public static final int INDENT = 20;

	private WrappedText() {
	}

	/** Column of a FormLayout: the width, or more if its content needs it */
	public static String column(int width) {
		return "max(" + width + "px;pref):grow"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** HTML (with its own tags like br, b, font) wrapping at a width in pixels */
	public static String html(String html, int width) {
		return "<html><div style='width:" + width + "px'>" + html + "</div></html>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	/** A plain text, escaped, wrapping at a width in pixels */
	public static String text(String text, int width) {
		return html(escape(text), width);
	}

	/** A label of a plain text wrapping at a width in pixels */
	public static JLabel label(String text, int width) {
		return new JLabel(text(text, width));
	}

	/** Makes the plain text of a check box or radio button wrap, the whole button being at most a width in pixels */
	public static <T extends AbstractButton> T wrap(T button, int width) {
		Icon icon = UIManager.getIcon(button instanceof JRadioButton ? "RadioButton.icon" : "CheckBox.icon"); //$NON-NLS-1$ //$NON-NLS-2$
		int iconWidth = (icon == null ? 16 : icon.getIconWidth()) + button.getIconTextGap();
		button.setText(text(button.getText(), width - iconWidth - button.getInsets().left - button.getInsets().right));
		button.setVerticalTextPosition(AbstractButton.TOP);
		return button;
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	}
}
