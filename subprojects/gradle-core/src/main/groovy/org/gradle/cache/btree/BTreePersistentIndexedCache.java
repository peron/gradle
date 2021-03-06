/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.cache.btree;

import org.gradle.api.UncheckedIOException;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

// todo - stream serialised value to file
// todo - handle hash collisions
// todo - don't store null links to child blocks in leaf index blocks
// todo - align block boundaries
// todo - concurrency control
// todo - remove the check-sum from each block
// todo - merge small values into a single data block
// todo - discard when file corrupt
// todo - include data directly in index entry when serializer can guarantee small fixed sized data
// todo - free list leaks disk space
// todo - merge adjacent free blocks
// todo - use more efficient lookup for free block with nearest size
public class BTreePersistentIndexedCache<K, V> implements PersistentIndexedCache<K, V> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BTreePersistentIndexedCache.class);
    private final File cacheFile;
    private final PersistentCache backingCache;
    private final Serializer<V> serializer;
    private final short maxChildIndexEntries;
    private final int minIndexChildNodes;
    private final StateCheckBlockStore store;
    private HeaderBlock header;

    public BTreePersistentIndexedCache(PersistentCache backingCache, Serializer<V> serializer) {
        this(backingCache, serializer, (short) 512, 512);
    }

    public BTreePersistentIndexedCache(PersistentCache backingCache, Serializer<V> serializer,
                                       short maxChildIndexEntries, int maxFreeListEntries) {
        this.backingCache = backingCache;
        this.serializer = serializer;
        this.maxChildIndexEntries = maxChildIndexEntries;
        this.minIndexChildNodes = maxChildIndexEntries / 2;
        cacheFile = new File(backingCache.getBaseDir(), "cache.bin");
        BlockStore cachingStore = new CachingBlockStore(new FileBackedBlockStore(cacheFile), IndexBlock.class, FreeListBlockStore.FreeListBlock.class);
        store = new StateCheckBlockStore(new FreeListBlockStore(cachingStore, maxFreeListEntries));
        try {
            open();
        } catch (Exception e) {
            throw new UncheckedIOException(String.format("Could not open %s.", this), e);
        }
    }

    @Override
    public String toString() {
        return String.format("cache '%s'", cacheFile);
    }

    private void open() throws Exception {
        try {
            doOpen();
        } catch (CorruptedCacheException e) {
            rebuild();
        }
    }

    private void doOpen() throws Exception {
        BlockStore.Factory factory = new BlockStore.Factory() {
            public Object create(Class<? extends BlockPayload> type) {
                if (type == HeaderBlock.class) {
                    return new HeaderBlock();
                }
                if (type == IndexBlock.class) {
                    return new IndexBlock();
                }
                if (type == DataBlock.class) {
                    return new DataBlock();
                }
                throw new UnsupportedOperationException();
            }
        };
        Runnable initAction = new Runnable() {
            public void run() {
                header = new HeaderBlock();
                store.write(header);
                header.index.newRoot();
                store.flush();
                backingCache.markValid();
            }
        };

        store.open(initAction, factory);
        header = store.readFirst(HeaderBlock.class);
    }

    public V get(K key) {
        try {
            try {
                DataBlock block = header.getRoot().get(key);
                if (block != null) {
                    return block.getValue();
                }
                return null;
            } catch (CorruptedCacheException e) {
                rebuild();
                return null;
            }
        } catch (Exception e) {
            throw new UncheckedIOException(String.format("Could not read entry '%s' from %s.", key, this), e);
        }
    }

    public void put(K key, V value) {
        try {
            String keyString = key.toString();
            long hashCode = keyString.hashCode();
            Lookup lookup = header.getRoot().find(hashCode);
            boolean needNewBlock = true;
            if (lookup.entry != null) {
                DataBlock block = store.read(lookup.entry.dataBlock, DataBlock.class);
                needNewBlock = !block.useNewValue(value);
                if (needNewBlock) {
                    store.remove(block);
                }
            }
            if (needNewBlock) {
                DataBlock block = new DataBlock(keyString, value);
                store.write(block);
                lookup.indexBlock.put(hashCode, block.getPos());
            }
            store.flush();
        } catch (Exception e) {
            throw new UncheckedIOException(String.format("Could not add entry '%s' to %s.", key, this), e);
        }
    }

    public void remove(K key) {
        try {
            Lookup lookup = header.getRoot().find(key.toString());
            if (lookup.entry == null) {
                return;
            }
            lookup.indexBlock.remove(lookup.entry);
            DataBlock block = store.read(lookup.entry.dataBlock, DataBlock.class);
            store.remove(block);
            store.flush();
        } catch (Exception e) {
            throw new UncheckedIOException(String.format("Could not remove entry '%s' from %s.", key, this), e);
        }
    }

    private IndexBlock load(BlockPointer pos, IndexRoot root, IndexBlock parent, int index) {
        IndexBlock block = store.read(pos, IndexBlock.class);
        block.root = root;
        block.parent = parent;
        block.parentEntryIndex = index;
        return block;
    }

    public void reset() {
        close();
        try {
            open();
        } catch (Exception e) {
            throw new UncheckedIOException(e);
        }
    }

    public void close() {
        try {
            store.close();
        } catch (Exception e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean isOpen() {
        return store.isOpen();
    }

    private void rebuild() throws Exception {
        LOGGER.warn(String.format("%s is corrupt. Discarding.", this));
        store.clear();
        close();
        doOpen();
    }

    public void verify() {
        try {
            doVerify();
        } catch (Exception e) {
            throw new UncheckedIOException(String.format("Some problems were found when checking the integrity of %s.",
                    this), e);
        }
    }

    private void doVerify() throws Exception {
        List<BlockPayload> blocks = new ArrayList<BlockPayload>();

        HeaderBlock header = store.readFirst(HeaderBlock.class);
        blocks.add(header);
        verifyTree(header.getRoot(), "", blocks, Long.MAX_VALUE, true);

        Collections.sort(blocks, new Comparator<BlockPayload>() {
            public int compare(BlockPayload block, BlockPayload block1) {
                return block.getPos().compareTo(block1.getPos());
            }
        });

        for (int i = 0; i < blocks.size() - 1; i++) {
            Block b1 = blocks.get(i).getBlock();
            Block b2 = blocks.get(i + 1).getBlock();
            if (b1.getPos().getPos() + b1.getSize() > b2.getPos().getPos()) {
                throw new IOException(String.format("%s overlaps with %s", b1, b2));
            }
        }
    }

    private void verifyTree(IndexBlock current, String prefix, Collection<BlockPayload> blocks, long maxValue,
                            boolean loadData) throws Exception {
        blocks.add(current);

        if (!prefix.equals("") && current.entries.size() < maxChildIndexEntries / 2) {
            throw new IOException(String.format("Too few entries found in %s", current));
        }
        if (current.entries.size() > maxChildIndexEntries) {
            throw new IOException(String.format("Too many entries found in %s", current));
        }

        boolean isLeaf = current.entries.size() == 0 || current.entries.get(0).childIndexBlock.isNull();
        if (isLeaf ^ current.tailPos.isNull()) {
            throw new IOException(String.format("Mismatched leaf/tail-node in %s", current));
        }

        long min = Long.MIN_VALUE;
        for (IndexEntry entry : current.entries) {
            if (isLeaf ^ entry.childIndexBlock.isNull()) {
                throw new IOException(String.format("Mismatched leaf/non-leaf entry in %s", current));
            }
            if (entry.hashCode >= maxValue || entry.hashCode <= min) {
                throw new IOException(String.format("Out-of-order key in %s", current));
            }
            min = entry.hashCode;
            if (!entry.childIndexBlock.isNull()) {
                IndexBlock child = store.read(entry.childIndexBlock, IndexBlock.class);
                verifyTree(child, "   " + prefix, blocks, entry.hashCode, loadData);
            }
            if (loadData) {
                DataBlock block = store.read(entry.dataBlock, DataBlock.class);
                blocks.add(block);
            }
        }
        if (!current.tailPos.isNull()) {
            IndexBlock tail = store.read(current.tailPos, IndexBlock.class);
            verifyTree(tail, "   " + prefix, blocks, maxValue, loadData);
        }
    }

    private class IndexRoot {
        private BlockPointer rootPos = new BlockPointer();
        private HeaderBlock owner;

        private IndexRoot(HeaderBlock owner) {
            this.owner = owner;
        }

        public void setRootPos(BlockPointer rootPos) {
            this.rootPos = rootPos;
            store.write(owner);
        }

        public IndexBlock getRoot() {
            return load(rootPos, this, null, 0);
        }

        public IndexBlock newRoot() {
            IndexBlock block = new IndexBlock();
            store.write(block);
            setRootPos(block.getPos());
            return block;
        }
    }

    private class HeaderBlock extends BlockPayload {
        private IndexRoot index;

        private HeaderBlock() {
            index = new IndexRoot(this);
        }

        @Override
        protected int getType() {
            return 0x55;
        }

        @Override
        protected int getSize() {
            return Block.LONG_SIZE + Block.SHORT_SIZE;
        }

        @Override
        protected void read(DataInputStream instr) throws Exception {
            index.rootPos = new BlockPointer(instr.readLong());

            short actualChildIndexEntries = instr.readShort();
            if (actualChildIndexEntries != maxChildIndexEntries) {
                throw blockCorruptedException();
            }
        }

        @Override
        protected void write(DataOutputStream outstr) throws Exception {
            outstr.writeLong(index.rootPos.getPos());
            outstr.writeShort(maxChildIndexEntries);
        }

        public IndexBlock getRoot() throws Exception {
            return index.getRoot();
        }
    }

    private class IndexBlock extends BlockPayload {
        private final List<IndexEntry> entries = new ArrayList<IndexEntry>();
        private BlockPointer tailPos = new BlockPointer();
        // Transient fields
        private IndexBlock parent;
        private int parentEntryIndex;
        private IndexRoot root;

        @Override
        protected int getType() {
            return 0x77;
        }

        @Override
        protected int getSize() {
            return Block.INT_SIZE + Block.LONG_SIZE + (3 * Block.LONG_SIZE) * maxChildIndexEntries;
        }

        public void read(DataInputStream instr) throws IOException {
            int count = instr.readInt();
            entries.clear();
            for (int i = 0; i < count; i++) {
                IndexEntry entry = new IndexEntry();
                entry.hashCode = instr.readLong();
                entry.dataBlock = new BlockPointer(instr.readLong());
                entry.childIndexBlock = new BlockPointer(instr.readLong());
                entries.add(entry);
            }
            tailPos = new BlockPointer(instr.readLong());
        }

        public void write(DataOutputStream outstr) throws IOException {
            outstr.writeInt(entries.size());
            for (IndexEntry entry : entries) {
                outstr.writeLong(entry.hashCode);
                outstr.writeLong(entry.dataBlock.getPos());
                outstr.writeLong(entry.childIndexBlock.getPos());
            }
            outstr.writeLong(tailPos.getPos());
        }

        public void put(long hashCode, BlockPointer pos) throws Exception {
            int index = Collections.binarySearch(entries, new IndexEntry(hashCode));
            IndexEntry entry;
            if (index >= 0) {
                entry = entries.get(index);
            } else {
                assert tailPos.isNull();
                entry = new IndexEntry();
                entry.hashCode = hashCode;
                entry.childIndexBlock = new BlockPointer();
                index = -index - 1;
                entries.add(index, entry);
            }

            entry.dataBlock = pos;
            store.write(this);

            maybeSplit();
        }

        private void maybeSplit() throws Exception {
            if (entries.size() > maxChildIndexEntries) {
                int splitPos = entries.size() / 2;
                IndexEntry splitEntry = entries.remove(splitPos);
                if (parent == null) {
                    parent = root.newRoot();
                }
                IndexBlock sibling = new IndexBlock();
                store.write(sibling);
                List<IndexEntry> siblingEntries = entries.subList(splitPos, entries.size());
                sibling.entries.addAll(siblingEntries);
                siblingEntries.clear();
                sibling.tailPos = tailPos;
                tailPos = splitEntry.childIndexBlock;
                splitEntry.childIndexBlock = new BlockPointer();
                parent.add(this, splitEntry, sibling);
            }
        }

        private void add(IndexBlock left, IndexEntry entry, IndexBlock right) throws Exception {
            int index = left.parentEntryIndex;
            if (index < entries.size()) {
                IndexEntry parentEntry = entries.get(index);
                assert parentEntry.childIndexBlock.equals(left.getPos());
                parentEntry.childIndexBlock = right.getPos();
            } else {
                assert index == entries.size() && (tailPos.isNull() || tailPos.equals(left.getPos()));
                tailPos = right.getPos();
            }
            entries.add(index, entry);
            entry.childIndexBlock = left.getPos();
            store.write(this);

            maybeSplit();
        }

        public DataBlock get(K key) throws Exception {
            Lookup lookup = find(key.toString());
            if (lookup.entry == null) {
                return null;
            }

            return store.read(lookup.entry.dataBlock, DataBlock.class);
        }

        public Lookup find(String keyString) throws Exception {
            return find((long) keyString.hashCode());
        }

        private Lookup find(long hashCode) throws Exception {
            int index = Collections.binarySearch(entries, new IndexEntry(hashCode));
            if (index >= 0) {
                return new Lookup(this, entries.get(index));
            }

            index = -index - 1;
            BlockPointer childBlockPos;
            if (index == entries.size()) {
                childBlockPos = tailPos;
            } else {
                childBlockPos = entries.get(index).childIndexBlock;
            }
            if (childBlockPos.isNull()) {
                return new Lookup(this, null);
            }

            IndexBlock childBlock = load(childBlockPos, root, this, index);
            return childBlock.find(hashCode);
        }

        public void remove(IndexEntry entry) throws Exception {
            int index = entries.indexOf(entry);
            assert index >= 0;
            entries.remove(index);
            store.write(this);

            if (entry.childIndexBlock.isNull()) {
                maybeMerge();
            } else {
                // Not a leaf node. Move up an entry from a leaf node, then possibly merge the leaf node
                IndexBlock leafBlock = load(entry.childIndexBlock, root, this, index);
                leafBlock = leafBlock.findHighestLeaf();
                IndexEntry highestEntry = leafBlock.entries.remove(leafBlock.entries.size() - 1);
                highestEntry.childIndexBlock = entry.childIndexBlock;
                entries.add(index, highestEntry);
                store.write(leafBlock);
                leafBlock.maybeMerge();
            }
        }

        private void maybeMerge() throws Exception {
            if (parent == null) {
                if (entries.size() == 0 && !tailPos.isNull()) {
                    // This is an empty root block, discard it
                    header.index.setRootPos(tailPos);
                    store.remove(this);
                }
                return;
            }
            if (entries.size() < minIndexChildNodes) {
                IndexBlock left = parent.getPrevious(this);
                if (left != null) {
                    if (left.entries.size() > minIndexChildNodes) {
                        // Redistribute entries with lhs block
                        left.mergeFrom(this);
                        left.maybeSplit();
                    } else if (left.entries.size() + entries.size() <= maxChildIndexEntries) {
                        // Merge with the lhs block
                        left.mergeFrom(this);
                        parent.maybeMerge();
                        return;
                    }
                }
                IndexBlock right = parent.getNext(this);
                if (right != null) {
                    if (right.entries.size() > minIndexChildNodes) {
                        // Redistribute entries with rhs block
                        mergeFrom(right);
                        maybeSplit();
                        return;
                    } else if (right.entries.size() + entries.size() <= maxChildIndexEntries) {
                        // Merge with the rhs block
                        mergeFrom(right);
                        parent.maybeMerge();
                        return;
                    }
                }

                throw new UnsupportedOperationException("implement me");
            }
        }

        private void mergeFrom(IndexBlock right) throws Exception {
            IndexEntry newChildEntry = parent.entries.remove(parentEntryIndex);
            if (right.getPos().equals(parent.tailPos)) {
                parent.tailPos = getPos();
            } else {
                IndexEntry newParentEntry = parent.entries.get(parentEntryIndex);
                assert newParentEntry.childIndexBlock.equals(right.getPos());
                newParentEntry.childIndexBlock = getPos();
            }
            entries.add(newChildEntry);
            entries.addAll(right.entries);
            newChildEntry.childIndexBlock = tailPos;
            tailPos = right.tailPos;
            store.write(parent);
            store.write(this);
            store.remove(right);
        }

        private IndexBlock getNext(IndexBlock indexBlock) throws Exception {
            int index = indexBlock.parentEntryIndex + 1;
            if (index > entries.size()) {
                return null;
            }
            if (index == entries.size()) {
                return load(tailPos, root, this, index);
            }
            return load(entries.get(index).childIndexBlock, root, this, index);
        }

        private IndexBlock getPrevious(IndexBlock indexBlock) throws Exception {
            int index = indexBlock.parentEntryIndex - 1;
            if (index < 0) {
                return null;
            }
            return load(entries.get(index).childIndexBlock, root, this, index);
        }

        private IndexBlock findHighestLeaf() throws Exception {
            if (tailPos.isNull()) {
                return this;
            }
            return load(tailPos, root, this, entries.size()).findHighestLeaf();
        }
    }

    private static class IndexEntry implements Comparable<IndexEntry> {
        long hashCode;
        BlockPointer dataBlock;
        BlockPointer childIndexBlock;

        private IndexEntry() {
        }

        private IndexEntry(long hashCode) {
            this.hashCode = hashCode;
        }

        public int compareTo(IndexEntry indexEntry) {
            if (hashCode > indexEntry.hashCode) {
                return 1;
            }
            if (hashCode < indexEntry.hashCode) {
                return -1;
            }
            return 0;
        }
    }

    private class Lookup {
        final IndexBlock indexBlock;
        final IndexEntry entry;

        private Lookup(IndexBlock indexBlock, IndexEntry entry) {
            this.indexBlock = indexBlock;
            this.entry = entry;
        }
    }

    private class DataBlock extends BlockPayload {
        private int size;
        private byte[] serialisedValue;
        private V value;

        private DataBlock() {
        }

        public DataBlock(String key, V value) throws Exception {
            this.value = value;
            setValue(value);
            size = serialisedValue.length;
        }

        public void setValue(V value) throws Exception {
            ByteArrayOutputStream outStr = new ByteArrayOutputStream();
            serializer.write(outStr, value);
            this.serialisedValue = outStr.toByteArray();
        }

        public V getValue() throws Exception {
            if (value == null) {
                value = serializer.read(new ByteArrayInputStream(serialisedValue));
            }
            return value;
        }

        @Override
        protected int getType() {
            return 0x33;
        }

        @Override
        protected int getSize() {
            return 2 * Block.INT_SIZE + size;
        }

        public void read(DataInputStream instr) throws Exception {
            size = instr.readInt();
            int bytes = instr.readInt();
            serialisedValue = new byte[bytes];
            instr.readFully(serialisedValue);
        }

        public void write(DataOutputStream outstr) throws Exception {
            outstr.writeInt(size);
            outstr.writeInt(serialisedValue.length);
            outstr.write(serialisedValue);
        }

        public boolean useNewValue(V value) throws Exception {
            setValue(value);
            boolean ok = serialisedValue.length <= size;
            if (ok) {
                store.write(this);
            }
            return ok;
        }
    }
}
