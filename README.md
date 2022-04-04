**Mars** - Molecule Archive Suite - A framework for storage and reproducible processing of single-molecule datasets.

This repository contains a scijava command that converts [LUMICKS](https://lumicks.com) h5 format to mars molecule archives.

This importer makes several assumptions about the structure of the h5 provided and basic set of BlueLake fields. 

In particular, a single molecule record is created using the Force 1x and Force 2x arrays in the Force HF subgroup.

Currently limitations: The charting library used used by mars-fx (chartfx) can handle large datasets using several data reduction algorithms. However, charts will be sluggish when plotting datasets containing more than a few million data points. The importer provides a downsampler to address this limitation. 

Mars documentation can be found at https://duderstadt-lab.github.io/mars-docs/
