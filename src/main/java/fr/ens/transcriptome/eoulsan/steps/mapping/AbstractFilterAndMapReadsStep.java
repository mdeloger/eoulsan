/*
 *                  Eoulsan development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public License version 2.1 or
 * later and CeCILL-C. This should be distributed with the code.
 * If you do not have a copy, see:
 *
 *      http://www.gnu.org/licenses/lgpl-2.1.txt
 *      http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.txt
 *
 * Copyright for this code is held jointly by the Genomic platform
 * of the Institut de Biologie de l'École Normale Supérieure and
 * the individual authors. These should be listed in @author doc
 * comments.
 *
 * For more information on the Eoulsan project and its aims,
 * or to join the Eoulsan Google group, visit the home page
 * at:
 *
 *      http://www.transcriptome.ens.fr/eoulsan
 *
 */

package fr.ens.transcriptome.eoulsan.steps.mapping;

import static fr.ens.transcriptome.eoulsan.EoulsanLogger.getLogger;
import static fr.ens.transcriptome.eoulsan.core.OutputPortsBuilder.singleOutputPort;
import static fr.ens.transcriptome.eoulsan.data.DataFormats.GENOME_DESC_TXT;
import static fr.ens.transcriptome.eoulsan.data.DataFormats.MAPPER_RESULTS_SAM;
import static fr.ens.transcriptome.eoulsan.data.DataFormats.READS_FASTQ;

import java.util.Map;
import java.util.Set;

import fr.ens.transcriptome.eoulsan.EoulsanException;
import fr.ens.transcriptome.eoulsan.Globals;
import fr.ens.transcriptome.eoulsan.bio.alignmentsfilters.MultiReadAlignmentsFilterBuilder;
import fr.ens.transcriptome.eoulsan.bio.readsfilters.MultiReadFilterBuilder;
import fr.ens.transcriptome.eoulsan.bio.readsmappers.SequenceReadsMapper;
import fr.ens.transcriptome.eoulsan.bio.readsmappers.SequenceReadsMapperService;
import fr.ens.transcriptome.eoulsan.core.InputPorts;
import fr.ens.transcriptome.eoulsan.core.InputPortsBuilder;
import fr.ens.transcriptome.eoulsan.core.OutputPorts;
import fr.ens.transcriptome.eoulsan.core.Parameter;
import fr.ens.transcriptome.eoulsan.steps.AbstractStep;
import fr.ens.transcriptome.eoulsan.util.Version;

/**
 * This class define an abstract step for read filtering, mapping and alignments
 * filtering.
 * @since 1.0
 * @author Laurent Jourdren
 */
public abstract class AbstractFilterAndMapReadsStep extends AbstractStep {

  private static final String STEP_NAME = "filterandmap";
  private static final String COUNTER_GROUP = "filter_map_reads";

  protected static final String READS_PORT_NAME = "reads";
  protected static final String MAPPER_INDEX_PORT_NAME = "mapperindex";
  protected static final String GENOME_DESCRIPTION_PORT_NAME =
      "genomedescription";

  protected static final int HADOOP_TIMEOUT =
      AbstractReadsMapperStep.HADOOP_TIMEOUT;

  private boolean pairedEnd;

  private Map<String, String> readsFiltersParameters;
  private Map<String, String> alignmentsFiltersParameters;
  private SequenceReadsMapper mapper;
  private String mapperArguments;
  private int hadoopThreads = -1;

  private int mappingQualityThreshold = -1;

  //
  // Getters
  //

  /**
   * Get the counter group to use for this step.
   * @return the counter group of this step
   */
  protected String getCounterGroup() {
    return COUNTER_GROUP;
  }

  /**
   * Test if the step works in pair end mode.
   * @return true if the pair end mode is enable
   */
  protected boolean isPairedEnd() {
    return this.pairedEnd;
  }

  /**
   * Get the name of the mapper to use.
   * @return Returns the mapperName
   */
  protected String getMapperName() {
    return this.mapper.getMapperName();
  }

  /**
   * Get the name of the mapper to use.
   * @return Returns the mapperName
   */
  protected String getMapperArguments() {
    return this.mapperArguments;
  }

  /**
   * Get the name of the mapper to use.
   * @return Returns the mapperName
   */
  protected int getMapperHadoopThreads() {
    return this.hadoopThreads;
  }

  /**
   * Get the mapper.
   * @return the mapper object
   */
  protected SequenceReadsMapper getMapper() {

    return this.mapper;
  }

  /**
   * Get the mapping quality threshold.
   * @return the quality mapping threshold
   */
  protected int getMappingQualityThreshold() {

    return this.mappingQualityThreshold;
  }

  //
  // Step methods
  //

  @Override
  public String getName() {

    return STEP_NAME;
  }

  @Override
  public Version getVersion() {

    return Globals.APP_VERSION;
  }

  @Override
  public InputPorts getInputPorts() {

    final InputPortsBuilder builder = new InputPortsBuilder();
    builder.addPort(READS_PORT_NAME, READS_FASTQ);
    builder.addPort(MAPPER_INDEX_PORT_NAME, this.mapper.getArchiveFormat());
    builder.addPort(GENOME_DESCRIPTION_PORT_NAME, GENOME_DESC_TXT);

    return builder.create();
  }

  @Override
  public OutputPorts getOutputPorts() {
    return singleOutputPort(MAPPER_RESULTS_SAM);
  }

  @Override
  public String getDescription() {

    return "This step filters, map reads and filter alignment results.";
  }

  @Override
  public void configure(final Set<Parameter> stepParameters)
      throws EoulsanException {

    String mapperName = null;
    final MultiReadFilterBuilder mrfb = new MultiReadFilterBuilder();
    final MultiReadAlignmentsFilterBuilder mrafb =
        new MultiReadAlignmentsFilterBuilder();

    for (Parameter p : stepParameters) {

      if ("mapper".equals(p.getName()))
        mapperName = p.getStringValue();

      else if ("mapperarguments".equals(p.getName())
          || "mapper.arguments".equals(p.getName()))
        this.mapperArguments = p.getStringValue();
      else if ("hadoop.threads".equals(p.getName()))
        this.hadoopThreads = p.getIntValue();

      else {

        // Add read filters parameters
        if (!(mrfb.addParameter(
            AbstractReadsFilterStep.convertCompatibilityFilterKey(p.getName()),
            p.getStringValue(), true) ||
        // Add read alignments filters parameters
        mrafb.addParameter(
            AbstractSAMFilterStep.convertCompatibilityFilterKey(p.getName()),
            p.getStringValue(), true))) {

          throw new EoulsanException("Unknown parameter: " + p.getName());
        }
      }

    }

    // Force parameter checking
    mrfb.getReadFilter();
    mrafb.getAlignmentsFilter();

    this.readsFiltersParameters = mrfb.getParameters();
    this.alignmentsFiltersParameters = mrafb.getParameters();

    if (mapperName == null)
      throw new EoulsanException("No mapper set.");

    this.mapper =
        SequenceReadsMapperService.getInstance().newService(mapperName);

    if (this.mapper == null)
      throw new EoulsanException("Unknown mapper: " + mapperName);

    if (this.mapper.isIndexGeneratorOnly())
      throw new EoulsanException(
          "The selected mapper can only be used for index generation: "
              + mapperName);

    // Log Step parameters
    getLogger().info(
        "In "
            + getName() + ", mapper=" + this.mapper.getMapperName()
            + " (version: " + mapper.getMapperVersion() + ")");
    getLogger().info(
        "In " + getName() + ", mapperarguments=" + this.mapperArguments);
  }

  /**
   * Get the parameters of the read filters.
   * @return a map with all the parameters of the filters
   */
  protected Map<String, String> getReadFilterParameters() {

    return this.readsFiltersParameters;
  }

  /**
   * Get the parameters of the read alignments filters.
   * @return a map with all the parameters of the filters
   */
  protected Map<String, String> getAlignmentsFilterParameters() {

    return this.alignmentsFiltersParameters;
  }

}
