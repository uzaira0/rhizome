package com.geekbeast.rhizome.hazelcast.processors;

import com.hazelcast.cache.BackupAwareEntryProcessor;
import com.hazelcast.map.EntryProcessor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.MutableEntry;

public abstract class AbstractRhizomeEntryProcessor<K, V, R>
        implements EntryProcessor<K, V, R>, BackupAwareEntryProcessor<K, V, R> {
    private static final long    serialVersionUID = 5060655249179605949L;
    private final        boolean applyOnBackup;

    /**
     * Creates an EntryProcessor that applies the {@link #process(java.util.Map.Entry)} to primary and backups.
     */
    public AbstractRhizomeEntryProcessor() {
        this( true );
    }

    public AbstractRhizomeEntryProcessor( boolean applyOnBackup ) {
        this.applyOnBackup = applyOnBackup;
    }

    @Override
    public abstract R process( @Nonnull Map.Entry<K, V> entry );

    @Override
    public final EntryProcessor<K, V, R> getBackupProcessor() {
        if ( applyOnBackup ) {
            return this;
        }
        return null;
    }

    @SuppressFBWarnings(
            value = "IL_INFINITE_RECURSIVE_LOOP",
            justification = "MutableEntry extends Map.Entry, so the single-argument process(entry) "
                    + "call resolves to the fixed-arity abstract process(Map.Entry) overload, not this "
                    + "varargs JCache method. Java overload resolution prefers the non-varargs method, so "
                    + "this is not recursion; SpotBugs flags it from the ambiguous bytecode dispatch." )
    @Override public R process( MutableEntry<K, V> entry, Object... arguments ) throws EntryProcessorException {
        return process( (Map.Entry<K, V>) entry );
    }

    @Override public javax.cache.processor.EntryProcessor<K, V, R> createBackupEntryProcessor() {
        return this;
    }
}
