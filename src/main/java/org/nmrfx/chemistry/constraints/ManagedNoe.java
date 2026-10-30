package org.nmrfx.chemistry.constraints;

import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.chemistry.SpatialSet;
import org.nmrfx.chemistry.SpatialSetGroup;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;

//This class extends Noe by setting resonance information
//also keeps track of containing noeSet

public class ManagedNoe extends Noe {
    private AtomResonance resonance1 =null;
    private AtomResonance resonance2 =null;
    public ManagedNoeSet noeSet = null;

    public ManagedNoe(Peak p, SpatialSet sp1, SpatialSet sp2, double newScale) {
        super(p, sp1, sp2, newScale);
    }

    public ManagedNoe(Peak p, SpatialSetGroup spg1, SpatialSetGroup spg2, double newScale) {
        super(p, spg1, spg2, newScale);
    }

    public AtomResonance getResonance1() {
        return resonance1;
    }

    public AtomResonance getResonance2() {
        return resonance2;
    }

    public void setResonance1(AtomResonance resonance) {
        resonance1=resonance;
    }

    public void setResonance2(AtomResonance resonance) {
        resonance2=resonance;
    }

    public void setPeak(Peak peak) {
        resonance1 = null;
        resonance2 = null;
        //This is essentially a setPeak method that returns the NOE
        peak(peak);
        if (peak != null) {
            for (PeakDim peakDim : peak.getPeakDims()) {
                if (((AtomResonance) peakDim.getResonance()).getAtom() == getSpg1().getAnAtom()) {
                    resonance1 = (AtomResonance) peakDim.getResonance();
                }
                if (((AtomResonance) peakDim.getResonance()).getAtom() == getSpg2().getAnAtom()) {
                    resonance2 = (AtomResonance) peakDim.getResonance();
                }
            }
        }
    }

    //the below method is an exact copy from the Noe class, except that some private fields
    //are replaced by getters and the resonance ID is written in the appropriate field
    @Override
    public String toSTARString(int id, int memberId) {
        String logic = ".";
        if (getNPossible() > 1) {
            logic = "OR";
        }

        StringBuilder result = new StringBuilder();
        char sep = ' ';

        //        Gen_dist_constraint.ID
        result.append(id);
        result.append(sep);
        //_Gen_dist_constraint.Member_ID
        result.append(memberId);
        result.append(sep);
        //_Gen_dist_constraint.Member_logic_code
        result.append(logic);
        result.append(sep);
        getSpg1().addToSTARString(result);
        result.append(sep);
        //_Gen_dist_constraint.Resonance_ID_1
        if (getResonance1() == null) {
            result.append(".");
        } else {
            result.append(getResonance1().getID());
        }
        result.append(sep);
        getSpg2().addToSTARString(result);
        result.append(sep);
        //_Gen_dist_constraint.Resonance_ID_2
        if (getResonance2() == null) {
            result.append(".");
        } else {
            result.append(getResonance2().getID());
        }
        result.append(sep);
        //_Gen_dist_constraint.Intensity_val
        result.append(getIntensity());
        result.append(sep);
        //_Gen_dist_constraint.Intensity_lower_val_err
        result.append('.');
        result.append(sep);
        //_Gen_dist_constraint.Intensity_upper_val_err
        result.append('.');
        result.append(sep);
        //_Gen_dist_constraint.Distance_val
        if (target < lower) {
            target = (lower + upper) / 2.0;
        }
        result.append(target);
        result.append(sep);
        //_Gen_dist_constraint.Distance_lower_bound_val
        result.append(lower);
        result.append(sep);
        //_Gen_dist_constraint.Distance_upper_bound_val
        result.append(upper);
        result.append(sep);
        //_Gen_dist_constraint.Contribution_fractional_val
        result.append('.');
        result.append(sep);
        //NEW
        //_Gen_dist_contraint.Weight
        result.append(getScale());
        result.append(sep);
        //_Gen_dist_constraint.Spectral_peak_ID
        if (getPeak() == null) {
            result.append('.');
            result.append(sep);
            result.append('.');
        } else {
            result.append(getPeak().getIdNum());
            result.append(sep);
            //_Gen_dist_constraint.Spectral_peak_list_ID
            result.append(getPeak().getPeakList().getId());
        }
        result.append(sep);

        result.append("."); // fixme do we need to save ssid here

        result.append(sep);
        result.append("1");
        return result.toString();
    }

    public void setNoeSet(ManagedNoeSet set) {
        noeSet = set;
    }

}
