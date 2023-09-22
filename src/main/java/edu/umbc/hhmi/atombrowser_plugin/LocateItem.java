package edu.umbc.hhmi.atombrowser_plugin;

import edu.umbc.hhmi.acquisition_plugin.AnnoLineWithText;
import javafx.scene.paint.Color;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.annotations.AnnoLine;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LocateItem {
    private final AtomBrowser atomBrowser;
    Atom atom;
    HashMap<PolyChart, List<AnnoLine>> annotations;

    LocateItem(AtomBrowser atomBrowser, Atom atom) {
        this.atomBrowser = atomBrowser;
        this.atom = atom;
        annotations = new HashMap<>();
    }

    @Override
    public boolean equals(Object o) {
        if ((o instanceof LocateItem)) {
            return this.atom == ((LocateItem) o).atom;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    public void remove() {
        annotations.forEach((chart, annoList) -> {
            for (AnnoLine anno : annoList) {
                chart.removeAnnotation(anno);
                chart.refresh();
                //refresh chart?
                //GraphicsContextProxy gcPeaks = new GraphicsContextProxy(chart.peakCanvas.getGraphicsContext2D());
                //chart.drawPeakLists(true, gcPeaks);
                //chart.drawAnnotations(gcPeaks);
            }
        });
        annotations.clear();
    }

    public void update() {
        remove();
        add();
    }

    public void add() {
        for (int i = 0; i < atomBrowser.controller.getCharts().size(); i++) {
            PolyChart chart = atomBrowser.controller.getCharts().get(i);
            add(chart);
        }
    }

    public void add(PolyChart chart) {
        if (atom.getResonance() != null) {
            for (PeakDim peakDim : atom.getResonance().getPeakDims()) {
                for (PeakListAttributes peakAttr : chart.getPeakListAttributes()) {
                    PeakList peakList = peakAttr.getPeakList();
                    if (peakDim.getPeakList() == peakList) {
                        AnnoLineWithText annoLine;
                        if (peakDim.getSpectralDim() == atomBrowser.centerDim) {
                            annoLine = new AnnoLineWithText(atom.getShortName(), peakDim.getChemShiftValue(), 0.99, peakDim.getChemShiftValue(), 0, peakDim.getChemShiftValue(), 1, CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.FRACTION);
                        } else if (peakDim.getSpectralDim() == atomBrowser.rangeDim) {
                            annoLine = new AnnoLineWithText(atom.getShortName(), 0.01, peakDim.getChemShiftValue(), 0, peakDim.getChemShiftValue(), 1, peakDim.getChemShiftValue(), CanvasAnnotation.POSTYPE.FRACTION, CanvasAnnotation.POSTYPE.WORLD);
                        } else {
                            continue;
                        }
                        if (peakDim.isFrozen()) {
                            annoLine.setStroke(Color.RED);
                        } else {
                            annoLine.setStroke(Color.GRAY);
                        }
                        annotations.putIfAbsent(chart, new ArrayList<>());
                        if (!annotations.get(chart).contains(annoLine)) {
                            annotations.get(chart).add(annoLine);
                            chart.addAnnotation(annoLine);
                            chart.refresh();
                        }
                    }
                }
            }
        }
    }
}
