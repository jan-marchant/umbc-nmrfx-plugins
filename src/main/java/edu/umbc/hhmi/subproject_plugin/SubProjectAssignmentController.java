package edu.umbc.hhmi.subproject_plugin;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import edu.umbc.hhmi.acquisition_plugin.ManagedNoeSet;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
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
import org.nmrfx.project.ProjectBase;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SubProjectAssignmentController implements SubProjMenu {

    double xOffset=50;

    private final Stage stage;
    private final GridPane grid;
    private final ObjectProperty<ProjectBase> subProject = new SimpleObjectProperty<>();
    ChoiceBox<String> subProjPPMSetType =new ChoiceBox<>();
    ChoiceBox<String> mainProjPPMSetType =new ChoiceBox<>();
    ChoiceBox<Integer> subProjPPMSet =new ChoiceBox<>();
    ChoiceBox<Integer> mainProjPPMSet =new ChoiceBox<>();
    private MenuButton subProjMenuButton;
    Button transferButton=new Button("Transfer Assignments");
    Label mainProjLabel;

    public void show(double x, double y) {
        populateMenu();
        //if (SubProjRelations.getSubProjRelations().size()>0) {
        mainProjLabel.setText(ProjectBase.getActive().getName());
        subProjPPMSet.setItems(FXCollections.observableArrayList(0,1,2,3,4));
        subProjPPMSet.setValue(0);
        mainProjPPMSet.setItems(FXCollections.observableArrayList(0,1,2,3,4));
        mainProjPPMSet.setValue(0);
        subProjPPMSetType.setItems(FXCollections.observableArrayList("PPM Set", "Ref Set"));
        subProjPPMSetType.setValue("PPM Set");
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

    public SubProjectAssignmentController() {
        stage = new Stage(StageStyle.DECORATED);
        grid=new GridPane();
        Scene scene = new Scene(grid);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setTitle("Subproject Assignment transfer");
        initialize();
    }

    private void initialize() {
        subProjMenuButton =new MenuButton();
        subProjMenuButton.setText(ProjectRelations.getProjectRelations().size() + " project"+(ProjectRelations.getProjectRelations().size()==1?"":"s"));
        //populateMenu();
        mainProjLabel =new Label("No active mol");
        Label arrow1 = GlyphsDude.createIconLabel(FontAwesomeIcon.ARROW_CIRCLE_ALT_RIGHT,"","16px", "0pt", ContentDisplay.TOP);
        Label arrow2 = GlyphsDude.createIconLabel(FontAwesomeIcon.ARROW_CIRCLE_ALT_RIGHT,"","16px", "0pt", ContentDisplay.TOP);
        subProjPPMSet.setDisable(true);
        mainProjPPMSet.setDisable(true);
        subProjPPMSetType.setDisable(true);
        mainProjPPMSetType.setDisable(true);
        //combo1.setOnAction(e-> updateNoeSets());
        //combo2.setOnAction(e->updateNoeSets());
        transferButton.setOnAction(e-> {
            BidiMap<Entity, Entity> map = ProjectRelations.getEntityMap(subProject.get());
            //int fromSet, int toSet, boolean fromRef, boolean toRef,
            ProjectRelations.transferAssignments(subProjPPMSet.getValue(), mainProjPPMSet.getValue(), subProjPPMSetType.getValue().equals("Ref Set"), mainProjPPMSetType.getValue().equals("Ref Set"), map);
            stage.close();
        });
        grid.add(subProjMenuButton,0,0);
        grid.add(arrow1,1,0);
        grid.add(mainProjLabel,2,0);
        grid.add(subProjPPMSetType,0,1);
        grid.add(subProjPPMSet,1,1);
        grid.add(arrow2,2,1);
        grid.add(mainProjPPMSetType,3,1);
        grid.add(mainProjPPMSet,4,1);
        grid.add(transferButton,5,1);
        for (Node child : grid.getChildren()) {
            GridPane.setFillWidth(child, true);
            ((Control) child).setMaxWidth(Double.MAX_VALUE);
        }


        //removeButton.disableProperty().bind(subProjectProperty().isNull());
        subProjPPMSet.disableProperty().bind(subProjectProperty().isNull());
        mainProjPPMSet.disableProperty().bind(subProjectProperty().isNull());
        subProjPPMSetType.disableProperty().bind(subProjectProperty().isNull());
        mainProjPPMSetType.disableProperty().bind(subProjectProperty().isNull());

        transferButton.disableProperty().bind(subProjectProperty().isNull());

        subProjectProperty().addListener(e->{
            if (subProject.get()==null) {
                subProjMenuButton.setText(ProjectRelations.getProjectRelations().size() + " project"+(ProjectRelations.getProjectRelations().size()==1?"":"s"));
            } else {
                subProjMenuButton.setText(subProject.get().getName());
                BidiMap<Entity, Entity> map = ProjectRelations.getEntityMap(subProject.get());
            }
            populateMenu();
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
}
