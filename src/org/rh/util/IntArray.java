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

import java.util.Arrays;

public final class IntArray {
    private int lastIndex = -1;
    private int[] data;

    public IntArray() {
        this(10);
    }

    public IntArray(int initSize) {
        data = new int[initSize];
    }

    public void ensureCapacity(int minCapacity) {
        int oldCapacity = data.length;
        if (minCapacity > oldCapacity) {
            int newCapacity = (oldCapacity * 3) / 2 + 1;
            if (newCapacity < minCapacity) {
                newCapacity = minCapacity;
            }
            data = Arrays.copyOf(data, newCapacity);
        }
    }

    public void add(int c) {
        ensureCapacity(++lastIndex + 1);
        data[lastIndex] = c;
    }

    public int size() {
        return lastIndex + 1;
    }

    public int get(int index) {
        return data[index];
    }

    public void set(int index, int value) {
        data[index] = value;
    }

    public boolean contains(int v) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] == v) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        lastIndex = -1;
    }

    public boolean remove(int index) {
        if (lastIndex - index >= 0) {
            for (int i = index; i < lastIndex; i++) {
                data[i] = data[i + 1];
            }
            lastIndex--;
            return true;
        }
        return false;
    }

    public boolean removeTarget(int value) {
        int i = 0;
        for (; i <= lastIndex; i++) {
            if (data[i] == value) {
                break;
            }
        }
        return remove(i);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < lastIndex; i++) {
            sb.append(data[i]).append(',');
        }
        sb.append(data[lastIndex]).append("}");
        return sb.toString();
    }

    public int[] toArray() {
        int[] a = new int[size()];
        System.arraycopy(data, 0, a, 0, a.length);
        return a;
    }
}
