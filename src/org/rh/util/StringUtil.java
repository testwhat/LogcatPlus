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

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.LinkedList;

public final class StringUtil {
    public static String ln = System.getProperty("line.separator", "\r\n");

    private StringUtil() {
    }

    public static String join(final String[] strs, final String delimiter) {
        if (strs == null || strs.length == 0) {
            return "";
        }
        if (strs.length < 2) {
            return strs[0];
        }

        StringBuilder buffer = new StringBuilder(strs[0]);
        for (int i = 1; i < strs.length; i++) {
            buffer.append(delimiter).append(strs[i]);
        }

        return buffer.toString();
    }

    public static void copyToClipboard(String str) {
        StringSelection stringSelection = new StringSelection(str);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    public static class AhoCorasick {
        final static int FIRST_VISIBLE_ASCII = 32;
        final static int LAST_VISIBLE_ASCII = 126;
        final static int TRIE_SIZE = LAST_VISIBLE_ASCII - FIRST_VISIBLE_ASCII + 1;

        private final static int TRIE_ROOT = 1;
        private final static int TRIE_PATTERN = 2;

        static class Trie {
            Trie[] mNext = new Trie[TRIE_SIZE];
            Trie mFailLink;
            IntArray mPatternIdx = new IntArray(1);
            int mVal;
        }

        Trie mRoot;
        String[] mPatterns;

        public AhoCorasick() {
        }

        public AhoCorasick(String[] patterns) {
            rebuild(patterns);
        }

        public void rebuild(String[] patterns) {
            mRoot = new Trie();
            mRoot.mVal = TRIE_ROOT;
            mPatterns = patterns;

            for (int i = 0; i < patterns.length; i++) {
                Trie r = mRoot, t = null;
                String p = patterns[i];
                for (int j = 0; j < p.length(); j++) {
                    int index = p.charAt(j) - FIRST_VISIBLE_ASCII;
                    t = r.mNext[index];
                    if (t == null) {
                        t = new Trie();
                    }
                    t.mVal = TRIE_PATTERN;
                    r.mNext[index] = t;
                    r = t;
                }
                r.mPatternIdx.add(i);
            }

            LinkedList<Trie> q = new LinkedList<>();
            for (int i = 0; i < TRIE_SIZE; i++) {
                if (mRoot.mNext[i] != null && mRoot.mNext[i].mVal > 0) {
                    mRoot.mNext[i].mFailLink = mRoot;
                    q.add(mRoot.mNext[i]);
                } else {
                    mRoot.mNext[i] = mRoot;
                }
            }

            Trie head = null;
            while ((head = q.poll()) != null) {
                for (int i = 0; i < TRIE_SIZE; i++) {
                    Trie t = head;
                    if (t.mNext[i] != null && (t.mNext[i].mVal > 0)) {
                        q.add(t.mNext[i]);
                        Trie f = t.mFailLink;

                        while ((f.mNext[i] != null && f.mNext[i].mVal == 0) || f.mNext[i] == null) {
                            f = f.mFailLink;
                        }

                        t.mNext[i].mFailLink = f.mNext[i];
                        if (f.mNext[i].mPatternIdx.size() > 0) {
                            for (int j = 0; j < f.mNext[i].mPatternIdx.size(); j++) {
                                t.mNext[i].mPatternIdx.add(f.mNext[i].mPatternIdx.get(j));
                            }
                        }
                    }
                }
            }
        }

        public boolean contains(String text) {
            return getMatchedPatternIndex(text, true) != null;
        }

        public int[] getMatchedPatternIndex(String text) {
            return getMatchedPatternIndex(text, false);
        }

        private static final int[] FOUND = new int[0];

        private int[] getMatchedPatternIndex(String text, boolean findAny) {
            IntArray res = null;
            Trie p = mRoot;
            for (int i = 0, len = text.length(); i < len; i++) {
                int c = text.charAt(i) - FIRST_VISIBLE_ASCII;
                if (c >= TRIE_SIZE) {
                    System.out.println("strange char:" + c + " in " + text);
                    continue;
                }
                while ((p.mNext[c] != null && (p.mNext[c].mVal == 0)) || (p.mNext[c] == null)) {
                    p = p.mFailLink;
                }
                p = p.mNext[c];
                for (int j = 0; j < p.mPatternIdx.size(); j++) {
                    if (findAny) {
                        return FOUND;
                    }
                    if (res == null) {
                        res = new IntArray(mPatterns.length);
                    }
                    res.add(p.mPatternIdx.get(j));
                }
            }
            return res == null ? null : res.toArray();
        }
    }
}
