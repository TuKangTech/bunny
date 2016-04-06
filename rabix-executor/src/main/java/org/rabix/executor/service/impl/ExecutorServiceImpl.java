package org.rabix.executor.service.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.rabix.bindings.model.Job;
import org.rabix.bindings.model.Job.JobStatus;
import org.rabix.executor.execution.JobHandlerCommandDispatcher;
import org.rabix.executor.execution.command.StartCommand;
import org.rabix.executor.execution.command.StatusCommand;
import org.rabix.executor.execution.command.StopCommand;
import org.rabix.executor.model.JobData;
import org.rabix.executor.service.ExecutorService;
import org.rabix.executor.service.JobDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class ExecutorServiceImpl implements ExecutorService {

  private static final Logger logger = LoggerFactory.getLogger(ExecutorServiceImpl.class);

  private final JobDataService jobDataService;
  private final JobHandlerCommandDispatcher jobHandlerCommandDispatcher;

  private final Provider<StopCommand> stopCommandProvider;
  private final Provider<StartCommand> startCommandProvider;
  private final Provider<StatusCommand> statusCommandProvider;
  
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  @Inject
  public ExecutorServiceImpl(JobDataService jobDataService, JobHandlerCommandDispatcher jobHandlerCommandDispatcher, Provider<StopCommand> stopCommandProvider,
      Provider<StartCommand> startCommandProvider, Provider<StatusCommand> statusCommandProvider) {
    this.jobDataService = jobDataService;
    this.stopCommandProvider = stopCommandProvider;
    this.startCommandProvider = startCommandProvider;
    this.statusCommandProvider = statusCommandProvider;
    this.jobHandlerCommandDispatcher = jobHandlerCommandDispatcher;
  }

  @Override
  public void start(final Job job, String contextId) {
    logger.debug("start(id={}, important={}, uploadOutputs={})", job.getId());

    final JobData jobData = new JobData(job, JobStatus.READY, false, false);
    jobDataService.save(jobData, contextId);

    jobHandlerCommandDispatcher.dispatch(jobData, startCommandProvider.get());
    jobHandlerCommandDispatcher.dispatch(jobData, statusCommandProvider.get());
  }

  @Override
  public void stop(String jobId, String contextId) {
    logger.debug("stop(id={})", jobId);

    final JobData jobData = jobDataService.find(jobId, contextId);
    jobHandlerCommandDispatcher.dispatch(jobData, stopCommandProvider.get());
  }

  @Override
  public JobStatus findStatus(String jobId, String contextId) {
    logger.debug("findStatus(id={})", jobId);

    JobData jobData = jobDataService.find(jobId, contextId);
    if (jobData != null) {
      return jobData.getStatus();
    }
    return null;
  }

  @Override
  public void shutdown(Boolean stopEverything) {
    logger.debug("shutdown(stopEverything={})", stopEverything);

    List<JobData> jobsToStop = jobDataService.find(JobStatus.STARTED, JobStatus.READY);

    int abortedJobsCount = 0;
    if (jobsToStop != null) {
      for (JobData jobData : jobsToStop) {
        if (!stopEverything && jobData.isImportant()) {
          continue;
        }
        jobHandlerCommandDispatcher.dispatch(jobData, stopCommandProvider.get());
        abortedJobsCount++;
      }
    }
    stopped.set(true);
    String message = String.format("Shutdown%s executed. Worker has stopped %d %s.", stopEverything ? " now" : "", abortedJobsCount, abortedJobsCount == 1 ? "job" : "jobs");
    logger.info(message);
  }

  @Override
  public Map<String, Object> getResult(String id, String contextId) {
    JobData jobData = jobDataService.find(id, contextId);
    return jobData.getResult();
  }
  
  @Override
  public boolean isRunning(String id, String contextId) {
    logger.debug("isRunning(id={})", id);

    JobData jobData = jobDataService.find(id, contextId);
    if (jobData != null && !isFinished(jobData.getStatus())) {
      logger.info("Command line tool {} is running. The status is {}", id, jobData.getStatus());
      return true;
    }
    logger.info("Command line tool {} is not running", id);
    return false;
  }

  private boolean isFinished(JobStatus jobStatus) {
    switch (jobStatus) {
    case COMPLETED:
    case FAILED:
    case ABORTED:
      return true;
    default:
      return false;
    }
  }

  @Override
  public boolean isStopped() {
    return stopped.get();
  }

}
