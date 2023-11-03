package edu.umbc.hhmi.acquisition_plugin;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.chemistry.utilities.NvUtil;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.project.SubProject;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;
import org.nmrfx.star.SaveframeProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ManagedListSaveframeProcessor implements SaveframeProcessor {
    //this is bollards
    static List<PeakDim> peakDimsWithoutRes = new ArrayList<>();
    @Override
    public void process(Saveframe saveframe) throws ParseException, IOException {
        /*
            Notes:
                - Sample and Condition will not exist on read, as peaks are always written first. Needs to be handled later.
                - Need to read as normal peakList to avoid issue with resonance reindex. This secondary processing upgrades to a managedList
                - Requires that PeakList doesn't write out Experiment_class (true as of 11.3.3)
         */

        System.out.println("process managed list");
        String listName = saveframe.getValue("_Spectral_peak_list", "Sf_framecode");
        ResonanceFactory resFactory = SubProject.resFactory();
        String id = saveframe.getLabelValue("_Spectral_peak_list", "ID");
        String sampleLabel = saveframe.getLabelValue("_Spectral_peak_list", "Sample_label");
        String sampleConditionLabel = saveframe.getOptionalValue("_Spectral_peak_list", "Sample_condition_list_label").replace("^'", "").replace("'$", "");
        ;
        String datasetName = saveframe.getLabelValue("_Spectral_peak_list", "Experiment_name");
        String nDimString = saveframe.getValue("_Spectral_peak_list", "Number_of_spectral_dimensions");
        String dataFormat = saveframe.getOptionalValue("_Spectral_peak_list", "Text_data_format");
        String expType = saveframe.getOptionalValue("_Spectral_peak_list", "Experiment_type");
        String details = saveframe.getOptionalValue("_Spectral_peak_list", "Details");
        String slidable = saveframe.getOptionalValue("_Spectral_peak_list", "Slidable");
        String scaleStr = saveframe.getOptionalValue("_Spectral_peak_list", "Scale");

        String experimentClass;
        try {
            experimentClass = saveframe.getLabelValue("_Spectral_peak_list", "Experiment_class");
        } catch (Exception e) {
            System.out.println(listName + " is not managed");
            PeakList original = PeakList.get(listName);
            for (Peak peak : original.peaks()) {
                for (PeakDim peakDim : peak.getPeakDims()) {
                    try {
                        AtomResonance resonance = (AtomResonance) peakDim.getResonance();
                        Atom atom = resonance.getPossibleAtom();
                        if (atom.getResonance() == null) {
                            atom.setResonance(((AtomResonance) peakDim.getResonance()));
                        } else {
                            atom.getResonance().add(peakDim);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            return;
        }

        String sampleID = saveframe.getOptionalValue("_Spectral_peak_list", "Sample_ID");
        String sampleName = sampleLabel.replace("^'", "").replace("'$", "");
        String conditionID = saveframe.getOptionalValue("_Spectral_peak_list", "Sample_condition_list_ID");
        String experimentName = expType.replace("^'", "").replace("'$", "");

        if (dataFormat.equals("text")) {
            System.out.println("Aaaack, peak list is in text format, skipping list");
            System.out.println(details);
            return;
        }
        if (nDimString.equals("?")) {
            return;
        }
        if (nDimString.equals(".")) {
            return;
        }
        int nDim = NvUtil.toInt(nDimString);

        int nSpectralDim = saveframe.loopCount("_Spectral_dim");
        if (nSpectralDim > nDim) {
            throw new IllegalArgumentException("Too many _Spectral_dim values " + listName + " " + nSpectralDim + " " + nDim);
        }

        //for portability
        Experiment experiment = Experiment.get(experimentName);
        if (experiment == null) {
            experiment = new Experiment(experimentName, experimentClass);
        } else if (!experiment.toCode().equals(experimentClass)) {
            Integer suffix = 2;
            while (experiment.get(experimentName + suffix.toString()) != null) {
                suffix++;
            }
            experiment = new Experiment(experimentName + suffix, experimentClass);
        }

        int x = 0;
        HashMap<ExpDim, Integer> dimMap = new HashMap<>();
        for (ExpDim expDim : experiment.obsDims) {
            String dimNo;
            try {
                dimNo = saveframe.getValueIfPresent("_Spectral_dim_transfer", "Spectral_dim_ID_1", x);
            } catch (Exception e) {
                try {
                    dimNo = saveframe.getValueIfPresent("_Spectral_dim_transfer", "Spectral_dim_ID_2", x - 1);
                } catch (Exception e2) {
                    System.out.println("Error with dimMap");
                    break;
                }
            }
            dimMap.put(expDim, Integer.parseInt(dimNo));
            x++;
        }

        //fixme: what about acquisitions with more than one list associated with them?
        //fixme: I think we just tnd up with two acquisitions, shouldn't be breaking
        Acquisition acquisition = new Acquisition();
        acquisition.setDataset(Dataset.getDataset(datasetName));
        Sample sample = Sample.get(sampleName);
        if (sample == null) {
            sample = new Sample(sampleName);
        }
        acquisition.setSample(sample);
        Condition condition = Condition.get(sampleConditionLabel);
        if (condition == null) {
            condition = new Condition(sampleConditionLabel);
        }
        acquisition.setCondition(condition);
        acquisition.setExperiment(experiment);

        PeakList original = PeakList.get(listName);

        int index = 1;
        String tempName = listName;
        while (PeakList.get(String.format("%s_%d",tempName,index))!=null) {
            index+=1;
        }
        ManagedList peakList = new ManagedList(original, listName, String.format("%s_%d",tempName,index), nDim, acquisition, dimMap, NvUtil.toInt(id));

        acquisition.attachManagedList(peakList);
    }
}
