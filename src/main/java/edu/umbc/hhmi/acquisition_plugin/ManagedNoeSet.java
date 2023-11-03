//Temporary until I get some changes in NoeSet
/*
 * NMRFx Structure : A Program for Calculating Structures
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package edu.umbc.hhmi.acquisition_plugin;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.commons.collections4.BidiMap;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.constraints.Constraint;
import org.nmrfx.chemistry.constraints.ConstraintSet;
import org.nmrfx.chemistry.constraints.MolecularConstraints;
import org.nmrfx.peaks.ManagedList;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.project.ProjectUtilities;
import org.nmrfx.project.SubProject;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.SaveframeWriter;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.rna.InteractionType;
import org.nmrfx.structure.rna.SSGen;
import org.nmrfx.utils.GUIUtils;
import org.python.modules.math;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.Map.Entry;

public class ManagedNoeSet implements ConstraintSet, Iterable<Constraint>, SaveframeWriter, Comparable<ManagedNoeSet> {

    private static final double MIN_DIST = 5.5;
    static HashMap<ProjectBase, HashMap<String, ManagedNoeSet>> projectNoeSetsMap= new HashMap<>();
    private final MolecularConstraints molecularConstraints;
    private HashMap<Integer,ManagedNoe> noeMap = new HashMap<>();

    private final ObservableList<ManagedNoe> constraints = FXCollections.observableArrayList();
    private final Map<Peak, List<ManagedNoe>> peakMap = new TreeMap<>();
    private final String name;
    public static Peak lastPeakWritten = null;
    public static int memberID = 0;
    public static int ID = 0;
    private boolean calibratable = true;
    private boolean dirty = true;
    public Set<ManagedList> associatedLists = new HashSet<>();
    private final int setId;

    private final static HashMap<ProjectBase,Integer> projectSaveFramesAdded = new HashMap<>();
    private final static HashMap<ProjectBase,Integer> projectSaveFramesWritten = new HashMap<>();

    static public Integer getSaveFramesAdded() {
        return getSaveFramesAdded(ProjectBase.getActive());
    }

    static public Integer getSaveFramesWritten() {
        return getSaveFramesWritten(ProjectBase.getActive());
    }

    static public Integer getSaveFramesAdded(ProjectBase project) {
        return projectSaveFramesAdded.computeIfAbsent(project, k -> 0);
    }

    static public Integer getSaveFramesWritten(ProjectBase project) {
        Integer saveFramesWritten = projectSaveFramesWritten.get(project);
        if (saveFramesWritten == null) {
            saveFramesWritten = 0;
            projectSaveFramesAdded.put(project,saveFramesWritten);
        }
        return saveFramesWritten;
    }


    private ManagedNoeSet(MolecularConstraints molecularConstraints,
                          String name) {
        this.name = name;
        this.molecularConstraints = molecularConstraints;
        ProjectBase.getActive().addSaveframe(this);
        ProjectUtilities.sortExtraSaveFrames();
        setId = getSaveFramesAdded()+1;
        projectSaveFramesAdded.put(ProjectBase.getActive(),setId);
    }

    public static ManagedNoeSet newSet(MolecularConstraints molecularConstraints,
                                       String name) {
        return new ManagedNoeSet(molecularConstraints, name);
    }

    static public HashMap<String, ManagedNoeSet> getManagedNoeSetsMap() {
        return getManagedNoeSetsMap(ProjectBase.getActive());
    }

    static public HashMap<String, ManagedNoeSet> getManagedNoeSetsMap(ProjectBase project) {
        return projectNoeSetsMap.computeIfAbsent(project, k -> new HashMap<>());
    }

    static public ManagedNoeSet getManagedNoeSet(String name) {
        return getManagedNoeSetsMap().get(name);
    }

    public static void doStartup() {
        //Can't add an noe set without an active molecule
        //NoeSetup.addSet("default");
        ManagedNoeSetSaveframeProcessor managedNoeSetSaveframeProcessor = new ManagedNoeSetSaveframeProcessor();
        ProjectBase.addSaveframeProcessor("general_distance_constraints2", managedNoeSetSaveframeProcessor);
        ProjectBase.addSaveframeProcessor("peak_constraint_links", managedNoeSetSaveframeProcessor);
    }

    public static ManagedNoeSet addSet(String name) {
        MolecularConstraints molConstr = Molecule.getActive().getMolecularConstraints();
        ManagedNoeSet noeSet = newSet(molConstr,name);
        getManagedNoeSetsMap().put(name, noeSet);
        //ACTIVE_SET = noeSet;
        return noeSet;
    }

    public static ManagedNoeSet addSet(String name, ProjectBase project) {
        MolecularConstraints molConstr = project.getActiveMolecule().getMolecularConstraints();
        ManagedNoeSet noeSet = newSet(molConstr,name);
        getManagedNoeSetsMap(project).put(name, noeSet);
        //ACTIVE_SET = noeSet;
        return noeSet;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getCategory() {
        return "general_distance_constraints2";
    }

    @Override
    public String getListType() {
        return "_Gen_dist_constraint_list";
    }

    @Override
    public String getType() {
        return "NOE";
    }

    @Override
    public int getSize() {
        return constraints.size();
    }

    @Override
    public void clear() {
        constraints.clear();
        peakMap.clear();
        noeMap.clear();
    }

    @Override
    public void add(Constraint constraint) {
        ManagedNoe noe = (ManagedNoe) constraint;
        noe.setID(constraints.size());
        noeMap.put(constraints.size(),noe);
        constraints.add(noe);
        noe.setNoeSet(this);
        if (noe.getPeak() != null) {
            List<ManagedNoe> noeList = getConstraintsForPeak(noe.getPeak());
            noeList.add(noe);
        }
        dirty = true;
    }

    public ObservableList<ManagedNoe> getConstraints() {
        return constraints;
    }

    @Override
    public ManagedNoe get(int i) {
        return constraints.get(i);
    }

    @Override
    public MolecularConstraints getMolecularConstraints() {
        return molecularConstraints;
    }

    @Override
    public Iterator iterator() {
        return constraints.iterator();
    }

    public List<ManagedNoe> getConstraintsForPeak(Peak peak) {
        List<ManagedNoe> noeList = new ArrayList<>();
        if (peak != null) {
            noeList = peakMap.computeIfAbsent(peak, k -> new ArrayList<>());
        }
        return noeList;
    }

    public Set<Entry<Peak, List<ManagedNoe>>> getPeakMapEntries() {
        return peakMap.entrySet();
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void setDirty() {
        dirty = true;
    }

    public void setDirty(boolean state) {
        dirty = state;
    }

    public boolean isCalibratable() {
        return calibratable;
    }
    public void setCalibratable(final boolean state) {
        calibratable = state;
    }

    //NB: Added Weight
    static String[] noeLoopStrings = {
            "_Gen_dist_constraint.ID",
            "_Gen_dist_constraint.Member_ID",
            "_Gen_dist_constraint.Member_logic_code",
            "_Gen_dist_constraint.Assembly_atom_ID_1",
            "_Gen_dist_constraint.Entity_assembly_ID_1",
            "_Gen_dist_constraint.Entity_ID_1",
            "_Gen_dist_constraint.Comp_index_ID_1",
            "_Gen_dist_constraint.Seq_ID_1",
            "_Gen_dist_constraint.Comp_ID_1",
            "_Gen_dist_constraint.Atom_ID_1",
            "_Gen_dist_constraint.Atom_type_1",
            "_Gen_dist_constraint.Atom_isotope_number_1",
            "_Gen_dist_constraint.Resonance_ID_1",
            "_Gen_dist_constraint.Assembly_atom_ID_2",
            "_Gen_dist_constraint.Entity_assembly_ID_2",
            "_Gen_dist_constraint.Entity_ID_2",
            "_Gen_dist_constraint.Comp_index_ID_2",
            "_Gen_dist_constraint.Seq_ID_2",
            "_Gen_dist_constraint.Comp_ID_2",
            "_Gen_dist_constraint.Atom_ID_2",
            "_Gen_dist_constraint.Atom_type_2",
            "_Gen_dist_constraint.Atom_isotope_number_2",
            "_Gen_dist_constraint.Resonance_ID_2",
            "_Gen_dist_constraint.Intensity_val",
            "_Gen_dist_constraint.Intensity_lower_val_err",
            "_Gen_dist_constraint.Intensity_upper_val_err",
            "_Gen_dist_constraint.Distance_val",
            "_Gen_dist_constraint.Distance_lower_bound_val",
            "_Gen_dist_constraint.Distance_upper_bound_val",
            "_Gen_dist_constraint.Contribution_fractional_val",
            "_Gen_dist_constraint.Weight",
            "_Gen_dist_constraint.Spectral_peak_ID",
            "_Gen_dist_constraint.Spectral_peak_list_ID",
            "_Gen_dist_constraint.Entry_ID",
            "_Gen_dist_constraint.Gen_dist_constraint_list_ID",};

    @Override
    public String[] getLoopStrings() {
        return noeLoopStrings;
    }

    @Override
    public void resetWriting() {
        memberID = -1;
        lastPeakWritten = null;
        ID = 0;
    }


    @Override
    public void write(Writer chan) throws ParseException, IOException {

        if (getSaveFramesWritten() == 0) {
            chan.write("\n\n");
            chan.write("    ####################################\n");
            chan.write("    #         Managed NOE Sets         #\n");
            chan.write("    ####################################\n");
            chan.write("\n\n");
        }


        String saveFrameName = getName();
        String saveFrameCategory = getCategory();
        String thisCategory = getListType();
        String constraintType = getType();

        chan.write("save_" + saveFrameName + "\n");

        chan.write(thisCategory + ".Sf_category    ");
        chan.write(saveFrameCategory + "\n");

        chan.write(thisCategory + ".Sf_framecode   ");
        chan.write(saveFrameName + "\n");

        chan.write(thisCategory + ".Constraint_type   ");
        chan.write('\'' + constraintType + "'\n");

        chan.write(thisCategory + ".Details        ");
        chan.write(".\n");

        chan.write("\n");

        String[] loopStrings = getLoopStrings();
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
        for (Constraint constraint : this) {
            if (constraint == null) {
                throw new ParseException("writeConstraints: constraint null at ");
            }
            chan.write(constraint.toSTARString() + "\n");
        }
        chan.write("stop_\n");
        chan.write("\n");

        chan.write("save_\n\n");

        for (ManagedList managedList : associatedLists) {
            managedList.writePeakConstraintLinks(chan);
        }

        Integer count = getSaveFramesWritten();
        projectSaveFramesWritten.put(ProjectBase.getActive(),count+1);

        if (getSaveFramesWritten().equals(getSaveFramesAdded())) {
            chan.write("\n\n");
            projectSaveFramesWritten.put(ProjectBase.getActive(),0);
            resetWriting();
        }
    }

    public int getId() {
        return ID;
    }
    @Override
    public int compareTo(ManagedNoeSet o) {
        return getId() - o.getId();
    }

    public int getSetId() {
        return setId;
    }

    public boolean noeExists (Atom atom1, Atom atom2) {
        for (ManagedNoe noe : getConstraints()) {
            if (atom1 == noe.spg1.getAnAtom() && atom2 == noe.spg2.getAnAtom() ||
                    atom2 == noe.spg1.getAnAtom() && atom1 == noe.spg2.getAnAtom()) {
                return true;
            }
        }
        return false;
    }

    public void generateNOEsByAttributes() {
        Molecule mol = (Molecule) getMolecularConstraints().molecule;
        //looks like molecule can't be null?
        /*
        if (mol==null) {
            noeSet.molecule=Molecule.getActive();
            mol=noeSet.molecule;
        }

         */
        String vienna = mol.getDotBracket();
        if (Objects.equals(vienna, "")) {
            GUIUtils.warn("No Vienna","Please enter secondary structure before running.");
            return;
        }
        SSGen ss = new SSGen(mol, vienna);
        //iterate through every combination of two residues
        List<Residue> rnaResidues = new ArrayList<>();
        for (Polymer polymer : mol.getPolymers()) {
            if (polymer.isRNA()) {
                rnaResidues.addAll(polymer.getResidues());
            }
        }
        int start=0;
        for (Residue residue : rnaResidues) {
            for (int i = start;i<rnaResidues.size();i++) {
                Residue residue2=rnaResidues.get(i);
                String iType = InteractionType.determineType(residue,residue2);

                if (iType==null) {continue;}
                List<ResidueDistances> rDists = new ArrayList<>();
                rDists.add(new ResidueDistances(iType,""+residue.getOneLetter(),""+residue2.getOneLetter()));
                rDists.add(new ResidueDistances(iType,"r",""+residue2.getOneLetter()));
                rDists.add(new ResidueDistances(iType,""+residue.getOneLetter(),"r"));
                rDists.add(new ResidueDistances(iType,"r","r"));

                for (ResidueDistances distance : rDists) {

                    int index = ResidueDistances.distancesList.indexOf(distance);
                    if (index != -1) {
                        for (Map.Entry<String[], double[]> entry : ResidueDistances.distancesList.get(index).distances.entrySet()) {
                            double dist = entry.getValue()[0] / entry.getValue()[1];
                            if (dist < MIN_DIST && entry.getValue()[1] > 10) {
                                //add new NOE!!
                                String aString1 = entry.getKey()[0];
                                String aString2 = entry.getKey()[1];
                                Atom atom1 = residue.getAtom(aString1);
                                Atom atom2 = residue2.getAtom(aString2);
                                if (atom1==null || atom2 == null) {continue;}
                                if (!noeExists(atom1, atom2)) {
                                    if (atom1.getResonance() == null) {
                                        atom1.setResonance((AtomResonance) SubProject.resFactory().build());
                                    }
                                    if (atom2.getResonance() == null) {
                                        atom2.setResonance((AtomResonance) SubProject.resFactory().build());
                                    }
                                    //should scale be set based on dist ?
                                    ManagedNoe noe = new ManagedNoe(null, atom1.getSpatialSet(), atom2.getSpatialSet(), 1.0);
                                    noe.setResonance1(atom1.getResonance());
                                    noe.setResonance2(atom2.getResonance());
                                    double scaleConst = 100.0/ math.pow(2.0,-6);
                                    noe.setIntensity(math.pow(dist, -6)*scaleConst);
                                    add(noe);
                                }
                            }
                        }
                    }
                }
            }
            start++;
        }
    }

    public void transferNoes(ManagedNoeSet fromSet, BidiMap<Entity, Entity> map) {
        //Should we set chemical shifts here? I say no - that can be done explicitly
        //only activate button if there's a map?
        if (map != null && map.size()>0) {
            for (ManagedNoe noe : fromSet.getConstraints()) {
                //don't really understand the logic here - but at least for ManagedNoes this should be OK
                Atom a1 = noe.spg1.getAnAtom();
                Atom a2 = noe.spg2.getAnAtom();
                Entity toEntity1;
                Entity toEntity2;
                Atom toAtom1;
                Atom toAtom2;
                //I guess this is always going to be true?
                if (a1.getEntity() instanceof Residue && a2.getEntity() instanceof Residue) {
                    toEntity1 = map.getKey(a1.getEntity());
                    toEntity2 = map.getKey(a2.getEntity());
                } else {
                    continue;
                }
                if (toEntity1 instanceof Residue
                        && toEntity1.getName().equals(a1.getEntity().getName())
                        && toEntity2 instanceof Residue
                        && toEntity2.getName().equals(a2.getEntity().getName())) {
                    toAtom1 = ((Residue) toEntity1).getAtom(a1.getName());
                    toAtom2 = ((Residue) toEntity2).getAtom(a2.getName());
                } else {
                    continue;
                }

                if (toAtom1.getSpatialSet() != null && toAtom2.getSpatialSet() != null) {
                    if (!noeExists(toAtom1, toAtom2)) {
                        //might want to worry about the scale?
                        ManagedNoe newNoe = new ManagedNoe(null, toAtom1.getSpatialSet(), toAtom2.getSpatialSet(), noe.getScale());
                        add(newNoe);
                        System.out.println("Added NOE from " + toAtom1.getFullName() + " to " + toAtom2.getFullName());
                    } else {
                        System.out.println("NOE already exists: " + toAtom1.getFullName() + " to " + toAtom2.getFullName());
                    }
                }
            }
        } else {
            System.out.println("empty entity map");
        }
    }

    public ManagedNoe getConstraintByID(int id) {
        return noeMap.get(id);
    }

    public void getNoesFromPeakList(PeakList peakList) {
        for (Peak peak : peakList.peaks()) {
            try {
                Atom toAtom1 = ((AtomResonance) peak.getPeakDim(0).getResonance()).getAtom();
                Atom toAtom2 = ((AtomResonance) peak.getPeakDim(1).getResonance()).getAtom();
                if (toAtom1.getSpatialSet() != null && toAtom2.getSpatialSet() != null) {
                    if (!noeExists(toAtom1, toAtom2)) {
                        //might want to worry about the scale?
                        ManagedNoe newNoe = new ManagedNoe(null, toAtom1.getSpatialSet(), toAtom2.getSpatialSet(), peak.getIntensity());
                        add(newNoe);
                        System.out.println("Added NOE from " + toAtom1.getFullName() + " to " + toAtom2.getFullName());
                    } else {
                        System.out.println("NOE already exists: " + toAtom1.getFullName() + " to " + toAtom2.getFullName());
                    }
                }
            } catch (Exception ignored) {}

        }
    }
    public void getNoesFromSubPeakList(PeakList peakList, BidiMap<Entity, Entity> map) {
        if (map != null && map.size()>0) {
            for (Peak peak : peakList.peaks()) {
                try {
                    Atom a1 = ((AtomResonance) peak.getPeakDim(0).getResonance()).getAtom();
                    Atom a2 = ((AtomResonance) peak.getPeakDim(1).getResonance()).getAtom();
                    Entity toEntity1;
                    Entity toEntity2;
                    Atom toAtom1;
                    Atom toAtom2;

                    if (a1.getEntity() instanceof Residue && a2.getEntity() instanceof Residue) {
                        toEntity1 = map.getKey(a1.getEntity());
                        toEntity2 = map.getKey(a2.getEntity());
                    } else {
                        continue;
                    }
                    if (toEntity1 instanceof Residue
                            && toEntity1.getName().equals(a1.getEntity().getName())
                            && toEntity2 instanceof Residue
                            && toEntity2.getName().equals(a2.getEntity().getName())) {
                        toAtom1 = ((Residue) toEntity1).getAtom(a1.getName());
                        toAtom2 = ((Residue) toEntity2).getAtom(a2.getName());
                    } else {
                        continue;
                    }
                    if (toAtom1.getSpatialSet() != null && toAtom2.getSpatialSet() != null) {
                        if (!noeExists(toAtom1, toAtom2)) {
                            //might want to worry about the scale?
                            ManagedNoe newNoe = new ManagedNoe(null, toAtom1.getSpatialSet(), toAtom2.getSpatialSet(), peak.getIntensity());
                            add(newNoe);
                            System.out.println("Added NOE from " + toAtom1.getFullName() + " to " + toAtom2.getFullName());
                        } else {
                            System.out.println("NOE already exists: " + toAtom1.getFullName() + " to " + toAtom2.getFullName());
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
