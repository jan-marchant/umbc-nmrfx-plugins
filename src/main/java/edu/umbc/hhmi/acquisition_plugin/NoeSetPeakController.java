package edu.umbc.hhmi.acquisition_plugin;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.PeakMenuTarget;
import org.nmrfx.project.ProjectBase;

import java.util.Optional;

public class NoeSetPeakController implements PeakMenuTarget {

    private static NoeSetPeakController noeSetPeakController;

    double xOffset=50;

    private final Stage stage;
    private final GridPane grid;
    CheckBox getAssignments = new CheckBox("Get assignments");
    CheckBox getNOEs = new CheckBox("Get NOEs");
    ChoiceBox<String> mainProjPPMSetType =new ChoiceBox<>();
    ChoiceBox<Integer> mainProjPPMSet =new ChoiceBox<>();
    ChoiceBox<ManagedNoeSet> mainProjNoeSetCombo =new ChoiceBox<>();
    ObservableList<ManagedNoeSet> mainProjNoeSetList = FXCollections.observableArrayList();
    Button transferButton=new Button("Transfer Assignments");
    private PeakList peakList;
    private Label peakListLabel = new Label();
    private MenuButton peakListMenu = new MenuButton("List");


    public void show(double x, double y) {
        updatePeakListMenu();
        mainProjNoeSetList.clear();
        mainProjNoeSetList.addAll(ManagedNoeSet.getManagedNoeSetsMap().values());
        if (ManagedNoeSet.getManagedNoeSetsMap().values().size()==1) {
            mainProjNoeSetCombo.setValue(ManagedNoeSet.getManagedNoeSetsMap().values().iterator().next());
        } else {
            mainProjNoeSetCombo.setValue(null);
        }

        mainProjPPMSet.setItems(FXCollections.observableArrayList(0,1,2,3,4));
        mainProjPPMSet.setValue(0);
        mainProjPPMSetType.setItems(FXCollections.observableArrayList("Ref Set", "PPM Set"));
        mainProjPPMSetType.setValue("Ref Set");

        //} else {
        //    label.setText("No active mol");
        //}

        double screenWidth = Screen.getPrimary().getBounds().getWidth();
        if (x > (screenWidth / 2)) {
            x = x - stage.getWidth() - xOffset;
        } else {
            x = x + 100;
        }

        y = y - stage.getHeight() / 2.0;

        stage.setX(x);
        stage.setY(y);
        stage.show();
    }

    public Stage getStage() {
        return stage;
    }

    public NoeSetPeakController() {
        stage = new Stage(StageStyle.DECORATED);
        grid=new GridPane();
        Scene scene = new Scene(grid);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setTitle("PeakList transfer");
        initialize();
    }

    public void updatePeakListMenu() {
        peakListMenu.getItems().clear();
        for (String peakListName : ProjectBase.getActive().getPeakListNames()) {
            //todo: only necessary for NOE transfer. Bind NOE box disabled on this?
            if (ProjectBase.getActive().getPeakList(peakListName).getNDim()==2) {
                MenuItem menuItem = new MenuItem(peakListName);
                menuItem.setOnAction(e -> {
                    setPeakList(ProjectBase.getActive().getPeakList(peakListName));
                });
                peakListMenu.getItems().add(menuItem);
            }
        }
    }

    private void initialize() {
        Label arrow2 = GlyphsDude.createIconLabel(FontAwesomeIcon.ARROW_CIRCLE_ALT_RIGHT,"","16px", "0pt", ContentDisplay.TOP);
        mainProjPPMSet.setDisable(true);
        mainProjPPMSetType.setDisable(true);
        mainProjNoeSetCombo.setDisable(true);
        //combo1.setOnAction(e-> updateNoeSets());
        //combo2.setOnAction(e->updateNoeSets());
        transferButton.setOnAction(e-> {
            //int fromSet, int toSet, boolean fromRef, boolean toRef,
            if (getAssignments.isSelected()) {
                transferAssignmentsFromPeaklist(peakList, mainProjPPMSet.getValue(), mainProjPPMSetType.getValue().equals("Ref Set"), true);
            }
            if (getNOEs.isSelected()) {
                mainProjNoeSetCombo.getValue().getNoesFromPeakList(peakList);
            }
            stage.close();
        });
        grid.add(peakListMenu,0,1);
        grid.add(peakListLabel,2,1);
        grid.add(getAssignments,0,2);
        //grid.add(getNOEs,1,1);
        grid.add(arrow2,1,2);
        grid.add(mainProjPPMSetType,2,2);
        grid.add(mainProjPPMSet,3,2);
        grid.add(getNOEs,0,3);
        grid.add(mainProjNoeSetCombo,2,3);
        grid.add(transferButton,0,4);
        for (Node child : grid.getChildren()) {
            GridPane.setFillWidth(child, true);
            ((Control) child).setMaxWidth(Double.MAX_VALUE);
        }

        peakListLabel.setText("None Selected");
        updatePeakListMenu();

        //removeButton.disableProperty().bind(subProjectProperty().isNull());
        mainProjPPMSet.disableProperty().bind(getAssignments.selectedProperty().not());
        mainProjPPMSetType.disableProperty().bind(getAssignments.selectedProperty().not());
        mainProjNoeSetCombo.disableProperty().bind(getNOEs.selectedProperty().not());

        transferButton.disableProperty().bind(Bindings.and(getAssignments.selectedProperty().not(),getNOEs.selectedProperty().not()));

        mainProjNoeSetCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(ManagedNoeSet object) {
                if (object==null) {
                    return "main project NOE set";
                }
                return object.getName();
            }

            @Override
            public ManagedNoeSet fromString(String string) {
                return null;
            }
        });

        mainProjNoeSetList.add(null);
        mainProjNoeSetCombo.setItems(mainProjNoeSetList);
    }

    private static void transferAssignmentsFromPeaklist(PeakList peakList, int toSet, boolean toRef, boolean requireFrozen) {
        for (Peak peak : peakList.peaks()) {
            for (PeakDim peakDim : peak.getPeakDims()) {
                if (peakDim.isFrozen() || !requireFrozen) {
                    try {
                        Atom atom = ((AtomResonance) peakDim.getResonance()).getAtom();
                        Float shift = peakDim.getChemShift();
                        if (toRef) {
                            atom.setRefPPM(toSet, shift);
                        } else {
                            atom.setPPM(toSet, shift);
                        }
                        //todo: should this update unfrozen peakDims?
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    @Override
    public void setPeakList(PeakList peakList) {
        this.peakList = peakList;
        if (peakList == null) {
            peakListLabel.setText("None selected");
        } else {
            peakListLabel.setText(peakList.getName());
        }
    }

    @Override
    public PeakList getPeakList() {
        return peakList;
    }

    @Override
    public void refreshPeakView() {

    }

    @Override
    public void refreshChangedListView() {

    }

    @Override
    public void copyPeakTableView() {

    }

    @Override
    public void deletePeaks() {

    }

    @Override
    public void restorePeaks() {

    }

    @Override
    public Optional<Peak> getPeak() {
        return Optional.empty();
    }

    @FXML
    public static void showController(ActionEvent actionEvent) {
        if (noeSetPeakController == null) {
            noeSetPeakController = new NoeSetPeakController();
            noeSetPeakController.show(400,400);
        }
        if (noeSetPeakController != null) {
            noeSetPeakController.show(noeSetPeakController.getStage().getX(), noeSetPeakController.getStage().getY());
            //noeSetPeakController.getStage().show();
            noeSetPeakController.getStage().toFront();
        } else {
            System.out.println("Couldn't make NoeSetPeakController ");
        }
    }
}
