package org.rabix.bindings.protocol.draft4;

import java.util.ArrayList;
import java.util.List;

import org.rabix.bindings.BindingException;
import org.rabix.bindings.ProtocolTranslator;
import org.rabix.bindings.helper.DAGValidationHelper;
import org.rabix.bindings.model.ApplicationPort;
import org.rabix.bindings.model.Job;
import org.rabix.bindings.model.LinkMerge;
import org.rabix.bindings.model.ScatterMethod;
import org.rabix.bindings.model.dag.DAGContainer;
import org.rabix.bindings.model.dag.DAGLink;
import org.rabix.bindings.model.dag.DAGLinkPort;
import org.rabix.bindings.model.dag.DAGLinkPort.LinkPortType;
import org.rabix.bindings.model.dag.DAGNode;
import org.rabix.bindings.protocol.draft4.bean.Draft4DataLink;
import org.rabix.bindings.protocol.draft4.bean.Draft4Job;
import org.rabix.bindings.protocol.draft4.bean.Draft4Step;
import org.rabix.bindings.protocol.draft4.bean.Draft4Workflow;
import org.rabix.bindings.protocol.draft4.helper.Draft4JobHelper;
import org.rabix.bindings.protocol.draft4.helper.Draft4SchemaHelper;
import org.rabix.common.helper.InternalSchemaHelper;

public class Draft4Translator implements ProtocolTranslator {

  @Override
  public DAGNode translateToDAG(Job job) throws BindingException {
    Draft4Job draft4Job = Draft4JobHelper.getDraft4Job(job);
    DAGNode dagNode = processBatchInfo(draft4Job, transformToGeneric(draft4Job.getId(), draft4Job));
    DAGValidationHelper.detectLoop(dagNode);
    processPorts(dagNode);
    return dagNode;
  }
  
  @SuppressWarnings("unchecked")
  private DAGNode processBatchInfo(Draft4Job job, DAGNode node) {
    Object batch = job.getScatter();

    if (batch != null) {
      List<String> scatterList = new ArrayList<>();
      if (batch instanceof List<?>) {
        for (String scatter : ((List<String>) batch)) {
          scatterList.add(Draft4SchemaHelper.normalizeId(scatter));
        }
      } else if (batch instanceof String) {
        scatterList.add(Draft4SchemaHelper.normalizeId((String) batch));
      } else {
        throw new RuntimeException("Failed to process batch properties. Invalid application structure.");
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

  private DAGNode transformToGeneric(String globalJobId, Draft4Job job) throws BindingException {
    List<DAGLinkPort> inputPorts = new ArrayList<>();
    
    for (ApplicationPort port : job.getApp().getInputs()) {
      DAGLinkPort linkPort = new DAGLinkPort(Draft4SchemaHelper.normalizeId(port.getId()), job.getId(), LinkPortType.INPUT, LinkMerge.merge_nested, port.getScatter() != null ? port.getScatter() : false, null, null);
      inputPorts.add(linkPort);
    }
    List<DAGLinkPort> outputPorts = new ArrayList<>();
    for (ApplicationPort port : job.getApp().getOutputs()) {
      DAGLinkPort linkPort = new DAGLinkPort(Draft4SchemaHelper.normalizeId(port.getId()), job.getId(), LinkPortType.OUTPUT, LinkMerge.merge_nested, false, null, null);
      outputPorts.add(linkPort);
    }
    
    ScatterMethod scatterMethod = job.getScatterMethod() != null? ScatterMethod.valueOf(job.getScatterMethod()) : ScatterMethod.dotproduct;
    if (!job.getApp().isWorkflow()) {
      return new DAGNode(job.getId(), inputPorts, outputPorts, scatterMethod, job.getApp(), job.getInputs());
    }

    Draft4Workflow workflow = (Draft4Workflow) job.getApp();

    List<DAGNode> children = new ArrayList<>();
    for (Draft4Step step : workflow.getSteps()) {
      children.add(transformToGeneric(globalJobId, step.getJob()));
    }

    List<DAGLink> links = new ArrayList<>();
    for (Draft4DataLink dataLink : workflow.getDataLinks()) {
      String source = dataLink.getSource();
      String sourceNodeId = null;
      String sourcePortId = null;
      if (!source.contains(InternalSchemaHelper.SLASH_SEPARATOR)) {
        sourceNodeId = job.getId();
        sourcePortId = source.substring(0);
      } else {
        sourceNodeId = job.getId() + InternalSchemaHelper.SEPARATOR + source.substring(0, source.indexOf(InternalSchemaHelper.SLASH_SEPARATOR));
        sourcePortId = source.substring(source.indexOf(InternalSchemaHelper.SLASH_SEPARATOR) + 1);
      }

      String destination = dataLink.getDestination();
      String destinationPortId = null;
      String destinationNodeId = null;
      if (!destination.contains(InternalSchemaHelper.SLASH_SEPARATOR)) {
        destinationNodeId = job.getId();
        destinationPortId = destination.substring(0);
      } else {
        destinationNodeId = job.getId() + InternalSchemaHelper.SEPARATOR + destination.substring(0, destination.indexOf(InternalSchemaHelper.SLASH_SEPARATOR));
        destinationPortId = destination.substring(destination.indexOf(InternalSchemaHelper.SLASH_SEPARATOR) + 1);
      }
      boolean isSourceFromWorkflow = !dataLink.getSource().contains(InternalSchemaHelper.SLASH_SEPARATOR);

      DAGLinkPort sourceLinkPort = new DAGLinkPort(sourcePortId, sourceNodeId, isSourceFromWorkflow ? LinkPortType.INPUT : LinkPortType.OUTPUT, LinkMerge.merge_nested, false, null, null);
      DAGLinkPort destinationLinkPort = new DAGLinkPort(destinationPortId, destinationNodeId, LinkPortType.INPUT, dataLink.getLinkMerge(), dataLink.getScattered() != null ? dataLink.getScattered() : false, null, null);

      int position = dataLink.getPosition() != null ? dataLink.getPosition() : 1;
      links.add(new DAGLink(sourceLinkPort, destinationLinkPort, dataLink.getLinkMerge(), position));
    }
    return new DAGContainer(job.getId(), inputPorts, outputPorts, job.getApp(), scatterMethod, links, children, job.getInputs());
  }
  
  private void processPorts(DAGNode dagNode) {
    if (dagNode instanceof DAGContainer) {
      DAGContainer dagContainer = (DAGContainer) dagNode;
      
      for (DAGLink dagLink : dagContainer.getLinks()) {
        dagLink.getDestination().setLinkMerge(dagLink.getLinkMerge());
        processPorts(dagLink, dagNode);
        
        for (DAGNode childNode : dagContainer.getChildren()) {
          processPorts(dagLink, childNode);
          if (childNode instanceof DAGContainer) {
            processPorts(childNode);
          }
        }
      }
    }
  }
  
  private void processPorts(DAGLink dagLink, DAGNode dagNode) {
    for (DAGLinkPort dagLinkPort : dagNode.getInputPorts()) {
      if (dagLinkPort.equals(dagLink.getDestination())) {
        dagLinkPort.setLinkMerge(dagLink.getLinkMerge());
      }
    }
    for (DAGLinkPort dagLinkPort : dagNode.getOutputPorts()) {
      if (dagLinkPort.equals(dagLink.getDestination())) {
        dagLinkPort.setLinkMerge(dagLink.getLinkMerge());
      }
    }
  }

}
