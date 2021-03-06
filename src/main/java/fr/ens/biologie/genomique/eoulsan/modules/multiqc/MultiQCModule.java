package fr.ens.biologie.genomique.eoulsan.modules.multiqc;

import static fr.ens.biologie.genomique.eoulsan.core.OutputPortsBuilder.singleOutputPort;
import static fr.ens.biologie.genomique.eoulsan.data.DataFormats.MULTIQC_REPORT_HTML;
import static fr.ens.biologie.genomique.eoulsan.requirements.DockerRequirement.newDockerRequirement;
import static fr.ens.biologie.genomique.eoulsan.requirements.PathRequirement.newPathRequirement;
import static java.util.Collections.unmodifiableSet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Splitter;

import fr.ens.biologie.genomique.eoulsan.EoulsanException;
import fr.ens.biologie.genomique.eoulsan.Globals;
import fr.ens.biologie.genomique.eoulsan.annotations.LocalOnly;
import fr.ens.biologie.genomique.eoulsan.core.InputPorts;
import fr.ens.biologie.genomique.eoulsan.core.InputPortsBuilder;
import fr.ens.biologie.genomique.eoulsan.core.Modules;
import fr.ens.biologie.genomique.eoulsan.core.OutputPorts;
import fr.ens.biologie.genomique.eoulsan.core.Parameter;
import fr.ens.biologie.genomique.eoulsan.core.StepConfigurationContext;
import fr.ens.biologie.genomique.eoulsan.core.TaskContext;
import fr.ens.biologie.genomique.eoulsan.core.TaskResult;
import fr.ens.biologie.genomique.eoulsan.core.TaskStatus;
import fr.ens.biologie.genomique.eoulsan.core.Version;
import fr.ens.biologie.genomique.eoulsan.data.Data;
import fr.ens.biologie.genomique.eoulsan.data.DataFile;
import fr.ens.biologie.genomique.eoulsan.data.DataFormat;
import fr.ens.biologie.genomique.eoulsan.modules.AbstractModule;
import fr.ens.biologie.genomique.eoulsan.requirements.Requirement;
import fr.ens.biologie.genomique.eoulsan.util.FileUtils;
import fr.ens.biologie.genomique.eoulsan.util.process.DockerManager;
import fr.ens.biologie.genomique.eoulsan.util.process.SimpleProcess;
import fr.ens.biologie.genomique.eoulsan.util.process.SystemSimpleProcess;

/**
 * This class define a module for MultiQC.
 * @since 2.2
 * @author Laurent Jourdren
 */
@LocalOnly
public class MultiQCModule extends AbstractModule {

  /** Module name */
  private static final String MODULE_NAME = "multiqc";

  private static final String MULTIQC_DOCKER_IMAGE = "ewels/multiqc:v1.3";
  private static final String MULTIQC_EXECUTABLE = "multiqc";

  private boolean dockerMode;
  private String dockerImage = MULTIQC_DOCKER_IMAGE;
  private final Set<Requirement> requirements = new HashSet<>();
  private final Map<DataFormat, InputPreprocessor> formats = new HashMap<>();

  @Override
  public String getName() {

    return MODULE_NAME;
  }

  @Override
  public Version getVersion() {

    return Globals.APP_VERSION;
  }

  @Override
  public InputPorts getInputPorts() {

    final InputPortsBuilder builder = new InputPortsBuilder();

    int count = 0;
    for (DataFormat format : this.formats.keySet()) {

      builder.addPort("inputport" + count++, true, format);
    }

    return builder.create();
  }

  @Override
  public OutputPorts getOutputPorts() {
    return singleOutputPort(MULTIQC_REPORT_HTML);
  }

  @Override
  public Set<Requirement> getRequirements() {

    return unmodifiableSet(this.requirements);
  }

  @Override
  public void configure(StepConfigurationContext context,
      Set<Parameter> stepParameters) throws EoulsanException {

    // By default only process FastQC reports
    String reports = FastQCInputPreprocessor.REPORT_NAME;

    for (Parameter p : stepParameters) {

      switch (p.getName()) {

      case "use.docker":
        this.dockerMode = p.getBooleanValue();
        break;

      case "docker.image":
        this.dockerImage = p.getStringValue().trim();
        if (this.dockerImage.isEmpty()) {
          Modules.badParameterValue(context, p,
              "The docker image name is empty");
        }
        break;

      case "reports":
        reports = p.getStringValue();
        break;

      default:
        Modules.unknownParameter(context, p);
        break;
      }
    }

    // Parse report parameter and set the formats to handle
    for (InputPreprocessor ip : parseReportParameter(reports,
        context.getCurrentStep().getId())) {
      this.formats.put(ip.getDataFormat(), ip);
    }

    // Define requirements
    if (this.dockerMode) {
      this.requirements.add(newDockerRequirement(this.dockerImage));
    } else {
      this.requirements.add(newPathRequirement(MULTIQC_EXECUTABLE));
    }
  }

  @Override
  public TaskResult execute(final TaskContext context,
      final TaskStatus status) {

    // Define the output file
    File multiQCReportFile = context.getOutputData(MULTIQC_REPORT_HTML, "all")
        .getDataFile().toFile();

    try {

      // Create a temporary directory where all the preprocessed files for
      // MultiQC while be saved
      File multiQCInputDir =
          FileUtils.createTempDir(context.getLocalTempDirectory());

      // Preprocess input data for MultiQC
      for (Map.Entry<DataFormat, InputPreprocessor> e : this.formats
          .entrySet()) {
        for (Data d : context.getInputData(e.getKey()).getListElements()) {
          e.getValue().preprocess(context, d, multiQCInputDir);
        }
      }

      // Launch MultiQC
      if (this.dockerMode) {
        createMultiQCReportWithDocker(this.dockerImage, multiQCInputDir,
            multiQCReportFile, context.getCommandName(),
            context.getLocalTempDirectory());
      } else {
        createMultiQCReport(multiQCInputDir, multiQCReportFile,
            context.getCommandName(), context.getLocalTempDirectory());
      }

      // Cleanup temporary directory
      new DataFile(multiQCInputDir).delete(true);

    } catch (IOException | EoulsanException e) {
      return status.createTaskResult(e);
    }

    return status.createTaskResult();
  }

  /**
   * Parse the "reports" step parameter.
   * @param reports the parameter value
   * @param stepId stepId
   * @return a collection of InputPreprocessor
   * @throws EoulsanException if the parameter value is invalid
   */
  private static Collection<InputPreprocessor> parseReportParameter(
      final String reports, final String stepId) throws EoulsanException {

    final Map<String, InputPreprocessor> result = new HashMap<>();

    for (String report : Splitter.on(',').trimResults().omitEmptyStrings()
        .splitToList(reports.toLowerCase())) {

      switch (report) {

      case FastQCInputPreprocessor.REPORT_NAME:
        if (!result.containsKey(report)) {
          result.put(report, new FastQCInputPreprocessor());
        }
        break;

      case MapperInputPreprocessor.REPORT_NAME:
        if (!result.containsKey(report)) {
          result.put(report, new MapperInputPreprocessor());
        }
        break;
      case ExpressionInputPreprocessor.REPORT_NAME:
        if (!result.containsKey(report)) {
          result.put(report, new ExpressionInputPreprocessor());
        }
        break;

      default:
        throw new EoulsanException("In step \""
            + stepId + "\", invalid MultiQC configuration: unknown report type"
            + report);
      }
    }

    if (result.isEmpty()) {
      throw new EoulsanException("In step \""
          + stepId + "\", invalid MultiQC configuration: no report selected");
    }

    return result.values();
  }

  /**
   * Create the MultiQC report using docker.
   * @param dockerImage docker image to use
   * @param inputDirectory input directory
   * @param multiQCReportFile output report
   * @param projectName project name
   * @throws IOException if an error occurs while creating the report
   * @throws EoulsanException if MultiQC execution fails
   */
  private static void createMultiQCReportWithDocker(final String dockerImage,
      final File inputDirectory, final File multiQCReportFile,
      final String projectName, final File temporaryDirectory)
      throws IOException, EoulsanException {

    SimpleProcess process =
        DockerManager.getInstance().createImageInstance(dockerImage);

    File executionDirectory = multiQCReportFile.getParentFile();
    File stdoutFile = new File(executionDirectory, "multiqc.stdout");
    File stderrFile = new File(executionDirectory, "multiqc.stderr");

    // Define the list of the files/directory to mount in the Docker instance
    List<File> filesUsed = new ArrayList<>();
    filesUsed.add(executionDirectory);
    filesUsed.add(temporaryDirectory);
    filesUsed.add(multiQCReportFile);

    for (File f : inputDirectory.listFiles()) {

      // Do not handle files and directory that starts with '.' and files that
      // are not symbolic links
      if (!f.getName().startsWith(".") && Files.isSymbolicLink(f.toPath())) {
        filesUsed.add(f.toPath().toRealPath().toFile());
      }
    }

    // Launch Docker container
    final int exitValue = process.execute(
        createMultiQCOptions(inputDirectory, multiQCReportFile, projectName),
        executionDirectory, temporaryDirectory, stdoutFile, stderrFile,
        filesUsed.toArray(new File[filesUsed.size()]));

    if (exitValue > 0) {
      throw new EoulsanException("Invalid exit code of MultiQC: " + exitValue);
    }
  }

  /**
   * Create the MultiQC report using docker.
   * @param inputDirectory input directory
   * @param multiQCReportFile output report
   * @param projectName project name
   * @throws IOException if an error occurs while creating the report
   * @throws EoulsanException if MultiQC execution fails
   */
  private void createMultiQCReport(final File inputDirectory,
      final File multiQCReportFile, final String projectName,
      final File temporaryDirectory) throws IOException, EoulsanException {

    SimpleProcess process = new SystemSimpleProcess();

    File executionDirectory = multiQCReportFile.getParentFile();
    File stdoutFile = new File(executionDirectory, "multiqc.stdout");
    File stderrFile = new File(executionDirectory, "multiqc.stderr");

    // Launch Docker container
    int exitValue = process.execute(
        createMultiQCOptions(inputDirectory, multiQCReportFile, projectName),
        executionDirectory, temporaryDirectory, stdoutFile, stderrFile);

    if (exitValue > 0) {
      throw new EoulsanException("Invalid exit code of MultiQC: " + exitValue);
    }
  }

  /**
   * Creating MultiQC command line.
   * @param inputDirectories input directories
   * @param multiQCReportFile output report
   * @param projectName project name
   * @return a list with the MultiQC arguments
   */
  private static List<String> createMultiQCOptions(final File inputDirectory,
      final File multiQCReportFile, final String projectName) {

    List<String> result = new ArrayList<>();

    // The MultiQC executable name
    result.add(MULTIQC_EXECUTABLE);

    // MultiQC options
    result.add("--title");
    result.add("Project " + projectName + " report");
    result.add("--filename");
    result.add(multiQCReportFile.getAbsolutePath());

    // MultiQC input directory
    result.add(inputDirectory.getAbsolutePath());

    return result;
  }

}
