package dev.cbcmuzzlelight.client;
/** Commit ordering is based on request order, not worker completion order. */
public final class BuildOrder {
 public static final java.util.concurrent.atomic.LongAdder REJECTED=new java.util.concurrent.atomic.LongAdder(),SKIPPED=new java.util.concurrent.atomic.LongAdder();
 private long requested,committed;
 public synchronized long request(){return ++requested;}
 public synchronized boolean commit(long version,Runnable apply){if(version<committed){REJECTED.increment();return false;}apply.run();committed=version;return true;}
 public synchronized boolean obsolete(long version){boolean old=version<committed;if(old)SKIPPED.increment();return old;}
}
