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

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.rh.tools.logcat.LogcatPlus.LogSource;
import org.rh.util.LLog;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.log.EventLogParser;
import com.android.ddmlib.log.LogCatReceiverTask;
import com.android.ddmlib.log.LogReceiver;
import com.android.ddmlib.log.LogReceiver.LogEntry;

public class ByDdmlib implements LogcatPlus.LogProvider, LogReceiver.ILogListener {

    final LogcatPlus mL;
    LogCatReceiverTask.Logger mLogTask;
    LogCatReceiverTask mRunningTask;
    EventLogParser mEventLogParser;
    int mSelectedDevice;
    ArrayList<IDevice> mDevices = new ArrayList<>();
    AtomicBoolean mStarted = new AtomicBoolean();
    
    public ByDdmlib(LogcatPlus logger) {
        mL = logger;
        mLogTask = new LogCatReceiverTask.Logger() {
            @Override
            public void onLog(String[] logs) {
                mL.onLog(logs);
            }

            @Override
            public void onStop(String reason) {
                LLog.i(reason);
            }
        };
    }

    public void start() {
        if (mStarted.compareAndSet(false, true)) {
            startLogThread();
        }
    }

    public void stop() {
        mStarted.set(false);
        if (mRunningTask != null) {
            mRunningTask.stop();
            mRunningTask = null;
        }
    }

    private void startLogThread() {
        if (mDevices.isEmpty()) {
            return;
        }
        if (mL.getLogSource() == LogSource.events) {
            if (mEventLogParser == null) {
                mEventLogParser = new EventLogParser();
                IDevice device = mDevices.get(mSelectedDevice);
                mEventLogParser.init(device);
            }
        }
        mRunningTask = new LogCatReceiverTask(mDevices.get(mSelectedDevice),
                mL.getCommand(), mLogTask);
        new Thread(mRunningTask).start();
    }

    @Override
    public void init() {
        AndroidDebugBridge.initIfNeeded();
        AndroidDebugBridge.addDeviceChangeListener(new AndroidDebugBridge.IDeviceChangeListener() {

            @Override
            public void deviceDisconnected(IDevice device) {
                LLog.v(device + " has disconnected\n");
                mDevices.remove(device);
            }

            @Override
            public void deviceConnected(IDevice device) {
                LLog.v(device + " has connected\n");
                mDevices.add(device);
                if (mStarted.get()) {
                    startLogThread();
                }
            }

            @Override
            public void deviceChanged(IDevice device, int changeMask) {
            }
        });
        AndroidDebugBridge.createBridge();
    }

    @Override
    public void exit() {
        AndroidDebugBridge.terminate();
    }

    @Override
    public void newEntry(LogEntry entry) {
        mEventLogParser.parse(entry);
    }

    @Override
    public void newData(byte[] data, int offset, int length) {
        System.out.println("wtf " + new String(data, offset, length));
    }
}
