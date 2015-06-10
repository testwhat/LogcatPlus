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

package org.rh.util;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

public class JFilterTextField extends JTextField {
    private final static Color sRegExpMode = new Color(200, 250, 200);
    private Runnable mOnLengthSatisfiedAction, mOnEmpty;
    private String mCurrentLowerCaseStr = "";
    private String[] mCurrentSepBySpace;
    private boolean mIsRegExpMode;
    private Pattern mRegExp;

    public JFilterTextField(int columns, final int filterLen) {
        this(columns, filterLen, false);
    }

    public JFilterTextField(int columns, final int filterLen, boolean supportRegexp) {
        super(columns);

        if (supportRegexp) {
            setToolTipText("Press ctrl + R to enter regular expression mode");
            KeyStroke ctrlR = KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_MASK);
            getInputMap().put(ctrlR, "ctrlR");
            getActionMap().put("ctrlR", new AbstractAction() {

                public void actionPerformed(ActionEvent e) {
                    mIsRegExpMode = !mIsRegExpMode;
                    if (mIsRegExpMode) {
                        setBackground(sRegExpMode);
                    } else {
                        setBackground(Color.WHITE);
                    }
                    getCaretListeners()[0].caretUpdate(null);
                }
            });
        }

        addCaretListener(new CaretListener() {
            int previousLen;
            String previousText;

            @Override
            public void caretUpdate(CaretEvent e) {
                JTextField tf = e == null ? JFilterTextField.this : (JTextField) e.getSource();
                String text = tf.getText();
                if ((text == null || text.length() == 0)
                        && (previousText != null && previousText.length() > 0)) {
                    mOnEmpty.run();
                }
                if (text == null || (e != null && text.equals(previousText))) {
                    return;
                }
                if (mIsRegExpMode) {
                    try {
                        mRegExp = Pattern.compile(text);
                    } catch (Exception syntaxE) {
                        mRegExp = null;
                    }
                }
                mCurrentLowerCaseStr = text.toLowerCase();
                mCurrentSepBySpace = mCurrentLowerCaseStr.split(" ");

                int len = text.length();
                if (mOnLengthSatisfiedAction != null) {
                    if (len > filterLen) {
                        mOnLengthSatisfiedAction.run();
                    } else if (previousLen > len && previousLen == filterLen + 1) {
                        mOnLengthSatisfiedAction.run();
                    }
                }
                previousLen = len;
                previousText = text;
            }
        });

        setDocument(new PlainDocument() {
            private static final int limit = 128;

            @Override
            public void insertString(int offs, String str, AttributeSet a)
                    throws BadLocationException {
                if (str == null) {
                    return;
                }

                if ((getLength() + str.length()) <= limit) {
                    super.insertString(offs, str, a);
                }
            }
        });
    }

    public boolean isRegExpMode() {
        return mIsRegExpMode;
    }

    public boolean match(String text) {
        return mRegExp != null && mRegExp.matcher(text).find();
    }

    public int getLength() {
        return getDocument().getLength();
    }

    public String getCurrentLowerCaseStr() {
        return mCurrentLowerCaseStr;
    }

    public String[] getCurrentSepBySpace() {
        return mCurrentSepBySpace;
    }

    public void setOnLengthSatisfiedListener(Runnable action) {
        mOnLengthSatisfiedAction = action;
    }

    public void setOnEmpty(Runnable action) {
        mOnEmpty = action;
    }
}
