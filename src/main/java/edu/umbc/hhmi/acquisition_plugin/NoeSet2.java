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
import org.nmrfx.peaks.Peak;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.*;
import java.util.Map.Entry;

/**
 *
 * @author brucejohnson
 */
public class NoeSet2 implements ConstraintSet, Iterable {

    private final MolecularConstraints molecularConstraints;

    private final ObservableList<Noe2> constraints = FXCollections.observableArrayList();
    private final Map<Peak, List<Noe2>> peakMap = new TreeMap<>();
    private final String name;
    public static Peak lastPeakWritten = null;
    public static int memberID = 0;
    public static int ID = 0;
    private boolean calibratable = true;
    private boolean dirty = true;

    private NoeSet2(MolecularConstraints molecularConstraints,
                   String name) {
        this.name = name;
        this.molecularConstraints = molecularConstraints;
    }

    public static NoeSet2 newSet(MolecularConstraints molecularConstraints,
                                                                String name) {
        NoeSet2 noeSet = new NoeSet2(molecularConstraints,
                name);
        return noeSet;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getCategory() {
        return "general_distance_constraints";
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
        Noe2 noe = (Noe2) constraint;
        noe.setID(constraints.size());
        constraints.add(noe);
        List<Noe2> noeList = getConstraintsForPeak(noe.getPeak());
        noeList.add(noe);
        dirty = true;
    }

    public ObservableList<Noe2> getConstraints() {
        return constraints;
    }

    @Override
    public Noe2 get(int i) {
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

    public List<Noe2> getConstraintsForPeak(Peak peak) {
        List<Noe2> noeList = peakMap.get(peak);
        if (noeList == null) {
            noeList = new ArrayList<>();
            peakMap.put(peak, noeList);
        }
        return noeList;
    }

    public Set<Entry<Peak, List<Noe2>>> getPeakMapEntries() {
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


}
