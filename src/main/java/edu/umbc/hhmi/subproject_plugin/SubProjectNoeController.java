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
import org.nmrfx.project.ProjectBase;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SubProjectNoeController implements SubProjMenu {

    double xOffset=50;

    private final Stage stage;
    private final GridPane grid;
    private final ObjectProperty<ProjectBase> subProject = new SimpleObjectProperty<>();
    ChoiceBox<ManagedNoeSet> subProjNoeSetCombo =new ChoiceBox<>();
    ChoiceBox<ManagedNoeSet> mainProjNoeSetCombo =new ChoiceBox<>();
    ObservableList<ManagedNoeSet> subProjNoeSetList = FXCollections.observableArrayList();
    ObservableList<ManagedNoeSet> mainProjNoeSetList =FXCollections.observableArrayList();
    private MenuButton subProjMenuButton;
    Button transferButton=new Button("Transfer NOEs");
    Label mainProjLabel;

    public void show(double x, double y) {
        populateMenu();
        //if (SubProjRelations.getSubProjRelations().size()>0) {
        mainProjLabel.setText(ProjectBase.getActive().getName());
        mainProjNoeSetList.clear();
        mainProjNoeSetList.addAll(ManagedNoeSet.getManagedNoeSetsMap().values());
        if (ManagedNoeSet.getManagedNoeSetsMap().values().size()==1) {
            mainProjNoeSetCombo.setValue(ManagedNoeSet.getManagedNoeSetsMap().values().iterator().next());
        } else {
            mainProjNoeSetCombo.setValue(null);
        }
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

    public SubProjectNoeController() {
        stage = new Stage(StageStyle.DECORATED);
        grid=new GridPane();
        Scene scene = new Scene(grid);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setTitle("Subproject NOE transfer");
        initialize();
    }

    private void initialize() {
        subProjMenuButton =new MenuButton();
        subProjMenuButton.setText(ProjectRelations.getProjectRelations().size() + " project"+(ProjectRelations.getProjectRelations().size()==1?"":"s"));
        //populateMenu();
        mainProjLabel =new Label("No active mol");
        Label arrow1 = GlyphsDude.createIconLabel(FontAwesomeIcon.ARROW_CIRCLE_ALT_RIGHT,"","16px", "0pt", ContentDisplay.TOP);
        Label arrow2 = GlyphsDude.createIconLabel(FontAwesomeIcon.ARROW_CIRCLE_ALT_RIGHT,"","16px", "0pt", ContentDisplay.TOP);
        subProjNoeSetCombo.setDisable(true);
        mainProjNoeSetCombo.setDisable(true);
        //combo1.setOnAction(e-> updateNoeSets());
        //combo2.setOnAction(e->updateNoeSets());
        transferButton.setOnAction(e-> {
            BidiMap<Entity, Entity> map = ProjectRelations.getEntityMap(subProject.get());
            mainProjNoeSetCombo.getValue().transferNoes(subProjNoeSetCombo.getValue(), map);
            stage.close();
        });
        grid.add(subProjMenuButton,0,0);
        grid.add(arrow1,1,0);
        grid.add(mainProjLabel,2,0);
        grid.add(subProjNoeSetCombo,0,1);
        grid.add(arrow2,1,1);
        grid.add(mainProjNoeSetCombo,2,1);
        grid.add(transferButton,3,1);
        for (Node child : grid.getChildren()) {
            GridPane.setFillWidth(child, true);
            ((Control) child).setMaxWidth(Double.MAX_VALUE);
        }


        //removeButton.disableProperty().bind(subProjectProperty().isNull());
        subProjNoeSetCombo.disableProperty().bind(subProjectProperty().isNull());
        mainProjNoeSetCombo.disableProperty().bind(subProjectProperty().isNull());
        subProjNoeSetCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(ManagedNoeSet object) {
                if (object==null) {
                    return "subproject NOE set";
                }
                return object.getName();
            }

            @Override
            public ManagedNoeSet fromString(String string) {
                return null;
            }
        });
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

        transferButton.disableProperty().bind(Bindings.or(subProjNoeSetCombo.getSelectionModel().selectedItemProperty().isNull(), mainProjNoeSetCombo.getSelectionModel().selectedItemProperty().isNull()));
        subProjNoeSetList.add(null);
        subProjNoeSetCombo.setItems(subProjNoeSetList);
        mainProjNoeSetList.add(null);
        mainProjNoeSetCombo.setItems(mainProjNoeSetList);

        subProjectProperty().addListener(e->{
            subProjNoeSetList.clear();
            subProjNoeSetList.add(null);

            if (subProject.get()==null) {
                subProjMenuButton.setText(ProjectRelations.getProjectRelations().size() + " project"+(ProjectRelations.getProjectRelations().size()==1?"":"s"));
            } else {
                subProjMenuButton.setText(subProject.get().getName());
                BidiMap<Entity, Entity> map = ProjectRelations.getEntityMap(subProject.get());
                if (map!=null) {
                    subProjNoeSetList.addAll(ManagedNoeSet.getManagedNoeSetsMap(subProject.get()).values());
                    if (ManagedNoeSet.getManagedNoeSetsMap(subProject.get()).values().size() == 1) {
                        subProjNoeSetCombo.setValue(ManagedNoeSet.getManagedNoeSetsMap(subProject.get()).values().iterator().next());
                    } else {
                        subProjNoeSetCombo.setValue(null);
                    }
                } else {
                    subProjNoeSetCombo.setValue(null);
                }
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
