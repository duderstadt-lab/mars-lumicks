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

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveIndex;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.molecule.SingleMolecule;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import net.imagej.ops.Initializable;
import org.scijava.table.DoubleColumn;

import javax.swing.JLabel;

@Plugin(type = Command.class, label = "LUMICKS h5", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Mars", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 'm'),
		@Menu(label = "Import", weight = 110,
			mnemonic = 'i'),
		@Menu(label = "LUMICKS h5", weight = 1, mnemonic = 'l')})
public class ImportLumicksh5Command extends DynamicCommand implements Command, Initializable {

	@Parameter
	private LogService logService;

    @Parameter
    private StatusService statusService;

	@Parameter
    private MoleculeArchiveService moleculeArchiveService;

	@Parameter
    private UIService uiService;

	//@Parameter(label = "X Column", choices = { "a", "b", "c" })
	//private String xColumn;


	@Override
	public void initialize() {
		//ArrayList<String> columns = new ArrayList<String>();
		//columns.addAll(moleculeArchiveService.getArchives().get(0).properties()
		//	.getColumnSet());
		//columns.sort(String::compareToIgnoreCase);

		//final MutableModuleItem<String> xColumnItems = getInfo().getMutableInput(
		//	"xColumn", String.class);
		//xColumnItems.setChoices(columns);

		//final MutableModuleItem<String> yColumnItems = getInfo().getMutableInput(
		//	"yColumn", String.class);
		//yColumnItems.setChoices(columns);
	}

	@Override
	public void run() {
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();

		//Build log message
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("LUMICKS h5 Import");

		addInputParameterLog(builder);
		log += builder.buildParameterList();

		//Output first part of log message...
		logService.info(log);

		final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(testDirPath);

		final HDF5DataSetInformation datasetInfo = reader.object().getDataSetInformation(pathName);

		//Read stuff from the h5...
		final HDF5DataSetInformation datasetInfo = reader.object().getDataSetInformation(pathName);
			final long[] dimensions = datasetInfo.getDimensions();
			int[] blockSize = overrideBlockSize ? null : datasetInfo.tryGetChunkSizes();

			final HDF5DataTypeInformation attributeInfo = reader.object().getAttributeInformation(pathName, key);

			///

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(LogBuilder.endBlock(true));
	    archive.logln(LogBuilder.endBlock(true));
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("xxx", xxx;
	}

	public String[] list(String pathName) throws IOException {

		if (pathName.equals(""))
			pathName = "/";

		try {
			final List<String> members = reader.object().getGroupMembers(pathName);
			return members.toArray(new String[members.size()]);
		} catch (final Exception e) {
			throw new IOException(e);
		}
	}

	/**
	 * Structured attributes that are stored as JSON will be classified as
	 * String.class.
	 */
	@Override
	public Map<String, Class<?>> listAttributes(final String pathName) throws IOException {

		final String finalPathName = pathName.equals("") ? "/" : pathName;

		final HashMap<String, Class<?>> attributes = new HashMap<>();
		reader
				.object()
				.getAttributeNames(finalPathName)
				.forEach(
						attributeName -> attributes
								.put(
										attributeName,
										reader
												.object()
												.getAttributeInformation(finalPathName, attributeName)
												.tryGetJavaType()));
		return attributes;
	}
}
