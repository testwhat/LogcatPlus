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

import java.awt.BorderLayout;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class LLog {
    static boolean sUseUi = false;
    static boolean sVerb = true;

    private final static SimpleDateFormat sdf = new SimpleDateFormat("MM-dd kk:mm:ss:SSS");
    private final static Date date = new Date();

    private synchronized static String time() {
        date.setTime(System.currentTimeMillis());
        return sdf.format(date);
    }

    static interface P {
        void print(CharSequence str);
        void println(CharSequence str);
    }

    static final P sStdOut = new P() {
        @Override
        public void print(CharSequence str) {
            System.out.print(str);
        }

        @Override
        public void println(CharSequence str) {
            System.out.println(time() + " " + str);
        }
    };

    static P sOut = sStdOut;

    public static void e(String msg) {
        sOut.println(msg);
        if (sUseUi) {
            UILogWindow.log(msg);
        }
    }

    public static void ex(Throwable e) {
        String s = exception(e);
        sOut.println(s);
        if (sUseUi) {
            UILogWindow.log(s);
        }
    }

    public static void i(String msg) {
        sOut.println(msg);
        if (sUseUi) {
            UILogWindow.log(msg);
        }
    }

    public static void i(Object o) {
        String s = o == null ? "null" : o.toString();
        sOut.println(s);
        if (sUseUi) {
            UILogWindow.log(s);
        }
    }

    public static void v(String msg) {
        sOut.println(msg);
        if (sUseUi) {
            UILogWindow.log(msg);
        }
    }

    public static String exception(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    private static P wl;

    public static class UILogWindow extends JFrame implements P {
        private JTextArea logTA = new JTextArea();
        private static UILogWindow sSingle;

        private UILogWindow(String title) {
            super(title);
            setLayout(new BorderLayout());
            JScrollPane SP = new JScrollPane(logTA);
            add(SP, BorderLayout.CENTER);
            setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            logTA.setEditable(false);
            setBounds(140, 140, 450, 250);
            sSingle = this;
        }

        public static void showLog() {
            if (sSingle == null) {
                return;
            }
            sSingle.setVisible(true);
        }

        public synchronized static void log(String log) {
            if (wl == null) {
                wl = new UILogWindow("Internal Log");
            }
            wl.println(log);
        }

        @Override
        public void print(CharSequence str) {
            logTA.append(String.valueOf(str));
            logTA.setCaretPosition(logTA.getDocument().getLength());
        }

        @Override
        public void println(CharSequence log) {
            logTA.append(time() + " " + log + "\n");
            logTA.setCaretPosition(logTA.getDocument().getLength());
        }
    }
}
