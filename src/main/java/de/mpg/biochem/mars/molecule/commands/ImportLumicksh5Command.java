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
import java.util.List;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DoubleColumn;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.UIService;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.molecule.MarsRecord;
import de.mpg.biochem.mars.molecule.SingleMolecule;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.util.MarsRegion;

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
	
	@Parameter
	private UIService uiService;
	
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
	
	private String[] float64CalibrationFields = new String[] {"Offset (pN)", "Response (pN/V)", "Sign", "Bead diameter (um)", 
		"Fit range (max.) (Hz)", "Fit range (min.) (Hz)", "Fit tolerance", "Max iterations", "Number of samples", "Points per block", 
		"Sample rate (Hz)", "Temperature (C)", "Viscosity (Pa*s)", "D (V^2/s)", "Rd (um/V)", "Rf (pN/V)", "alpha", "backing (%)", 
		"chi_squared_per_deg", "err_D", "err_alpha", "err_f_diode", "err_fc", "f_diode (Hz)", "fc (Hz)","kappa (pN/nm)", "ps_fitted", "ps_model_fit"};
	
	@Override
	public void run() {
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();

		//Build log message
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("LUMICKS h5 Import");
		
		reader = HDF5Factory.openForReading(file.getAbsolutePath());
		
		//Before we start let's check to see this is actually the type of LUMICKS file we expect.
		if (reader.string().getAttr("/", "Bluelake version") == null) {
			uiService.showDialog("BlueLake version not found, is this a lumicks file?", MessageType.ERROR_MESSAGE);
			logService.info(LogBuilder.endBlock(false));
			archive = null;
			return;
		}

		addInputParameterLog(builder);
		log += builder.buildParameterList();

		//Output first part of log message...
		logService.info(log);
		
		archive = new SingleMoleculeArchive("Imported LUMICKS archive");

		String metaUID = MarsMath.getUUID58().substring(0, 10);
		MarsOMEMetadata meta = archive.createMetadata(metaUID);
		
		meta.setParameter("Bluelake version", reader.string().getAttr("/", "Bluelake version"));
		meta.setParameter("Export time (ns)", reader.int64().getAttr("/", "File format version"));
		meta.setParameter("GUID", reader.string().getAttr("/", "GUID"));
		
		meta.setNotes("Description: " + reader.string().getAttr("/", "Description"));
		meta.addNote("Experiment: " + reader.string().getAttr("/", "Experiment"));
		
		List<String> members = reader.getGroupMembers("/");
		
		if (members.contains("Calibration")) {
			List<String> calibrationMember = reader.getGroupMembers("/Calibration/");
			List<String> calibrationSubMembers = reader.getGroupMembers("/Calibration/" + calibrationMember.get(0));
			String basePath = "/Calibration/" + calibrationMember.get(0) + "/";
			if (calibrationSubMembers.contains("Force 1x")) setMetadataCalibrationParameters(meta, basePath, "Force 1x");
			if (calibrationSubMembers.contains("Force 1y")) setMetadataCalibrationParameters(meta, basePath, "Force 1y");
			if (calibrationSubMembers.contains("Force 2x")) setMetadataCalibrationParameters(meta, basePath, "Force 2x");
			if (calibrationSubMembers.contains("Force 2y")) setMetadataCalibrationParameters(meta, basePath, "Force 2y");
		}
		
		archive.putMetadata(meta);
		
		if (!members.contains("Force HF")) {
			uiService.showDialog("No Force HF directory found. Not sure where to locate the force data.", MessageType.ERROR_MESSAGE);
			logService.info(LogBuilder.endBlock(false));
			archive = null;
			return;
		}
				
		List<String> forceMembers = reader.getGroupMembers("/Force HF/");
		if (!forceMembers.contains("Force 1x") || !forceMembers.contains("Force 2x")) {
			uiService.showDialog("Both Force 1x and Force 2x could not be located.", MessageType.ERROR_MESSAGE);
			logService.info(LogBuilder.endBlock(false));
			archive = null;
			return;
		}
	
		//Import Force 1x and 2x force data
		double samplingRate = reader.uint64().getAttr("/Force HF/Force 1x/", "Sample rate (Hz)");
		double force1xStartTime = reader.uint64().getAttr("/Force HF/Force 1x/", "Start time (ns)");
		
		if (downsample) {
			//Check the value provided is reasonable
			if (downsampleToHz < 1) {
				uiService.showDialog("The downsampling rate must be greater than or equal to 1 Hz.", MessageType.ERROR_MESSAGE);
				logService.info(LogBuilder.endBlock(false));
				archive = null;
				return;
			}
			if (downsampleToHz > samplingRate/2) {
				uiService.showDialog("The desired downsampling rate of " + downsampleToHz + " Hz is less than 2-fold reduced from the\n" +
					"original sampling rate of " + samplingRate + " Hz. Please choose a smaller downsampling rate.", MessageType.ERROR_MESSAGE);
				logService.info(LogBuilder.endBlock(false));
				archive = null;
				return;
			}
			samplingRate = downsampleToHz;
		}
		
		logService.info("Located Force 1x and Force 2x. Loading sampling rate from Force 1x.");
		
		double[] force1x = (downsample) ? 
				downsample(reader.float64().readArray("/Force HF/Force 1x"), (int)Math.round(samplingRate/downsampleToHz)) 
				: reader.float64().readArray("/Force HF/Force 1x");
		double[] force2x = (downsample) ?
				downsample(reader.float64().readArray("/Force HF/Force 2x"), (int)Math.round(samplingRate/downsampleToHz)) 
				: reader.float64().readArray("/Force HF/Force 2x");
				
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
		
		if (members.contains("Trap position") && reader.getGroupMembers("Trap position").contains("1X")) {
				double[] trapPosition1x = (downsample) ?
					downsample(reader.float64().readArray("/Trap position/1X"), (int)Math.round(samplingRate/downsampleToHz)) 
					: reader.float64().readArray("/Trap position/1X");
				
				DoubleColumn trapPosition1xCol = new DoubleColumn("Trap_position_1X");
				trapPosition1xCol.setArray(trapPosition1x);
				trapPosition1xCol.setSize(trapPosition1x.length);
				table.add(trapPosition1xCol);
		}
		
		SingleMolecule molecule = archive.createMolecule(MarsMath.getUUID58(), table);
		setStartStopParameters(molecule, "/Force HF/", "Force 1x");
		String Force1xkind = reader.string().getAttr("/Force HF/Force 1x", "Kind");
		if (Force1xkind != null) molecule.setParameter("Force 1x_Kind", Force1xkind);
		molecule.setParameter("Force 1x_Sample rate (Hz)", samplingRate);
		
		setStartStopParameters(molecule, "/Force HF/", "Force 2x");
		String Force2xkind = reader.string().getAttr("/Force HF/Force 2x", "Kind");
		if (Force2xkind != null) molecule.setParameter("Force 2x_Kind", Force2xkind);
		molecule.setParameter("Force 2x_Sample rate (Hz)", samplingRate);
		
		//Import and convert Markers
		if (members.contains("Marker")) {
			List<String> markers = reader.getGroupMembers("/Marker/");
			for (String name : markers) {
				double start = (reader.uint64().getAttr("/Marker/" + name, "Start time (ns)") - force1xStartTime)/1_000_000_000;
				double stop = (reader.uint64().getAttr("/Marker/" + name, "Stop time (ns)") - force1xStartTime)/1_000_000_000;
				molecule.putRegion(new MarsRegion(name, "Time_(s)", start, stop, "#FFCA28", 0.2));
			}
		}
		
		molecule.setMetadataUID(metaUID);
		archive.put(molecule);

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	  logService.info(LogBuilder.endBlock(true));
	  
	  archive.logln(log);
	  archive.logln(LogBuilder.endBlock(true));
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Downsample", downsample);
		builder.addParameter("Downsample to Hz", downsampleToHz);
	}
	
	private void setMetadataCalibrationParameters(MarsRecord meta, String basePath, String member) {
		String kind = reader.string().getAttr(basePath + member, "Kind");
		if (kind != null) meta.setParameter(member + "_Kind", kind);
		
		List<String> attrs = reader.object().getAllAttributeNames(basePath + member);
		for (String field : float64CalibrationFields)
			if (attrs.contains(field))
				meta.setParameter(member + "_" + field, reader.float64().getAttr(basePath + member, field));
		
		setStartStopParameters(meta, basePath, member);
	}
	
	private void setStartStopParameters(MarsRecord record, String basePath, String member) {
		List<String> force1xAttrs = reader.object().getAllAttributeNames(basePath + member);
		if (force1xAttrs.contains("Start time (ns)"))
			record.setParameter(member + "_Start time (ns)", reader.uint64().getAttr(basePath + member, "Start time (ns)"));
		
		if (force1xAttrs.contains("Stop time (ns)"))
			record.setParameter(member + "_Stop time (ns)", reader.uint64().getAttr(basePath + member, "Stop time (ns)"));
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
