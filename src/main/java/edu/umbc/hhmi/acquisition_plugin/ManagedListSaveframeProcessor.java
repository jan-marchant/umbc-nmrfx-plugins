package edu.umbc.hhmi.acquisition_plugin;

import org.nmrfx.chemistry.utilities.NvUtil;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.star.Loop;
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
        //String name = saveframe.getName();
        //is this the same?
        //String name = saveframe.getValue("_Sample_condition_list", "Name").replace("^'", "").replace("'$","");
        System.out.println("process managed list");
        //Condition condition = new Condition(name);
        //condition.readSTARSaveFrame(saveframe);
        ResonanceFactory resFactory = PeakList.resFactory();
        String listName = saveframe.getValue("_Spectral_peak_list", "Sf_framecode");
        String sampleLabel = saveframe.getLabelValue("_Spectral_peak_list", "Sample_label").replace("^'", "").replace("'$","");;
        String sampleConditionLabel = saveframe.getOptionalValue("_Spectral_peak_list", "Sample_condition_list_label").replace("^'", "").replace("'$","");;
        String idString = saveframe.getLabelValue("_Spectral_peak_list", "ID");
        String datasetName = saveframe.getLabelValue("_Spectral_peak_list", "Experiment_name");
        String nDimString = saveframe.getValue("_Spectral_peak_list", "Number_of_spectral_dimensions");
        String dataFormat = saveframe.getOptionalValue("_Spectral_peak_list", "Text_data_format");
        String details = saveframe.getOptionalValue("_Spectral_peak_list", "Details");
        String slidable = saveframe.getOptionalValue("_Spectral_peak_list", "Slidable");
        String sampleID = saveframe.getOptionalValue("_Spectral_peak_list","Sample_ID");
        String conditionID = saveframe.getOptionalValue("_Spectral_peak_list","Sample_condition_list_ID");
        String experimentClass = saveframe.getLabelValue("_Spectral_peak_list","Experiment_class");
        String experimentName = saveframe.getOptionalValue("_Spectral_peak_list","Experiment_type").replace("^'", "").replace("'$","");

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
        int id = NvUtil.toInt(idString);

        int nSpectralDim = saveframe.loopCount("_Spectral_dim");
        if (nSpectralDim > nDim) {
            throw new IllegalArgumentException("Too many _Spectral_dim values " + listName + " " + nSpectralDim + " " + nDim);
        }

        //for portability
        Experiment experiment=Experiment.get(experimentName);
        if (experiment==null) {
            experiment=new Experiment(experimentName,experimentClass);
        } else if (!experiment.toCode().equals(experimentClass)) {
            Integer suffix=2;
            while (experiment.get(experimentName+suffix.toString())!=null) {
                suffix++;
            }
            experiment=new Experiment(experimentName+suffix,experimentClass);
        }

        int x=0;
        HashMap<ExpDim,Integer> dimMap=new HashMap<>();
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

        //This fails if sample, condition etc. not already processed
        Acquisition acquisition=new Acquisition();
        acquisition.setDataset(Dataset.getDataset(datasetName));
        acquisition.setSample(Sample.get(sampleLabel));
        acquisition.setCondition(Condition.get(sampleConditionLabel));
        acquisition.setExperiment(experiment);
        //do we need same list ID? Possible for links? Watch out!
        ManagedList peakList=new ManagedList(listName,nDim,acquisition,dimMap,id);
        acquisition.attachManagedList(peakList);
        acquisition.addSaveframe();

        peakList.setSampleLabel(sampleLabel);
        peakList.setSampleConditionLabel(sampleConditionLabel);
        peakList.setDatasetName(datasetName);
        peakList.setDetails(details);
        peakList.setSlideable(slidable.equals("yes"));

        for (int i = 0; i < nSpectralDim; i++) {
            SpectralDim sDim = peakList.getSpectralDim(i);

            String value = null;
            value = saveframe.getValueIfPresent("_Spectral_dim", "Atom_type", i);
            if (value != null) {
                sDim.setAtomType(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Atom_isotope_number", i);
            if (value != null) {
                sDim.setAtomIsotopeValue(NvUtil.toInt(value));
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Spectral_region", i);
            if (value != null) {
                sDim.setSpectralRegion(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Magnetization_linkage", i);
            if (value != null) {
                sDim.setMagLinkage(NvUtil.toInt(value) - 1);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Sweep_width", i);
            if (value != null) {
                sDim.setSw(NvUtil.toDouble(value));
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Spectrometer_frequency", i);
            if (value != null) {
                sDim.setSf(NvUtil.toDouble(value));
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Encoding_code", i);
            if (value != null) {
                sDim.setEncodingCode(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Encoded_source_dimension", i);
            if (value != null) {
                sDim.setEncodedSourceDim(NvUtil.toInt(value) - 1);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Dataset_dimension", i);
            if (value != null) {
                sDim.setDataDim(NvUtil.toInt(value) - 1);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Dimension_name", i);
            if (value != null) {
                sDim.setDimName(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "ID_tolerance", i);
            if (value != null) {
                sDim.setIdTol(NvUtil.toDouble(value));
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Pattern", i);
            if (value != null) {
                sDim.setPattern(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Relation", i);
            if (value != null) {
                sDim.setRelation(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Aliasing", i);
            if (value != null) {
                sDim.setAliasing(value);
            }
            value = saveframe.getValueIfPresent("_Spectral_dim", "Precision", i);
            if (value != null) {
                sDim.setPrecision(NvUtil.toInt(value));
            }
        }

        Loop loop = saveframe.getLoop("_Peak");
        if (loop != null) {
            List<String> idColumn = loop.getColumnAsList("ID");
            List<String> detailColumn = loop.getColumnAsListIfExists("Details");
            List<String> fomColumn = loop.getColumnAsListIfExists("Figure_of_merit");
            List<String> typeColumn = loop.getColumnAsListIfExists("Type");
            List<String> statusColumn = loop.getColumnAsListIfExists("Status");
            List<String> colorColumn = loop.getColumnAsListIfExists("Color");
            List<String> flagColumn = loop.getColumnAsListIfExists("Flag");
            List<String> cornerColumn = loop.getColumnAsListIfExists("Label_corner");

            for (int i = 0, n = idColumn.size(); i < n; i++) {
                int idNum = Integer.parseInt((String) idColumn.get(i));
                ManagedPeak peak = new ManagedPeak(peakList, nDim);
                peak.setIdNum(idNum);
                String value = null;
                if ((value = NvUtil.getColumnValue(fomColumn, i)) != null) {
                    float fom = NvUtil.toFloat(value);
                    peak.setFigureOfMerit(fom);
                }
                if ((value = NvUtil.getColumnValue(detailColumn, i)) != null) {
                    peak.setComment(value);
                }
                if ((value = NvUtil.getColumnValue(typeColumn, i)) != null) {
                    int type = Peak.getType(value);
                    peak.setType(type);
                }
                if ((value = NvUtil.getColumnValue(statusColumn, i)) != null) {
                    int status = NvUtil.toInt(value);
                    peak.setStatus(status);
                }
                if ((value = NvUtil.getColumnValue(colorColumn, i)) != null) {
                    value = value.equals(".") ? null : value;
                    peak.setColor(value);
                }
                if ((value = NvUtil.getColumnValue(flagColumn, i)) != null) {
                    for (int iFlag = 0; iFlag < Peak.NFLAGS; iFlag++) {
                        if (value.length() > iFlag) {
                            peak.setFlag(iFlag, (value.charAt(iFlag) == '1'));
                        } else {
                            peak.setFlag(iFlag, false);
                        }
                    }
                }
                if ((value = NvUtil.getColumnValue(cornerColumn, i)) != null) {
                    peak.setCorner(value);
                }

                peakList.peaks().add(peak);
                peakList.clearIndex();
            }

            loop = saveframe.getLoop("_Peak_general_char");
            if (loop != null) {
                List<String> peakidColumn = loop.getColumnAsList("Peak_ID");
                List<String> methodColumn = loop.getColumnAsList("Measurement_method");
                List<String> intensityColumn = loop.getColumnAsList("Intensity_val");
                List<String> errorColumn = loop.getColumnAsList("Intensity_val_err");
                for (int i = 0, n = peakidColumn.size(); i < n; i++) {
                    String value = null;
                    int idNum = 0;
                    if ((value = NvUtil.getColumnValue(peakidColumn, i)) != null) {
                        idNum = NvUtil.toInt(value);
                    } else {
                        //throw new TclException("Invalid peak id value at row \""+i+"\"");
                        continue;
                    }
                    Peak peak = peakList.getPeakByID(idNum);
                    String method = "height";
                    if ((value = NvUtil.getColumnValue(methodColumn, i)) != null) {
                        method = value;
                    }
                    if ((value = NvUtil.getColumnValue(intensityColumn, i)) != null) {
                        float iValue = NvUtil.toFloat(value);
                        if (method.equals("height")) {
                            peak.setIntensity(iValue);
                        } else if (method.equals("volume")) {
                            // FIXME should set volume/evolume
                            peak.setVolume1(iValue);
                        } else {
                            // FIXME throw error if don't know type, or add new type dynamically?
                            peak.setIntensity(iValue);
                        }
                    }
                    if ((value = NvUtil.getColumnValue(errorColumn, i)) != null) {
                        if (!value.equals(".")) {
                            float iValue = NvUtil.toFloat(value);
                            if (method.equals("height")) {
                                peak.setIntensityErr(iValue);
                            } else if (method.equals("volume")) {
                                // FIXME should set volume/evolume
                                peak.setVolume1Err(iValue);
                            } else {
                                // FIXME throw error if don't know type, or add new type dynamically?
                                peak.setIntensityErr(iValue);
                            }
                        }
                    }
                    // FIXME set error value
                }
            }

            loop = saveframe.getLoop("_Peak_char");
            if (loop == null) {
                throw new ParseException("No \"_Peak_char\" loop");
            }
            if (loop != null) {
                List<String> peakIdColumn = loop.getColumnAsList("Peak_ID");
                List<String> sdimColumn = loop.getColumnAsList("Spectral_dim_ID");
                String[] peakCharStrings = Peak.getSTAR3CharStrings();
                for (int j = 0; j < peakCharStrings.length; j++) {
                    String tag = peakCharStrings[j].substring(peakCharStrings[j].indexOf(".") + 1);
                    if (tag.equals("Sf_ID") || tag.equals("Entry_ID") || tag.equals("Spectral_peak_list_ID")) {
                        continue;
                    }
                    if (tag.equals("Resonance_ID") || tag.equals("Resonance_count")) {
                        continue;
                    }
                    List<String> column = loop.getColumnAsListIfExists(tag);
                    if (column != null) {
                        for (int i = 0, n = column.size(); i < n; i++) {
                            int idNum = Integer.parseInt((String) peakIdColumn.get(i));
                            int sDim = Integer.parseInt((String) sdimColumn.get(i)) - 1;
                            String value = (String) column.get(i);
                            if (!value.equals(".") && !value.equals("?")) {
                                Peak peak = peakList.getPeakByID(idNum);
                                PeakDim peakDim = peak.getPeakDim(sDim);
                                if (peakDim != null) {
                                    peakDim.setAttribute(tag, value);
                                }
                            }
                        }
                    }
                }
                loop = saveframe.getLoop("_Assigned_peak_chem_shift");

                if (loop != null) {
                    List<String> peakidColumn = loop.getColumnAsList("Peak_ID");
                    List<String> spectralDimColumn = loop.getColumnAsList("Spectral_dim_ID");
                    List<String> valColumn = loop.getColumnAsList("Val");
                    List<String> resonanceColumn = loop.getColumnAsList("Resonance_ID");
                    for (int i = 0, n = peakidColumn.size(); i < n; i++) {
                        String value = null;
                        int idNum = 0;
                        if ((value = NvUtil.getColumnValue(peakidColumn, i)) != null) {
                            idNum = NvUtil.toInt(value);
                        } else {
                            //throw new TclException("Invalid peak id value at row \""+i+"\"");
                            continue;
                        }
                        int sDim = 0;
                        long resonanceID = -1L;
                        if ((value = NvUtil.getColumnValue(spectralDimColumn, i)) != null) {
                            sDim = NvUtil.toInt(value) - 1;
                        } else {
                            throw new ParseException("Invalid spectral dim value at row \"" + i + "\"");
                        }
                        if ((value = NvUtil.getColumnValue(valColumn, i)) != null) {
                            NvUtil.toFloat(value);  // fixme shouldn't we use this
                        }
                        if ((value = NvUtil.getColumnValue(resonanceColumn, i)) != null) {
                            resonanceID = NvUtil.toLong(value);
                        }
                        Peak peak = peakList.getPeakByID(idNum);
                        PeakDim peakDim = peak.getPeakDim(sDim);
                        if (resonanceID == -1L) {
                            peakDimsWithoutRes.add(peakDim);
                        } else {
                            Resonance resonance = resFactory.build(resonanceID);
                            resonance.add(peakDim);
                        }
                    }
                } else {
                    System.out.println("No \"Assigned Peak Chem Shift\" loop");
                }
            }
        }
    }
}
