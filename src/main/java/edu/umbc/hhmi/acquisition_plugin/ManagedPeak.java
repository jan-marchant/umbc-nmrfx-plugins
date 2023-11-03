package edu.umbc.hhmi.acquisition_plugin;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.PPMv;
import org.nmrfx.peaks.*;
import org.nmrfx.project.SubProject;
import org.nmrfx.utils.GUIUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ManagedPeak extends Peak {
    private Set<ManagedNoe> noes=new HashSet<>();

    public void addNoe (ManagedNoe noe) {
        noes.add(noe);
    }

    public ManagedPeak(int nDim) {
        super(nDim);
    }

    public ManagedPeak(PeakList peakList, int nDim, Set<ManagedNoe> noes, HashMap<Integer, Atom> atoms) {
        super(peakList, nDim);
        this.noes=noes;

        float scale=1f;

        for (int i = 0; i < nDim; i++) {
            AtomResonance resonance=getAtomResonance(atoms.get(i));

            if (resonance.getPeakDims().size()>0 && resonance.getPeakDims().get(0).isFrozen()) {
                this.getPeakDim(i).setFrozen(true);
            }

            resonance.add(this.getPeakDim(i));
            this.getPeakDim(i).setLabel(atoms.get(i).getShortName());
            //atoms.get(i).setResonance(resonance);
            //Need to remove this for peak picker to work. But I probably added it for some reason....
            //setShiftFromAtom(atoms.get(i),getPeakDim(i));
        }

        for (ManagedNoe noe : noes) {
            if (noe.getPeak()==null || !PeakList.peakLists().contains(noe.getPeak().peakList)) {
                noe.setPeak(this);
            }
        }

        for (int i = 0; i < nDim; i++) {
            //foundResonance = findResonance(i);
            //foundPeakDim - the matching peakDim from the noe peak field. Used as a proxy for the picked peak
            //foundResonance - the resonance for this atom

            PeakDim foundPeakDim=null;
            AtomResonance foundResonance=null;
            for (ManagedNoe noe : noes) {
                for (PeakDim peakDim : noe.getPeak().getPeakDims()) {
                    if (((AtomResonance) peakDim.getResonance()).getAtom()==atoms.get(i)) {
                        foundPeakDim=peakDim;
                        foundResonance=(AtomResonance) peakDim.getResonance();
                    }
                }
            }
            if (foundResonance==null) {
                foundResonance = getAtomResonance(atoms.get(i));
            }

            if (foundResonance.getPeakDims().size()>0 && foundResonance.getPeakDims().get(0).isFrozen()) {
                getPeakDim(i).setFrozen(true);
            }
            foundResonance.add(getPeakDim(i));
            getPeakDim(i).setLabel(atoms.get(i).getShortName());
            atoms.get(i).setResonance(foundResonance);

            float width = ((ManagedList) peakList).getAcquisition().getDefaultPeakWidth(i);

            if (foundPeakDim!=null) {
                PeakDim thisPeakDim=getPeakDim(i);
                Float pickedShift = foundPeakDim.getChemShift();

                List<PeakDim> peakDims = foundPeakDim.getResonance().getPeakDims();
                Set<PeakDim> updateMe = new HashSet<>();
                updateMe.add(thisPeakDim);
                boolean freezeMe = false;

                for (PeakDim peakDim : peakDims) {
                    if (peakDim == foundPeakDim || peakDim==thisPeakDim) {
                        continue;
                    }
                    if (peakDim.getSampleConditionLabel().equals(thisPeakDim.getSampleConditionLabel())
                    && peakDim.getSampleLabel().equals(thisPeakDim.getSampleLabel())) {
                        if (peakDim.isFrozen()) {
                            pickedShift = peakDim.getChemShift();
                            freezeMe = true;
                        } else {
                            updateMe.add(peakDim);
                        }
                        if (thisPeakDim.getSpectralDimObj()==peakDim.getSpectralDimObj()) {
                            width = peakDim.getLineWidthValue();
                        }
                    }
                }
                for (PeakDim peakDim : updateMe) {
                    if (pickedShift != null) {
                        peakDim.setChemShift(pickedShift);
                    } else {
                        setShiftFromAtom(atoms.get(i),getPeakDim(i));
                    }
                }
                thisPeakDim.setFrozen(freezeMe);
            } else {
                setShiftFromAtom(atoms.get(i),getPeakDim(i));
            }
            getPeakDim(i).setLineWidthValue(width);
        }
        for (PeakDim peakDim : getPeakDims()) {
            peakDim.setLineWidthValue(peakDim.getLineWidthValue()*scale);
            peakDim.setBoundsValue(peakDim.getLineWidthValue()*1.5f);
        }

        setupManagedNoeListener(((ManagedList) peakList).noeSet);
    }

    private void setupManagedNoeListener(ManagedNoeSet noeSet) {
        if (noeSet!=null) {
            noeSet.getConstraints().addListener((ListChangeListener.Change<? extends ManagedNoe> c) -> {
                while (c.next()) {
                    for (ManagedNoe removedNoe : c.getRemoved()) {
                        if (noes.contains(removedNoe)) {
                            remove();
                        }
                    }
                }
            });
        }
    }

    private void setShiftFromAtom(Atom atom,PeakDim peakDim) {
        PPMv ppm;
        ppm = atom.getPPM(((ManagedList) getPeakList()).getPpmSet());
        if (ppm == null) {
            ppm = atom.getRefPPM(((ManagedList) getPeakList()).getRPpmSet());
        }
        if (ppm != null) {
            peakDim.setChemShift((float) ppm.getValue());
            peakDim.setChemShiftErrorValue((float) ppm.getError());
        }
    }

    public ManagedPeak(PeakList peakList, int nDim) {
        super(peakList, nDim);
    }

    public static ManagedPeak copyFrom(Peak originalPeak, ManagedList list) {
        ManagedPeak peak = new ManagedPeak(list,originalPeak.getNDim());
        originalPeak.copyTo(peak);
        for (int i = 0; i < originalPeak.peakDims.length; i++) {
            peak.peakDims[i].setResonance(originalPeak.peakDims[i].getResonance());
            originalPeak.peakDims[i].getResonance().add(peak.peakDims[i]);
            peak.peakDims[i].setFrozen(originalPeak.peakDims[i].isFrozen());
        }
        peak.updateFrozenColor();
        return peak;
    }

    private AtomResonance getAtomResonance(Atom atom) {
        AtomResonance resonance=null;
        if (atom.getResonance()!=null) {
            resonance=atom.getResonance();
        }
        if (resonance==null) {
            resonance = (AtomResonance) SubProject.resFactory().build();
            resonance.setAtom(atom);
            atom.setResonance(resonance);
        }
        return resonance;
    }

    @Override
    public void setStatus(int status) {
        boolean updateStatus=false;
        if (status < 0) {
            if (((ManagedList) peakList).noeSet==null) {
                GUIUtils.warn("Cannot remove peak", "Peak patterns are determined by sample and experiment type only.");
                return;
            }

            if (noes.size()==1) {
                ((ManagedList) peakList).noeSet.getConstraints().removeAll(noes);
                updateStatus=true;
            } else {
                //popup if multiple NOE dims
                for (ManagedNoe noe: noes) {
                    //TODO: Will this fail for relabelled peaks? Need to watch and update. Are the spatial sets set up on loading?
                    // can add an NOE without a peak for the other NOE dims.
                    //fixme: better to have a single window with all possible suggestions
                    boolean result=GUIUtils.affirm("Delete NOE between "+noe.spg1.getAnAtom()+" and "+noe.spg2.getAnAtom()+"?");
                    if (result) {
                        updateStatus=true;
                        ((ManagedList) peakList).noeSet.getConstraints().remove(noe);
                    }
                }
            }
        }
        if (updateStatus) {
            super.setStatus(status);
        }
    }

    @Override
    public void setFrozen(boolean state, boolean allConditions) {
        //it's my peakList and I'll freeze if I want to
        super.setFrozen(state, allConditions);
        String conditionString;
        if (allConditions) {
            conditionString = null;
        } else {
            conditionString = getPeakList().getSampleConditionLabel();
        }
        for (PeakDim peakDim : getPeakDims()) {
            Double ppmAvg = ((AtomResonance) peakDim.getResonance()).getPPMAvg(conditionString);
            //just use atomresonance atom better?
            Atom atom = MoleculeBase.getAtomByName(peakDim.getLabel());
            if (atom != null) {
                atom.setPPM(ppmAvg);
            }
        }
    }

    private void remove() {
        //worried about race condition with setStatus
        Platform.runLater(() -> {
            for (PeakDim peakDim : this.peakDims) {
                peakDim.remove();
                if (peakDim.hasMultiplet()) {
                    Multiplet multiplet = peakDim.getMultiplet();
                }
            }
            PeakList.unLinkPeak(this);
            this.markDeleted();
            peakList.peaks().remove(this);
            peakList.reIndex();
        });
    }

    public Set<ManagedNoe> getNoes() {
        return noes;
    }

    //fixme: consider alternative using getPossibleAtom throughout?
    public void setResonanceAtoms() {
        for (PeakDim peakDim : getPeakDims()) {
            AtomResonance res = (AtomResonance) peakDim.getResonance();
            Atom atom = res.getAtom();
            if (atom == null) {
                atom = res.getPossibleAtom();
            }
            if (atom!=null) {
                if (atom.getResonance() == null) {
                    atom.setResonance(res);
                } else {
                    getAtomResonance(atom).add(peakDim);
                }
            }
        }
    }

    @Override
    public void peakUpdated(Object object) {
        super.peakUpdated(object);
    }
}
