package edu.umbc.hhmi.acquisition_plugin;


import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import org.nmrfx.utils.GUIUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class Experiment {
    //Any reason experiments shouldn't be global?
    static public ObservableList<Experiment> experimentList = FXCollections.observableArrayList();
    static public ObservableList<Experiment> getActiveExperimentList() {
        return experimentList;
    }

    /*static HashMap<ProjectBase, ObservableList> projectExperimentsMap = new HashMap<>();
    static public ObservableList<Experiment> getActiveExperimentList() {
        ObservableList<Experiment> experimentList = projectExperimentsMap.get(ProjectBase.getActive());
        if (experimentList == null) {
            experimentList = FXCollections.observableArrayList();
            projectExperimentsMap.put(ProjectBase.getActive(),experimentList);
        }
        return experimentList;
    }
    */

    static void doStartup() {
        readPar("data/experiments");
    }

    public static ExperimentListSceneController experimentListController;

    @FXML
    static void showExperimentList(ActionEvent event) {
        if (experimentListController == null) {
            experimentListController = ExperimentListSceneController.create();
            experimentListController.setExperimentList(Experiment.getActiveExperimentList());
        }
        if (experimentListController != null) {
            experimentListController.setExperimentList(Experiment.getActiveExperimentList());
            experimentListController.getStage().show();
            experimentListController.getStage().toFront();
        } else {
            System.out.println("Couldn't make experimentListController ");
        }
    }


    public class ExpDims implements Iterable<ExpDim> {
        boolean obs;
        public ExpDims(boolean obs) {
            this.obs=obs;
        }
        @Override
        public Iterator<ExpDim> iterator() {
            return new Iterator<>() {

                private ExpDim following=first;
                {
                    if (obs) {
                        while (following != null && !following.isObserved()) {
                            following = following.getNextExpDim();
                        }
                    }
                }

                @Override
                public boolean hasNext() {
                    return following!=null;
                }

                @Override
                public ExpDim next() {
                    if (following == null) {
                        throw new NoSuchElementException();
                    }
                    ExpDim toReturn = following;
                    if (obs) {
                        following = following.getNextExpDim();
                        while (following != null && !following.isObserved()) {
                            following = following.getNextExpDim();
                        }
                    } else {
                        following = following.getNextExpDim();
                    }
                    return toReturn;
                }
            };
        }
    }
    public ExpDims expDims = new ExpDims(false);
    public ExpDims obsDims = new ExpDims(true);

    //public Iterator<ExpDim> obsIterator = obsDims.iterator();
    private final StringProperty name=new SimpleStringProperty();
    private int size;
    private final IntegerProperty numObsDims=new SimpleIntegerProperty();
    private final StringProperty description=new SimpleStringProperty();


    private ExpDim first;
    private ExpDim last;

    public Experiment(String name){
        setName(name);
        size=0;
        numObsDims.set(0);
        //UmbcProject.experimentList.add(this);
    }

    public Experiment(String name, String code){
        setName(name);
        size=0;
        numObsDims.set(0);
        //todo implement as name:experiment map to make replacing easier
        String[] codeStrings=code.split("\\|");
        for (int i=0;i<codeStrings.length;) {
            Connectivity connectivity=createConnectivity(codeStrings[i++]);
            ExpDim expDim=new ExpDim(codeStrings[i++]);
            this.add(connectivity,expDim);
        }
        getActiveExperimentList().add(this);
    }

    public static Experiment get(String name) {
        for (Experiment experiment : getActiveExperimentList()) {
            if (experiment.getName().equals(name)) {
                return experiment;
            }
        }
        return null;
    }

    public Connectivity createConnectivity(String code) {
        if (code.equals("")) {
            return null;
        } else {
            return new Connectivity(code);
        }
    }

    public String toCode() {
        StringBuilder toReturn= new StringBuilder();
        for (ExpDim expDim : expDims) {
            toReturn.append(expDim.getNextCon(false) == null ? "" : "|" + expDim.getNextCon(false).toCode());
            toReturn.append("|");
            toReturn.append(expDim.toCode());
        }
        return toReturn.toString();
    }

    @Override
    public String toString() {
        return getName();
    }

    public String getName() {
        return name.get();
    }

    public String describe() {
        StringBuilder toReturn= new StringBuilder();
        for (ExpDim expDim : expDims) {
            toReturn.append(expDim.getNextCon(false) == null ? "" : expDim.getNextCon(false).toString() + "→");
            toReturn.append(expDim).append("(").append(expDim.getNucleus().getNumberName()).append("): ").append(expDim.getPattern());
            toReturn.append(expDim.getNextCon(true) == null ? "" : "→");
        }
        return toReturn.toString();
    }
    public int getNumObsDims() {
        return numObsDims.get();
    }
    public IntegerProperty numObsDimsProperty() {
        return numObsDims;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public void remove(boolean prompt) {
        boolean delete=true;
        if (prompt) {
            delete = GUIUtils.affirm("Are you sure you want to delete? This cannot be undone.");
        }
        if (delete) {
            getActiveExperimentList().remove(this);
            writePar("data/experiments");
        }
    }

    public static void addNew() {
        new ExperimentSetup();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public static class Pair {
        public final Connectivity connectivity;
        public final ExpDim expDim;
        public Pair(Connectivity connectivity, ExpDim expDim) {
            this.connectivity =connectivity;
            this.expDim = expDim;
        }
    }

    public final boolean add (Pair... values) {
        ExpDim new_first=null;
        ExpDim new_last=null;
        Connectivity new_first_con=null;
        int new_size=size;
        int newNumObsDims=numObsDims.get();
        boolean added = false;
        for (Pair pair : values) {
            if (pair.expDim == null) {
                return false;
            }
            if (new_first == null) {
                new_first = pair.expDim;
                new_first_con=pair.connectivity;
                new_last = pair.expDim;
                added = true;
            } else if (pair.connectivity != null) {
                new_last.setNext(pair.connectivity, pair.expDim);
                new_last=pair.expDim;
                added = true;
            } else {
                return false;
            }
            new_size += 1;
            if ((pair.expDim).isObserved()) {
                newNumObsDims += 1;
            }
        }
        if (added) {
            if (first==null) {
                first=new_first;
            } else {
                if (new_first_con==null) {
                    return false;
                }
                last.setNext(new_first_con,new_first);
            }
            last=new_last;
            size=new_size;
            numObsDims.set(newNumObsDims);
        }
        setDescription(describe());
        return added;
    }

    public boolean add (Connectivity connectivity, ExpDim expDim) {
        boolean added=false;
        if (expDim==null) {return false;}
        if (first==null) {
            first=expDim;
            last=expDim;
            added=true;
        } else if (connectivity!=null) {
            last.setNext(connectivity,expDim);
            last=expDim;
            added=true;
        }
        if (added) {
            size+=1;
            if (expDim.isObserved()) {numObsDims.set(numObsDims.get()+1);}
        }
        setDescription(describe());
        return added;
    }

    public ExpDim getFirst() {
        return first;
    }

    public ExpDim getLast() {
        return last;
    }

    public int getSize() {
        return size;
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public void setDescription(String description) {
        this.description.set(description);
    }

    //fixme: choose a better location - currently I think user changes will be lost when updating NMRFx
    public static void writePar(String resourceName) {
        ClassLoader cl = ClassLoader.getSystemClassLoader();

        try (PrintStream pStream = new PrintStream(cl.getResource(resourceName).getPath())) {
            for (Experiment experiment : getActiveExperimentList()) {
                pStream.printf("%s = %s\n", experiment.getName(),experiment.toCode());
            }
        } catch (IOException ioE) {
            System.out.println("error " + ioE.getMessage());
        }
    }

    public static void readPar(String resourceName) {
        try {
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            InputStream iStream = cl.getResourceAsStream(resourceName);
            Scanner inputStream = new Scanner(iStream);
            while (inputStream.hasNextLine()) {
                String data = inputStream.nextLine();
                if (!data.isEmpty()) {
                    String[] arrOfStr = data.split("=");
                    if (arrOfStr.length != 2) {
                        System.out.println("Error reading experiment: " + data);
                    } else {
                        String name = arrOfStr[0].trim();
                        String code = arrOfStr[1].trim();
                        new Experiment(name, code);
                    }
                }
            }
            iStream.close();
        } catch (IOException e) {
            System.out.println("Couldn't read "+resourceName);
        }
    }
}
