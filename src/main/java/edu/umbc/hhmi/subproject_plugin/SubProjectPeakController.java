package edu.umbc.hhmi.subproject_plugin;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import edu.umbc.hhmi.acquisition_plugin.ManagedNoeSet;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import org.apache.commons.collections4.BidiMap;
import org.nmrfx.chemistry.Entity;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.PeakMenuTarget;
import org.nmrfx.project.ProjectBase;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubProjectPeakController implements SubProjMenu, PeakMenuTarget {

    double xOffset=50;

    private final Stage stage;
    private final GridPane grid;
    private final ObjectProperty<ProjectBase> subProject = new SimpleObjectProperty<>();
    CheckBox getAssignments = new CheckBox("Get assignments");
    CheckBox getNOEs = new CheckBox("Get NOEs");
    ChoiceBox<String> mainProjPPMSetType =new ChoiceBox<>();
    ChoiceBox<Integer> mainProjPPMSet =new ChoiceBox<>();
    ChoiceBox<ManagedNoeSet> mainProjNoeSetCombo =new ChoiceBox<>();
    ObservableList<ManagedNoeSet> mainProjNoeSetList =FXCollections.observableArrayList();
    private MenuButton subProjMenuButton;
    Button transferButton=new Button("Transfer Assignments");
    Label mainProjLabel;
    private PeakList peakList;
    private Label peakListLabel = new Label();
    private MenuButton peakListMenu = new MenuButton("List");


    public void show(double x, double y) {
        populateMenu();

        mainProjLabel.setText(ProjectBase.getActive().getName());

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

    public SubProjectPeakController() {
        stage = new Stage(StageStyle.DECORATED);
        grid=new GridPane();
        Scene scene = new Scene(grid);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setTitle("Subproject PeakList transfer");
        initialize();
    }

    public void updatePeakListMenu() {
        peakListMenu.getItems().clear();

        if (subProjectProperty().get()!=null) {
            for (String peakListName : subProjectProperty().get().getPeakListNames()) {
                if (subProjectProperty().get().getPeakList(peakListName).getNDim()==2) {
                    MenuItem menuItem = new MenuItem(peakListName);
                    menuItem.setOnAction(e -> {
                        setPeakList(subProjectProperty().get().getPeakList(peakListName));
                    });
                    peakListMenu.getItems().add(menuItem);
                }
            }
        }
    }

    private void initialize() {
        subProjMenuButton =new MenuButton();
        subProjMenuButton.setText(ProjectRelations.getProjectRelations().size() + " project"+(ProjectRelations.getProjectRelations().size()==1?"":"s"));
        //populateMenu();
        mainProjLabel =new Label("No active mol");
        Label arrow1 = GlyphsDude.createIconLabel(FontAwesomeIcon.ARROW_CIRCLE_ALT_RIGHT,"","16px", "0pt", ContentDisplay.TOP);
        Label arrow2 = GlyphsDude.createIconLabel(FontAwesomeIcon.ARROW_CIRCLE_ALT_RIGHT,"","16px", "0pt", ContentDisplay.TOP);
        mainProjPPMSet.setDisable(true);
        mainProjPPMSetType.setDisable(true);
        mainProjNoeSetCombo.setDisable(true);
        //combo1.setOnAction(e-> updateNoeSets());
        //combo2.setOnAction(e->updateNoeSets());
        transferButton.setOnAction(e-> {
            BidiMap<Entity, Entity> map = ProjectRelations.getEntityMap(subProject.get());
            //int fromSet, int toSet, boolean fromRef, boolean toRef,
            if (getAssignments.isSelected()) {
                ProjectRelations.transferAssignmentsFromPeaklist(peakList, mainProjPPMSet.getValue(), mainProjPPMSetType.getValue().equals("Ref Set"), map, true);
            }
            if (getNOEs.isSelected()) {
                mainProjNoeSetCombo.getValue().getNoesFromSubPeakList(peakList, map);
            }
            stage.close();
        });
        grid.add(subProjMenuButton,0,0);
        grid.add(arrow1,1,0);
        grid.add(mainProjLabel,2,0);
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

        //removeButton.disableProperty().bind(subProjectProperty().isNull());
        mainProjPPMSet.disableProperty().bind(Bindings.or(subProjectProperty().isNull(),getAssignments.selectedProperty().not()));
        mainProjPPMSetType.disableProperty().bind(Bindings.or(subProjectProperty().isNull(),getAssignments.selectedProperty().not()));
        mainProjNoeSetCombo.disableProperty().bind(subProjectProperty().isNull());

        transferButton.disableProperty().bind(subProjectProperty().isNull());

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

        subProjectProperty().addListener(e->{
            if (subProject.get()==null) {
                subProjMenuButton.setText(ProjectRelations.getProjectRelations().size() + " project"+(ProjectRelations.getProjectRelations().size()==1?"":"s"));
                setPeakList(null);
            } else {
                subProjMenuButton.setText(subProject.get().getName());
                BidiMap<Entity, Entity> map = ProjectRelations.getEntityMap(subProject.get());
            }
            populateMenu();
            updatePeakListMenu();
        });

        ProjectRelations.getProjectRelations(ProjectBase.getActive()).addListener((ListChangeListener<? super ProjectRelations>) e-> populateMenu());
    }

    private void populateMenu() {
        subProjMenuButton.getItems().clear();
        List<Path> seen = new ArrayList<>();
        seen.add(ProjectBase.getActive().getDirectory());
        for (Object menu : ProjectRelations.getSubProjMenus(ProjectBase.getActive(),this, seen)) {
            if (menu instanceof Menu) {
                subProjMenuButton.getItems().add((Menu) menu);
            } else {
                subProjMenuButton.getItems().add((MenuItem) menu);
            }
        }
    }

    public ObjectProperty<ProjectBase> subProjectProperty() {
        return subProject;
    }


    @Override
    public void setSubProject(ProjectBase subProject) {
        this.subProject.set(subProject);
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
}
