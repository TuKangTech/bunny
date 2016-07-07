package org.rabix.bindings.protocol.draft3;

import java.util.Map;

import org.rabix.bindings.BindingException;
import org.rabix.bindings.ProtocolFilePathMapper;
import org.rabix.bindings.filemapper.FileMapper;
import org.rabix.bindings.model.Job;
import org.rabix.bindings.protocol.draft3.bean.Draft3Job;
import org.rabix.bindings.protocol.draft3.helper.Draft3JobHelper;
import org.rabix.bindings.protocol.draft3.processor.Draft3PortProcessor;
import org.rabix.bindings.protocol.draft3.processor.Draft3PortProcessorException;
import org.rabix.bindings.protocol.draft3.processor.callback.Draft3FilePathMapProcessorCallback;

public class Draft3FilePathMapper implements ProtocolFilePathMapper {

  @Override
  public Job mapInputFilePaths(final Job job, final FileMapper fileMapper) throws BindingException {
    Draft3Job draft3Job = Draft3JobHelper.getDraft3Job(job);
    
    Draft3PortProcessor draft3PortProcessor = new Draft3PortProcessor(draft3Job);
    try {
      Map<String, Object> inputs = draft3PortProcessor.processInputs(job.getInputs(), new Draft3FilePathMapProcessorCallback(fileMapper));
      return Job.cloneWithInputs(job, inputs);
    } catch (Draft3PortProcessorException e) {
      throw new BindingException(e);
    }
  }

  @Override
  public Job mapOutputFilePaths(final Job job, final FileMapper fileMapper) throws BindingException {
    Draft3Job draft3Job = Draft3JobHelper.getDraft3Job(job);
    
    Draft3PortProcessor draft3PortProcessor = new Draft3PortProcessor(draft3Job);
    try {
      Map<String, Object> outputs = draft3PortProcessor.processOutputs(job.getOutputs(), new Draft3FilePathMapProcessorCallback(fileMapper));
      return Job.cloneWithOutputs(job, outputs);
    } catch (Draft3PortProcessorException e) {
      throw new BindingException(e);
    }
  }

}
