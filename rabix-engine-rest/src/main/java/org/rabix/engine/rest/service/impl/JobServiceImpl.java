package org.rabix.engine.rest.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rabix.bindings.BindingException;
import org.rabix.bindings.Bindings;
import org.rabix.bindings.BindingsFactory;
import org.rabix.bindings.ProtocolType;
import org.rabix.bindings.model.Context;
import org.rabix.bindings.model.Job;
import org.rabix.bindings.model.Job.JobStatus;
import org.rabix.bindings.model.dag.DAGNode;
import org.rabix.engine.JobHelper;
import org.rabix.engine.db.DAGNodeDB;
import org.rabix.engine.event.impl.InitEvent;
import org.rabix.engine.event.impl.JobStatusEvent;
import org.rabix.engine.model.ContextRecord;
import org.rabix.engine.model.JobRecord;
import org.rabix.engine.processor.EventProcessor;
import org.rabix.engine.processor.EventProcessor.IterationCallback;
import org.rabix.engine.processor.handler.EventHandlerException;
import org.rabix.engine.rest.db.JobDB;
import org.rabix.engine.rest.plugin.BackendPluginDispatcher;
import org.rabix.engine.rest.plugin.BackendPluginType;
import org.rabix.engine.rest.service.JobService;
import org.rabix.engine.rest.service.JobServiceException;
import org.rabix.engine.service.ContextRecordService;
import org.rabix.engine.service.JobRecordService;
import org.rabix.engine.service.JobRecordService.JobState;
import org.rabix.engine.service.VariableRecordService;
import org.rabix.engine.validator.JobStateValidationException;
import org.rabix.engine.validator.JobStateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class JobServiceImpl implements JobService {

  private final static Logger logger = LoggerFactory.getLogger(JobServiceImpl.class);
  
  private final JobRecordService jobRecordService;
  private final VariableRecordService variableRecordService;
  private final ContextRecordService contextRecordService;
  
  private final JobDB jobDB;
  private final DAGNodeDB dagNodeDB;
  
  private final EventProcessor eventProcessor;
  private final BackendPluginDispatcher backendPluginDispatcher;

  @Inject
  public JobServiceImpl(EventProcessor eventProcessor, JobRecordService jobRecordService, VariableRecordService variableRecordService, ContextRecordService contextRecordService, BackendPluginDispatcher backendPluginDispatcher, DAGNodeDB dagNodeDB, JobDB jobDB) {
    this.jobDB = jobDB;
    this.dagNodeDB = dagNodeDB;
    this.eventProcessor = eventProcessor;
    
    this.jobRecordService = jobRecordService;
    this.variableRecordService = variableRecordService;
    this.contextRecordService = contextRecordService;
    this.backendPluginDispatcher = backendPluginDispatcher;

    List<IterationCallback> callbacks = new ArrayList<>();
    callbacks.add(new EndJobCallback());
    callbacks.add(new SendJobsCallback());
    this.eventProcessor.start(callbacks);
  }
  
  @Override
  public void update(Job job) throws JobServiceException {
    try {
      Bindings bindings = BindingsFactory.create(job);
      ProtocolType protocolType = bindings.getProtocolType();
      
      JobRecord jobRecord = jobRecordService.find(job.getNodeId(), job.getContext().getId());
      
      JobStatusEvent statusEvent = null;
      JobStatus status = job.getStatus();
      switch (status) {
      case RUNNING:
        JobStateValidator.checkState(jobRecord, JobState.RUNNING);
        statusEvent = new JobStatusEvent(job.getNodeId(), job.getContext().getId(), JobState.RUNNING, job.getOutputs(), protocolType);
        eventProcessor.addToQueue(statusEvent);
        break;
      case FAILED:
        JobStateValidator.checkState(jobRecord, JobState.FAILED);
        statusEvent = new JobStatusEvent(job.getNodeId(), job.getContext().getId(), JobState.FAILED, null, protocolType);
        eventProcessor.addToQueue(statusEvent);
        break;
      case COMPLETED:
        JobStateValidator.checkState(jobRecord, JobState.COMPLETED);
        statusEvent = new JobStatusEvent(job.getNodeId(), job.getContext().getId(), JobState.COMPLETED, job.getOutputs(), protocolType);
        eventProcessor.addToQueue(statusEvent);
        break;
      default:
        break;
      }
    } catch (BindingException e) {
      logger.error("Cannot find Bindings", e);
      throw new JobServiceException("Cannot find Bindings", e);
    } catch (JobStateValidationException e) {
      logger.error("Failed to update Job state");
      throw new JobServiceException("Failed to update Job state", e);
    }
  }
  
  @Override
  public Set<Job> getReady(EventProcessor eventProcessor, String contextId) throws JobServiceException {
    return JobHelper.createReadyJobs(jobRecordService, variableRecordService, contextRecordService, dagNodeDB, contextId);
  }
  
  @Override
  public String create(Job job) throws JobServiceException {
    String contextId = Context.createUniqueID();
    
    Context context = job.getContext() != null? job.getContext() : createContext(contextId);
    job = Job.cloneWithId(job, contextId);
    context.setId(contextId);
    job = Job.cloneWithContext(job, context);
    jobDB.add(job);

    Bindings bindings = null;
    try {
      bindings = BindingsFactory.create(job);

      DAGNode node = bindings.translateToDAG(job);
      InitEvent initEvent = new InitEvent(context, node, job.getInputs());

      eventProcessor.send(initEvent);
      return context.getId();
    } catch (BindingException e) {
      logger.error("Failed to create Bindings", e);
      throw new JobServiceException("Failed to create Bindings", e);
    } catch (EventHandlerException e) {
      throw new JobServiceException("Failed to start job", e);
    }
  }
  
  @Override
  public Set<Job> get() {
    return jobDB.getJobs();
  }

  @Override
  public Job get(String id) {
    return jobDB.get(id);
  }

  private Context createContext(String contextId) {
    Map<String, String> contextConfig = new HashMap<String, String>();
    contextConfig.put("backend.type", BackendPluginType.WAGNER.name());
    return new Context(contextId, contextConfig);
  }
  
  private class SendJobsCallback implements IterationCallback {
    @Override
    public void call(EventProcessor eventProcessor, String contextId, int iteration) throws Exception {
      Set<Job> jobs = getReady(eventProcessor, contextId);
      backendPluginDispatcher.send(jobs);
    }
  }

  private class EndJobCallback implements IterationCallback {
    @Override
    public void call(EventProcessor eventProcessor, String contextId, int iteration) {
      ContextRecord context = contextRecordService.find(contextId);
      
      Job job = null;
      switch (context.getStatus()) {
      case COMPLETED:
        job = jobDB.get(contextId);
        job = Job.cloneWithStatus(job, JobStatus.COMPLETED);
        jobDB.update(job);
        break;
      case FAILED:
        job = jobDB.get(contextId);
        job = Job.cloneWithStatus(job, JobStatus.FAILED);
        jobDB.update(job);
        break;
      default:
        break;
      }
    }
  }

}
