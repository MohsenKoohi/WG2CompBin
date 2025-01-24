# WebGraph To Binary Conversion

This repository contains a parallel code to convert WebGraph format to binary CSX (Compressed Sparse Row/Column).
For each input three files are created: 

- A `_offsets.bin` file which is the `offsets` array, containing |V|+1 elements, with 8 Bytes per element.
- A `_edges.bin` file which is the `edges` array with `b` Bytes per element, where `b = [log10(|V|)]`.
- A `_props.txt` file which includes `|V|`, `|E|`, `b`.

### Sample exec
`make test`
