package org.nmrfx.chemistry.io;

import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.relax.RelaxationData;
import org.nmrfx.peaks.*;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.project.SubProject;
import org.nmrfx.star.Loop;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.STAR3;
import org.nmrfx.star.Saveframe;
import org.nmrfx.utilities.NvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;
import java.util.Map;

public class SubNMRStarReader extends NMRStarReader {
    //This subclass replaces all references to PeakList.resFactory() with SubProject.resFactory()
    private static final Logger log = LoggerFactory.getLogger(SubNMRStarReader.class);

    public SubNMRStarReader(File starFile, STAR3 star3) {
        super(starFile, star3);
    }

    //Not sure how static method hiding works - so just changing method name
    public static STAR3 readSub(File starFile) throws ParseException {
        FileReader fileReader;
        try {
            fileReader = new FileReader(starFile);
        } catch (FileNotFoundException ex) {
            throw new ParseException("Could not find file " + starFile);
        }
        BufferedReader bfR = new BufferedReader(fileReader);

        STAR3 star = new STAR3(bfR, "star3");

        try {
            star.scanFile();
        } catch (ParseException parseEx) {
            throw new ParseException(parseEx.getMessage() + " " + star.getLastLine());
        }
        SubNMRStarReader reader = new SubNMRStarReader(starFile, star);
        reader.process();
        return star;
    }

    @Override
    public void process(String[] argv) throws ParseException, IllegalArgumentException {
        if ((argv.length != 0) && (argv.length != 3)) {
            throw new IllegalArgumentException("?shifts fromSet toSet?");
        }
        log.debug("nSave " + star3.getSaveFrameNames());
        //fixme: Better if each project has its own Resonance factory
        ResonanceFactory resFactory = SubProject.resFactory();
        if (argv.length == 0) {
            hasResonances = false;
            var compoundMap = MoleculeBase.compoundMap();
            compoundMap.clear();
            buildExperiments();
            log.debug("process molecule");
            buildMolecule();
            log.debug("process peak lists");
            buildPeakLists();
            log.debug("process resonance lists");
            buildResonanceLists();
            log.debug("process chem shifts");
            buildChemShifts(-1, 0);
            log.debug("process conformers");
            buildConformers();
            log.debug("process dist constraints");
            buildGenDistConstraints();
            log.debug("process angle constraints");
            buildDihedralConstraints();
            log.debug("process rdc constraints");
            buildRDCConstraints();
            log.debug("process NOE");
            buildNOE();
            for (var relaxType : RelaxationData.relaxTypes.values()) {
                if ((relaxType != RelaxationData.relaxTypes.NOE) && (relaxType != RelaxationData.relaxTypes.S2)) {
                    log.debug("process {}", relaxType);
                    buildRelaxation(relaxType);
                }
            }
            log.debug("process Order");
            buildOrder();
            log.debug("process runabout");
            buildRunAbout();
            log.debug("process paths");
            buildPeakPaths();
            log.debug("clean resonances");
            resFactory.clean();

            ProjectBase.processExtraSaveFrames(star3);
            log.debug("process done");
        } else if ("shifts".startsWith(argv[0])) {
            int fromSet = Integer.parseInt(argv[1]);
            int toSet = Integer.parseInt(argv[2]);
            buildChemShifts(fromSet, toSet);
        }
    }

    @Override
    public void buildResonanceLists() throws ParseException {
        var compoundMap = MoleculeBase.compoundMap();
        for (Saveframe saveframe : star3.getSaveFrames().values()) {
            if (saveframe.getCategoryName().equals("resonance_linker")) {
                hasResonances = true;
                log.debug("process resonances {}", saveframe.getName());
                SubNMRStarReader.processSTAR3ResonanceList(this, saveframe, compoundMap);
            }
        }
    }

    @Override
    public void addMissingResonances() {
        ResonanceFactory resFactory = SubProject.resFactory();
        peakDimsWithoutResonance.forEach((peakDim) -> {
            Resonance resonance = resFactory.build();
            resonance.add(peakDim);
        });
    }

    @Override
    public void processSTAR3PeakList(Saveframe saveframe) throws ParseException {
        ResonanceFactory resFactory = SubProject.resFactory();
        String listName = saveframe.getValue("_Spectral_peak_list", "Sf_framecode");
        String id = saveframe.getValue("_Spectral_peak_list", "ID");
        String sampleLabel = saveframe.getLabelValue("_Spectral_peak_list", "Sample_label");
        String sampleConditionLabel = saveframe.getOptionalLabelValue("_Spectral_peak_list", "Sample_condition_list_label");
        String datasetName = saveframe.getLabelValue("_Spectral_peak_list", "Experiment_name");
        String nDimString = saveframe.getValue("_Spectral_peak_list", "Number_of_spectral_dimensions");
        String dataFormat = saveframe.getOptionalValue("_Spectral_peak_list", "Text_data_format");
        String expType = saveframe.getOptionalValue("_Spectral_peak_list", "Experiment_type");
        String details = saveframe.getOptionalValue("_Spectral_peak_list", "Details");
        String slidable = saveframe.getOptionalValue("_Spectral_peak_list", "Slidable");
        String scaleStr = saveframe.getOptionalValue("_Spectral_peak_list", "Scale");

        if (dataFormat.equals("text")) {
            log.warn("Peak list is in text format, skipping list");
            log.warn(details);
            return;
        }
        if (nDimString.equals("?")) {
            return;
        }
        if (nDimString.equals(".")) {
            return;
        }
        int nDim = NvUtil.toInt(nDimString);

        PeakList peakList = new PeakList(listName, nDim, NvUtil.toInt(id));

        int nSpectralDim = saveframe.loopCount("_Spectral_dim");
        if (nSpectralDim > nDim) {
            throw new IllegalArgumentException("Too many _Spectral_dim values " + listName + " " + nSpectralDim + " " + nDim);
        }

        peakList.setSampleLabel(sampleLabel);
        peakList.setSampleConditionLabel(sampleConditionLabel);
        peakList.setDatasetName(datasetName);
        peakList.setDetails(details);
        peakList.setExperimentType(expType);
        peakList.setSlideable(slidable.equals("yes"));
        if (scaleStr.length() > 0) {
            peakList.setScale(NvUtil.toDouble(scaleStr));
        }

        for (int i = 0; i < nSpectralDim; i++) {
            SpectralDim sDim = peakList.getSpectralDim(i);

            String value;
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
                int idNum = Integer.parseInt(idColumn.get(i));
                Peak peak = new Peak(peakList, nDim);
                peak.setIdNum(idNum);
                String value;
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
                peakList.addPeakWithoutResonance(peak);
            }

            loop = saveframe.getLoop("_Peak_general_char");
            if (loop != null) {
                List<String> peakidColumn = loop.getColumnAsList("Peak_ID");
                List<String> methodColumn = loop.getColumnAsList("Measurement_method");
                List<String> intensityColumn = loop.getColumnAsList("Intensity_val");
                List<String> errorColumn = loop.getColumnAsList("Intensity_val_err");
                for (int i = 0, n = peakidColumn.size(); i < n; i++) {
                    String value;
                    int idNum;
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
                        switch (method) {
                            case "height":
                                peak.setIntensity(iValue);
                                break;
                            case "volume":
                                // FIXME should set volume/evolume
                                peak.setVolume1(iValue);
                                break;
                            default:
                                // FIXME throw error if don't know type, or add new type dynamically?
                                peak.setIntensity(iValue);
                                break;
                        }
                    }
                    if ((value = NvUtil.getColumnValue(errorColumn, i)) != null) {
                        if (!value.equals(".")) {
                            float iValue = NvUtil.toFloat(value);
                            switch (method) {
                                case "height":
                                    peak.setIntensityErr(iValue);
                                    break;
                                case "volume":
                                    // FIXME should set volume/evolume
                                    peak.setVolume1Err(iValue);
                                    break;
                                default:
                                    // FIXME throw error if don't know type, or add new type dynamically?
                                    peak.setIntensityErr(iValue);
                                    break;
                            }
                        }
                    }
                    // FIXME set error value
                }
            }

            loop = saveframe.getLoop("_Peak_char");
            if (loop == null) {
                throw new ParseException("No \"_Peak_char\" loop");
            } else {
                List<String> peakIdColumn = loop.getColumnAsList("Peak_ID");
                List<String> sdimColumn = loop.getColumnAsList("Spectral_dim_ID");
                String[] peakCharStrings = Peak.getSTAR3CharStrings();
                for (String peakCharString : peakCharStrings) {
                    String tag = peakCharString.substring(peakCharString.indexOf(".") + 1);
                    if (tag.equals("Sf_ID") || tag.equals("Entry_ID") || tag.equals("Spectral_peak_list_ID")) {
                        continue;
                    }
                    if (tag.equals("Resonance_ID") || tag.equals("Resonance_count")) {
                        continue;
                    }
                    List<String> column = loop.getColumnAsListIfExists(tag);
                    if (column != null) {
                        for (int i = 0, n = column.size(); i < n; i++) {
                            int idNum = Integer.parseInt(peakIdColumn.get(i));
                            int sDim = Integer.parseInt(sdimColumn.get(i)) - 1;
                            String value = column.get(i);
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
                        String value;
                        int idNum;
                        if ((value = NvUtil.getColumnValue(peakidColumn, i)) != null) {
                            idNum = NvUtil.toInt(value);
                        } else {
                            //throw new TclException("Invalid peak id value at row \""+i+"\"");
                            continue;
                        }
                        int sDim;
                        long resonanceID = -1;
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
                        if (resonanceID != -1L) {
                            Resonance resonance = resFactory.build(resonanceID);
                            resonance.add(peakDim);
                        } else {
                            peakDimsWithoutResonance.add(peakDim);
                        }
                    }
                } else {
                    log.info("No \"Assigned Peak Chem Shift\" loop");
                }
            }
            loop = saveframe.getLoop("_Peak_coupling");
            if (loop != null) {
                List<Integer> peakIdColumn = loop.getColumnAsIntegerList("Peak_ID", null);
                List<Integer> sdimColumn = loop.getColumnAsIntegerList("Spectral_dim_ID", null);
                List<Double> couplingColumn = loop.getColumnAsDoubleList("Coupling_val", null);
                List<Double> strongCouplingColumn = loop.getColumnAsDoubleList("Strong_coupling_effect_val", null);
                List<Double> intensityColumn = loop.getColumnAsDoubleList("Intensity_val", null);
                List<String> couplingTypeColumn = loop.getColumnAsList("Type");
                int from = 0;
                int to;
                for (int i = 0; i < peakIdColumn.size(); i++) {
                    int currentID = peakIdColumn.get(from);
                    int currentDim = sdimColumn.get(i) - 1;
                    if ((i == (peakIdColumn.size() - 1))
                            || (peakIdColumn.get(i + 1) != currentID)
                            || (sdimColumn.get(i + 1) - 1 != currentDim)) {
                        Peak peak = peakList.getPeakByID(currentID);
                        to = i + 1;
                        Multiplet multiplet = peak.getPeakDim(currentDim).getMultiplet();
                        CouplingPattern couplingPattern = new CouplingPattern(multiplet,
                                couplingColumn.subList(from, to),
                                couplingTypeColumn.subList(from, to),
                                strongCouplingColumn.subList(from, to),
                                intensityColumn.get(from)
                        );
                        multiplet.setCoupling(couplingPattern);
                        from = to;
                    }
                }
            }
            processTransitions(saveframe, peakList);
        }
    }

    @Override
    public void processChemicalShifts(Saveframe saveframe, int ppmSet) throws ParseException {
        Loop loop = saveframe.getLoop("_Atom_chem_shift");
        if (loop != null) {
            boolean refMode = false;
            if (ppmSet < 0) {
                refMode = true;
                ppmSet = -1 - ppmSet;
            }
            var compoundMap = MoleculeBase.compoundMap();
            // map may be empty if we're importing shifts into new project
            // without reading star file with entity
            if (compoundMap.isEmpty()) {
                if (molecule == null) {
                    molecule = MoleculeFactory.getActive();
                }
                molecule.buildCompoundMap();
            }
            List<String> entityAssemblyIDColumn = loop.getColumnAsList("Entity_assembly_ID");
            List<String> entityIDColumn = loop.getColumnAsList("Entity_ID");
            List<String> compIdxIDColumn = loop.getColumnAsList("Comp_index_ID");
            List<String> atomColumn = loop.getColumnAsList("Atom_ID");
            List<String> typeColumn = loop.getColumnAsList("Atom_type");
            List<String> valColumn = loop.getColumnAsList("Val");
            List<String> valErrColumn = loop.getColumnAsList("Val_err");
            List<String> resColumn = loop.getColumnAsList("Resonance_ID");
            List<Integer> ambigColumn = loop.getColumnAsIntegerList("Ambiguity_code", -1);
            ResonanceFactory resFactory = SubProject.resFactory();
            for (int i = 0; i < entityAssemblyIDColumn.size(); i++) {
                String iEntity = entityIDColumn.get(i);
                String entityAssemblyID = entityAssemblyIDColumn.get(i);
                if (iEntity.equals("?")) {
                    continue;
                }
                String iRes = compIdxIDColumn.get(i);
                String atomName = atomColumn.get(i);
                String atomType = typeColumn.get(i);
                String value = valColumn.get(i);
                String valueErr = valErrColumn.get(i);
                String resIDStr = ".";
                if (resColumn != null) {
                    resIDStr = resColumn.get(i);
                }
                if (entityAssemblyID.equals(".")) {
                    entityAssemblyID = "1";
                }
                String mapID = entityAssemblyID + "." + iEntity + "." + iRes;
                Compound compound = compoundMap.get(mapID);
                if (compound == null) {
                    log.warn("invalid compound in assignments saveframe \"{}\"", mapID);
                    continue;
                }
                Atom atom = compound.getAtomLoose(atomName);
                if (atom == null) {
                    if (atomName.startsWith("H")) {
                        atom = compound.getAtom(atomName + "1");
                    }
                }
                if (atom == null) {
                    atom = Atom.genAtomWithElement(atomName, atomType);
                    compound.addAtom(atom);
                }
                if (atom == null) {
                    throw new ParseException("invalid atom in assignments saveframe \"" + mapID + "." + atomName + "\"");
                }
                SpatialSet spSet = atom.spatialSet;
                if (spSet == null) {
                    throw new ParseException("invalid spatial set in assignments saveframe \"" + mapID + "." + atomName + "\"");
                }
                try {
                    if (refMode) {
                        spSet.setRefPPM(ppmSet, Double.parseDouble(value));
                        if (!valueErr.equals(".")) {
                            spSet.setRefError(ppmSet, Double.parseDouble(valueErr));
                        }
                    } else {
                        spSet.setPPM(ppmSet, Double.parseDouble(value), false);
                        spSet.getPPM(ppmSet).setAmbigCode(ambigColumn.get(i));
                        if (!valueErr.equals(".")) {
                            spSet.setPPM(ppmSet, Double.parseDouble(valueErr), true);
                        }
                    }
                } catch (NumberFormatException nFE) {
                    throw new ParseException("Invalid chemical shift value (not double) \"" + value + "\" error \"" + valueErr + "\"");
                }
                if (!refMode && hasResonances && !resIDStr.equals(".")) {
                    long resID = Long.parseLong(resIDStr);
                    if (resID >= 0) {
                        AtomResonance resonance = (AtomResonance) resFactory.get(resID);
                        if (resonance == null) {
                            throw new ParseException("atom elem resonance " + resIDStr + ": invalid resonance");
                        }
                        atom.setResonance(resonance);
                        resonance.setAtom(atom);
                    }
                }
            }
        }
    }


    //From AtomResonance
    public static void processSTAR3ResonanceList(final NMRStarReader nmrStar,
                                                 Saveframe saveframe, Map<String, Compound> compoundMap) throws ParseException {
        // fixme unused String listName = saveframe.getValue(interp,"_Resonance_linker_list","Sf_framecode");
        // FIXME String details = saveframe.getValue(interp,"_Resonance_linker_list","Details");

        //  FIXME Should have Resonance lists PeakList peakList = new PeakList(listName,nDim);
        Loop loop = saveframe.getLoop("_Resonance");
        if (loop == null) {
            throw new ParseException("No \"_Resonance\" loop");
        }
        List<String> idColumn = loop.getColumnAsList("ID");
        List<String> nameColumn = loop.getColumnAsList("Name");
        List<String> resSetColumn = loop.getColumnAsList("Resonance_set_ID");
        // fixme unused ArrayList ssColumn = loop.getColumnAsList("Spin_system_ID");
        ResonanceFactory resFactory = SubProject.resFactory();
        for (int i = 0, n = idColumn.size(); i < n; i++) {
            String value;
            long idNum;
            if ((value = org.nmrfx.chemistry.utilities.NvUtil.getColumnValue(idColumn, i)) != null) {
                idNum = org.nmrfx.chemistry.utilities.NvUtil.toLong(value);
            } else {
                //throw new TclException(interp,"Invalid id \""+value+"\"");
                continue;
            }

            AtomResonance resonance = (AtomResonance) resFactory.get(idNum);
            if (resonance == null) {
                resonance = (AtomResonance) resFactory.build(idNum);
            }
            if ((value = org.nmrfx.chemistry.utilities.NvUtil.getColumnValue(nameColumn, i)) != null) {
                resonance.setName(value);
            }
//            if ((value = NvUtil.getColumnValue(resSetColumn, i)) != null) {
//                long resSet = NvUtil.toLong(value);
//                ResonanceSet resonanceSet = ResonanceSet.get(resSet);
//                if (resonanceSet == null) {
//                    resonanceSet = ResonanceSet.newInstance(resSet);
//                }
//                resonanceSet.addResonance(resonance);
//            }
            /* FIXME handle spinSystem
             if ((value = NvUtil.getColumnValue(ssColumn,i)) != null) {
             long spinSystem = NvUtil.toLong(interp,value);
             resonance.setSpinSystem(spinSystem);
             }
             */
        }

        loop = saveframe.getLoop("_Resonance_assignment");
        if (loop != null) {
            List<String> resSetIDColumn = loop.getColumnAsList("Resonance_set_ID");
            List<String> entityAssemblyIDColumn = loop.getColumnAsList("Entity_assembly_ID");
            List<String> entityIDColumn = loop.getColumnAsList("Entity_ID");
            List<String> compIdxIDColumn = loop.getColumnAsList("Comp_index_ID");
            List<String> compIDColumn = loop.getColumnAsList("Comp_ID");
            List<String> atomIDColumn = loop.getColumnAsList("Atom_ID");
            // fixme unused ArrayList atomSetIDColumn = loop.getColumnAsList("Atom_set_ID");
            for (int i = 0, n = resSetIDColumn.size(); i < n; i++) {
                String value;
                long idNum = 0;
                if ((value = org.nmrfx.chemistry.utilities.NvUtil.getColumnValue(resSetIDColumn, i)) != null) {
                    idNum = org.nmrfx.chemistry.utilities.NvUtil.toLong(value);
                } else {
                    //throw new TclException("Invalid peak id value at row \""+i+"\"");
                    continue;
                }
//                ResonanceSet resonanceSet = ResonanceSet.get(idNum);
//                if (resonanceSet == null) {
//                    continue;
//                }
                String atomName = "";
                String iRes = "";
                String entityAssemblyID = "";
                String entityID = "";
                if ((value = org.nmrfx.chemistry.utilities.NvUtil.getColumnValue(entityAssemblyIDColumn, i)) != null) {
                    entityAssemblyID = value;
                }
                if (entityAssemblyID.equals("")) {
                    entityAssemblyID = "1";
                }
                if ((value = org.nmrfx.chemistry.utilities.NvUtil.getColumnValue(entityIDColumn, i)) != null) {
                    entityID = value;
                } else {
                    throw new ParseException("No entity ID");
                }
                if ((value = org.nmrfx.chemistry.utilities.NvUtil.getColumnValue(compIdxIDColumn, i)) != null) {
                    iRes = value;
                } else {
                    throw new ParseException("No compound ID");
                }
                if ((value = org.nmrfx.chemistry.utilities.NvUtil.getColumnValue(atomIDColumn, i)) != null) {
                    atomName = value;
                } else {
                    throw new ParseException("No atom ID");
                }
                // fixme if ((value = NvUtil.getColumnValue(atomSetIDColumn,i)) != null) {
                // fixme unused atomSetNum = NvUtil.toLong(interp,value);
                //}

                String mapID = entityAssemblyID + "." + entityID + "." + iRes;
                Compound compound = compoundMap.get(mapID);
                if (compound == null) {
                    log.warn("invalid compound in assignments saveframe \"{}\"", mapID);
                    continue;
                }
                Atom atom = compound.getAtomLoose(atomName);
                if (atom == null) {
                    if (atomName.equals("H")) {
                        atom = compound.getAtom(atomName + "1");
                    }
                }
                if (atom == null) {
                    log.warn("invalid atom in assignments saveframe \"{}.{}\"", mapID, atomName);
                } else {
//                    resonance.setAtom(atom);
                }
            }
        }
    }




}
