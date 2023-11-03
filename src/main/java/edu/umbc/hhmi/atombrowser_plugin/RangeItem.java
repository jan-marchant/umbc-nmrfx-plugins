package edu.umbc.hhmi.atombrowser_plugin;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.KeyBindings;

public class RangeItem {

    StringProperty name = new SimpleStringProperty();
    DoubleProperty min = new SimpleDoubleProperty();
    DoubleProperty max = new SimpleDoubleProperty();

    RangeItem(String name, double min, double max) {
        setName(name);
        setMin(min);
        setMax(max);
    }

    public void remove(AtomBrowser browser) {
        browser.getRangeItems().remove(this);
    }

    @Override
    public String toString() {
        return getName();
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public double getMin() {
        return min.get();
    }

    public DoubleProperty minProperty() {
        return min;
    }

    public void setMin(double min) {
        this.min.set(min);
    }

    public double getMax() {
        return max.get();
    }

    public DoubleProperty maxProperty() {
        return max;
    }

    public void setMax(double max) {
        this.max.set(max);
    }
}
