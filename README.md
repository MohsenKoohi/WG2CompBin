# WebGraph To CompBin Conversion

This repository contains a shared-memory parallel code to convert WebGraph format to CompBin format. 
CompBin is a compact presnetation base on CSR/CSC formats, skipping allocating space for edges array in a byte-level granularity.
For an input graph compressed in WebGraph format, three files are created: 

- The `XXX_offsets.bin` file which is the `offsets` array, containing |V|+1 elements, with 8 Bytes per element.
- The `XXX_edges.bin` file which is the `edges` array, containing |E| elements, with `b` Bytes per element, where `b = ceil(log2(|V|)/8)`.
- The `XXX_props.txt` file which includes `|V|`, `|E|`, `b`.

### Paper
  [DOI: 10.48550/arXiv.2507.00716](https://doi.org/10.48550/arXiv.2507.00716)

### Execution
`make WG2Bin args="path/to/graph path/to/bin/folder"`

### Sample exec
`make test`

### Citation

```
@misc{pg_fuse,
      title={Accelerating Loading WebGraphs in ParaGrapher}, 
      author={Mohsen {Koohi Esfahani}},
      year={2025},
      eprint={2507.00716},
      archivePrefix={arXiv},
      primaryClass={cs.DC},
      url={https://arxiv.org/abs/2507.00716}, 
}
```
