package edu.umbc.hhmi.acquisition_plugin;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.chemistry.PPMv;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.utils.GUIUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ManagedPeak extends Peak {
    private Set<ManagedNoe> noes=new HashSet<>();

    public ManagedPeak(int nDim) {
        super(nDim);
    }

    public ManagedPeak(PeakList peakList, int nDim, Set<ManagedNoe> noes, HashMap<Integer, Atom> atoms) {
        super(peakList, nDim);
        this.noes=noes;

        float scale=1f;

        for (ManagedNoe noe : noes) {
            if (noe.getPeak()==null || !PeakList.peakLists().contains(noe.getPeak().peakList)) {
                for (int i = 0; i < nDim; i++) {
                    AtomResonance resonance=null;
                    if (resonance==null && atoms.get(i).getResonance()!=null) {
                        resonance=atoms.get(i).getResonance();
                    }
                    if (resonance==null) {
                        resonance = (AtomResonance) PeakList.resFactory().build();
                    }
                    if (resonance.getPeakDims().size()>0 && resonance.getPeakDims().get(0).isFrozen()) {
                        this.getPeakDim(i).setFrozen(true);
                    }
                    resonance.add(this.getPeakDim(i));
                    this.getPeakDim(i).setLabel(atoms.get(i).getShortName());
                    atoms.get(i).setResonance(resonance);

                    PPMv ppm;
                    ppm = atoms.get(i).getPPM(((ManagedList) getPeakList()).getPpmSet());
                    if (ppm == null) {
                        ppm = atoms.get(i).getRefPPM(((ManagedList) getPeakList()).getRPpmSet());
                    }
                    if (ppm != null) {
                        this.getPeakDim(i).setChemShift((float) ppm.getValue());
                        this.getPeakDim(i).setChemShiftErrorValue((float) ppm.getError());
                    }
                }
                noe.setPeak(this);
            }
        }
        for (int i = 0; i < nDim; i++) {
            PeakDim peakDim0=null;
            AtomResonance resonance=null;
            for (ManagedNoe noe : noes) {
                for (PeakDim peakDim : noe.getPeak().getPeakDims()) {
                    if (((AtomResonance) peakDim.getResonance()).getAtom()==atoms.get(i)) {
                        peakDim0=peakDim;
                        resonance=(AtomResonance) peakDim.getResonance();
                    }
                }
            }
            if (resonance==null && atoms.get(i).getResonance()!=null) {
                resonance=atoms.get(i).getResonance();
            }
            if (resonance==null) {
                resonance = (AtomResonance) PeakList.resFactory().build();
            }
            if (resonance.getPeakDims().size()>0 && resonance.getPeakDims().get(0).isFrozen()) {
                this.getPeakDim(i).setFrozen(true);
            }
            resonance.add(this.getPeakDim(i));
            this.getPeakDim(i).setLabel(atoms.get(i).getShortName());
            atoms.get(i).setResonance(resonance);


            Dataset dataset=((ManagedList) peakList).getAcquisition().getDataset();
            /*float width=(float) dataset.ptWidthToPPM(i,2);
            if (width<0.01f) {width=0.01f;}
            */
            float width = ((ManagedList) peakList).getAcquisition().getDefaultPeakWidth(i);
            /*
            float width;
            switch (atoms.get(i).getElementName()) {
                case "C":
                    width= 0.4f;
                    //scale=3f;
                    break;
                case "N":
                    width= 0.9f;
                    //scale=3f;
                    break;
                default:
                    width= 0.01f;
            }
             */


            if (peakDim0!=null) {
                PeakDim thisPeakDim=this.getPeakDim(i);
                if (thisPeakDim.getSpectralDim()==peakDim0.getSpectralDim()) {
                    Dataset dataset0=Dataset.getDataset(peakDim0.getPeakList().getDatasetName());
                    if (dataset0!=null) {
                        //width = (float) (peakDim0.getLineWidthValue()*dataset.ptWidthToPPM(i,2)/dataset0.ptWidthToPPM(peakDim0.getSpectralDim(),2));
                    }
                }
                Float pickedShift = peakDim0.getChemShift();

                List<PeakDim> peakDims = peakDim0.getResonance().getPeakDims();
                Set<PeakDim> updateMe = new HashSet<>();
                updateMe.add(thisPeakDim);
                Boolean freezeMe = false;

                for (PeakDim peakDim : peakDims) {
                    if (peakDim == peakDim0 || peakDim==thisPeakDim) {
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
                    peakDim.setChemShift(pickedShift);
                }
                thisPeakDim.setFrozen(freezeMe);
            } else {
                PPMv ppm;
                ppm = atoms.get(i).getPPM(((ManagedList) getPeakList()).getPpmSet());
                if (ppm == null) {
                    ppm = atoms.get(i).getRefPPM(((ManagedList) getPeakList()).getRPpmSet());
                }
                if (ppm != null) {
                    this.getPeakDim(i).setChemShift((float) ppm.getValue());
                    this.getPeakDim(i).setChemShiftErrorValue((float) ppm.getError());
                }
            }

            this.getPeakDim(i).setLineWidthValue(width);
        }
        for (PeakDim peakDim : getPeakDims()) {
            peakDim.setLineWidthValue(peakDim.getLineWidthValue()*scale);
            peakDim.setBoundsValue(peakDim.getLineWidthValue()*1.5f);
        }

        if (((ManagedList) peakList).noeSet!=null) {
            ((ManagedList) peakList).noeSet.getConstraints().addListener((ListChangeListener.Change<? extends ManagedNoe> c) -> {
                while (c.next()) {
                    for (ManagedNoe removedNoe : c.getRemoved()) {
                        if (this.noes.contains(removedNoe)) {
                            remove();
                        }
                    }
                }
            });
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

    private void remove() {
        //worried about race condition with setStatus
        Platform.runLater(() -> {
            for (PeakDim peakDim : this.peakDims) {
                peakDim.remove();
                if (peakDim.hasMultiplet()) {
                    Multiplet multiplet = peakDim.getMultiplet();
                }
            }
            peakList.unLinkPeak(this);
            this.markDeleted();
            peakList.peaks().remove(this);
            peakList.reIndex();
        });
    }

    public Set<ManagedNoe> getNoes() {
        return noes;
    }

}
