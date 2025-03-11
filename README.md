# WebGraph To Binary Conversion

This repository contains a parallel code to convert WebGraph format to binary CSX (Compressed Sparse Row/Column).
For each input three files are created: 

- A `_offsets.bin` file which is the `offsets` array, containing |V|+1 elements, with 8 Bytes per element.
- A `_edges.bin` file which is the `edges` array, containing |E| elements, with `b` Bytes per element, where `b = ceil(log2(|V|)/8)`.
- A `_props.txt` file which includes `|V|`, `|E|`, `b`.

### Execution
`make WG2Bin args="path/to/graph path/to/bin/folder"`

### Sample exec
`make test`
