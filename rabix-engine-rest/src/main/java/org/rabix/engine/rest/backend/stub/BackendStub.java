package org.rabix.engine.rest.backend.stub;

import java.util.Set;

import org.rabix.bindings.model.Job;
import org.rabix.engine.rest.backend.Backend;
import org.rabix.engine.rest.backend.HeartbeatInfo;

public interface BackendStub {

  void start();
  
  void stop();

  void send(Job job);
  
  void send(Set<Job> jobs);
  
  HeartbeatInfo getHeartbeat();
  
  Backend getBackend();

}
