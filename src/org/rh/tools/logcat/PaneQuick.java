/*
 * [The "BSD licence"]
 * Copyright (c) 2014 Riddle Hsu
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.rh.tools.logcat;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.text.JTextComponent;

import org.rh.util.JDefaultContextMenuUtil;
import org.rh.util.StringUtil;

public class PaneQuick extends JTextArea implements LogcatPlus.LoggerPane,
        JDefaultContextMenuUtil.NoAutoContextMenu {
    private boolean mIsAutoScroll = true;

    public PaneQuick() {
        final JPopupMenu popMenu = new JPopupMenu();
        JMenuItem copyMI = new JMenuItem("Copy");
        copyMI.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                StringUtil.copyToClipboard(getSelectedText());
            }
        });
        popMenu.add(copyMI);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    popMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
        setLineWrap(true);
        setWrapStyleWord(true);
    }

    public int getLength() {
        return getDocument().getLength();
    }

    @Override
    public void append(String str) {
        super.append(str);
        if (mIsAutoScroll) {
            int len = getLength();
            int selEnd = getSelectionEnd();
            if (selEnd != getLength() && selEnd == getSelectionStart()) {
                setCaretPosition(len);
            }
        }
    }

    public void paintTextBackground(Color color, int start, int end) {

    }

    public void setAutoScroll(boolean selected) {
        mIsAutoScroll = selected;
    }

    @Override
    public JTextComponent asJTextComponent() {
        return this;
    }
}