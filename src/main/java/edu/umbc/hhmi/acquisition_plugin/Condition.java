package edu.umbc.hhmi.acquisition_plugin;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.star.*;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;

public class Condition implements SaveframeWriter {

    static void doStartup() {
        ConditionSaveframeProcessor conditionSaveFrameProcessor = new ConditionSaveframeProcessor();
        ProjectBase.addSaveframeProcessor("sample_conditions", conditionSaveFrameProcessor);
    }

    //todo: I think this fails on project replace, as happens if you start setting things up before saving
    //todo: Either needs to be added to the project or detect project type on plugin load? Seems fragile.
    static HashMap<ProjectBase, ObservableList> projectConditionsMap= new HashMap<>();
    public static ConditionListSceneController conditionListController;

    static int count=0;

    //possibly this should just be a string
    StringProperty name=new SimpleStringProperty();
    DoubleProperty temperature=new SimpleDoubleProperty();
    DoubleProperty pressure=new SimpleDoubleProperty(1.0);
    DoubleProperty pH=new SimpleDoubleProperty();
    StringProperty details=new SimpleStringProperty(".");

    int id;

    public Condition(String name) {
        setName(name);
        getActiveConditionList().add(this);
        ProjectBase.getActive().addSaveframe(this);
        count++;
        id=count;
    }

    static public ObservableList<Condition> getActiveConditionList() {
        ObservableList<Condition> conditionList = projectConditionsMap.get(ProjectBase.getActive());
        if (conditionList == null) {
            conditionList = FXCollections.observableArrayList();
            projectConditionsMap.put(ProjectBase.getActive(),conditionList);
        }
        return conditionList;
    }

    public void remove(boolean prompt) {
    //todo: implement
    }


    public static Condition get(String name) {
        for (Condition condition : getActiveConditionList()) {
            if (condition.getName().equals(name)) {
                return condition;
            }
        }
        return null;

    }

    public static void addNew () {
        String base="New Condition ";
        int suffix=1;
        while (Condition.get(base+suffix)!=null) {
            suffix++;
        }
        new Condition(base+suffix);
    }

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

    public int getId() {
        return id;
    }

    /*public String getDetails() {
        return details;
    }

    public void setDetails(String text) {
        details=text;
    }*/

    private String getpHErr() {
        //todo
        return "0";
    }

    /*private String getPh() {
        return pH.toString();
    }*/

    private String getPressureErr() {
        return "0";
    }

    /*private String getPressure() {
        return pressure.toString();
    }*/

    private String getTemperatureErr() {
        return "0";
    }

    /*private String getTemperature() {
        return temperature.toString();
    }*/

    public void setVariable(String type, double val, double valErr) {
        switch (type) {
            case "temperature":
                setTemperature(val);
                break;
            case "pressure":
                setPressure(val);
                break;
            case "pH":
                setpH(val);
                break;
            default:
                System.out.println("Couldn't process condition value for "+type);
        }
    }

    public double getTemperature() {
        return temperature.get();
    }

    public DoubleProperty temperatureProperty() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature.set(temperature);
    }

    public double getPressure() {
        return pressure.get();
    }

    public DoubleProperty pressureProperty() {
        return pressure;
    }

    public void setPressure(double pressure) {
        this.pressure.set(pressure);
    }

    public double getpH() {
        return pH.get();
    }

    public DoubleProperty pHProperty() {
        return pH;
    }

    public void setpH(double pH) {
        this.pH.set(pH);
    }

    public String getDetails() {
        return details.get();
    }

    public StringProperty detailsProperty() {
        return details;
    }

    public void setDetails(String details) {
        this.details.set(details);
    }

    @FXML
    static void showConditionList(ActionEvent event) {
        if (conditionListController == null) {
            conditionListController = ConditionListSceneController.create();
            conditionListController.setConditionList(Condition.getActiveConditionList());
        }
        if (conditionListController != null) {
            /* todo: this should happen when project changes instead really
                     or do we need to set up and update a single ObservableList? */
            conditionListController.setConditionList(Condition.getActiveConditionList());
            conditionListController.getStage().show();
            conditionListController.getStage().toFront();
        } else {
            System.out.println("Couldn't make conditionListController ");
        }
    }


    @Override
    public void write(Writer chan) throws ParseException, IOException {
        //categoryName is sample_conditions
        //id is from getName()
        //category is sample_conditions
        chan.write("save_sample_conditions"+getName().replaceAll("\\W", "")+"\n");
        chan.write("_Sample_condition_list.ID                          ");
        chan.write(getId() + "\n");
        chan.write("_Sample_condition_list.Name                ");
        chan.write("'"+getName() + "'\n");
        chan.write("_Sample_condition_list.Sf_category                 ");
        chan.write("sample_conditions\n");
        chan.write("_Sample_condition_list.Sf_framecode                ");
        chan.write("sample_conditions"+getName().replaceAll("\\W", "")+"\n");
        chan.write("_Sample_condition_list.Details                        ");
        chan.write("'"+getDetails() + "'\n");
        chan.write("\n");

        chan.write("loop_\n");
        chan.write("_Sample_condition_variable.Sample_condition_list_ID\n");
        chan.write("_Sample_condition_variable.Type\n");
        chan.write("_Sample_condition_variable.Val\n");
        chan.write("_Sample_condition_variable.Val_err\n");
        chan.write("_Sample_condition_variable.Val_units\n");
        chan.write("\n");

        chan.write(String.format("%d temperature %s %s K\n",getId(),getTemperature(),getTemperatureErr()));
        chan.write(String.format("%d pressure    %s %s atm\n",getId(),getPressure(),getPressureErr()));
        chan.write(String.format("%d pH          %s %s -\n",getId(),getpH(), getpHErr()));

        chan.write("stop_\n");
        chan.write("save_\n\n");
    }

    public void readSTARSaveFrame(Saveframe saveframe) throws ParseException {
        setDetails(saveframe.getValue("_Sample_condition_list", "Details").replace("^'", "").replace("'$",""));
        Loop loop = saveframe.getLoop("_Sample_condition_variable");
        if (loop != null) {
            List<String> typeLabels = loop.getColumnAsList("Type");
            List<String> valString = loop.getColumnAsList("Val");
            List<String> valErrString = loop.getColumnAsList("Val_err");

            for (int i = 0; i<typeLabels.size(); i++) {
                setVariable(typeLabels.get(i),Double.parseDouble(valString.get(i)),Double.parseDouble(valErrString.get(i)));
            }
        }

    }
}
