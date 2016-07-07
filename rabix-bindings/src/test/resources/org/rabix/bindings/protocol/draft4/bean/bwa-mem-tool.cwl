---
cwlVersion: cwl:draft-4
class: CommandLineTool
hints:
- class: DockerRequirement
  dockerPull: images.sbgenomics.com/rabix/bwa
  dockerImageId: 9d3b9b0359cf
- class: ResourceRequirement
  coresMin: 4
inputs:
  reference:
    type: File
    inputBinding:
      position: 2
  reads:
    type:
      type: array
      items: File
    inputBinding:
      position: 3
  minimum_seed_length:
    type: int
    inputBinding:
      position: 1
      prefix: "-m"
  min_std_max_min:
    type:
      type: array
      items: int
    inputBinding:
      position: 1
      prefix: "-I"
      itemSeparator: ","
outputs:
- id: sam
  type: File
  outputBinding:
    glob: output.sam
baseCommand:
- bwa
- mem
arguments:
- valueFrom: "$(runtime.cores)"
  position: 1
  prefix: "-t"
stdout: output.sam
