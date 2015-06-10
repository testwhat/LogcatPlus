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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JOptionPane;

import org.rh.util.LLog;

public class ByAdbCommand implements LogcatPlus.LogProvider {

    final LogcatPlus mL;
    String mTargetDevice;
    Process mAdbProcess;
    Timer mProcTimer;

    public ByAdbCommand(LogcatPlus logger) {
        mL = logger;
    }

    @Override
    public void start() {
        if (mProcTimer != null) {
            mProcTimer.cancel();
        }
        mTargetDevice = null;
        stop();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String dev = selectDevice();
                    if (dev == null) {
                        mL.setText("No device connected");
                        return;
                    }
                    mTargetDevice = dev;
                    runLogcat();
                } catch (Exception e) {
                    LLog.ex(e);
                }
            }
        }).start();
    }

    private void runLogcat() throws IOException, InterruptedException {
        String cmd = mL.getCommand();
        String fullCmd = "adb"
                + (mTargetDevice != null ? (" -s " + mTargetDevice + " " + cmd) : cmd);
        LLog.i("start cmd: " + fullCmd);
        mAdbProcess = Runtime.getRuntime().exec(fullCmd);

        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                mAdbProcess.getInputStream(), "utf-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                mL.onLog(line);
            }
        } finally {
            if (mAdbProcess != null) {
                mAdbProcess.destroy();
            }
        }

        LLog.i("Logger stopped:" + cmd);
    }

    @Override
    public void stop() {
        if (mAdbProcess != null) {
            mAdbProcess.destroy();
            mAdbProcess = null;
        }
    }

    void viewProcess() throws IOException, InterruptedException {
        stop();
        mL.setInputEnable(false);
        mL.mPane.setAutoScroll(false);
        mL.mPane.setFont(null);
        mTargetDevice = selectDevice();
        if (mTargetDevice == null) {
            mL.setText("No device");
            return;
        }
        mL.setText("Loading...");
        if (mProcTimer != null) {
            mProcTimer.cancel();
        }
        mProcTimer = new Timer();
        mProcTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                String[] zygotePids = new String[2];
                int zIdx = 0;
                StringBuilder sb = new StringBuilder(512);
                sb.append(new java.util.Date().toString());
                sb.append("\nUSER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME        OOM-ADJ\n");
                ArrayList<String> lines = adbShell("ps");
                final int lc = lines.size();
                for (int i = 0; i < lc; i++) {
                    String line = lines.get(i);
                    if (line.contains("zygote")) {
                        zygotePids[zIdx++] = line.split("[ ]+")[1];
                    }
                    if (zIdx == 2) {
                        break;
                    }
                }
                for (String zPid : zygotePids) {
                    if (zPid == null) {
                        continue;
                    }
                    StringBuilder getAdjCmd = new StringBuilder(512);
                    ArrayList<String> vmpLines = new ArrayList<>(32);
                    for (int i = 0; i < lc; i++) {
                        String line = lines.get(i);
                        String[] a = line.split("[ ]+");
                        String ppid = a[2];
                        if (zPid.equals(ppid)) {
                            getAdjCmd.append("cat proc/" + a[1] + "/oom_adj;");
                            vmpLines.add(line);
                        }
                    }
                    ArrayList<String> adjs = adbShell(getAdjCmd.toString());
                    for (int i = 0; i < vmpLines.size(); i++) {
                        sb.append(vmpLines.get(i)).append(" ").append(adjs.get(i)).append("\n");
                    }
                }
                mL.setText("");
                mL.appendText(sb.toString());
            }
        }, 20, 120 * 1000);
    }

    private ArrayList<String> adbCheckDevices() {
        ArrayList<String> devices = new ArrayList<>();
        try {
            Process adbP = Runtime.getRuntime().exec("adb devices");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(adbP.getInputStream(),
                    "utf-8"))) {
                int l = 0;
                String line;
                while ((line = r.readLine()) != null) {
                    if (l == 0 && !line.startsWith("List of devices")) {
                        mL.setText("Cannot execute adb");
                        break;
                    } else if (l > 0 && line.endsWith("device")) {
                        String[] segs = line.split("[ \t]+");
                        devices.add(segs[0]);
                    }
                    l++;
                }
            }
            adbP.destroy();
        } catch (IOException e) {
            LLog.ex(e);
        }
        return devices;
    }

    private ArrayList<String> adbShell(String cmd) {
        ArrayList<String> lines = new ArrayList<>(512);
        try {
            Process adbP = Runtime.getRuntime().exec("adb -s " + mTargetDevice + " shell " + cmd);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(adbP.getInputStream(),
                    "utf-8"))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.length() > 0) {
                        lines.add(line);
                    }
                }
            }
            adbP.destroy();
        } catch (IOException e) {
            LLog.ex(e);
        }
        return lines;
    }

    private String selectDevice() {
        String dev = null;
        ArrayList<String> devices = adbCheckDevices();
        if (devices.size() == 1) {
            dev = devices.get(0);
        } else if (devices.size() > 1) {
            int sel = JOptionPane.showOptionDialog(null, "Select which device",
                    "Multiple devices are connected", JOptionPane.YES_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null,
                    devices.toArray(new String[devices.size()]), null);
            dev = devices.get(sel);
        }
        return dev;
    }

    @Override
    public void init() {
    }

    @Override
    public void exit() {
    }
}
