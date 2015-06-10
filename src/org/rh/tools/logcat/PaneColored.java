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
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

import org.rh.util.JDefaultContextMenuUtil;
import org.rh.util.LLog;
import org.rh.util.StringUtil;

public class PaneColored extends JTextPane implements LogcatPlus.LoggerPane,
        JDefaultContextMenuUtil.NoAutoContextMenu {

    private boolean mIsAutoScroll = true;
    private final StyleContext mSc;
    private final HashMap<Color, AttributeSet> mAttrColorCache = new HashMap<>();
    private final DefaultStyledDocument mDoc;

    public PaneColored() {
        super(new DefaultStyledDocument());
        mDoc = (DefaultStyledDocument) getDocument();
        mSc = StyleContext.getDefaultStyleContext();

        final JPopupMenu popMenu = new JPopupMenu();
        JMenuItem copyMI = new JMenuItem("Copy");
        copyMI.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                StringUtil.copyToClipboard(getSelectedText());
            }
        });
        popMenu.add(copyMI);

        JMenuItem hlMI = new JMenuItem("Highlight");
        hlMI.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                paintTextBackground(Color.GREEN, getSelectionStart(), getSelectionEnd());
            }
        });
        popMenu.add(hlMI);

        JMenu flagM = new JMenu("Decode flag");
        for (final String miText : FlagUtil.getDecoders().keySet()) {
            JMenuItem fMI = new JMenuItem(miText);
            fMI.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String selText = getSelectedText();
                    String flgText = FlagUtil.getDecoders().get(miText).decode(selText);
                    if (flgText != null) {
                        LLog.UILogWindow.log("\n============================\nflag str=" + selText
                                + "\n" + flgText);
                    } else {
                        LLog.i("Wrong text selection for decode flag");
                    }
                    LLog.UILogWindow.showLog();
                }
            });
            flagM.add(fMI);
        }
        popMenu.add(flagM);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    popMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    @Override
    public int getLength() {
        return getDocument().getLength();
    }

    @Override
    public void append(String str) {
        AttributeSet aset = getAttributeSet(LogLine.DeviceForUI.getLevelByString(str).color);
        int len = getLength();
        int selEnd = getSelectionEnd();
        if (selEnd != getLength() && selEnd == getSelectionStart()) {
            setCaretPosition(len);
            setCharacterAttributes(aset, false);
        }

        try {
            mDoc.insertString(len, str, aset);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void paintTextBackground(Color color, int start, int end) {
        AttributeSet aset = StyleContext.getDefaultStyleContext().addAttribute(
                SimpleAttributeSet.EMPTY, StyleConstants.Background, color);
        mDoc.setCharacterAttributes(start, end - start, aset, false);
    }

    @Override
    public void setAutoScroll(boolean selected) {
    }

    @Override
    public JTextComponent asJTextComponent() {
        return this;
    }

    private AttributeSet getAttributeSet(Color c) {
        AttributeSet aset = mAttrColorCache.get(c);
        if (aset == null) {
            aset = mSc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c);
            mAttrColorCache.put(c, aset);
        }
        return aset;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true; // Text wrap
    }

    @Override
    public void scrollRectToVisible(Rectangle aRect) {
        if (mIsAutoScroll) {
            super.scrollRectToVisible(aRect);
        }
    }
}
