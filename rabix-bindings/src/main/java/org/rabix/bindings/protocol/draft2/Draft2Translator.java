package org.rabix.bindings.protocol.draft2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.rabix.bindings.BindingException;
import org.rabix.bindings.ProtocolTranslator;
import org.rabix.bindings.model.dag.DAGContainer;
import org.rabix.bindings.model.dag.DAGLink;
import org.rabix.bindings.model.dag.DAGLinkPort;
import org.rabix.bindings.model.dag.DAGLinkPort.LinkPortType;
import org.rabix.bindings.model.dag.DAGNode;
import org.rabix.bindings.model.dag.DAGNode.ScatterMethod;
import org.rabix.bindings.protocol.draft2.bean.Draft2DataLink;
import org.rabix.bindings.protocol.draft2.bean.Draft2Job;
import org.rabix.bindings.protocol.draft2.bean.Draft2JobApp;
import org.rabix.bindings.protocol.draft2.bean.Draft2Port;
import org.rabix.bindings.protocol.draft2.bean.Draft2Step;
import org.rabix.bindings.protocol.draft2.bean.Draft2Workflow;
import org.rabix.bindings.protocol.draft2.helper.Draft2SchemaHelper;
import org.rabix.common.helper.InternalSchemaHelper;
import org.rabix.common.helper.JSONHelper;
import org.rabix.common.json.BeanSerializer;

public class Draft2Translator implements ProtocolTranslator {

  /**
   * Translates from Draft2 format to generic one
   */
  @Override
  public DAGNode translateToDAGFromPayload(String payload) throws BindingException {
    Draft2Job job = BeanSerializer.deserialize(payload, Draft2Job.class);
    return processBatchInfo(job, transformToGeneric(job.getId(), job));
  }
  
  @Override
  public DAGNode translateToDAG(String app, String inputs) throws BindingException {
    Map<String, Object> inputsMap = JSONHelper.readMap(inputs);
    Draft2JobApp draft2JobApp = BeanSerializer.deserialize(app, Draft2JobApp.class);
    Draft2Job draft2Job = new Draft2Job(draft2JobApp, inputsMap);
    
    Draft2JobProcessor draft2JobProcessor = new Draft2JobProcessor();
    draft2Job = draft2JobProcessor.process(draft2Job);
    return processBatchInfo(draft2Job, transformToGeneric(draft2Job.getId(), draft2Job));
  }

  @Override
  public Map<String, Object> translateInputsFromPayload(String payload) {
    Draft2Job job = BeanSerializer.deserialize(payload, Draft2Job.class);
    return job.getInputs();
  }

  @SuppressWarnings("unchecked")
  private DAGNode processBatchInfo(Draft2Job job, DAGNode node) {
    Object batch = job.getScatter();

    if (batch != null) {
      List<String> scatterList = new ArrayList<>();
      if (batch instanceof List<?>) {
        for (String scatter : ((List<String>) batch)) {
          scatterList.add(Draft2SchemaHelper.normalizeId(scatter));
        }
      } else if (batch instanceof String) {
        scatterList.add(Draft2SchemaHelper.normalizeId((String) batch));
      } else {
        throw new RuntimeException("Failed to process bacth properties. Invalid application structure.");
      }

      for (String scatter : scatterList) {
        for (DAGLinkPort inputPort : node.getInputPorts()) {
          if (inputPort.getId().equals(scatter)) {
            inputPort.setScatter(true);
          }
        }

        if (node instanceof DAGContainer) {
          DAGContainer container = (DAGContainer) node;
          for (DAGLink link : container.getLinks()) {
            if (link.getSource().getId().equals(scatter) && link.getSource().getType().equals(LinkPortType.INPUT)) {
              link.getSource().setScatter(true);
            }
          }
        }
      }
    }
    return node;
  }

  private DAGNode transformToGeneric(String globalJobId, Draft2Job job) throws BindingException {
    List<DAGLinkPort> inputPorts = new ArrayList<>();
    for (Draft2Port port : job.getApp().getInputs()) {
      DAGLinkPort linkPort = new DAGLinkPort(Draft2SchemaHelper.normalizeId(port.getId()), job.getId(), LinkPortType.INPUT, port.getScatter() != null ? port.getScatter() : false);
      inputPorts.add(linkPort);
    }
    List<DAGLinkPort> outputPorts = new ArrayList<>();
    for (Draft2Port port : job.getApp().getOutputs()) {
      DAGLinkPort linkPort = new DAGLinkPort(Draft2SchemaHelper.normalizeId(port.getId()), job.getId(), LinkPortType.OUTPUT, false);
      outputPorts.add(linkPort);
    }
    
    ScatterMethod scatterMethod = job.getScatterMethod() != null? ScatterMethod.valueOf(job.getScatterMethod()) : null;
    if (!job.getApp().isWorkflow()) {
      return new DAGNode(job.getId(), inputPorts, outputPorts, scatterMethod, job.getApp());
    }

    Draft2Workflow workflow = (Draft2Workflow) job.getApp();

    List<DAGNode> children = new ArrayList<>();
    for (Draft2Step step : workflow.getSteps()) {
      children.add(transformToGeneric(globalJobId, step.getJob()));
    }

    List<DAGLink> links = new ArrayList<>();
    for (Draft2DataLink dataLink : workflow.getDataLinks()) {
      String source = dataLink.getSource();
      String sourceNodeId = null;
      String sourcePortId = null;
      if (!source.contains(InternalSchemaHelper.SEPARATOR)) {
        sourceNodeId = job.getId();
        sourcePortId = source.substring(1);
      } else {
        sourceNodeId = job.getId() + InternalSchemaHelper.SEPARATOR + source.substring(1, source.indexOf(InternalSchemaHelper.SEPARATOR));
        sourcePortId = source.substring(source.indexOf(InternalSchemaHelper.SEPARATOR) + 1);
      }

      String destination = dataLink.getDestination();
      String destinationPortId = null;
      String destinationNodeId = null;
      if (!destination.contains(InternalSchemaHelper.SEPARATOR)) {
        destinationNodeId = job.getId();
        destinationPortId = destination.substring(1);
      } else {
        destinationNodeId = job.getId() + InternalSchemaHelper.SEPARATOR + destination.substring(1, destination.indexOf(InternalSchemaHelper.SEPARATOR));
        destinationPortId = destination.substring(destination.indexOf(InternalSchemaHelper.SEPARATOR) + 1);
      }
      boolean isSourceFromWorkflow = !dataLink.getSource().contains(InternalSchemaHelper.SEPARATOR);

      DAGLinkPort sourceLinkPort = new DAGLinkPort(sourcePortId, sourceNodeId, isSourceFromWorkflow ? LinkPortType.INPUT : LinkPortType.OUTPUT, false);
      DAGLinkPort destinationLinkPort = new DAGLinkPort(destinationPortId, destinationNodeId, LinkPortType.INPUT, dataLink.getScattered() != null ? dataLink.getScattered() : false);
      links.add(new DAGLink(sourceLinkPort, destinationLinkPort));
    }
    return new DAGContainer(job.getId(), inputPorts, outputPorts, job.getApp(), scatterMethod, links, children);
  }

  @Override
  public Object translateInputs(String inputs) throws BindingException {
    return JSONHelper.readMap(inputs);
  }

}
