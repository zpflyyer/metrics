package com.codahale.metrics;

import static java.util.Arrays.binarySearch;

import java.util.ArrayDeque;
import java.util.Deque;

class ChunkedAssociativeLongArray {
    private static final long[] EMPTY = new long[0];
    private static final int DEFAULT_CHUNK_SIZE = 512;

    private final int defaultChunkSize;
    private Chunk activeChunk;

    ChunkedAssociativeLongArray() {
        this(DEFAULT_CHUNK_SIZE);
    }

    ChunkedAssociativeLongArray(int chunkSize) {
        this.defaultChunkSize = chunkSize;
        this.activeChunk = new Chunk(chunkSize);
    }

    synchronized boolean put(long key, long value) {
        if (activeChunk.cursor != 0 && activeChunk.keys[activeChunk.cursor - 1] > key) {
            return false; // key should be the same as last inserted or bigger
        }
        boolean isFull = activeChunk.cursor - activeChunk.startIndex == activeChunk.chunkSize;
        if (isFull) {
            activeChunk = new Chunk(activeChunk, this.defaultChunkSize);
        }
        activeChunk.append(key, value);
        return true;
    }

    private int traverse(Deque<Chunk> traversedChunksDeque) {
        Chunk currentChunk = activeChunk;
        int valuesSize = 0;
        while (true) {
            valuesSize += currentChunk.cursor - currentChunk.startIndex;
            if (traversedChunksDeque != null) {
                traversedChunksDeque.addLast(currentChunk);
            }
            if (currentChunk.tailChunk == null) {
                break;
            }
            currentChunk = currentChunk.tailChunk;
        }
        return valuesSize;
    }

    synchronized long[] values() {
        Deque<Chunk> chunksDeque = new ArrayDeque<Chunk>();
        int valuesSize = traverse(chunksDeque);
        if (valuesSize == 0) {
            return EMPTY;
        }
        long[] values = new long[valuesSize];
        int valuesIndex = 0;
        while (!chunksDeque.isEmpty()) {
            Chunk copySourceChunk = chunksDeque.removeLast();
            int length = copySourceChunk.cursor - copySourceChunk.startIndex;
            int itemsToCopy = Math.min(valuesSize - valuesIndex, length);
            System.arraycopy(copySourceChunk.values, copySourceChunk.startIndex, values, valuesIndex, itemsToCopy);
            valuesIndex += length;
        }
        return values;
    }

    synchronized int size() {
        int valueSize = traverse(null);
        return valueSize;
    }

    @Override
    public synchronized String toString() {
        Deque<Chunk> chunksDeque = new ArrayDeque<Chunk>();
        int valuesSize = traverse(chunksDeque);
        if (valuesSize == 0) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder();
        while (!chunksDeque.isEmpty()) {
            Chunk copySourceChunk = chunksDeque.removeLast();
            builder.append('[');
            for (int i = copySourceChunk.startIndex; i < copySourceChunk.cursor; i++) {
                long key = copySourceChunk.keys[i];
                long value = copySourceChunk.values[i];
                builder.append('(').append(key).append(": ").append(value).append(')').append(' ');
            }
            builder.append(']');
            if (!chunksDeque.isEmpty()) {
                builder.append("->");
            }
        }
        return builder.toString();
    }

    /**
     * Try to trim all beyond specified boundaries.
     * All items that are less then startKey or greater/equals then endKey
     *
     * @param startKey
     * @param endKey
     */
    synchronized void trim(long startKey, long endKey) {
        /*
         * [3, 4, 5, 9] -> [10, 13, 14, 15] -> [21, 24, 29, 30] -> [31] :: start layout
         *       |5______________________________23|                    :: trim(5, 23)
         *       [5, 9] -> [10, 13, 14, 15] -> [21]                     :: result layout
         */
        Chunk head = activeChunk.findChunkWhereKeyShouldBe(endKey);
        activeChunk = head;
        activeChunk.cursor = head.findIndexOfFirstElementGreaterOrEqualThan(endKey);

        Chunk tail = head.findChunkWhereKeyShouldBe(startKey);
        int newStartIndex = tail.findIndexOfFirstElementGreaterOrEqualThan(startKey);
        if (tail.startIndex != newStartIndex) {
            tail.startIndex = newStartIndex;
            tail.chunkSize = tail.cursor - tail.startIndex;
            tail.tailChunk = null;
        }
    }

    /**
     * Clear all in specified boundaries.
     * Remove all items between startKey(inclusive) and endKey(exclusive)
     *
     * @param startKey
     * @param endKey
     */
    synchronized void clear(long startKey, long endKey) {
        /*
         * [3, 4, 5, 9] -> [10, 13, 14, 15] -> [21, 24, 29, 30] -> [31] :: start layout
         *       |5______________________________23|                    :: clear(5, 23)
         * [3, 4]               ->                 [24, 29, 30] -> [31] :: result layout
         */
        Chunk tail = activeChunk.findChunkWhereKeyShouldBe(endKey);
        Chunk gapStartChunk = tail.splitOnTwoSeparateChunks(endKey);
        if (gapStartChunk == null) {
            return;
        }
        // now we should skip specified gap [startKey, endKey]
        // and concatenate our tail with new head four after gap
        Chunk afterGapHead = gapStartChunk.findChunkWhereKeyShouldBe(startKey);
        if (afterGapHead == null) {
            return;
        }

        int newEndIndex = afterGapHead.findIndexOfFirstElementGreaterOrEqualThan(startKey);
        if (newEndIndex == afterGapHead.startIndex) {
            tail.tailChunk = null;
            return;
        }
        if (afterGapHead.cursor != newEndIndex) {
            afterGapHead.cursor = newEndIndex;
            afterGapHead.chunkSize = afterGapHead.cursor - afterGapHead.startIndex;
        }
        tail.tailChunk = afterGapHead; // concat
    }

    synchronized void clear() {
        activeChunk.tailChunk = null;
        activeChunk.startIndex = 0;
        activeChunk.chunkSize = activeChunk.keys.length;
        activeChunk.cursor = 0;
    }


    private static class Chunk {

        private final long[] keys;
        private final long[] values;

        private int chunkSize; // can differ from keys.length after half clear()
        private int startIndex;
        private int cursor;
        private Chunk tailChunk;

        private Chunk(int chunkSize) {
            this(null, chunkSize);
        }

        private Chunk(Chunk tailChunk, int size) {
            this(new long[size], new long[size], 0, 0, size, tailChunk);
        }

        private Chunk(long[] keys, long[] values, int startIndex, int cursor, int chunkSize, Chunk tailChunk) {
            this.keys = keys;
            this.values = values;
            this.startIndex = startIndex;
            this.cursor = cursor;
            this.chunkSize = chunkSize;
            this.tailChunk = tailChunk;
        }

        private void append(long key, long value) {
            keys[cursor] = key;
            values[cursor] = value;
            cursor++;
        }

        private Chunk splitOnTwoSeparateChunks(long key) {
           /*
            * [1, 2, 3, 4, 5, 6, 7, 8] :: beforeSplit
            * |s--------chunk-------e|
            *
            *  splitOnTwoSeparateChunks(5)
            *
            * [1, 2, 3, 4, 5, 6, 7, 8] :: afterSplit
            * |s--tail--e||s--head--e|
            */
            int splitIndex = findIndexOfFirstElementGreaterOrEqualThan(key);
            if (splitIndex == startIndex || splitIndex == cursor) {
                return tailChunk;
            }
            int newTailSize = splitIndex - startIndex;
            Chunk newTail = new Chunk(keys, values, startIndex, splitIndex, newTailSize, tailChunk);
            startIndex = splitIndex;
            chunkSize = chunkSize - newTailSize;
            tailChunk = newTail;
            return newTail;
        }

        private Chunk findChunkWhereKeyShouldBe(long key) {
            Chunk currentChunk = this;
            while (currentChunk.isFirstElementIsEmptyOrGreaterOrEqualThan(key) && currentChunk.tailChunk != null) {
                currentChunk = currentChunk.tailChunk;
            }
            return currentChunk;
        }

        private boolean isFirstElementIsEmptyOrGreaterOrEqualThan(long key) {
            return cursor == startIndex || keys[startIndex] >= key;
        }

        private int findIndexOfFirstElementGreaterOrEqualThan(long key) {
            if (isFirstElementIsEmptyOrGreaterOrEqualThan(key)) {
                return startIndex;
            }
            int searchIndex = binarySearch(keys, startIndex, cursor, key);
            return searchIndex >= 0 ? searchIndex : -(searchIndex + 1);
        }
    }
}
