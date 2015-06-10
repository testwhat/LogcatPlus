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

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.JPopupMenu;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

public class JDefaultContextMenuUtil {
    private static JPopupMenu menu = new JPopupMenu();
    private static Object[] defaultMenuItems = {
            new CutAction(), new CopyAction(),
            new PasteAction(), new DeleteAction(),
            new JPopupMenu.Separator(), new SelectAllAction()
    };

    public interface NoAutoContextMenu {
    }

    public static void enable() {
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(new ContextMenuEventQueue());
    }

    public static class ContextMenuEventQueue extends EventQueue {
        @Override
        protected void dispatchEvent(AWTEvent event) {
            super.dispatchEvent(event);
            if (!(event instanceof MouseEvent)) {
                return;
            }

            MouseEvent me = (MouseEvent) event;
            if (!me.isPopupTrigger()) {
                return;
            }

            Component comp = SwingUtilities.getDeepestComponentAt(me.getComponent(), me.getX(),
                    me.getY());
            if (!(comp instanceof JTextComponent) || comp instanceof NoAutoContextMenu) {
                return;
            }
            if (MenuSelectionManager.defaultManager().getSelectedPath().length > 0) {
                return;
            }

            JTextComponent tc = (JTextComponent) comp;
            comp.requestFocus();

            menu.removeAll();
            for (Object o : defaultMenuItems) {
                if (o instanceof Component) {
                    menu.add((Component) o);
                } else if (o instanceof TextAction) {
                    TextAction ta = (TextAction) o;
                    ta.setTextComp(tc);
                    menu.add(ta);
                }
            }

            Point pt = SwingUtilities.convertPoint(me.getComponent(), me.getPoint(), tc);
            menu.show(tc, pt.x, pt.y);
        }
    }

    static abstract class TextAction extends AbstractAction {
        protected JTextComponent comp;

        public void setTextComp(JTextComponent c) {
            comp = c;
        }

        public TextAction(String label) {
            super(label);
        }
    }

    static class CutAction extends TextAction {

        public CutAction() {
            super("Cut");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            comp.cut();
        }

        @Override
        public boolean isEnabled() {
            return comp.isEditable() && comp.isEnabled() && comp.getSelectedText() != null;
        }
    }

    static class PasteAction extends TextAction {

        public PasteAction() {
            super("Paste");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            comp.paste();
        }

        @Override
        public boolean isEnabled() {
            if (comp.isEditable() && comp.isEnabled()) {
                Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard()
                        .getContents(this);
                return contents.isDataFlavorSupported(DataFlavor.stringFlavor);
            } else {
                return false;
            }
        }
    }

    static class DeleteAction extends TextAction {

        public DeleteAction() {
            super("Delete");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            comp.replaceSelection(null);
        }

        @Override
        public boolean isEnabled() {
            return comp.isEditable() && comp.isEnabled() && comp.getSelectedText() != null;
        }
    }

    static class CopyAction extends TextAction {

        public CopyAction() {
            super("Copy");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            comp.copy();
        }

        @Override
        public boolean isEnabled() {
            return comp.isEnabled() && comp.getSelectedText() != null;
        }
    }

    static class SelectAllAction extends TextAction {

        public SelectAllAction() {
            super("Select All");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            comp.selectAll();
        }

        @Override
        public boolean isEnabled() {
            return comp.isEnabled() && comp.getText().length() > 0;
        }

    }
}
