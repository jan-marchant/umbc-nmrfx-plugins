package edu.umbc.hhmi.acquisition_plugin;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.peaks.ManagedList;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.utils.GUIUtils;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.UnaryOperator;

public class ManagedListSetup {

    Stage stage;
    BorderPane borderPane;
    double xOffset = 50;
    Acquisition acquisition;

    TextField nameField=new TextField();
    ChoiceBox<Integer> ppmSetChoices = new ChoiceBox<>();
    ChoiceBox<Integer> rPpmSetChoices = new ChoiceBox<>();
    //all NOE dims must use same NoeSet
    ComboBox<ManagedNoeSet> noeSet = new ComboBox<>();
    HashMap<ExpDim,ComboBox<Integer>> dimBoxes=new HashMap<>();
    boolean dimBoxesOk=true;
    boolean noesOk=true;
    //TextField bondField = new TextField();
    //TextField minField = new TextField();
    //TextField maxField = new TextField();

    public ManagedListSetup(Acquisition acquisition) {
        this.acquisition=acquisition;
        if (acquisition.getDataset()==null || acquisition.getSample()==null || acquisition.getExperiment()==null) {
            GUIUtils.warn("Cannot add list", "You must define all acquisition parameters before adding any lists.");
        } else {
            create();
            show();
        }
    }


    public void create() {
        stage = new Stage(StageStyle.DECORATED);
        borderPane = new BorderPane();
        Scene scene = new Scene(borderPane);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setTitle("Managed List Setup");
        stage.setAlwaysOnTop(true);

        HashMap<Nuclei, ObservableList<Integer>> datasetMap=new HashMap<>();
        for (int dim =0;dim<acquisition.getDataset().getNDim();dim++) {
            datasetMap.putIfAbsent(acquisition.getDataset().getNucleus(dim), FXCollections.observableArrayList());
            datasetMap.get(acquisition.getDataset().getNucleus(dim)).add(dim);
        }

        Label nameLabel=new Label("Name:");

        String name="managed_"+acquisition.getDataset().getName().split("\\.")[0];

        if (PeakList.get(name)!=null) {
            int suffix = 2;
            while (PeakList.get(name + suffix) != null) {
                suffix += 1;
            }
            nameField.setText(name+ suffix);
        } else {
            nameField.setText(name);
        }
        nameField.setPrefWidth(300);
        /*
        Label ppmSetLabel=new Label("PPM Set: ");

        ppmSetChoices.getItems().add(0);
        ppmSetChoices.getItems().add(1);
        ppmSetChoices.getItems().add(2);
        ppmSetChoices.getItems().add(3);
        ppmSetChoices.getItems().add(4);
        ppmSetChoices.setValue(0);
        */

        Label rPpmSetLabel=new Label("Ref PPM Set: ");

        rPpmSetChoices.getItems().add(0);
        rPpmSetChoices.getItems().add(1);
        rPpmSetChoices.getItems().add(2);
        rPpmSetChoices.getItems().add(3);
        rPpmSetChoices.getItems().add(4);
        rPpmSetChoices.setValue(0);

        HBox hBox=new HBox(nameLabel,nameField);
        HBox hBox2=new HBox(rPpmSetLabel,rPpmSetChoices);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox2.setAlignment(Pos.CENTER_LEFT);
        VBox vBox=new VBox(hBox,hBox2);
        UnaryOperator<TextFormatter.Change> bondFilter = change -> {
            String newText = change.getControlNewText();
            if (newText.matches("([1-5,])?")) {
                return change;
            }
            return null;
        };

        UnaryOperator<TextFormatter.Change> integerFilter = change -> {
            String newText = change.getControlNewText();
            if (newText.matches("([1-5])?")) {
                return change;
            }
            return null;
        };

        Button ok = new Button("OK");
        Button cancel = new Button("Cancel");
        ok.setOnAction((event) -> {
            doCreate();
            stage.close();
        });

        cancel.setOnAction(e -> stage.close());

        Label connLabel;
        GridPane connPane=new GridPane();
        int row=0;
        for (ExpDim expDim : acquisition.getExperiment().expDims) {
            connLabel = new Label(expDim.toString() + "("+expDim.getNucleus().getNumberName()+"): "+expDim.getPattern());
            connPane.add(connLabel, 0, row, 1, 1);
            GridPane.setHgrow(connLabel, Priority.ALWAYS);
            ComboBox<Integer> dimBox=new ComboBox<>();
            dimBox.setMaxWidth(Double.MAX_VALUE);
            if (expDim.isObserved()) {
                dimBox.setItems(datasetMap.get(expDim.getNucleus()));
                dimBox.setPromptText("Dim:");
                if (datasetMap.get(expDim.getNucleus()).size()==1) {
                    dimBox.setValue(datasetMap.get(expDim.getNucleus()).get(0));
                    dimBox.setDisable(true);
                } else {
                    dimBoxesOk=false;
                    ok.setDisable(true);
                    dimBox.valueProperty().addListener((observable,oldValue,newValue) -> {
                        if (newValue!=null) {
                            List<ComboBox<Integer>> blank=new ArrayList<>();
                            List<Integer> notSeen = new ArrayList<>(dimBox.getItems());
                            notSeen.remove(newValue);
                            int seen=0;
                            for (ExpDim obsDim : acquisition.getExperiment().obsDims) {
                                if (!obsDim.equals(expDim)) {
                                    if (obsDim.getNucleus() == expDim.getNucleus()) {
                                        if (Objects.equals(dimBoxes.get(obsDim).getValue(), newValue)) {
                                            dimBoxes.get(obsDim).setValue(null);
                                        }
                                        if (dimBoxes.get(obsDim).getValue() == null) {
                                            blank.add(dimBoxes.get(obsDim));
                                        } else {
                                            notSeen.remove(dimBoxes.get(obsDim).getValue());
                                        }
                                    }
                                }
                                if (dimBoxes.get(obsDim).getValue()!=null) {
                                    seen++;
                                }
                            }
                            if (blank.size()==1 && notSeen.size()==1) {
                                blank.get(0).setValue(notSeen.get(0));
                            }
                            if (seen==dimBoxes.size()) {
                                dimBoxesOk=true;
                                if (noesOk) {
                                    ok.setDisable(false);
                                }
                            }
                        } else {
                            dimBoxesOk=false;
                            ok.setDisable(true);
                        }
                    });
                }
            } else {
                dimBox.setPromptText("-");
            }
            dimBox.setConverter(new StringConverter<>() {
                @Override
                public String toString(Integer dim) {
                    return acquisition.getDataset().getLabel(dim);
                }

                @Override
                public Integer fromString(String string) {
                    //return comboBox.getItems().stream().filter(ap ->ap.getName().equals(string)).findFirst().orElse(null);
                    return null;
                }
            });

            dimBoxes.put(expDim,dimBox);
            //GridPane.setFillWidth(dimBox, true);
            GridPane.setHalignment(dimBox, HPos.RIGHT);
            connPane.add(dimBox,1,row++,1,1);

            if (expDim.getNextExpDim()!=null) {
                //connLabel = new Label(expDim.toString() + "-" + expDim.getNextExpDim().toString() + ": ");
                String labString = "↓";
                labString += expDim.getNextCon().toString();
                switch (expDim.getNextCon().getType()) {
                    case NOE:
                        //labString += " using NOE set: ";
                        //noeType.getItems().setAll(Connectivity.NOETYPE.values());
                        noeSet.setMaxWidth(Double.MAX_VALUE);
                        if (ManagedNoeSet.getManagedNoeSetsMap(acquisition.getProject()).values().size()<1) {
                            ManagedNoeSet.addSet("default",acquisition.getProject());
                        }
                        noeSet.getItems().setAll(ManagedNoeSet.getManagedNoeSetsMap(acquisition.getProject()).values());
                        noeSet.setPromptText("NOE Set:");
                        noeSet.setConverter(new StringConverter<>() {

                            @Override
                            public String toString(ManagedNoeSet noeSet) {
                                Optional<Map.Entry<String, ManagedNoeSet>> optionalEntry = ManagedNoeSet.getManagedNoeSetsMap(acquisition.getProject()).entrySet().stream().filter(ap -> ap.getValue().equals(noeSet)).findFirst();
                                return (optionalEntry.map(Map.Entry::getKey).orElse(null));
                            }

                            @Override
                            public ManagedNoeSet fromString(String string) {
                                return ManagedNoeSet.getManagedNoeSetsMap(acquisition.getProject()).get(string);
                            }
                        });

                        connPane.add(new Label(labString), 0, row, 1, 1);
                        connPane.add(noeSet, 1, row++, 1, 1);
                        noesOk=false;
                        ok.setDisable(true);
                        noeSet.valueProperty().addListener((observable, oldValue, newValue) -> {
                            if (newValue != null) {
                                noesOk=true;
                                if (dimBoxesOk) {
                                    ok.setDisable(false);
                                }
                            } else {
                                noesOk=false;
                                ok.setDisable(true);
                            }
                        });
                        break;
                    case J:
                    /*bondField.setTextFormatter(
                            new TextFormatter<String>(new DefaultStringConverter(), expDim.getNextCon().getNumBonds(), bondFilter));
                     */
                    case TOCSY:
                    /*minField.setTextFormatter(
                            new TextFormatter<Integer>(new IntegerStringConverter(), expDim.getNextCon().getMinTransfers(), integerFilter));
                    maxField.setTextFormatter(
                            new TextFormatter<Integer>(new IntegerStringConverter(), expDim.getNextCon().getMaxTransfers(), integerFilter));
                     */
                    case HBOND:
                    default:
                        connPane.add(new Label(labString), 0, row++, 2, 1);
                        break;
                }
            }
        }

        ButtonBar buttonBar = new ButtonBar();
        ButtonBar.setButtonData(ok, ButtonBar.ButtonData.OK_DONE);
        ButtonBar.setButtonData(cancel, ButtonBar.ButtonData.CANCEL_CLOSE);
        buttonBar.getButtons().addAll(cancel, ok);

        ok.setOnAction(e -> doCreate());
        borderPane.setTop(vBox);
        borderPane.setCenter(connPane);
        borderPane.setBottom(buttonBar);
        stage.setAlwaysOnTop(true);
        stage.setOnCloseRequest(e -> cancel());
    }


    public void show() {
        Point p = MouseInfo.getPointerInfo().getLocation();
        List<Screen> screens = Screen.getScreens();

        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
        stage.show();
        Double x=null;
        Double y=null;

        if (p != null && screens != null) {
            Rectangle2D screenBounds;
            for (Screen screen : screens) {
                screenBounds=screen.getVisualBounds();
                if (screenBounds.contains(p.getX(),p.getY())) {
                    x=p.getX()-stage.getWidth()/2;
                    y=p.getY()-stage.getHeight()/2;
                    if (x+stage.getWidth()>screenBounds.getMaxX()) {
                        x=screenBounds.getMaxX()-stage.getWidth()-50;
                    }
                    if (x<screenBounds.getMinX()) {
                        x=screenBounds.getMinX()+50;
                    }
                    if (y+stage.getHeight()>screenBounds.getMaxY()) {
                        y=screenBounds.getMaxY()-stage.getHeight()-50;
                    }
                    if (y<screenBounds.getMinY()) {
                        y=screenBounds.getMinY()+50;
                    }
                }
            }
        }
        stage.centerOnScreen();
        if (x!=null) {
            stage.setX(x);
        }
        if (y!=null) {
            stage.setY(y);
        }
    }


    void cancel() {
        stage.close();
    }

    void doCreate() {
        if (acquisition.getDataset()==null || acquisition.getSample()==null || acquisition.getExperiment()==null) {
            GUIUtils.warn("Cannot add list", "You must define all acquisition parameters before adding any lists.");
        } else {
            acquisition.setSampleLoaded();
            HashMap<ExpDim,Integer> dimMap=new HashMap<>();
            for (Map.Entry<ExpDim,ComboBox<Integer>> entry : dimBoxes.entrySet()) {
                dimMap.put(entry.getKey(),entry.getValue().getValue());
            }
            ManagedList managedList=new ManagedList(acquisition,nameField.getText(),0,rPpmSetChoices.getValue(),noeSet.getValue(),dimMap);
            acquisition.getManagedLists().add(managedList);
        }
        stage.close();
    }
}
