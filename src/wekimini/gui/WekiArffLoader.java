/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package wekimini.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import wekimini.GlobalSettings;
import wekimini.Wekinator;
import wekimini.osc.OSCClassificationOutput;
import wekimini.osc.OSCNumericOutput;
import wekimini.osc.OSCOutput;
import wekimini.util.Util;

/**
 *
 * @author rebecca
 */
public class WekiArffLoader {

    private static final Logger logger = Logger.getLogger(WekiArffLoader.class.getName());
    private final Wekinator w;
    private final ArffLoaderNotificationReceiver recv;
    private final ArffLoader af;
    private Instances structure;

    public static File getArffFile() {
        String lastLocation = GlobalSettings.getInstance().getStringValue("arffLoadLocation", "");
        if (lastLocation.equals("")) {
            lastLocation = System.getProperty("user.home");
        }
        File f = Util.findLoadFile("arff", "ARFF file", lastLocation, null);
        if (f != null) {
            try {
                GlobalSettings.getInstance().setStringValue("arffLoadLocation", f.getCanonicalPath());
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        return f;
    }

    public WekiArffLoader(Wekinator w, File f, final ArffLoaderNotificationReceiver recv) {
        this.w = w;
        this.recv = recv;
        this.af = new ArffLoader();
       /* File f = getArffFile();
        if (f == null) {
            recv.completed();
            return;
        } */

        try {
            af.setFile(f);
            structure = af.getStructure();
        } catch (IOException ex) {
            w.getStatusUpdateCenter().warn(this, "Could not load from file " + f.getAbsolutePath());
            logger.log(Level.SEVERE, null, ex);
            recv.completed();
            return;
        }

        int numColumns = structure.numAttributes();
        String[] attributeNames = new String[numColumns];
        boolean[] isNumeric = new boolean[numColumns];
        boolean[] isNominal = new boolean[numColumns];

        for (int i = 0; i < numColumns; i++) {
            attributeNames[i] = structure.attribute(i).name();
            isNumeric[i] = structure.attribute(i).isNumeric();
            isNominal[i] = structure.attribute(i).isNominal();
        }

        String[] currentInputs = w.getInputManager().getInputNames();
        String[] currentOutputs = w.getOutputManager().getOutputGroup().getOutputNames();
        boolean[] isProjectOutputNumeric = new boolean[currentOutputs.length];
        String[] projectNames = new String[currentInputs.length + currentOutputs.length + 1];
        projectNames[0] = "None (Ignore)";
        System.arraycopy(currentInputs, 0, projectNames, 1, currentInputs.length);
        System.arraycopy(currentOutputs, 0, projectNames, currentInputs.length + 1, currentOutputs.length);

        List<OSCOutput> outputs = w.getOutputManager().getOutputGroup().getOutputs();
        for (int i = 0; i < currentOutputs.length; i++) {
            if (outputs.get(i) instanceof OSCNumericOutput) {
                isProjectOutputNumeric[i] = true;
            }
        }

        //Options for project matches for each column
        //Nominal attributes can be made numeric, but not the other way around
        List<List<String>> projectNamesPerColumn = new LinkedList<>();

        //Corresponding indices of project names, in projectNames array
        List<List<Integer>> projectIndicesPerColumn = new LinkedList<>();

        //Which initial values should be selected?
        int[] selectedIndicesPerColumn = new int[numColumns];

        for (int i = 0; i < attributeNames.length; i++) {
            boolean matched = false;
            List<String> candidateNames = new ArrayList<>(numColumns);
            List<Integer> candidateIndices = new ArrayList<>(numColumns);

            candidateNames.add(projectNames[0]);
            candidateIndices.add(0);
            int numAddedToColum = 1;
            
            for (int j = 0; j < currentInputs.length; j++) {
                if (isNumeric[i]) {
                    //We can match any numeric column to an input
                    candidateNames.add(currentInputs[j]);
                    candidateIndices.add(j);
                    numAddedToColum++;
                    if (!matched && matches(currentInputs[j], attributeNames[i])) {
                        matched = true;
                        selectedIndicesPerColumn[i] = numAddedToColum-1;
                    }
                } else if (isNominal[i]) {
                    //We can match any nominal column to an input, but warn first
                    candidateNames.add(currentInputs[j] + "*");
                    candidateIndices.add(j);
                    numAddedToColum++;
                    if (!matched && matches(currentInputs[j], attributeNames[i])) {
                        matched = true;
                        selectedIndicesPerColumn[i] = numAddedToColum-1;
                    }
                }
            }

            for (int j = 0; j < currentOutputs.length; j++) {
                if (isNumeric[i] && isProjectOutputNumeric[j]) {
                    //We can match a numeric column to a numeric output
                    candidateNames.add(currentOutputs[j]);
                    candidateIndices.add(currentInputs.length + j);
                    numAddedToColum++;
                    if (!matched && matches(currentOutputs[j], attributeNames[i])) {
                        matched = true;
                        selectedIndicesPerColumn[i] = numAddedToColum-1;
                    }
                } else if (isNominal[i] && isProjectOutputNumeric[j]) {
                    //We can match a nominal column to a numeric output, but warn first
                    candidateNames.add(currentOutputs[j] + "^");
                    candidateIndices.add(currentInputs.length + j);
                    numAddedToColum++;
                    if (!matched && matches(currentOutputs[j], attributeNames[i])) {
                        matched = true;
                        selectedIndicesPerColumn[i] = numAddedToColum-1;
                    }
                } else if (isNominal[i] && !isProjectOutputNumeric[j]) {
                    //May be able to match
                    //Allow match only if ARFF can reasonably be represented using the
                    //classes used in this project
                    //(For now, "0" is a special reserved class bookmarked for
                    //"none of the above," but this is not yet implemented.
                    int numClassesInArff = structure.attribute(i).numValues();
                    if (canMatch(structure.attribute(i), (OSCClassificationOutput) outputs.get(j))) {
                        candidateNames.add(currentOutputs[j]);
                        candidateIndices.add(currentInputs.length + j);
                        numAddedToColum++;
                        if (!matched && matches(currentOutputs[j], attributeNames[i])) {
                            matched = true;
                            selectedIndicesPerColumn[i] = numAddedToColum-1;
                        }
                    }
                } //else: column is nominal but output is numeric; can't do that. User should change Output in Wekinator.
            } // End for every output

            projectNamesPerColumn.add(candidateNames);
            projectIndicesPerColumn.add(candidateIndices);
            if (!matched) {
                selectedIndicesPerColumn[i] = 0;
            }
        }

        final WekiArffLoadFrame frame = new WekiArffLoadFrame(w,
                structure,
                projectNamesPerColumn,
                projectIndicesPerColumn,
                selectedIndicesPerColumn,
                new WekiArffLoadFrame.ArffConfiguredNotificationReceiver() {

                    @Override
                    public void arffConfigured(int[] selectedIndices, boolean overwrite) {
                        receivedConfiguration(selectedIndices, overwrite);
                    }

                    @Override
                    public void cancel() {
                        recv.completed();
                    }

                });
        frame.setVisible(true);
        frame.toFront();

        Util.CallableOnClosed callMe = new Util.CallableOnClosed() {
            @Override
            public void callMe() {
                recv.completed();
            }
        };
        Util.callOnClosed(frame, callMe);

    }

    private static boolean matches(String currentInput, String attributeName) {
        return (currentInput.equalsIgnoreCase(attributeName));
    }

    private static boolean hasValue(Attribute attribute, int val) {
        int numValues = attribute.numValues();
        for (int i = 0; i < numValues; i++) {
            if (attribute.value(i).equals(Integer.toString(val))) {
                return true;
            }
        }
        return false;
    }

    //Returns true if every attribute value is an integer between 0 and # of classes
    private static boolean canMatch(Attribute attribute, OSCClassificationOutput output) {
        int numSeen = 0;
        for (int i = 0; i <= output.getNumClasses(); i++) {
            if (hasValue(attribute, i)) {
                numSeen++;
            }
        }
        return (numSeen == attribute.numValues());
    }

    private void receivedConfiguration(int[] selectedIndices, boolean overwrite) {
        //Now load the data. TODO
        //For each instance: 
        
        //Slow, not great, but should work:
        //addImportedData(double[] inputs, double[][] outputs, boolean[] inputMask, boolean[] outputMask) {
        
        //w.getSupervisedLearningManager().addBundleToTraining(null, outputs, recordingMask);
          
        //w.getDataManager().addToTraining(inputs, outputs, recordingMask, recordingRound);
        w.getSupervisedLearningManager().incrementRecordingRound();
        
        if (overwrite) {
            w.getSupervisedLearningManager().deleteAllExamples();
        }
        
        boolean[] inputMaskForSet = createInputMaskForSet(selectedIndices);
        boolean[] outputMaskForSet = createOutputMaskForSet(selectedIndices);
        
        try {
            //Get enumerator for instances...
            Instance nextInstance = af.getNextInstance(structure);
            double[] inputs = new double[inputMaskForSet.length];
            int numInputs = inputs.length;
            double[] outputs = new double[outputMaskForSet.length];
            boolean[] inputMask = new boolean[inputMaskForSet.length];
            System.arraycopy(inputMaskForSet, 0, inputMask, 0, inputMask.length);
            boolean[] outputMask = new boolean[outputMaskForSet.length];
            System.arraycopy(outputMaskForSet, 0, outputMask, 0, outputMask.length);

            while (nextInstance != null) {
                 for (int i = 0; i < selectedIndices.length; i++) {
                     //selectedIndices[i] : says which input/output corresponds to the ith attribute
                     if (selectedIndices[i] == 0) {
                         //do nothing: ignore it
                     } else if (selectedIndices[i] <= inputs.length) { //it's an input
                         if (nextInstance.isMissing(i)) {
                             inputs[selectedIndices[i]-1] = 0;
                             inputMask[selectedIndices[i]-1] = false;
                         } else {
                            inputs[selectedIndices[i]-1] = nextInstance.value(i);
                         }
                     } else { //it's an output
                         if (nextInstance.isMissing(i)) {
                             outputs[selectedIndices[i]-1-numInputs] = 0;
                             outputMask[selectedIndices[i]-1-numInputs] = false;
                         } else {
                             outputs[selectedIndices[i]-1-numInputs] = nextInstance.value(i);
                         }
                     } 
                 }    
                w.getSupervisedLearningManager().addToTraining(inputs, outputs, inputMask, outputMask);
                nextInstance = af.getNextInstance(structure);
            }
            
        } catch (IOException ex) {
            w.getStatusUpdateCenter().warn(this, "Encountered error in reading from ARFF file.");
            Logger.getLogger(WekiArffLoader.class.getName()).log(Level.SEVERE, null, ex);
            recv.completed();
        }
        
        
        
        //TODO: Prevent this from being available when in DTW mode.
            
        recv.completed();
        }

    private boolean[] createInputMaskForSet(int[] selectedIndices) {
        boolean[] isPresent = new boolean[w.getInputManager().getNumInputs()];
        for (int i = 0; i < isPresent.length; i++) {
            isPresent[i] = existsInArray(i+1, selectedIndices);
        }
        return isPresent;
    }
    
    private boolean[] createOutputMaskForSet(int[] selectedIndices) {
        int numInputs = w.getInputManager().getNumInputs();
        boolean[] isPresent = new boolean[w.getOutputManager().getOutputGroup().getNumOutputs()];
        for (int i = 0; i < isPresent.length; i++) {
            isPresent[i] = existsInArray(i+1+numInputs, selectedIndices);
        }
        return isPresent;
    }

    private boolean existsInArray(int num, int[] array) {
        for (int i=0; i < array.length; i++) {
            if (num == array[i])
                return true;
        }
        return false;
    }

    public interface ArffLoaderNotificationReceiver {

        public void completed();
    }
}