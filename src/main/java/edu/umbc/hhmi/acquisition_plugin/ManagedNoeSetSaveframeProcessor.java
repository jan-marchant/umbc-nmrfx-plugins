package edu.umbc.hhmi.acquisition_plugin;

import org.nmrfx.chemistry.*;
import org.nmrfx.peaks.ManagedList;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.star.Loop;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;
import org.nmrfx.star.SaveframeProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ManagedNoeSetSaveframeProcessor implements SaveframeProcessor {
    private static final Logger log = LoggerFactory.getLogger(ManagedNoeSetSaveframeProcessor.class);

    @Override
    public void process(Saveframe saveframe) throws ParseException, IOException {
        // At the moment this process both "general_distance_constraints2" and "peak_constraint_links"
        System.out.println(String.format("process %s",saveframe.getCategoryName()));
        if (saveframe.getCategoryName().equals("general_distance_constraints2")) {
            processGenDistConstraints2(saveframe);
        } else if (saveframe.getCategoryName().equals("peak_constraint_links")) {
            processPeakConstraintLinks(saveframe);
        }
    }

    public void processGenDistConstraints2(Saveframe saveframe) throws ParseException {
        //NB: resonance ID not currently used, so no need to worry that this is called after cleaning resonances.
        //NB: but would be better if built-in NoeSet did keep track of resonances properly I think
        Loop loop = saveframe.getLoop("_Gen_dist_constraint");
        if (loop == null) {
            throw new ParseException("No \"_Gen_dist_constraint\" loop");
        }
        var compoundMap = MoleculeBase.compoundMap();
        List<String>[] entityAssemblyIDColumns = new ArrayList[2];
        List<String>[] entityIDColumns = new ArrayList[2];
        List<String>[] compIdxIDColumns = new ArrayList[2];
        List<String>[] atomColumns = new ArrayList[2];
        List<String>[] resonanceColumns = new ArrayList[2];
        entityAssemblyIDColumns[0] = loop.getColumnAsList("Entity_assembly_ID_1");
        entityIDColumns[0] = loop.getColumnAsList("Entity_ID_1");
        compIdxIDColumns[0] = loop.getColumnAsList("Comp_index_ID_1");
        atomColumns[0] = loop.getColumnAsList("Atom_ID_1");
        resonanceColumns[0] = loop.getColumnAsList("Resonance_ID_1");
        entityAssemblyIDColumns[1] = loop.getColumnAsList("Entity_assembly_ID_2");
        entityIDColumns[1] = loop.getColumnAsList("Entity_ID_2");
        compIdxIDColumns[1] = loop.getColumnAsList("Comp_index_ID_2");
        atomColumns[1] = loop.getColumnAsList("Atom_ID_2");
        resonanceColumns[1] = loop.getColumnAsList("Resonance_ID_2");
        List<String> constraintIDColumn = loop.getColumnAsList("ID");
        List<String> lowerColumn = loop.getColumnAsList("Distance_lower_bound_val");
        List<String> upperColumn = loop.getColumnAsList("Distance_upper_bound_val");
        List<String> weightColumn = loop.getColumnAsList("Weight");
        List<String> peakListIDColumn = loop.getColumnAsList("Spectral_peak_list_ID");
        List<String> peakIDColumn = loop.getColumnAsList("Spectral_peak_ID");
        Atom[] atoms = new Atom[2];
        SpatialSetGroup[] spSets = new SpatialSetGroup[2];
        String[] resIDStr = new String[2];
        PeakList peakList = null;
        String lastPeakListIDStr = "";

        ManagedNoeSet noeSet = ManagedNoeSetup.addSet(saveframe.getName().substring(5));

        for (int i = 0; i < entityAssemblyIDColumns[0].size(); i++) {
            for (int iAtom = 0; iAtom < 2; iAtom++) {
                spSets[iAtom] = null;
                String iEntity = entityIDColumns[iAtom].get(i);
                String entityAssemblyID = entityAssemblyIDColumns[iAtom].get(i);
                if (iEntity.equals("?")) {
                    continue;
                }
                String iRes = compIdxIDColumns[iAtom].get(i);
                String atomName = atomColumns[iAtom].get(i);
                resIDStr[iAtom] = ".";
                if (resonanceColumns[iAtom] != null) {
                    resIDStr[iAtom] = resonanceColumns[iAtom].get(i);
                }
                if (entityAssemblyID.equals(".")) {
                    entityAssemblyID = "1";
                }
                String mapID = entityAssemblyID + "." + iEntity + "." + iRes;
                Compound compound1 = compoundMap.get(mapID);
                if (compound1 == null) {
                    log.warn("invalid compound in distance constraints saveframe \"{}\"", mapID);
                } else if ((atomName.charAt(0) == 'Q') || (atomName.charAt(0) == 'M')) {
                    Residue residue = (Residue) compound1;
                    Atom[] pseudoAtoms = ((Residue) compound1).getPseudo(atomName);
                    if (pseudoAtoms == null) {
                        log.warn("{} {} {}", residue.getIDNum(), residue.getNumber(), residue.getName());
                        log.warn("invalid pseudo in distance constraints saveframe \"{}\" {}", mapID, atomName);
                    } else {
                        spSets[iAtom] = new SpatialSetGroup(pseudoAtoms);
                    }
                } else {
                    atoms[iAtom] = compound1.getAtomLoose(atomName);
                    if (atoms[iAtom] == null) {
                        throw new ParseException("invalid atom in distance constraints saveframe \"" + mapID + "." + atomName + "\"");
                    }
                    spSets[iAtom] = new SpatialSetGroup(atoms[iAtom].spatialSet);
                }
                if (spSets[iAtom] == null) {
                    throw new ParseException("invalid spatial set in distance constraints saveframe \"" + mapID + "." + atomName + "\"");
                }
            }
            String upperValue = upperColumn.get(i);
            String lowerValue = lowerColumn.get(i);
            String peakListIDStr = peakListIDColumn.get(i);
            String peakID = peakIDColumn.get(i);
            String constraintID = constraintIDColumn.get(i);
            if (!peakListIDStr.equals(lastPeakListIDStr)) {
                if (peakListIDStr.equals(".")) {
                    if (peakList == null) {
                        peakList = new PeakList("gendist", 2);
                    }
                } else {
                    try {
                        int peakListID = Integer.parseInt(peakListIDStr);
                        Optional<PeakList> peakListOpt = PeakList.get(peakListID);
                        if (peakListOpt.isPresent()) {
                            peakList = peakListOpt.get();
                        }
                    } catch (NumberFormatException nFE) {
                        throw new ParseException("Invalid peak list id (not int) \"" + peakListIDStr + "\"");
                    }
                }
            }
            lastPeakListIDStr = peakListIDStr;
            Peak peak;
            if (peakList != null) {
                if (peakID.equals(".")) {
                    peak = null;
                    //peakID = constraintID;
                    //int idNum = Integer.parseInt(peakID);
                    //while ((peak = peakList.getPeak(idNum)) == null) {
                    //    peakList.addPeak();
                    //}
                } else {
                    int idNum = Integer.parseInt(peakID);
                    peak = peakList.getPeakByID(idNum);
                }

                double weight;
                try {
                    weight = Double.parseDouble(weightColumn.get(i));
                } catch(Exception e) {
                    weight = 1.0;
                }
                //NB: Resonances should be set correctly during construction
                ManagedNoe noe = new ManagedNoe(peak, spSets[0], spSets[1], weight);
                //Maybe only do this if peak is null?
                noe.setResonance1(spSets[0].getAnAtom().getResonance());
                noe.setResonance2(spSets[1].getAnAtom().getResonance());
                double upper = 1000000.0;
                if (upperValue.equals(".")) {
                    log.warn("Upper value is a \".\" at line {}", i);
                } else {
                    upper = Double.parseDouble(upperValue);
                }
                noe.setUpper(upper);
                double lower = 1.8;
                if (!lowerValue.equals(".")) {
                    lower = Double.parseDouble(lowerValue);
                }
                noe.setLower(lower);
                noe.setPpmError(1.0);
                noe.setIntensity(Math.pow(upper, -6.0) * 10000.0);
                noe.setVolume(Math.pow(upper, -6.0) * 10000.0);
                noeSet.add(noe);
            }
        }
        //  noeSet.updateNPossible(null);
        noeSet.setCalibratable(false);
    }

    public void processPeakConstraintLinks(Saveframe saveframe) throws ParseException {
        //This should always follow setting up ManagedNoeSets, so should be safe to activate
        //ManagedList listeners as appropriate

        Loop loop = saveframe.getLoop("_Peak_constraint_link");
        if (loop == null) {
            throw new ParseException("No \"_Peak_constraint_link\" loop");
        }
        String noeSetName = saveframe.getValue("_Peak_constraint_link_list", "Name");

        ManagedNoeSet noeSet = ManagedNoeSetup.getNoeSet(noeSetName);
        List<String> constraintIDColumn = new ArrayList<>();
        List<String> constraintListIDColumn = new ArrayList<>();
        List<String> peakListIDColumn = new ArrayList<>();
        List<String> peakIDColumn = new ArrayList<>();
        List<String> peakListNameColumn = new ArrayList<>();

        constraintIDColumn = loop.getColumnAsList("Constraint_ID");
        constraintListIDColumn = loop.getColumnAsList("Constraint_list_ID");
        peakListIDColumn = loop.getColumnAsList("Spectral_peak_list_ID");
        peakIDColumn = loop.getColumnAsList("Peak_ID");
        peakListNameColumn = loop.getColumnAsList("Spectral_Peak_list_Sf_framecode");

        ManagedList peakList;
        ManagedPeak peak;
        ManagedNoe noe;
        for (int i = 0; i < constraintIDColumn.size(); i++) {
            String peakListIDStr = peakListIDColumn.get(i);
            String peakID = peakIDColumn.get(i);
            String constraintID = constraintIDColumn.get(i);
            //NOE idNums start at 1. todo: implement getConstraintById?
            noe = noeSet.get(Integer.parseInt(constraintID)-1);

            int peakListID = Integer.parseInt(peakListIDStr);
            Optional<PeakList> peakListOpt = PeakList.get(peakListID);
            if (peakListOpt.isPresent()) {
                peakList = (ManagedList) peakListOpt.get();
                int idNum = Integer.parseInt(peakID);
                peak = (ManagedPeak) peakList.getPeakByID(idNum);
                if (peak != null) {
                    peak.addNoe(noe);
                    peakList.setNoeSet(noeSet);
                    peakList.setupListener();
                }
            }
        }
    }
}


