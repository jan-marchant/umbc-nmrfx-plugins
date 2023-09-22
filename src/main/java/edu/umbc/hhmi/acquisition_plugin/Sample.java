package edu.umbc.hhmi.acquisition_plugin;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Entity;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.peaks.ManagedList;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.project.ProjectUtilities;
import org.nmrfx.star.Loop;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;
import org.nmrfx.star.SaveframeWriter;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.utils.GUIUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class Sample implements Comparable<Sample>, SaveframeWriter {

    static void doStartup() {
        SampleSaveframeProcessor sampleSaveFrameProcessor = new SampleSaveframeProcessor();
        ProjectBase.addSaveframeProcessor("sample", sampleSaveFrameProcessor);
    }

    //todo: I think this fails on project replace, as happens if you start setting things up before saving
    //todo: Either needs to be added to the project or detect project type on plugin load? Seems fragile.
    static HashMap<ProjectBase, ObservableList<Sample>> projectSamplesMap= new HashMap<>();
    public static SampleListSceneController sampleListController;

    static public ObservableList<Sample> getActiveSampleList() {
        return projectSamplesMap.computeIfAbsent(ProjectBase.getActive(), k -> FXCollections.observableArrayList());
    }

    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<Molecule> molecule = new SimpleObjectProperty<>();
    private final StringProperty labelString = new SimpleStringProperty("");
    private final HashMap<Atom, Double> atomFraction = new HashMap<>();
    private final HashMap<Entity, String> entityLabelString = new HashMap<>();

    public Sample(String name) {
        setName(name);
        setMolecule(Molecule.getActive());
        getActiveSampleList().add(this);
        ProjectBase.getActive().addSaveframe(this);
        ProjectUtilities.sortExtraSaveFrames();
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public int compareTo(Sample other) {
        return this.getName().compareTo(other.getName());
    }

    public String getLabelString() {
        return labelString.get();
    }

    public StringProperty labelStringProperty() {
        return labelString;
    }

    public ArrayList<ManagedList> getAssociatedLists() {
        ArrayList<ManagedList> toReturn = new ArrayList<>();
        for (Acquisition acquisition : Acquisition.getActiveAcquisitionList()) {
            if (acquisition.getSample()==this) {
                toReturn.addAll(acquisition.getManagedLists());
            }
        }
        return toReturn;
    }

    public void remove(boolean prompt) {
//todo implement
    }

    public static void addNew() {
        if (Molecule.getActive() == null) {
            GUIUtils.warn("Error", "Molecule must be set before adding samples");
            return;
        }
        String base = "New Sample ";
        int suffix = 1;
        while (Sample.get(base + suffix) != null) {
            suffix++;
        }
        new Sample(base + suffix);
    }

    public static Sample get(String name) {
        for (Sample sample : getActiveSampleList()) {
            if (sample.getName().equals(name)) {
                return sample;
            }
        }
        return null;
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

    public Molecule getMolecule() {
        return molecule.get();
    }

    public ObjectProperty<Molecule> moleculeProperty() {
        return molecule;
    }

    public void setMolecule(Molecule molecule) {
        this.molecule.set(molecule);
    }

    public void setupLabels() {
        boolean rna = false;
        Entity rnaEnt = null;
        if (molecule.get() != null) {
            for (Entity entity : molecule.get().entities.values()) {
                if (entity instanceof Polymer && ((Polymer) entity).isRNA()) {
                    rna = true;
                    rnaEnt = entity;
                }
            }
            if (rna) {
                if (RNALabelsSceneController.rnaLabelsController == null) {
                    RNALabelsSceneController.rnaLabelsController = RNALabelsSceneController.create();
                }
                if (RNALabelsSceneController.rnaLabelsController != null) {
                    RNALabelsSceneController.rnaLabelsController.getStage().show();
                    RNALabelsSceneController.rnaLabelsController.getStage().toFront();
                } else {
                    System.out.println("Couldn't make rnaLabelsController ");
                }
                RNALabelsSceneController.rnaLabelsController.setSampleAndEntity(this, rnaEnt);
            } else {
                GUIUtils.warn("Not implemented", "Sorry, labelling is only for RNA at the moment");
            }
        } else {
            GUIUtils.warn("No active molecule", "Add a molecule before setting up sample");
        }
    }

    public void setupLabels(Entity entity) {
        boolean rna = false;
        if (molecule.get() != null) {
            if (entity instanceof Polymer && ((Polymer) entity).isRNA()) {
                rna = true;
            }
            if (rna) {
                if (RNALabelsSceneController.rnaLabelsController == null) {
                    RNALabelsSceneController.rnaLabelsController = RNALabelsSceneController.create();
                }
                if (RNALabelsSceneController.rnaLabelsController != null) {
                    RNALabelsSceneController.rnaLabelsController.getStage().show();
                    RNALabelsSceneController.rnaLabelsController.getStage().toFront();
                } else {
                    System.out.println("Couldn't make rnaLabelsController ");
                }
                RNALabelsSceneController.rnaLabelsController.setSampleAndEntity(this, entity);
            } else {
                GUIUtils.warn("Not implemented", "Sorry, labelling is only for RNA at the moment");
            }
        } else {
            GUIUtils.warn("No active molecule", "Add a molecule before setting up sample");
        }
    }

    public double getAtomFraction(Atom atom) {
        //TODO: support labeling schemes like A28/Ar (i.e. should not see A2/8-Ar NOEs from same residue in same molecule
        // means subtle difference in label string parsing
        //TODO: Add GUI support for setting labeling percent

        if (atom != null) {
            double fraction;
            try {
                fraction = atomFraction.get(atom);
            } catch (Exception e) {
                fraction = getFraction(atom);
                if (fraction > 1.0) {
                    System.out.println("Check labeling string - " + atom.getFullName() + " is apparently " + fraction * 100 + "% labeled");
                    fraction = 1.0;
                }
                atomFraction.put(atom, fraction);
            }
            return fraction;
        } else {
            System.out.println("Couldn't find atom");
            return 0.0;
        }
    }

    public double getFraction(Atom atom) {
        //TODO: implement label scheme setup for arbitrary molecules
        if (atom != null) {
            if (atom.getTopEntity() instanceof Polymer) {
                if (((Polymer) atom.getTopEntity()).isRNA()) {
                    return RNALabels2.atomPercentLabelString(atom, getLabelString()) / 100;
                }
            }
            return 1.0;
        } else {
            System.out.println("Couldn't find atom");
            return 0;
        }
    }

    public String getEntityLabelString(Entity entity) {
        if (entity!=null) {
            entityLabelString.putIfAbsent(entity, "");
            return entityLabelString.get(entity);
        } else {
            return "";
        }
    }

    public void setEntityLabelString(Entity entity, String labels) {
        if (!labels.equals(getEntityLabelString(entity))) {
            Optional<ButtonType> result;
            if (getAssociatedLists().size() > 0) {
                if (getEntityLabelString(entity).equals("")) {
                    result = Optional.of(ButtonType.OK);
                } else {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Label Scheme Changed");
                    alert.setHeaderText("Reset labelling information for " + entity.getName() + "?");
                    alert.setContentText("This includes deleting all associated managed peakLists:" + getAssociatedLists());
                    result = alert.showAndWait();
                }
            } else {
                result = Optional.of(ButtonType.OK);
            }
            if (result.isPresent()) {
                if (result.get() == ButtonType.OK) {
                    atomFraction.clear();
                    entityLabelString.put(entity, labels.replace(entity.getName() + ":", ""));
                    updateLabelString();
                    for (Acquisition acquisition : Acquisition.getActiveAcquisitionList()) {
                        if (acquisition.getSample() == this) {
                            acquisition.resetAcquisitionTree();
                        }
                    }
                    for (ManagedList list : getAssociatedLists()) {
                        PeakList.remove(list.getName());
                    }
                }
            }
        }
    }

    public int getId() {
        //fixme
        return 0;
    }

    public ArrayList<Entity> getEntities() {
        return molecule.get().getEntities();
    }

    private void updateLabelString() {
        StringBuilder labels= new StringBuilder();
        for (Entity entity : entityLabelString.keySet()) {
            for (String group : entityLabelString.get(entity).split("[; ]")) {
                labels.append(entity.getName()).append(":").append(group).append(" ");
            }
        }
        labelString.set(labels.toString());
    }

    @FXML
    static void showSampleList(ActionEvent event) {
        if (sampleListController == null) {
            sampleListController = SampleListSceneController.create();
            sampleListController.setSampleList(Sample.getActiveSampleList());
        }
        if (sampleListController != null) {
            sampleListController.setSampleList(Sample.getActiveSampleList());
            sampleListController.getStage().show();
            sampleListController.getStage().toFront();
        } else {
            System.out.println("Couldn't make sampleListController ");
        }
    }

    public void readSTARSaveFrame(Saveframe saveframe) throws ParseException {
        Loop loop = saveframe.getLoop("_Sample_component");
        if (loop != null) {
            List<String> entityLabels = loop.getColumnAsList("Mol_common_name");
            List<String> starEntityLabelString = loop.getColumnAsList("Isotopic_labeling");

            for (int i = 0; i<entityLabels.size(); i++) {
                Entity entity = Molecule.getActive().entityLabels.get(entityLabels.get(i));
                String labels = starEntityLabelString.get(i).replace("^'", "").replace("'$","");
                entityLabelString.put(entity, labels.replace(entity.getName()+":",""));
                updateLabelString();
            }
        }
    }

    @Override
    public void write(Writer chan) throws IOException {
        if (molecule.get() == null) {
            return;
        }
        chan.write("save_sample_" + getName().replaceAll("\\W", "") + "\n");
        chan.write("_Sample.ID                          ");
        chan.write(getId() + "\n");
        chan.write("_Sample.Name                        ");
        chan.write("'" + getName() + "'\n");
        chan.write("_Sample.Sf_category                 ");
        chan.write("sample\n");
        chan.write("_Sample.Sf_framecode                ");
        chan.write("sample_" + getName().replaceAll("\\W", "") + "\n");
        chan.write("_Sample.Type                        ");
        chan.write(".\n");
        chan.write("\n");

        //fixme: handle multiple components with independent labeling. Reinstate IsotopeLabels class
        // and replace "molecule" column of table with component, splitting node into number of entities, each with independent labelString

        chan.write("loop_\n");
        chan.write("_Sample_component.Sample_ID\n");
        chan.write("_Sample_component.ID\n");
        chan.write("_Sample_component.Mol_common_name\n");
        //fixme: ? in STAR3 dictionary it says this should be a recognized standard...
        chan.write("_Sample_component.Isotopic_labeling\n");
        chan.write("\n");

        int entityID = 1;
        for (Entity entity : molecule.get().entityLabels.values()) {
            chan.write(String.format("%d %d %s '%s'", getId(), entityID, entity.label, getLabelString().equals("") ? "*" : getLabelString()));
            chan.write("\n");
            entityID++;
        }
        chan.write("stop_\n");
        chan.write("save_\n\n");
    }
}
