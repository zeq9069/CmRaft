package com.chicm.cmraft.util;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BlockingHashMap <K,V> {
  static final Log LOG = LogFactory.getLog(BlockingHashMap.class);
  
  private ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();
  private ConcurrentHashMap<K, KeyLock> locks = new ConcurrentHashMap<>();
  
  private void signalKeyArrive(K key) {
    final KeyLock lock = locks.get(key);
    if(lock == null)
      return;
    
    lock.lock();
    try {
      LOG.debug("singal key:[" + key + "] arrive");
      lock.signal();
    } finally {
      lock.unlock();
    }
  }
  
  public V put(K key, V value) {
    V ret = map.put(key, value);
    signalKeyArrive(key);
    return ret;
  }
  
  public V remove(K key) {
    locks.remove(key);
    return map.remove(key);
  }
  
  public V get(K key) {
    V ret = map.get(key);
    if(ret != null)
      return ret;
    KeyLock lock = locks.get(key);
    if(lock == null) {
      lock = new KeyLock();
      locks.put(key, lock);
    }
    
    try {
      lock.lock();
      
      while(ret == null) {
        lock.await(500, TimeUnit.MILLISECONDS);
        ret = map.get(key);
      }
      LOG.debug("wait done: " + key + ": " + ret);
    } catch (InterruptedException ex) {
      LOG.error("InterruptedException", ex);
      return ret;
    }
    finally {
        lock.unlock();
    }
    return ret;
  }
  
  public int size() {
    return map.size();
  }
  
  public boolean isEmpty() {
    return map.isEmpty();
  }
  
  public Set<K> keySet() {
    return map.keySet();
  }
  
  public Collection<V> values() {
    return map.values();
  }
  
  class KeyLock {
    private ReentrantLock lock = new ReentrantLock();
    private Condition condition = lock.newCondition();
    
    public ReentrantLock getLock() {
      return lock;
    }
    
    public Condition getCondition () {
      return condition;
    }
    
    public void lock() {
      lock.lock();
    }
    
    public void unlock() {
      lock.unlock();
    }
    
    public void signal() {
      condition.signal();
    }
    
    public void await(long time, TimeUnit unit) throws InterruptedException {
      condition.await(time, unit);
    }
  }

}