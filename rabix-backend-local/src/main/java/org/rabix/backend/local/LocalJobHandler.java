package org.rabix.backend.local;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import org.rabix.bindings.BindingException;
import org.rabix.bindings.Bindings;
import org.rabix.bindings.BindingsFactory;
import org.rabix.bindings.ProtocolType;
import org.rabix.bindings.model.Job;
import org.rabix.bindings.model.Job.JobStatus;
import org.rabix.engine.JobHelper;
import org.rabix.engine.db.DAGNodeDB;
import org.rabix.engine.event.impl.JobStatusEvent;
import org.rabix.engine.processor.EventProcessor;
import org.rabix.engine.processor.EventProcessor.IterationCallback;
import org.rabix.engine.service.ContextRecordService;
import org.rabix.engine.service.JobRecordService;
import org.rabix.engine.service.JobRecordService.JobState;
import org.rabix.engine.service.VariableRecordService;
import org.rabix.executor.service.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple (local) job handler that dispatches Job(s) to the single Executor 
 */
public class LocalJobHandler implements IterationCallback {

  private final static Logger logger = LoggerFactory.getLogger(LocalJobHandler.class);
  
  private DAGNodeDB dagNodeDB;
  private JobRecordService jobRecordService;
  private VariableRecordService variableRecordService;
  
  private ExecutorService executorService;
  private java.util.concurrent.ExecutorService threadPool = Executors.newCachedThreadPool();

  private Set<Job> runningJobs = new HashSet<>();
  private ContextRecordService contextRecordService;

  public LocalJobHandler(ExecutorService executorService, JobRecordService jobRecordService, VariableRecordService variableRecordService, ContextRecordService contextRecordService, DAGNodeDB dagNodeDB) {
    this.dagNodeDB = dagNodeDB;
    this.executorService = executorService;
    this.jobRecordService = jobRecordService;
    this.contextRecordService = contextRecordService;
    this.variableRecordService = variableRecordService;
  }

  @Override
  public void call(final EventProcessor eventProcessor, final String contextId, int iteration) {
    Set<Job> jobs = JobHelper.createReadyJobs(jobRecordService, variableRecordService, contextRecordService, dagNodeDB, contextId);
    for (final Job job : jobs) {
      if (!runningJobs.contains(job)) {
        runningJobs.add(job);

        executorService.start(job, contextId);
        threadPool.submit(new Runnable() {
          @Override
          public void run() {
            JobStatus status = executorService.findStatus(job.getId(), contextId);
            while (!(status.equals(JobStatus.COMPLETED) || status.equals(JobStatus.FAILED))) {
              try {
                Thread.sleep(7000);
              } catch (InterruptedException e) {
                logger.error("Fatal error happened.", e);
                System.exit(1);
              }
              status = executorService.findStatus(job.getId(), contextId);
            }
            try {
              Bindings bindings = BindingsFactory.create(job);
              if (status.equals(JobStatus.COMPLETED)) {
                Map<String, Object> results = executorService.getResult(job.getId(), contextId);

                ProtocolType protocolType = bindings.getProtocolType();
                JobStatusEvent statusEvent = new JobStatusEvent(job.getNodeId(), job.getContext().getId(), JobState.COMPLETED, results, protocolType);
                eventProcessor.addToQueue(statusEvent);
              } else {
                logger.error("Job {} has failed. Stop everything.", job.getId());
                System.exit(10);
              }
            } catch (BindingException e) {
              logger.error("Cannot find Bindings");
              System.exit(1);
            }
          }
        });
      }
    }
  }
}
