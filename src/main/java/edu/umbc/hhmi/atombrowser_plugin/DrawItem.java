package edu.umbc.hhmi.atombrowser_plugin;

import javafx.collections.ObservableList;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.project.ProjectBase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class DrawItem implements Comparator<DrawItem> {
    final SpectralDim centerSpectralDim;
    final SpectralDim rangeSpectralDim;
    final Nuclei centerNucleus;
    final Nuclei rangeNucleus;
    final Dataset dataset;
    final PeakList peakList;
    final int centerDimNo;
    final int rangeDimNo;
    HashMap<Integer, List<PeakDim>> dims = new HashMap<>();
    List<Peak> peaks=new ArrayList<>();
    ProjectBase project;
    PeakListener peakListener;

    DrawItem(PeakDim centerDim,PeakDim rangeDim,ProjectBase project) {
        this.project=project;
        Peak peak = centerDim.getPeak();
        peakList = peak.getPeakList();
        dataset = (Dataset) project.getDataset(peakList.getDatasetName());
        centerDimNo=centerDim.getSpectralDimObj().getDataDim();
        rangeDimNo=rangeDim.getSpectralDimObj().getDataDim();
        centerSpectralDim =peakList.getSpectralDim(centerDimNo);
        rangeSpectralDim=peakList.getSpectralDim(rangeDimNo);
        centerNucleus=Nuclei.findNuclei(centerSpectralDim.getNucleus());
        rangeNucleus=Nuclei.findNuclei(peakList.getSpectralDim(rangeDimNo).getNucleus());
        this.add(peak);
    }

    public void add(Peak peak) {
        this.peaks.add(peak);
        for (PeakDim peakDim : peak.getPeakDims()) {
            dims.putIfAbsent(peakDim.getSpectralDimObj().getDataDim(),new ArrayList<>());
            List<PeakDim> list=dims.get(peakDim.getSpectralDimObj().getDataDim());
            if (!list.contains(peakDim)) {
                list.add(peakDim);
            }
        }
    }

    public boolean addIfMatch(PeakDim centerDim, PeakDim rangeDim) {
        if (centerDim.getSpectralDimObj()!=centerSpectralDim || rangeDim.getSpectralDimObj()!=rangeSpectralDim) {
            return false;
        }
        Peak peak=centerDim.getPeak();
        for (PeakDim peakDim : peak.getPeakDims()) {
            int dim=peakDim.getSpectralDimObj().getDataDim();
            if (dim==centerDimNo || dim==rangeDimNo) {
                continue;
            }
            if (peakDim.getResonance()!=dims.get(dim).get(0).getResonance()) {
                return false;
            }
        }
        add(peak);
        return true;
    }

    @Override
    public int compare(DrawItem o1, DrawItem o2) {
        return o1.getShift().compareTo(o2.getShift());
    }

    Double getMinShift(List<PeakDim> peakDims) {
        return peakDims.stream().mapToDouble(val -> Double.parseDouble(val.getChemShift().toString())).min().orElse(Double.MIN_VALUE);
    }
    Double getMaxShift(List<PeakDim> peakDims) {
        return peakDims.stream().mapToDouble(val -> Double.parseDouble(val.getChemShift().toString())).max().orElse(Double.MAX_VALUE);
    }
    Double getShift() {
        return getAverageShift(centerDimNo);
    }
    Double getMinRange() {
        return getMinRange(dims.get(rangeDimNo));
    }
    Double getMinRange(List<PeakDim> peakDims) {
        return peakDims.stream().mapToDouble(val -> Double.parseDouble(((Float) (val.getChemShift()-val.getLineWidth()/2)).toString())).min().orElse(Double.MIN_VALUE);
    }

    Double getMaxRange(List<PeakDim> peakDims) {
        return peakDims.stream().mapToDouble(val -> Double.parseDouble(((Float) (val.getChemShift()+val.getLineWidth()/2)).toString())).max().orElse(Double.MAX_VALUE);
    }

    Double getMaxRange() {
        return getMaxRange(dims.get(rangeDimNo));
    }
    Double getAverageShift(int n) {
        return dims.get(n).stream().mapToDouble(val -> Double.parseDouble(val.getChemShift().toString())).average().orElse(0.0);
    }
    Double getMinShift(int n) {
        return getMinShift(dims.get(n));
    }
    Double getMaxShift(int n) {
        return getMaxShift(dims.get(n));
    }
    List<Double> getShifts (List<PeakDim> peakDims) {
        List<Double> shifts = new ArrayList<>();
        for (PeakDim peakDim :peakDims) {
            if (!shifts.contains(Double.parseDouble(peakDim.getChemShift().toString()))) {
                shifts.add(Double.parseDouble(peakDim.getChemShift().toString()));
            }
        }
        return shifts;
    }

    public boolean isAllowed(ObservableList<FilterItem> filterList) {
        for (FilterItem filterItem : filterList) {
            if (filterItem.matches(this) && !filterItem.isAllow()) {
                return false;
            }
        }
        return true;
    }

    public void remove() {
        peakList.removePeakChangeListener(peakListener);
    }

    public void addPeakChangeListener(PeakListener listener) {
        if (peakListener == null) {
            peakListener = listener;
            peakList.registerPeakChangeListener(listener);
        }
    }
}

