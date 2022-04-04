/*-
 * #%L
 * Lumicks h5 to molecule archive importer
 * %%
 * Copyright (C) 2019 - 2022 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.mpg.biochem.mars.molecule.commands;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DoubleColumn;
import org.scijava.ui.UIService;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.molecule.SingleMolecule;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;

@Plugin(type = Command.class, label = "LUMICKS h5", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Mars", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 'm'),
		@Menu(label = "Import", weight = 110,
			mnemonic = 'i'),
		@Menu(label = "LUMICKS h5", weight = 1, mnemonic = 'l')})
public class ImportLumicksh5Command extends DynamicCommand implements Command {

	@Parameter
	private LogService logService;
	
	@Parameter(label = "LUMICKS h5 file")
	private File file;
	
	@Parameter(label = "Downsample")
	private boolean downsample = false;
	
	@Parameter(label = "Downsample to Hz")
	private int downsampleToHz = 10000;
	
	/**
	 * OUTPUTS
	 */
	@Parameter(label = "Molecule Archive", type = ItemIO.OUTPUT)
	private SingleMoleculeArchive archive;

	private IHDF5Reader reader;

	@Override
	public void run() {
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();

		//Build log message
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("LUMICKS h5 Import");
		
		reader = HDF5Factory.openForReading(file.getAbsolutePath());

		addInputParameterLog(builder);
		log += builder.buildParameterList();

		//Output first part of log message...
		logService.info(log);

		archive = new SingleMoleculeArchive("Imported LUMICKS archive");
		String metaUID = MarsMath.getUUID58().substring(0, 10);
		MarsOMEMetadata meta = archive.createMetadata(metaUID);
		
		meta.setParameter("Bluelake version", reader.string().getAttr("/", "Bluelake version"));
		meta.setParameter("Export time (ns)", reader.int64().getAttr("/", "File format version"));
		meta.setParameter("File format version", reader.uint64().getAttr("/", "Export time (ns)"));
		meta.setParameter("GUID", reader.string().getAttr("/", "GUID"));
		
		meta.addNote("Description: " + reader.string().getAttr("/", "Description"));
		meta.addNote("Experiment: " + reader.string().getAttr("/", "Experiment"));
		
		//def members = reader.getGroupMembers("/Force HF/")
		
		archive.putMetadata(meta);
		
		double samplingRate = reader.uint64().getAttr("/Force HF/Force 1x/", "Sample rate (Hz)");
		
		double[] force1x = (downsample) ? 
				downsample(reader.float64().readArray("/Force HF/Force 1x"), (int)Math.round(samplingRate/downsampleToHz)) 
				: reader.float64().readArray("/Force HF/Force 1x");
		double[] force2x = (downsample) ?
				downsample(reader.float64().readArray("/Force HF/Force 2x"), (int)Math.round(samplingRate/downsampleToHz)) 
				: reader.float64().readArray("/Force HF/Force 2x");
		
		if (downsample) samplingRate = downsampleToHz;
				
		MarsTable table = new MarsTable("Force table");
		double[] time = new double[force1x.length];
		for (int i = 0;i < time.length; i++) {
			time[i] = i/samplingRate;
		}
		DoubleColumn tCol = new DoubleColumn("Time_(s)");
		tCol.setArray(time);
		tCol.setSize(time.length);
		table.add(tCol);
		
		DoubleColumn force1xCol = new DoubleColumn("Force_1x");
		force1xCol.setArray(force1x);
		force1xCol.setSize(force1x.length);
		table.add(force1xCol);
		
		DoubleColumn force2xCol = new DoubleColumn("Force_2x");
		force2xCol.setArray(force2x);
		force2xCol.setSize(force2x.length);
		table.add(force2xCol);
						
		SingleMolecule molecule = archive.createMolecule(MarsMath.getUUID58(), table);
		molecule.setMetadataUID(metaUID);
		archive.put(molecule);

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(LogBuilder.endBlock(true));
	    archive.logln(LogBuilder.endBlock(true));
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Downsample", downsample);
		builder.addParameter("Downsample to Hz", downsampleToHz);
	}
	
	private static double[] downsample(double[] array, int factor) {
		double[] downsampled = new double[(int)Math.floor(array.length/factor)];
		
		int binSize = (int)Math.floor(array.length/downsampled.length);
		
		double sum = 0;
		int count = 0;
		int index = 0;
		for (int i = 0; i < array.length; i++) {
			sum += array[i];
			count++;
			if (count == binSize && index < downsampled.length) {
				downsampled[index] = sum/count; 
				count = 0;
				sum = 0;
				index++;
			}
		}
		
		return downsampled;
	}
}
