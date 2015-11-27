package fr.ens.transcriptome.eoulsan.steps.mapping.local;

import static fr.ens.transcriptome.eoulsan.EoulsanLogger.getLogger;
import static fr.ens.transcriptome.eoulsan.data.DataFormats.MAPPER_RESULTS_SAM;

import fr.ens.transcriptome.eoulsan.EoulsanException;
import fr.ens.transcriptome.eoulsan.Globals;
import fr.ens.transcriptome.eoulsan.annotations.LocalOnly;
import fr.ens.transcriptome.eoulsan.core.InputPorts;
import fr.ens.transcriptome.eoulsan.core.InputPortsBuilder;
import fr.ens.transcriptome.eoulsan.core.OutputPorts;
import fr.ens.transcriptome.eoulsan.core.OutputPortsBuilder;
import fr.ens.transcriptome.eoulsan.core.Parameter;
import fr.ens.transcriptome.eoulsan.core.StepConfigurationContext;
import fr.ens.transcriptome.eoulsan.core.StepContext;
import fr.ens.transcriptome.eoulsan.core.StepResult;
import fr.ens.transcriptome.eoulsan.core.StepStatus;
import fr.ens.transcriptome.eoulsan.data.Data;
import fr.ens.transcriptome.eoulsan.data.DataFormats;
import fr.ens.transcriptome.eoulsan.steps.AbstractStep;
import fr.ens.transcriptome.eoulsan.util.Version;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
* This class merges SAM files of input of the same experiment.
* It uses Picard's MergeSamFiles to merge SAM files from inputs of the same experiment.
* @author Celine Hernandez - CSB lab - ENS - Paris
*/
@LocalOnly
public class MergeInputRepLocalStep extends AbstractStep {

    /**
     *
     */
    private static final String STEP_NAME = "mergeinput";

    //
    // Overridden methods 
    //

    /**
     * Name of the Step.
     */    
    @Override
    public String getName() {
        return this.STEP_NAME;
    }

    /**
     * A short description of the tool and what is done in the step.
     */    
    @Override
    public String getDescription() {
        return "This step merges Input files for each experiment. It uses Picard's MergeSamFiles.";
    }

    /**
     * Should a log file be created?
     */    
    @Override
    public boolean isCreateLogFiles() {
        return true;
    }

    /**
     * Version.
     */    
    @Override
    public Version getVersion() {
        return Globals.APP_VERSION;
    }

    /**
     * Define input port.
     */    
    @Override
    public InputPorts getInputPorts() {
        final InputPortsBuilder builder = new InputPortsBuilder();
        builder.addPort("input", true, DataFormats.MAPPER_RESULTS_SAM);
        return builder.create();
    }

    /**
     * Define output port.
     */    
    @Override
    public OutputPorts getOutputPorts() {
        final OutputPortsBuilder builder = new OutputPortsBuilder();
        builder.addPort("output", true, DataFormats.MAPPER_RESULTS_SAM);
        return builder.create();
    }

    /**
    * Set the parameters of the step to configure the step.
    * @param stepParameters parameters of the step
    * @throws EoulsanException if a parameter is invalid
    */
    @Override
    public void configure(final StepConfigurationContext context, final Set<Parameter> stepParameters)
        throws EoulsanException {
        
        for (Parameter p : stepParameters) {
        
            getLogger().info("MergeInputRep parameter: " + p.getName() + " : " + p.getStringValue());
            throw new EoulsanException("Unknown parameter for "
                + getName() + " step: " + p.getName());
        }
        
    }

    
    /**
    * Merge input replicates.
    * @throws EoulsanException if temp file can't be created.
    */
    @Override
    public StepResult execute(final StepContext context, final StepStatus status) {
        
        // Get input data (SAM format)
        final Data inData = context.getInputData(DataFormats.MAPPER_RESULTS_SAM);

        // Get file name created by Eoulsan
        final Data outputDataList = context.getOutputData(DataFormats.MAPPER_RESULTS_SAM, "mergedinput");

        HashMap<String, ArrayList<Data>> referenceSamples = new HashMap<String, ArrayList<Data>>();
        for(Data anInputData : inData.getListElements()) {
        
            getLogger().finest("Input file. ref : " + anInputData.getMetadata().get("Reference") + 
                             "| exp : " + anInputData.getMetadata().get("Experiment") + 
                             "| rep : " + anInputData.getMetadata().get("RepTechGroup"));

            boolean isReference = anInputData.getMetadata().get("Reference").toLowerCase().equals("true");

            // Only treat reference files 
            if(isReference) {
                final String experimentName = anInputData.getMetadata().get("Experiment");
                
                if(referenceSamples.containsKey(experimentName) && referenceSamples.get(experimentName) != null) {
                    referenceSamples.get(experimentName).add(anInputData);
                }
                else {
                    ArrayList<Data> tmpList = new ArrayList<Data>();
                    tmpList.add(anInputData);
                    referenceSamples.put(experimentName, tmpList);
                }
            }
            // If it's not a reference file, create a symlink with the correct output name (to make it available to further steps)
            else {
                
                final Data outputData = outputDataList.addDataToList(anInputData.getMetadata().get("Name").replaceAll("[^a-zA-Z0-9]", ""));
                outputData.getMetadata().set(anInputData.getMetadata());
                
                try {
                    anInputData.getDataFile().symlink(outputData.getDataFile());
                } catch (IOException ioe) {
                    getLogger().severe("Could not create symlink from " + anInputData.getDataFile() + " to " + outputData.getDataFile());
                    return status.createStepResult();
                }
            }
             
        }
        
        // Loop through all references
        for(String experimentName : referenceSamples.keySet()) {
        
            ArrayList<Data> expData = referenceSamples.get(experimentName);
            
            // If we have only one Input, just make a symlink having the correct output name for this step
            if(expData.size()==1) {
                
                // Get the one input of this experiment
                final Data inputData = expData.get(0);
                
                // Get file name created by Eoulsan
                final Data outputData = outputDataList.addDataToList(inputData.getMetadata().get("Name").replaceAll("[^a-zA-Z0-9]", ""));
                outputData.getMetadata().set(inputData.getMetadata());
                
                try {
                    inputData.getDataFile().symlink(outputData.getDataFile());
                } catch (IOException ioe) {
                    getLogger().severe("Could not create symlink from " + inputData.getDataFile() + " to " + outputData.getDataFile());
                    return status.createStepResult();
                }
            }
            
            // Use Picard's MegerSamFiles to sort and merge (only if more than one input exists)
            if(expData.size()>2) {
                
                getLogger().info("Running Picard's MergeSamFiles for experiment  " + experimentName);
                
                // Get file name created by Eoulsan
                final Data outputData = outputDataList.addDataToList(expData.get(0).getMetadata().get("Name").replaceAll("[^a-zA-Z0-9]", ""));
                outputData.getMetadata().set(expData.get(0).getMetadata());

                String[] arguments = new String[expData.size()+2];
                arguments[0] = "OUTPUT="+outputData.getDataFile();
                arguments[1] = "QUIET=true";

                int startPos = 2;
                for(Data anInputData : expData) {
                    arguments[startPos++] = "INPUT="+anInputData.getDataFile();
                }
                
                // Start MergeSamFiles
                new picard.sam.MergeSamFiles().instanceMain(arguments);
            
            }
        
        }
                
        return status.createStepResult();
        
    }

}