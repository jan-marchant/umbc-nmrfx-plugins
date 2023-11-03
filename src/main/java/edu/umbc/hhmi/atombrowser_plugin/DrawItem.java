package edu.umbc.hhmi.atombrowser_plugin;

import edu.umbc.hhmi.acquisition_plugin.AnnoLineWithText;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ObservableList;
import javafx.scene.input.MouseEvent;
import javafx.scene.robot.Robot;
import org.nmrfx.chart.Axis;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.project.ProjectBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DrawItem implements Comparator<DrawItem> {
    private static final Logger log = LoggerFactory.getLogger(DrawItem.class);

    private Atom atom;
    final SpectralDim centerSpectralDim;
    final SpectralDim rangeSpectralDim;
    final Nuclei centerNucleus;
    final Nuclei rangeNucleus;
    final Dataset dataset;
    final PeakList peakList;
    final int centerDimNo;
    final int rangeDimNo;
    private HashMap<Integer, DoubleProperty> shiftProperties = new HashMap<>();
    private PolyChart chart;
    private AtomBrowser atomBrowser;
    HashMap<Integer, List<PeakDim>> dims = new HashMap<>();
    List<Peak> peaks=new ArrayList<>();
    ProjectBase project;
    PeakListener peakListener;
    private List<AnnoLineWithText> annotations = new ArrayList<>();
    private List<LocateItem> locateItems = new ArrayList<>();

    DrawItem(AtomBrowser atomBrowser, Atom atom, PeakDim centerDim, PeakDim rangeDim, ProjectBase project) {
        this.atomBrowser = atomBrowser;
        this.atom = atom;
        this.project=project;
        Peak peak = centerDim.getPeak();
        peakList = peak.getPeakList();
        dataset = (Dataset) project.getDataset(peakList.getDatasetName());
        centerDimNo=centerDim.getSpectralDimObj().getDataDim();
        rangeDimNo=rangeDim.getSpectralDimObj().getDataDim();
        centerSpectralDim = peakList.getSpectralDim(centerDimNo);
        rangeSpectralDim = peakList.getSpectralDim(rangeDimNo);
        centerNucleus = Nuclei.findNuclei(centerSpectralDim.getNucleus());
        rangeNucleus = Nuclei.findNuclei(peakList.getSpectralDim(rangeDimNo).getNucleus());
        this.add(peak);

        for (int dim = 0; dim < dataset.getNDim(); dim++) {
            shiftProperties.put(dim, new SimpleDoubleProperty());
        }

        setShiftProperties();
        setupShiftPropertyListeners();
        addPeakChangeListener(e -> {
            setShiftProperties();
            updateLocateItems();
        });
    }

    public Atom getAtom() {
        return atom;
    }

    private void setupShiftPropertyListeners() {
        for (int dim = 0; dim < dataset.getNDim(); dim++) {
            if (! (dim==rangeDimNo)) {
                shiftProperties.get(dim).addListener(e -> {
                    updateChartBounds(true);
                });
            }
        }
    }

    public void updateChartBounds(boolean scroll) {
        boolean refresh = false;
        Axis centerAxis = chart.getAxes().get(atomBrowser.centerDim);
        if (scroll) {
            double width = chart.getAxes().get(atomBrowser.centerDim).getRange() / 2;
            if (centerAxis.getLowerBound() + width / 4 > shiftProperties.get(centerDimNo).get()) {
                centerAxis.setLowerBound(shiftProperties.get(centerDimNo).get() - width / 4);
                centerAxis.setUpperBound(shiftProperties.get(centerDimNo).get() + 7 * width / 4);
                refresh = true;
            } else if (centerAxis.getUpperBound() - width / 4 < shiftProperties.get(centerDimNo).get()) {
                centerAxis.setLowerBound(shiftProperties.get(centerDimNo).get() - 7 * width / 4);
                centerAxis.setUpperBound(shiftProperties.get(centerDimNo).get() + width / 4);
                refresh = true;
            }
        } else {
            centerAxis.setLowerBound(shiftProperties.get(centerDimNo).get() - atomBrowser.delta);
            centerAxis.setUpperBound(shiftProperties.get(centerDimNo).get() + atomBrowser.delta);
            refresh = true;
        }

        DatasetAttributes datasetAttr = chart.getActiveDatasetAttributes().get(0);

        for (int n = 2; n < dataset.getNDim(); n++) {
            int dataDim = datasetAttr.getDim(n);
            if (chart.getAxes().get(n).getLowerBound() != getMinShift(dataDim)) {
                chart.getAxes().get(n).setLowerBound(getMinShift(dataDim));
                refresh = true;
            }
            if (chart.getAxes().get(n).getUpperBound() != getMaxShift(dataDim)) {
                chart.getAxes().get(n).setUpperBound(getMaxShift(dataDim));
                refresh = true;
            }
        }
        if (refresh) {
            atomBrowser.refreshChart(chart);
        }
    }

    public void setShiftProperties() {
        for (int dim = 0; dim < dataset.getNDim(); dim++) {
            if (! (dim==rangeDimNo)) {
                double ppm = getAverageShift(dim);
                if (! (shiftProperties.get(dim).get() == ppm)) {
                    shiftProperties.get(dim).set(ppm);
                }
            }
        }
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
        try {
            return peakDims.stream().mapToDouble(val -> Double.parseDouble(((Float) (val.getChemShift()-val.getLineWidth()/2)).toString())).min().orElse(Double.MIN_VALUE);
        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
    }

    Double getMaxRange(List<PeakDim> peakDims) {
        try {
            return peakDims.stream().mapToDouble(val -> Double.parseDouble(((Float) (val.getChemShift() + val.getLineWidth() / 2)).toString())).max().orElse(Double.MAX_VALUE);
        } catch (Exception e) {
            return Double.MIN_VALUE;
        }
    }

    Double getMaxRange() {
        return getMaxRange(dims.get(rangeDimNo));
    }
    Double getAverageShift(int n) {
        return dims.get(n).stream().mapToDouble(val -> Double.parseDouble(val.getChemShift().toString())).average().orElse(0.0);
        //should be equivalent right? Given all peaks are linked.
        //return dims.get(n).get(0).getAverageShift();
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
        if (chart != null) {
            AtomBrowser.setLevelForDataset(chart.getActiveDatasetAttributes().get(0));
            chart.clearDataAndPeaks();
            chart = null;
        }
        shiftProperties.clear();
        peakList.removePeakChangeListener(peakListener);
        atomBrowser.removeLocateItem(atom);

    }

    public void addPeakChangeListener(PeakListener listener) {
        if (peakListener == null) {
            peakListener = listener;
            peakList.registerPeakChangeListener(listener);
        }
    }

    public void setChart(PolyChart chart) {
        this.chart = chart;
    }

    public void setupChartProperties() {
        //chart.useImmediateMode(false);
        //n.b. this must go before setDataset otherwise updateChart takes aaages.
        chart.getChartProperties().setTitles(true);
        chart.setDataset(dataset);
        chart.getPeakListAttributes().clear();
        chart.setupPeakListAttributes(peakList);
        for (PeakListAttributes peakListAttributes : chart.getPeakListAttributes()) {
            peakListAttributes.setLabelType("Label");
        }

        DatasetAttributes datasetAttr = chart.getActiveDatasetAttributes().get(0);
        datasetAttr.setDim(centerDimNo, atomBrowser.centerDim);
        datasetAttr.setDim(rangeDimNo, atomBrowser.rangeDim);
        dataset.setTitle(dataset.getName());

        /*
        chart.getCanvas().setOnMouseDragged((MouseEvent mouseEvent) -> {
            Optional<Peak> hit = chart.hitPeak(mouseEvent.getX(), mouseEvent.getY());
            if (hit.isPresent()) {
                Peak peak = hit.get();
                Platform.runLater(() -> {
                    Robot robot = new Robot();
                    Optional<PeakListAttributes> peakListAtopt = chart.getPeakListAttributes().stream().filter(e -> e.getPeakList() == peak.getPeakList()).findFirst();
                    if (peakListAtopt.isPresent()) {
                        int [] dim = peakListAtopt.get().getPeakDim();
                        robot.mouseMove(chart.getAxes().getX().getDisplayPosition(peak.peakDims[dim[0]].getChemShiftValue()),chart.getAxes().getX().getDisplayPosition(peak.peakDims[dim[1]].getChemShiftValue()));
                    }
                });
            }
        });
         */
    }

    public void setChartBounds() {
        Double ppm = shiftProperties.get(centerDimNo).get();
        if (getMinRange() < atomBrowser.getyMin()) {
            atomBrowser.setyMin(getMinRange());
        }
        if (getMaxRange() > atomBrowser.getyMax()) {
            atomBrowser.setyMax(getMaxRange());
        }

        chart.getAxes().get(atomBrowser.centerDim).setLowerBound(ppm - atomBrowser.delta);
        chart.getAxes().get(atomBrowser.centerDim).setUpperBound(ppm + atomBrowser.delta);

        chart.getAxes().get(atomBrowser.rangeDim).setLowerBound(atomBrowser.getyMin());
        chart.getAxes().get(atomBrowser.rangeDim).setUpperBound(atomBrowser.getyMax());

        DatasetAttributes datasetAttr = chart.getActiveDatasetAttributes().get(0);

        for (int n = 2; n < dataset.getNDim(); n++) {
            int dataDim = datasetAttr.getDim(n);
            chart.getAxes().get(n).setLowerBound(getMinShift(dataDim));
            chart.getAxes().get(n).setUpperBound(getMaxShift(dataDim));
        }
    }

    public PolyChart getChart() {
        return chart;
    }

    public void setupPlaneListeners() {
        DatasetAttributes datasetAttr = chart.getActiveDatasetAttributes().get(0);

        for (int n = 2; n < dataset.getNDim(); n++) {
            int dataDim = datasetAttr.getDim(n);
            int finalN = n;
            chart.getAxes().get(n).lowerBoundProperty().addListener(e -> {
                for (PeakDim peakDim : dims.get(dataDim)) {
                    if (!peakDim.isFrozen()) {
                        peakDim.setChemShift((float) chart.getAxes().get(finalN).getLowerBound());
                    }
                }
            });
            chart.getAxes().get(n).upperBoundProperty().addListener(e -> {
                for (PeakDim peakDim : dims.get(dataDim)) {
                    if (!peakDim.isFrozen()) {
                        peakDim.setChemShift((float) chart.getAxes().get(finalN).getUpperBound());
                    }
                }
            });
        }
    }

    private void updateLocateItems() {
        for (LocateItem locateItem : locateItems) {
            locateItem.update(this);
        }
    }

    public void removeAnnotation(AnnoLineWithText anno) {
        annotations.remove(anno);
        if (chart != null) {
            chart.removeAnnotation(anno);
        }
    }

    public void addAnnotation(AnnoLineWithText anno) {
        annotations.add(anno);
        if (chart != null) {
            chart.addAnnotation(anno);
        }
    }

    public void addLocateItem(LocateItem locateItem) {
        if (!locateItems.contains(locateItem)) {
            locateItems.add(locateItem);
        }
    }

    public void removeLocateItem(LocateItem locateItem) {
        locateItems.remove(locateItem);
    }
}

