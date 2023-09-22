package edu.umbc.hhmi.atombrowser_plugin;

import edu.umbc.hhmi.acquisition_plugin.Acquisition;
import edu.umbc.hhmi.acquisition_plugin.Condition;
import edu.umbc.hhmi.acquisition_plugin.Experiment;
import edu.umbc.hhmi.acquisition_plugin.Sample;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.nmrfx.peaks.ManagedList;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.project.Project;

public class FilterItem {

    Object object;
    public BooleanProperty allow = new SimpleBooleanProperty();

    public StringProperty name = new SimpleStringProperty();
    public StringProperty type = new SimpleStringProperty();

    FilterItem(Object object, boolean allow) {
        this.object = object;
        setAllow(allow);
        setName(getObjectName());
        setType(getObjectClassName());
    }

    public String getName() {
        return name.get();
    }

    public boolean isAllow() {
        return allow.get();
    }

    public BooleanProperty allowProperty() {
        return allow;
    }

    public void setAllow(boolean allow) {
        this.allow.set(allow);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public String getType() {
        return type.get();
    }

    public StringProperty typeProperty() {
        return type;
    }

    public void setType(String type) {
        this.type.set(type);
    }

    public String getObjectName() {
        return object.toString();
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    public String getObjectClassName() {
        return object.getClass().getSimpleName();
    }

    public void remove(AtomBrowser browser) {
        browser.filterList.remove(this);
    }

    public boolean matches(DrawItem drawItem) {
        if (object instanceof Dataset) {
            return drawItem.dataset == getObject();
        } else if (object instanceof PeakList) {
            return drawItem.peakList == getObject();
        } else if (object instanceof Project) {
            return drawItem.project == getObject();
        } else if (object instanceof Acquisition) {
            return ((ManagedList) drawItem.peakList).getAcquisition() == object;
        } else if (object instanceof Experiment) {
            return ((ManagedList) drawItem.peakList).getAcquisition().getExperiment() == object;
        } else if (object instanceof Sample) {
            return ((ManagedList) drawItem.peakList).getAcquisition().getSample() == object;
        } else if (object instanceof Condition) {
            return ((ManagedList) drawItem.peakList).getAcquisition().getCondition() == object;
        }
        return false;
    }

}
