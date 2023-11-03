package edu.umbc.hhmi.atombrowser_plugin;

import edu.umbc.hhmi.acquisition_plugin.AnnoLineWithText;
import javafx.scene.paint.Color;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LocateItem {
    private final AtomBrowser atomBrowser;
    Atom atom;
    HashMap<DrawItem, List<AnnoLineWithText>> annotations;

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
        for (DrawItem drawItem : annotations.keySet()) {
            //can't reuse remove(drawItem) because of concurrent access issues
            for (AnnoLineWithText anno : annotations.get(drawItem)) {
                drawItem.removeAnnotation(anno);
            }
            drawItem.removeLocateItem(this);
            if (drawItem.getChart() != null) {
                drawItem.getChart().refresh();
            }
        }
        annotations.clear();
    }

    public void remove(DrawItem drawItem) {
        for (AnnoLineWithText anno : annotations.get(drawItem)) {
            drawItem.removeAnnotation(anno);
        }
        drawItem.removeLocateItem(this);
        if (drawItem.getChart() != null) {
            drawItem.getChart().refresh();
        }
        annotations.remove(drawItem);
    }

    public void update(DrawItem drawItem) {
        //remove(drawItem);
        //add(drawItem);
        for (AnnoLineWithText anno : annotations.get(drawItem)) {
            setPositions(anno, drawItem);
        }
    }

    private void setPositions(AnnoLineWithText anno, DrawItem drawItem) {
        ShiftColor shiftColor;
        if (anno.getXPosType()==CanvasAnnotation.POSTYPE.WORLD) {
            shiftColor = getShiftColor(atomBrowser.centerDim, drawItem);
            if (shiftColor.getShift() != null) {
                anno.setX(shiftColor.getShift());
                anno.setStroke(shiftColor.getColor());
            }
        } else if (anno.getYPosType()==CanvasAnnotation.POSTYPE.WORLD) {
            shiftColor = getShiftColor(atomBrowser.rangeDim, drawItem);
            if (shiftColor.getShift() != null) {
                anno.setY(shiftColor.getShift());
                anno.setStroke(shiftColor.getColor());
            }
        }
    }

    public void update() {
        for (DrawItem drawItem : annotations.keySet()) {
            update(drawItem);
        }
    }

    public void add() {
        for (DrawItem drawItem : atomBrowser.drawItems) {
            add(drawItem);
        }
    }

    private static class ShiftColor {
        private Float shift;
        private Color color;
        public ShiftColor() {
            this(null,null);
        }
        public ShiftColor(Float shift, Color color) {
            this.shift = shift;
            this.color = color;
        }

        public Float getShift() {
            return shift;
        }

        public void setShift(Float shift) {
            this.shift = shift;
        }

        public Color getColor() {
            return color;
        }

        public void setColor(Color color) {
            this.color = color;
        }
    }

    public ShiftColor getShiftColor(int dimno, DrawItem drawItem) {
        ShiftColor shiftColor = new ShiftColor();

        PolyChart chart = drawItem.getChart();

        if (atom.getResonance() == null || chart == null) {
            return shiftColor;
        }

        for (PeakListAttributes peakAttr : chart.getPeakListAttributes()) {
            PeakList peakList = peakAttr.getPeakList();

            for (PeakDim peakDim : atom.getResonance().getPeakDims()) {
                if (peakDim.getPeakList() == peakList) {
                    if (peakDim.getSpectralDim() == dimno) {
                        shiftColor.setShift(peakDim.getChemShiftValue());
                        if (peakDim.isFrozen()) {
                            shiftColor.setColor(Color.RED);
                        } else {
                            shiftColor.setColor(Color.BLACK);
                        }
                        return shiftColor;
                    }
                }
            }
        }
        return shiftColor;
    }

    private void addAnnotation(AnnoLineWithText annoLine, DrawItem drawItem) {
        annotations.putIfAbsent(drawItem, new ArrayList<>());
        if (!annotations.get(drawItem).contains(annoLine)) {
            annotations.get(drawItem).add(annoLine);
            drawItem.addAnnotation(annoLine);
            drawItem.addLocateItem(this);
        }
    }

    public void add(DrawItem drawItem) {
        boolean refresh = false;
        ShiftColor shiftColor = getShiftColor(atomBrowser.centerDim, drawItem);
        AnnoLineWithText annoLine;
        if (shiftColor.getShift() != null) {
            annoLine = new AnnoLineWithText(atom.getShortName(), shiftColor.getShift(), 0.99, shiftColor.getShift(), 0, shiftColor.getShift(), 1, CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.FRACTION);
            annoLine.setStroke(shiftColor.getColor());
            addAnnotation(annoLine, drawItem);
            refresh = true;
        }
        shiftColor = getShiftColor(atomBrowser.rangeDim, drawItem);
        if (shiftColor.getShift() != null) {
            annoLine = new AnnoLineWithText(atom.getShortName(), 0.01, shiftColor.getShift(), 0, shiftColor.getShift(), 1, shiftColor.getShift(), CanvasAnnotation.POSTYPE.FRACTION, CanvasAnnotation.POSTYPE.WORLD);
            annoLine.setStroke(shiftColor.getColor());
            addAnnotation(annoLine, drawItem);
            refresh = true;
        }
        if (refresh) {
            drawItem.getChart().refresh();
        }
    }
}
