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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import javax.swing.text.Utilities;
import javax.swing.text.View;

import org.rh.util.JDefaultContextMenuUtil;
import org.rh.util.JFilterTextField;
import org.rh.util.LLog;
import org.rh.util.LookAndFeel;
import org.rh.util.StringUtil;

public class LogcatPlus {
    public static void main(String[] args) {
        new LogcatPlus().start();
    }

    interface LogProvider {
        void init();
        void exit();
        void start();
        void stop();
    }

    LogProvider mLogProvider;

    // TODO: set font, ddmlib, multi-device
    interface LoggerPane {
        int getLength();
        void append(String str);
        void paintTextBackground(Color color, int start, int end);
        void setAutoScroll(boolean selected);
        void setText(String string);
        void setFont(Font mDefaultFont);
        String getText();
        Document getDocument();
        Font getFont();
        JTextComponent asJTextComponent();
    }

    LoggerPane mPane;
    Font mDefaultFont;
    int mShowingLines;

    final static Color[] sBkgColors = {
        new Color(255, 217, 0), // yellow
        new Color(255, 116, 255), // purple
        new Color(0, 255, 229) // cyan
    };
    JFilterTextField mIncludeTF;
    JFilterTextField mExcludeTF;
    JFilterTextField mHighlightTF;
    UiControl mControl;

    final int mFilterLen = 1;

    volatile boolean mIsPaused;
    boolean mIsEnableOrInclude;
    boolean mIsEnableAndInclude;
    boolean mIsEnableOrExclude;
    boolean mIsEnableDefaultTagFilter;

    LogLine.DeviceForUI.Level mCurrentLevel = LogLine.DeviceForUI.Level.V;
    LogSource mLogSrc = LogSource.system_main;

    enum LogSource {
        system_main("system & main", "", true),
        system("system", "-b system", true),
        main("main", "-b main", true),
        events("events", "-b events", false),
        kernel("kernel", "", false),
        radio("radio", "-b radio", true),
        crash("crash", "-b crash", true),
        system_events("system & event", "-b system -b events", true),
        system_main_events("system & main & event", "-b system -b main -b events", true),
        all("all", "-b all", true);

        LogSource(String n, String b, boolean l) {
            name = n;
            cmd = b;
            hasLevel = l;
        }

        public final String name;
        public final String cmd;
        public final boolean hasLevel;

        public String getCommand() {
            return this != kernel ? (" logcat " + cmd + " -v threadtime")
                    : " shell cat /proc/kmsg ";
        }

        final static HashMap<String, LogSource> srcMapping = new HashMap<>();
        static {
            for (LogSource s : LogSource.values()) {
                srcMapping.put(s.name, s);
            }
        }

        public static LogSource getByName(String n) {
            return srcMapping.get(n);
        }
    }

    JCheckBox mAutoScrollCB;
    final ArrayList<LogLine.DeviceForUI> mLogs = new ArrayList<>(8192);

    public static int CLEAN_MIN = 5000;
    public static int CLEAN_MAX = 50000;
    public static int CLEAN_MAX_DIFF = 2000;
    int mLogCleanHighPos = CLEAN_MIN + 3000;
    int mLogCleanLowPos = mLogCleanHighPos - CLEAN_MAX_DIFF;
    int mLowLength;
    int mLowCount;

    //final static String[] TAG_BLACK_LIST = {};
    final StringUtil.AhoCorasick mExcFilter = new StringUtil.AhoCorasick();
    StringUtil.AhoCorasick mIncFilter;

    abstract static class TaskPool<T extends TaskPool<T>.Task> {
        private final LinkedList<T> tasks = new LinkedList<>();
        private int limit = 256;

        void preAllocate(int preSize) {
            limit = preSize;
            for (int i = 0; i < limit; i++) {
                tasks.add(create());
            }
        }

        abstract class Task implements Runnable {
            @Override
            public void run() {
                runTask();
                recycle();
            }

            abstract void runTask();

            @SuppressWarnings("unchecked")
            private void recycle() {
                synchronized (tasks) {
                    if (tasks.size() < limit) {
                        tasks.addLast((T) this);
                    }
                }
            }
        }

        int size() {
            return tasks.size();
        }

        abstract T create();

        T pick() {
            synchronized (tasks) {
                if (tasks.isEmpty() == false) {
                    return tasks.pop();
                }
            }
            return create();
        }
    }

    public void appendText(final String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    mPane.append(text);
                }
            });
        } else {
            mPane.append(text);
        }
    }

    private void appendLine(LogLine line) {
        int pos, start = -1, end = -1;
        String hl;
        if (mHighlightTF.getLength() > mFilterLen) {
            hl = mHighlightTF.getText();
            pos = line.mMsg.toLowerCase().indexOf(mHighlightTF.getCurrentLowerCaseStr());
            if (pos > -1) {
                start = mPane.getLength() + pos;
                end = start + hl.length();
            }
        }
        mPane.append(line.mMsg + "\n");
        if (end > start) {
            mPane.paintTextBackground(sBkgColors[0], start, end);
        }
        mShowingLines++;
    }

    private class AppendLogPool extends TaskPool<AppendLogPool.AppendLogTask> {
        final static int BUSY_THRESHOLD = 500;
        AtomicInteger mEventCount = new AtomicInteger(0);
        ArrayList<LogLine> mBusyBuf = new ArrayList<>(256);

        private class AppendLogTask extends TaskPool<AppendLogTask>.Task {
            private LogLine mLine;

            @Override
            void runTask() {
                appendLine(mLine);
                mLine = null;
                int remain = mEventCount.decrementAndGet();
                if (remain == 0 && !mBusyBuf.isEmpty()) {
                    for (int i = 0, s = mBusyBuf.size(); i < s; i++) {
                        if (i % BUSY_THRESHOLD == 0) {
                            checkTrimLogLocked();
                        }
                        appendLine(mBusyBuf.get(i));
                    }
                    mBusyBuf.clear();
                }
            }
        }

        private AppendLogPool() {
            preAllocate(2048);
        }

        @Override
        AppendLogTask create() {
            return new AppendLogTask();
        }

        private void appendLogLine(LogLine l) {
            AppendLogTask t = (AppendLogTask) pick();
            t.mLine = l;
            int remain = mEventCount.get();
            if (remain > BUSY_THRESHOLD) {
                mBusyBuf.add(l);
            } else {
                mEventCount.incrementAndGet();
                SwingUtilities.invokeLater(t);
            }
        }
    }

    private AppendLogPool mTextUpdater = new AppendLogPool();

    public LogcatPlus() {
        LookAndFeel.initDefault();

        mPane = new PaneColored();
                //new PaneQuick();
        mDefaultFont = mPane.getFont();
                //new Font("Monospaced", Font.PLAIN, 13);
        mPane.setFont(mDefaultFont);

        JScrollPane scrollPane = new JScrollPane(mPane.asJTextComponent());
        scrollPane.setRowHeaderView(new RowHeader(mPane.asJTextComponent()));

        JFrame f = new JFrame("Logcat Plus V0.1");
        JPanel baseP = new JPanel(new BorderLayout());

        Box controlPanel = new Box(BoxLayout.Y_AXIS);
        mControl = new UiControl(this, controlPanel);

        baseP.add(controlPanel, BorderLayout.NORTH);
        baseP.add(scrollPane, BorderLayout.CENTER);

        f.setContentPane(baseP);
        f.setBounds(100, 100, 840, 640);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                mLogProvider.exit();
            }
        });
        mLogProvider = new ByDdmlib(this);
        JDefaultContextMenuUtil.enable();
        f.setVisible(true);
    }

    public void start() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                mIncludeTF.requestFocus();
                new Thread() {
                    @Override
                    public void run() {
                        mLogProvider.init();
                        mLogProvider.start();
                    };
                }.start();
            }
        });
    }

    public void onLog(String[] lines) {
        for (String line : lines) {
            onLog(line);
        }
    }

    public void onLog(String line) {
        LogLine.DeviceForUI logline = new LogLine.DeviceForUI(line);
        boolean isNeed = okToShow(logline);
        synchronized (mLogs) {
            mLogs.add(logline);
            checkTrimLogLocked();
        }

        if (!mIsPaused && isNeed) {
            mTextUpdater.appendLogLine(logline);
        }
    }

    public String getCommand() {
        return mLogSrc.getCommand();
    }

    public LogSource getLogSource() {
        return mLogSrc;
    }

    void restartLogger() {
        mLogProvider.stop();
        setInputEnable(true);
        mLogs.clear();
        emptyPane();
        mPane.setFont(mDefaultFont);
        mPane.setAutoScroll(mAutoScrollCB.isSelected());
        System.gc();
        mLogProvider.start();
    }

    void emptyPane() {
        mPane.setText("");
        mShowingLines = 0;
    }

    void setInputEnable(boolean enable) {
        mIncludeTF.setEnabled(enable);
        mExcludeTF.setEnabled(enable);
        mHighlightTF.setEnabled(enable);
    }

    void checkTrimLogLocked() {
        int size = mLogs.size();
        if (size < mLogCleanLowPos) {
            return;
        }
        if (size < mLogCleanHighPos) {
            if (mLowLength == 0) {
                mLowCount = size;
                mLowLength = mPane.getLength();
            }
            return;
        }
        if (mShowingLines * 2 < size) {
            return;
        }
        //LLog.i("s:" + mShowingLines + " p:" + mPane.getLength() + " ls:" + size);

        final int cleanPos = mLowLength;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    //LLog.i("Trim " + cleanPos);
                    mPane.getDocument().remove(0, cleanPos);
                } catch (javax.swing.text.BadLocationException ex) {
                    LLog.e("checkTrimLog BadLocation " + cleanPos);
                }
            }
        });

        mLogs.subList(0, mLowCount).clear();
        mLowLength = 0;
        System.gc();
    }

    static ExecutorService sRerfeshEs = Executors.newSingleThreadExecutor();
    Runnable mRefresh = new Runnable() {
        @Override
        public void run() {
            emptyPane();
            synchronized (mLogs) {
                for (int i = 0, s = mLogs.size(); i < s; i++) {
                    LogLine.DeviceForUI line = mLogs.get(i);
                    if (okToShow(line)) {
                        appendLine(line);
                        if (i % 200 == 0) {
                            checkTrimLogLocked();
                        }
                    }
                }
            }
        }
    };

    void refreshContentByCondition() {
        sRerfeshEs.execute(mRefresh);
    }

    boolean okToShow(LogLine.DeviceForUI line) {
        if (line.mLevel.value < mCurrentLevel.value) {
            return false;
        }

        if (mLogSrc.hasLevel) {
            if (mIsEnableDefaultTagFilter && mExcFilter.contains(line.getTag())) {
                return false;
            }
            if (mIncFilter != null && !mIncFilter.contains(line.getTag())) {
                return false;
            }
        }

        String ex = mExcludeTF.getCurrentLowerCaseStr();
        String in = mIncludeTF.getCurrentLowerCaseStr();
        boolean normalExclude = ex.length() > mFilterLen && !mExcludeTF.isRegExpMode();
        boolean normalInclude = in.length() > mFilterLen && !mIncludeTF.isRegExpMode();
        String msg = (normalExclude || normalInclude) ? line.mMsg.toLowerCase() : "";
        if (normalExclude) {
            if (mIsEnableOrExclude) {
                for (String s : mExcludeTF.getCurrentSepBySpace()) {
                    if (msg.contains(s)) {
                        return false;
                    }
                }
            } else if (msg.contains(ex)) {
                return false;
            }
        } else if (mExcludeTF.isRegExpMode()) {
            return !mExcludeTF.match(line.mMsg);
        }
        if (normalInclude) {
            if (mIsEnableAndInclude) {
                for (String s : mIncludeTF.getCurrentSepBySpace()) {
                    if (msg.contains(s) == false) {
                        return false;
                    }
                }
            } else if (mIsEnableOrInclude) {
                for (String s : mIncludeTF.getCurrentSepBySpace()) {
                    if (msg.contains(s) == true) {
                        return true;
                    }
                }
                return false;
            } else if (msg.contains(in) == false) {
                return false;
            }
        } else if (mIncludeTF.isRegExpMode()) {
            return mIncludeTF.match(line.mMsg);
        }

        return true;
    }

    int mPausedIndex;

    public void setPause(boolean pause) {
        mIsPaused = pause;
        if (pause) {
            mPausedIndex = mLogs.size();
        } else {
            synchronized (mLogs) {
                for (int i = mPausedIndex, s = mLogs.size(); i < s; i++) {
                    LogLine.DeviceForUI line = mLogs.get(i);
                    if (okToShow(line)) {
                        appendLine(line);
                    }
                }
            }
        }
    }

    public void setText(String text) {
        mPane.setText("No device connected");
    }
}

class UiControl {
//http://stackoverflow.com/questions/942500/is-there-a-way-to-hide-the-tab-bar-of-jtabbedpane-if-only-one-tab-exists
    final LogcatPlus mL;
    JRadioButton[] mLogLevelRadioBtns;
    JButton mMoreFuncBtn;
    JPopupMenu mMenu;
    Timer mControlTimer = new Timer();

    public UiControl(LogcatPlus logger, Box container) {
        mL = logger;
        container.add(newControlFirstLine());
        container.add(newControlSecondLine());
    }

    JButton newClearTextButton(final JTextField tf) {
        JButton btn = new JButton("X");
        Border margin = new EmptyBorder(5, 10, 5, 10);
        btn.setBorder(margin);
        btn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String t = tf.getText();
                if (t != null && t.length() > 0) {
                    tf.setText("");
                    mL.refreshContentByCondition();
                }
            }
        });

        return btn;
    }

    private Action mLevelChangeAction = new AbstractAction() {

        @Override
        public void actionPerformed(ActionEvent e) {
            int level = e.getActionCommand().charAt(0) - 'A';
            mL.mCurrentLevel = LogLine.DeviceForUI.sLogLevelMap[level];
            mL.refreshContentByCondition();
        }
    };

    TimerTask mLastRefreshTask;
    Runnable mRefreshAction = new Runnable() {
        @Override
        public void run() {
            if (mLastRefreshTask != null) {
                mLastRefreshTask.cancel();
            }
            mControlTimer.purge();
            mControlTimer.schedule(mLastRefreshTask = new TimerTask() {
                @Override
                public void run() {
                    mL.refreshContentByCondition();
                }
            }, 200);
        }
    };

    private void setTextFieldOnChange(JFilterTextField tf) {
        tf.setOnLengthSatisfiedListener(mRefreshAction);
        tf.setOnEmpty(mRefreshAction);
    }

    private Box newControlFirstLine() {
        Box control1 = new Box(BoxLayout.X_AXIS);
        control1.add(Box.createHorizontalStrut(10));

        control1.add(new JLabel("Highlight: "));
        setTextFieldOnChange(mL.mHighlightTF = new JFilterTextField(20, mL.mFilterLen));
        control1.add(mL.mHighlightTF);
        control1.add((newClearTextButton(mL.mHighlightTF)));
        control1.add(Box.createHorizontalStrut(10));

        // ---------------------------------------------------
        int levelSize = LogLine.DeviceForUI.sLogLevels.length - 1;
        ButtonGroup levelGroup = new ButtonGroup();
        mLogLevelRadioBtns = new JRadioButton[levelSize];
        for (int i = 0; i < mLogLevelRadioBtns.length; i++) {
            JRadioButton rb = new JRadioButton(LogLine.DeviceForUI.sLogLevels[i].text + "  ");
            rb.setActionCommand(LogLine.DeviceForUI.sLogLevels[i].text);
            rb.setBackground(LogLine.DeviceForUI.sLogLevels[i].color);
            rb.setForeground(LogLine.DeviceForUI.sLogLevels[i].color);
            rb.setBorderPainted(true);
            rb.addActionListener(mLevelChangeAction);
            rb.setBorder(BorderFactory.createEtchedBorder());
            levelGroup.add(mLogLevelRadioBtns[i] = rb);
        }
        mLogLevelRadioBtns[0].setSelected(true);

        JPanel logLevelPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gBC = new GridBagConstraints();
        gBC.fill = GridBagConstraints.BOTH;
        for (gBC.gridx = 0; gBC.gridx < levelSize; gBC.gridx++) {
            logLevelPanel.add(mLogLevelRadioBtns[gBC.gridx], gBC);
        }

        // ---------------------------------------------------
        mL.mAutoScrollCB = new JCheckBox("Auto Scroll");
        mL.mAutoScrollCB.setMargin(new Insets(0, 10, 0, 10));
        mL.mAutoScrollCB.setSelected(true);
        mL.mAutoScrollCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mL.mPane.setAutoScroll(mL.mAutoScrollCB.isSelected());
            }
        });
        mL.mPane.setAutoScroll(mL.mAutoScrollCB.isSelected());
        logLevelPanel.add(mL.mAutoScrollCB);
        control1.add(logLevelPanel);

        // ---------------------------------------------------
        final JButton restart = new JButton("Restart");
        restart.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mL.restartLogger();
            }
        });
        control1.add(restart);

        // ---------------------------------------------------
        JButton clear = new JButton("Clear");
        clear.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mL.emptyPane();
                mL.mLogs.clear();
                System.gc();
            }
        });
        control1.add(clear);

        // ---------------------------------------------------
        final String pauseStr = "  Pause  ";
        final JButton pause = new JButton(pauseStr);
        pause.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pause.getText().equals(pauseStr)) {
                    mL.setPause(true);
                    pause.setText("Resume");
                } else {
                    pause.setText(pauseStr);
                    mL.setPause(false);
                }
            }
        });
        control1.add(pause);

        return control1;
    }

    private Box newControlSecondLine() {
        Box control2 = new Box(BoxLayout.X_AXIS);
        control2.add(Box.createHorizontalStrut(10));

        setTextFieldOnChange(mL.mIncludeTF = new JFilterTextField(20, mL.mFilterLen, true));
        control2.add(new JLabel("include: "));
        control2.add(mL.mIncludeTF);
        control2.add(newClearTextButton(mL.mIncludeTF));
        control2.add(Box.createHorizontalStrut(10));

        setTextFieldOnChange(mL.mExcludeTF = new JFilterTextField(20, mL.mFilterLen, true));
        control2.add(new JLabel("exclude: "));
        control2.add(mL.mExcludeTF);
        control2.add(newClearTextButton(mL.mExcludeTF));
        control2.add(Box.createHorizontalStrut(10));

        // ---------------------------------------------------
//        final TagFilterFrame tagFilterFrame = new TagFilterFrame("Tag Filter");
//        control2.add(tagFilterFrame.getToggleButton("QF"));
//        tagFilterFrame.setOnApplyAction(new Runnable() {
//            @Override
//            public void run() {
//                if (tagFilterFrame.isFilterEnable()) {
//                    String[] selTags = tagFilterFrame.getSelectedTags();
//                    if (selTags != null && selTags.length > 0) {
//                        mL.mIncFilter = new StringUtil.AhoCorasick(selTags);
//                    }
//                } else {
//                    mL.mIncFilter = null;
//                }
//                mL.refreshContentByCondition();
//            }
//        });

        // ---------------------------------------------------
        class LogSourceButtonGroup {
            final ButtonGroup group = new ButtonGroup();

            void setSelection(LogcatPlus.LogSource s) {
                for (Enumeration<AbstractButton> e = group.getElements(); e.hasMoreElements();) {
                    JRadioButtonMenuItem b = (JRadioButtonMenuItem) e.nextElement();
                    if (mL.mLogSrc.name.equals(b.getText())) {
                        group.setSelected(b.getModel(), true);
                        break;
                    }
                }
            }

            void updateSelection() {
                for (Enumeration<AbstractButton> e = group.getElements(); e.hasMoreElements();) {
                    JRadioButtonMenuItem b = (JRadioButtonMenuItem) e.nextElement();
                    if (b.getModel() == group.getSelection()) {
                        mL.mLogSrc = LogcatPlus.LogSource.getByName(b.getText());
                        break;
                    }
                }
                mL.restartLogger();
            }
        }
        final LogSourceButtonGroup logSrcBg = new LogSourceButtonGroup();

        final JButton quickSwtichLogBtn = new JButton(" evt log ");
        quickSwtichLogBtn.setForeground(Color.DARK_GRAY);
        quickSwtichLogBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (mL.mLogSrc != LogcatPlus.LogSource.system_main) {
                    quickSwtichLogBtn.setText(" evt log ");
                    mL.mLogSrc = LogcatPlus.LogSource.system_main;
                    logSrcBg.setSelection(mL.mLogSrc);
                    mL.restartLogger();
                } else if (mL.mLogSrc != LogcatPlus.LogSource.events) {
                    quickSwtichLogBtn.setText(" dev log");
                    mL.mLogSrc = LogcatPlus.LogSource.events;
                    logSrcBg.setSelection(mL.mLogSrc);
                    mL.restartLogger();
                }
            }
        });
        control2.add(quickSwtichLogBtn);

        mMenu = new JPopupMenu();
        // ---------------------------------------------------
        JMenu logSrcMenu = new JMenu("Log source");
        mMenu.add(logSrcMenu);
        ActionListener logSrcListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent paramActionEvent) {
                logSrcBg.updateSelection();
            }
        };
        for (LogcatPlus.LogSource s : LogcatPlus.LogSource.values()) {
            JMenuItem item = new JRadioButtonMenuItem(s.name);
            item.addActionListener(logSrcListener);
            logSrcBg.group.add(item);
            logSrcMenu.add(item);
        }
        logSrcBg.setSelection(LogcatPlus.LogSource.system_main);

        JMenu inMenu = new JMenu("Include condition");
        mMenu.add(inMenu);

        final ButtonGroup bg = new ButtonGroup();
        ActionListener inCondListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent paramActionEvent) {
                for (Enumeration<AbstractButton> e = bg.getElements(); e.hasMoreElements();) {
                    JRadioButtonMenuItem b = (JRadioButtonMenuItem) e.nextElement();
                    if (b.getModel() == bg.getSelection()) {
                        if (b.getText().equals("or")) {
                            if (mL.mIsEnableOrInclude) {
                                mL.mIsEnableOrInclude = false;
                                bg.clearSelection();
                            } else {
                                mL.mIsEnableOrInclude = true;
                                mL.mIsEnableAndInclude = false;
                            }
                        } else {
                            if (mL.mIsEnableAndInclude) {
                                mL.mIsEnableAndInclude = false;
                                bg.clearSelection();
                            } else {
                                mL.mIsEnableAndInclude = true;
                                mL.mIsEnableOrInclude = false;
                            }
                        }
                        mL.refreshContentByCondition();
                    }
                }
            }
        };

        JMenuItem mitem = new JRadioButtonMenuItem("or");
        mitem.addActionListener(inCondListener);
        bg.add(mitem);
        inMenu.add(mitem);

        mitem = new JRadioButtonMenuItem("and");
        mitem.addActionListener(inCondListener);
        bg.add(mitem);
        inMenu.add(mitem);

        // ---------------------------------------------------
        mitem = new JCheckBoxMenuItem("On/Off \"Or\" for exclude");
        mitem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mL.mIsEnableOrExclude = ((JCheckBoxMenuItem) e.getSource()).isSelected();
                mL.refreshContentByCondition();
            }
        });
        mMenu.add(mitem);

        // ---------------------------------------------------
        mitem = new JCheckBoxMenuItem("Copy to clipboard");
        mitem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                StringUtil.copyToClipboard(mL.mPane.getText());
                ((JCheckBoxMenuItem) e.getSource()).setSelected(false);
            }
        });
        mMenu.add(mitem);

        /*
        // ---------------------------------------------------
        mitem = new JCheckBoxMenuItem("Default TAG filter");
        mitem.setSelected(mL.mIsEnableDefaultTagFilter);
        mitem.setToolTipText("Default ignore: " + StringUtil.join(LogcatPlus.TAG_BLACK_LIST, ","));
        mitem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mL.mIsEnableDefaultTagFilter = ((JCheckBoxMenuItem) e.getSource()).isSelected();
                mL.refreshContentByCondition();
            }
        });
        mMenu.add(mitem);

        // ---------------------------------------------------
        mitem = new JMenuItem("View process");
        mitem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    //mL.viewProcess();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        mMenu.add(mitem);*/

        Box b = Box.createVerticalBox();
        final JLabel ctlLabel = new JLabel("Clean log threshold=" + mL.mLogCleanHighPos);
        b.add(ctlLabel);
        b.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        b.setPreferredSize(new Dimension(0, 50));
        final JSlider cltSlider = new JSlider(JSlider.HORIZONTAL,
                LogcatPlus.CLEAN_MIN, LogcatPlus.CLEAN_MAX, mL.mLogCleanHighPos);
        cltSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                mL.mLogCleanHighPos = cltSlider.getValue();
                mL.mLogCleanLowPos = mL.mLogCleanHighPos - LogcatPlus.CLEAN_MAX_DIFF;
                ctlLabel.setText("Clean log threshold=" + mL.mLogCleanHighPos);
            }
        });
        cltSlider.setMajorTickSpacing(10000);
        cltSlider.setMinorTickSpacing(5000);
        cltSlider.setPaintTicks(true);
        b.add(cltSlider);
        mMenu.add(new JSeparator());
        mMenu.add(b);

        mMoreFuncBtn = new JButton("Menu");
        mMoreFuncBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mMenu.show((Component) e.getSource(), 3, 28);
            }
        });

        control2.add(mMoreFuncBtn);
        return control2;
    }

    public void setControlEnable(boolean enable) {
        mL.mIncludeTF.setEnabled(enable);
        mL.mExcludeTF.setEnabled(enable);
        mL.mHighlightTF.setEnabled(enable);
        mMoreFuncBtn.setEnabled(enable);
        for (JRadioButton b : mLogLevelRadioBtns) {
            b.setEnabled(enable);
        }
    }
}

class RowHeader extends JPanel implements CaretListener, DocumentListener, PropertyChangeListener {
    private final static Border OUTER = new MatteBorder(0, 0, 0, 2, Color.GRAY);
    private final static int NUM_HEIGHT = Integer.MAX_VALUE - 1000000;

    private JTextComponent mComponent;
    private Document mDoc;
    private Element mRoot;
    private boolean mUpdateFont;
    private boolean mForceUpdateWidth;
    private int mMinimumDisplayDigits = 3;

    private int mLastDigits;
    private int mLastHeight;
    private int mLastLine;
    private Point mTmpPoint = new Point(0, 0);

    private HashMap<String, FontMetrics> mFonts;

    public RowHeader(JTextComponent c) {
        mComponent = c;
        mDoc = c.getDocument();
        mRoot = mDoc.getDefaultRootElement();
        mDoc.addDocumentListener(this);
        setFont(c.getFont());
        setBorderGap(5);
        c.addCaretListener(this);
        c.addPropertyChangeListener("font", this);
    }

    public boolean getUpdateFont() {
        return mUpdateFont;
    }

    public void setUpdateFont(boolean updateFont) {
        mUpdateFont = updateFont;
    }

    public void updateFont(Font font) {
        setFont(font);
        mForceUpdateWidth = true;
        setPreferredWidth();
        mForceUpdateWidth = false;
    }

    public void setBorderGap(int borderGap) {
        Border inner = new EmptyBorder(0, borderGap, 0, borderGap);
        setBorder(new CompoundBorder(OUTER, inner));
        mLastDigits = 0;
        setPreferredWidth();
    }

    private void setPreferredWidth() {
        int lines = mRoot.getElementCount();
        int digits = Math.max(String.valueOf(lines).length(), mMinimumDisplayDigits);

        if (mLastDigits != digits || mForceUpdateWidth) {
            mLastDigits = digits;
            FontMetrics fontMetrics = getFontMetrics(getFont());
            int width = fontMetrics.charWidth('0') * digits;
            Insets insets = getInsets();
            int preferredWidth = insets.left + insets.right + width;

            Dimension d = getPreferredSize();
            d.setSize(preferredWidth, NUM_HEIGHT);

            setPreferredSize(d);
            setSize(d);
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        FontMetrics fontMetrics = mComponent.getFontMetrics(mComponent.getFont());
        int fontHeight = fontMetrics.getHeight();
        Insets insets = getInsets();
        int availableWidth = getSize().width - insets.left - insets.right;

        Rectangle clip = g.getClipBounds();
        mTmpPoint.setLocation(0, clip.y);
        int rowStartOffset = mComponent.viewToModel(mTmpPoint);
        mTmpPoint.setLocation(0, clip.y + clip.height);
        int endOffset = mComponent.viewToModel(mTmpPoint);

        while (rowStartOffset <= endOffset) {
            try {
                int index = mRoot.getElementIndex(rowStartOffset);
                Element line = mRoot.getElement(index);

                String lineNumber = line.getStartOffset() == rowStartOffset ?
                        String.valueOf(index + 1) : "";
                int stringWidth = fontMetrics.stringWidth(lineNumber);
                if (stringWidth > 0) {
                    int x = (int) ((availableWidth - stringWidth) * 1) + insets.left;
                    int y = getOffsetY(rowStartOffset, fontMetrics);

                    int len = line.getEndOffset() - line.getStartOffset();
                    String log = mDoc.getText(line.getStartOffset(), len);
                    if (log.length() > 2) {
                        g.setColor(LogLine.DeviceForUI.getLevelByString(log).color);
                        g.drawString(lineNumber, x, y);
                        g.drawRect(x - 2, y - fontHeight + 4, stringWidth + 2, fontHeight - 2);
                    }
                }

                rowStartOffset = Utilities.getRowEnd(mComponent, rowStartOffset) + 1;
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }
    }

    private int getOffsetY(int rowStartOffset, FontMetrics fontMetrics) throws BadLocationException {
        Rectangle r = mComponent.modelToView(rowStartOffset);
        int lineHeight = fontMetrics.getHeight();
        int y = r.y + r.height;
        int descent = 0;

        if (r.height == lineHeight) {
            descent = fontMetrics.getDescent();
        } else {
            if (mFonts == null) {
                mFonts = new HashMap<>();
            }

            int index = mRoot.getElementIndex(rowStartOffset);
            Element line = mRoot.getElement(index);

            for (int i = 0; i < line.getElementCount(); i++) {
                Element child = line.getElement(i);
                AttributeSet as = child.getAttributes();
                String fontFamily = (String) as.getAttribute(StyleConstants.FontFamily);
                Integer fontSize = (Integer) as.getAttribute(StyleConstants.FontSize);
                String key = fontFamily + fontSize;
                FontMetrics fm = mFonts.get(key);
                if (fm == null) {
                    Font font = new Font(fontFamily, Font.PLAIN, fontSize);
                    fm = mComponent.getFontMetrics(font);
                    mFonts.put(key, fm);
                }
                descent = Math.max(descent, fm.getDescent());
            }
        }

        return y - descent;
    }

    @Override
    public void caretUpdate(CaretEvent e) {
        int caretPosition = mComponent.getCaretPosition();
        int currentLine = mRoot.getElementIndex(caretPosition);
        if (mLastLine != currentLine) {
            repaint();
            mLastLine = currentLine;
        }
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        documentChanged();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        documentChanged();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        documentChanged();
    }

    private static final Object COMPONENT_UI_PROPERTY_KEY = new StringBuffer("ComponentUIPropertyKey");
    private void documentChanged() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Object ui = mComponent.getClientProperty(COMPONENT_UI_PROPERTY_KEY);
                if (ui instanceof javax.swing.plaf.basic.BasicTextUI) {
                    javax.swing.plaf.basic.BasicTextUI t = (javax.swing.plaf.basic.BasicTextUI) ui;
                    View v = t.getRootView(null);
                    if (v == null || v.getParent() == null) {
                        return;
                    }
                }
                int preferredHeight = mComponent.getPreferredSize().height;

                if (mLastHeight != preferredHeight) {
                    setPreferredWidth();
                    repaint();
                    mLastHeight = preferredHeight;
                }
            }
        });
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getNewValue() instanceof Font) {
            if (mUpdateFont) {
                Font newFont = (Font) evt.getNewValue();
                setFont(newFont);
                mLastDigits = 0;
                setPreferredWidth();
            } else {
                repaint();
            }
        }
    }
}

class LogLine {
    public static final int FORMAT_LOGCAT_LEVEL_POS = 31;
    final static Color sMyGreen = new Color(0, 150, 0);
    final static Color sMyOrange = new Color(230, 140, 0);
    final public String mMsg;
    private String mTmpTimeStr;

    public Color getColor() {
        return Color.BLACK;
    }

    public LogLine(String s) {
        mMsg = s == null ? "" : s;
    }

    public String getContent() {
        return mMsg;
    }

    public String getTag() {
        return "";
    }

    public String getTimeString() {
        return "";
    }

    public String getTimeStr() {
        if (mTmpTimeStr == null) {
            mTmpTimeStr = getTimeString();
        }
        return mTmpTimeStr;
    }

    public String getPidStr() {
        return "";
    }

    public String getTidStr() {
        return "";
    }

    @Override
    public String toString() {
        return mMsg;
    }

    public static class DefaultLine extends LogLine {
        public DefaultLine(String line) {
            super(line);
        }

        @Override
        public String getTimeString() {
            return mMsg.substring(0, 18);
        }

        public String getPidStr() {
            return mMsg.substring(19, 24);
        }

        public String getTidStr() {
            return mMsg.substring(25, 30);
        }

        @Override
        public String getTag() {
            int tEnd = mMsg.indexOf(':', 34);
            return tEnd > 0 ? mMsg.substring(33, tEnd) : "";
        }

        @Override
        public String getContent() {
            int tEnd = mMsg.indexOf(':', 34);
            if (tEnd + 2 <= mMsg.length()) {
                return mMsg.substring(tEnd + 2);
            }
            return "";
        }
    }

    public static class DeviceLine extends DefaultLine {
        private String mTmpTag;

        public DeviceLine(String line) {
            super(line);
        }

        @Override
        public String getTimeString() {
            return mMsg.length() > 17 ? mMsg.substring(0, 18) : "";
        }

        public char getLevelChar() {
            return mMsg.charAt(FORMAT_LOGCAT_LEVEL_POS);
        }

        @Override
        public String getTag() {
            if (mTmpTag != null) {
                return mTmpTag;
            }
            int tEnd = mMsg.indexOf(':', 34);
            return mTmpTag = (tEnd > 0 ? mMsg.substring(33, tEnd).trim() : "");
        }

        @Override
        public String getContent() {
            int tEnd = mMsg.indexOf(':', 34);
            if (tEnd + 2 <= mMsg.length()) {
                return mMsg.substring(tEnd + 2);
            }
            return "";
        }
    }

    static class DeviceForUI extends DeviceLine {
        static enum Level {
            V('V', "V", 0, Color.BLACK),
            D('D', "D", 1, Color.BLUE),
            I('I', "I", 2, sMyGreen),
            W('W', "W", 3, sMyOrange),
            E('E', "E", 4, Color.RED),
            F('F', "F", 5, Color.DARK_GRAY);
            public final char label;
            public final String text;
            public final int value;
            public final Color color;
            Level(char l, String t, int v, Color c) {
                label = l;
                text = t;
                value = v;
                color = c;
            }
        }
        final static Level[] sLogLevels = Level.values();
        final static Level[] sLogLevelMap = new Level[128];

        static {
            for (Level l : sLogLevels) {
                sLogLevelMap[l.label - 'A'] = l;
            }
        }

        static Level getLevelByString(String str) {
            if (str.length() > FORMAT_LOGCAT_LEVEL_POS) {
                int levelChar = str.charAt(FORMAT_LOGCAT_LEVEL_POS) - 'A';
                if (levelChar > 0 && levelChar < sLogLevelMap.length) {
                    return  sLogLevelMap[levelChar];
                }
            }
            return Level.V;
        }

        Level mLevel;

        DeviceForUI(String rawLine, Level l, Color c) {
            super(rawLine);
            mLevel = l;
        }

        DeviceForUI(String rawLine) {
            super(rawLine);
            mLevel = getLevelByString(rawLine);
        }

        @Override
        public Color getColor() {
            return mLevel.color;
        }
    }
}

class FlagUtil {

    static ActivityIntentFlag[] ActivityIntentFlags = ActivityIntentFlag.values();
    static ReceiverIntentFlag[] ReceiverIntentFlags = ReceiverIntentFlag.values();
    static WMLayoutFlag[] WMLayoutFlags = WMLayoutFlag.values();
    static WMLayoutPrivateFlag[] WMLayoutPrivateFlags = WMLayoutPrivateFlag.values();
    static CommonFlag[] CommonFlags = CommonFlag.values();

    public interface FlagDecoder {
        String decode(String flagText);
    }
    static HashMap<String, FlagDecoder> sflgDcMap;

    public static HashMap<String, FlagDecoder> getDecoders() {
        if (sflgDcMap == null) {
            sflgDcMap = new HashMap<>();
            sflgDcMap.put("Activity intent", new FlagDecoder() {
                @Override
                public String decode(String flagText) {
                    return FlagUtil.list(FlagUtil.getActivityFlags(flagText));
                }
            });
            sflgDcMap.put("Receiver intent", new FlagDecoder() {
                @Override
                public String decode(String flagText) {
                    return FlagUtil.list(FlagUtil.getReceiverFlags(flagText));
                }
            });
            sflgDcMap.put("WM layout flag", new FlagDecoder() {
                @Override
                public String decode(String flagText) {
                    return FlagUtil.list(FlagUtil.getWMLayoutFlags(flagText));
                }
            });
        }
        return sflgDcMap;
    }

    interface WithCommonFlag {
    }

    interface Flag {
        int getValue();
    }

    public static class UnknownFlag implements Flag {
        int value;

        UnknownFlag(int v) {
            value = v;
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "UnknownFlag=0x" + Integer.toHexString(value);
        }
    }

    public enum CommonFlag implements Flag {
        FLAG_GRANT_READ_URI_PERMISSION(0x00000001),
        FLAG_GRANT_WRITE_URI_PERMISSION(0x00000002),
        FLAG_FROM_BACKGROUND(0x00000004),
        FLAG_DEBUG_LOG_RESOLUTION(0x00000008),
        FLAG_EXCLUDE_STOPPED_PACKAGES(0x00000010),
        FLAG_INCLUDE_STOPPED_PACKAGES(0x00000020),
        FLAG_GRANT_PERSISTABLE_URI_PERMISSION(0x00000040),
        FLAG_GRANT_PREFIX_URI_PERMISSION(0x00000080);

        int value;

        CommonFlag(int v) {
            value = v;
        }

        public int getValue() {
            return value;
        }
    }

    public enum ActivityIntentFlag implements Flag, WithCommonFlag {
        FLAG_ACTIVITY_NO_HISTORY(0x40000000),
        FLAG_ACTIVITY_SINGLE_TOP(0x20000000),
        FLAG_ACTIVITY_NEW_TASK(0x10000000),
        FLAG_ACTIVITY_MULTIPLE_TASK(0x08000000),
        FLAG_ACTIVITY_CLEAR_TOP(0x04000000),
        FLAG_ACTIVITY_FORWARD_RESULT(0x02000000),
        FLAG_ACTIVITY_PREVIOUS_IS_TOP(0x01000000),
        FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS(0x00800000),
        FLAG_ACTIVITY_BROUGHT_TO_FRONT(0x00400000),
        FLAG_ACTIVITY_RESET_TASK_IF_NEEDED(0x00200000),
        FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY(0x00100000),
        FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET(0x00080000),
        FLAG_ACTIVITY_NO_USER_ACTION(0x00040000),
        FLAG_ACTIVITY_REORDER_TO_FRONT(0X00020000),
        FLAG_ACTIVITY_NO_ANIMATION(0X00010000),
        FLAG_ACTIVITY_CLEAR_TASK(0X00008000),
        FLAG_ACTIVITY_TASK_ON_HOME(0X00004000),
        FLAG_ACTIVITY_RETAIN_IN_RECENTS(0x00002000);

        int value;

        ActivityIntentFlag(int v) {
            value = v;
        }

        public int getValue() {
            return value;
        }
    }

    public enum ReceiverIntentFlag implements Flag, WithCommonFlag {
        FLAG_RECEIVER_REGISTERED_ONLY(0x40000000),
        FLAG_RECEIVER_REPLACE_PENDING(0x20000000),
        FLAG_RECEIVER_FOREGROUND(0x10000000),
        FLAG_RECEIVER_NO_ABORT(0x08000000),
        FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT(0x04000000),
        FLAG_RECEIVER_BOOT_UPGRADE(0x02000000);

        int value;

        ReceiverIntentFlag(int v) {
            value = v;
        }

        public int getValue() {
            return value;
        }
    }

    public enum WMLayoutFlag implements Flag {
        FLAG_ALLOW_LOCK_WHILE_SCREEN_ON(0x00000001),
        FLAG_DIM_BEHIND(0x00000002),
        FLAG_BLUR_BEHIND(0x00000004),
        FLAG_NOT_FOCUSABLE(0x00000008),
        FLAG_NOT_TOUCHABLE(0x00000010),
        FLAG_NOT_TOUCH_MODAL(0x00000020),
        FLAG_TOUCHABLE_WHEN_WAKING(0x00000040),
        FLAG_KEEP_SCREEN_ON(0x00000080),
        FLAG_LAYOUT_IN_SCREEN(0x00000100),
        FLAG_LAYOUT_NO_LIMITS(0x00000200),
        FLAG_FULLSCREEN(0x00000400),
        FLAG_FORCE_NOT_FULLSCREEN(0x00000800),
        FLAG_DITHER(0x00001000),
        FLAG_SECURE(0x00002000),
        FLAG_SCALED(0x00004000),
        FLAG_IGNORE_CHEEK_PRESSES(0x00008000),
        FLAG_LAYOUT_INSET_DECOR(0x00010000),
        FLAG_ALT_FOCUSABLE_IM(0x00020000),
        FLAG_WATCH_OUTSIDE_TOUCH(0x00040000),
        FLAG_SHOW_WHEN_LOCKED(0x00080000),
        FLAG_SHOW_WALLPAPER(0x00100000),
        FLAG_TURN_SCREEN_ON(0x00200000),
        FLAG_DISMISS_KEYGUARD(0x00400000),
        FLAG_SPLIT_TOUCH(0x00800000),
        FLAG_HARDWARE_ACCELERATED(0x01000000),
        FLAG_LAYOUT_IN_OVERSCAN(0x02000000),
        FLAG_TRANSLUCENT_STATUS(0x04000000),
        FLAG_TRANSLUCENT_NAVIGATION(0x08000000),
        FLAG_LOCAL_FOCUS_MODE(0x10000000),
        FLAG_SLIPPERY(0x20000000),
        FLAG_LAYOUT_ATTACHED_IN_DECOR(0x40000000), // FLAG_NEEDS_MENU_KEY
        FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS(0x80000000);

        int value;

        WMLayoutFlag(int v) {
            value = v;
        }

        public int getValue() {
            return value;
        }
    }

    public enum WMLayoutPrivateFlag implements Flag {
        PRIVATE_FLAG_FAKE_HARDWARE_ACCELERATED(0x00000001),
        PRIVATE_FLAG_FORCE_HARDWARE_ACCELERATED(0x00000002),
        PRIVATE_FLAG_WANTS_OFFSET_NOTIFICATIONS(0x00000004),
        PRIVATE_FLAG_SET_NEEDS_MENU_KEY(0x00000008),
        PRIVATE_FLAG_SHOW_FOR_ALL_USERS(0x00000010),
        PRIVATE_FLAG_FORCE_SHOW_NAV_BAR(0x00000020),
        PRIVATE_FLAG_NO_MOVE_ANIMATION(0x00000040),
        PRIVATE_FLAG_COMPATIBLE_WINDOW(0x00000080),
        PRIVATE_FLAG_SYSTEM_ERROR(0x00000100),
        PRIVATE_FLAG_INHERIT_TRANSLUCENT_DECOR(0x00000200),
        PRIVATE_FLAG_KEYGUARD(0x00000400),
        PRIVATE_FLAG_DISABLE_WALLPAPER_TOUCH_EVENTS(0x00000800);

        int value;

        WMLayoutPrivateFlag(int v) {
            value = v;
        }

        public int getValue() {
            return value;
        }
    }

    static int fromHex(String hexFlagValue) {
        if (hexFlagValue.length() > 1) {
            char c = hexFlagValue.charAt(1);
            if (c == 'x' || c == 'X') {
                hexFlagValue = hexFlagValue.substring(2);
            }
        }
        long v = 0;
        try {
            v = Long.parseLong(hexFlagValue, 16);
        } catch (Exception e) {
            LLog.e("[FlagDecoder:fromHex] " + e.getMessage());
        }

        if (v > Integer.MAX_VALUE) {
            LLog.e(hexFlagValue + " exceeds max int");
        }
        return Integer.MAX_VALUE & (int) v;
    }

    public static Flag[] getActivityFlags(String hexFlagValue) {
        return getActivityFlags(fromHex(hexFlagValue));
    }

    public static Flag[] getActivityFlags(int flagValue) {
        return getFlags(ActivityIntentFlags, flagValue);
    }

    public static Flag[] getReceiverFlags(String hexFlagValue) {
        return getReceiverFlags(fromHex(hexFlagValue));
    }

    public static Flag[] getReceiverFlags(int flagValue) {
        return getFlags(ReceiverIntentFlags, flagValue);
    }

    public static Flag[] getWMLayoutFlags(String hexFlagValue) {
        return getWMLayoutFlags(fromHex(hexFlagValue));
    }

    public static Flag[] getWMLayoutFlags(int flagValue) {
        return getFlags(WMLayoutFlags, flagValue);
    }

    public static Flag[] getWMLayoutPrivateFlags(String hexFlagValue) {
        return getWMLayoutPrivateFlags(fromHex(hexFlagValue));
    }

    public static Flag[] getWMLayoutPrivateFlags(int flagValue) {
        return getFlags(WMLayoutPrivateFlags, flagValue);
    }

    public static Flag[] getFlags(Flag[] flags, int flagValue) {
        ArrayList<Flag> match = new ArrayList<>();
        for (Flag f : flags) {
            if ((flagValue & f.getValue()) != 0) {
                flagValue &= ~f.getValue();
                match.add(f);
            }
        }
        if (flagValue != 0) {
            if (flags[0] instanceof WithCommonFlag) {
                for (Flag f : CommonFlags) {
                    if ((flagValue & f.getValue()) != 0) {
                        flagValue &= ~f.getValue();
                        match.add(f);
                    }
                }
                if (flagValue != 0) {
                    match.add(new UnknownFlag(flagValue));
                }
            }
        }
        return match.toArray(new Flag[match.size()]);
    }

    public static String list(Flag[] flags) {
        if (flags.length < 1) {
            return null;
        }
        StringBuilder sb = new StringBuilder(128);
        for (Flag f : flags) {
            sb.append(f.toString()).append(" 0x").append(Integer.toHexString(f.getValue()))
                    .append("\n");
        }
        return sb.toString();
    }
}
