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
import org.nmrfx.chemistry.constraints.Constraint;
import org.nmrfx.chemistry.constraints.ConstraintSet;
import org.nmrfx.chemistry.constraints.MolecularConstraints;
import org.nmrfx.peaks.ManagedList;
import org.nmrfx.peaks.Peak;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.project.ProjectUtilities;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.SaveframeWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.Map.Entry;

/**
 *
 * @author brucejohnson
 */

public class ManagedNoeSet implements ConstraintSet, Iterable, SaveframeWriter, Comparable<ManagedNoeSet> {

    private final MolecularConstraints molecularConstraints;

    private final ObservableList<ManagedNoe> constraints = FXCollections.observableArrayList();
    private final Map<Peak, List<ManagedNoe>> peakMap = new TreeMap<>();
    private final String name;
    public static Peak lastPeakWritten = null;
    public static int memberID = 0;
    public static int ID = 0;
    private boolean calibratable = true;
    private boolean dirty = true;
    public Set<ManagedList> associatedLists = new HashSet<>();
    private int setId;

    private static HashMap<ProjectBase,Integer> projectSaveFramesAdded = new HashMap<>();
    private static HashMap<ProjectBase,Integer> projectSaveFramesWritten = new HashMap<>();

    static public Integer getSaveFramesAdded() {
        return getSaveFramesAdded(ProjectBase.getActive());
    }

    static public Integer getSaveFramesWritten() {
        return getSaveFramesWritten(ProjectBase.getActive());
    }

    static public Integer getSaveFramesAdded(ProjectBase project) {
        Integer saveFramesAdded = projectSaveFramesAdded.get(project);
        if (saveFramesAdded == null) {
            saveFramesAdded = 0;
            projectSaveFramesAdded.put(project,saveFramesAdded);
        }
        return saveFramesAdded;
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
        ManagedNoeSet noeSet = new ManagedNoeSet(molecularConstraints,
                name);
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
    }

    @Override
    public void add(Constraint constraint) {
        ManagedNoe noe = (ManagedNoe) constraint;
        noe.setID(constraints.size());
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
            noeList = peakMap.get(peak);
            if (noeList == null) {
                noeList = new ArrayList<>();
                peakMap.put(peak, noeList);
            }
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
        chan.write('\'' + constraintType + "\'\n");

        chan.write(thisCategory + ".Details        ");
        chan.write(".\n");

        chan.write("\n");

        String[] loopStrings = getLoopStrings();
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
        Iterator iter = iterator();
        while (iter.hasNext()) {
            Constraint constraint = (Constraint) iter.next();
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

        if (getSaveFramesWritten() == getSaveFramesAdded()) {
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
}
