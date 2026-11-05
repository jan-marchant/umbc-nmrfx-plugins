package edu.umbc.hhmi.subproject_plugin;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import org.apache.commons.collections4.BidiMap;
import org.nmrfx.chemistry.Entity;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utils.GUIUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class SubProjectSceneController implements SubProjMenu {

    double xOffset=50;
    private final Stage stage;
    private AlignmentViewer alignmentViewer;
    private final GridPane grid;
    private MenuButton subProjectMenuButton;
    private final ObjectProperty<ProjectBase> subProject = new SimpleObjectProperty<>();
    ChoiceBox<Entity> subProjectEntityCombo =new ChoiceBox<>();
    ChoiceBox<Entity> mainProjectEntityCombo =new ChoiceBox<>();
    Button linkButton=new Button("Link");
    BooleanProperty entitiesLinked =new SimpleBooleanProperty(false);
    StringProperty linkLabel = new SimpleStringProperty("Link");
    ObservableList<Entity> subProjectEntities =FXCollections.observableArrayList();
    ObservableList<Entity> mainProjectEntities =FXCollections.observableArrayList();
    Label label;


    public Stage getStage() {
        return stage;
    }

    public SubProjectSceneController() {
        stage = new Stage(StageStyle.DECORATED);
        grid=new GridPane();
        Scene scene = new Scene(grid);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setTitle("Associated projects");
        initialize();
    }
    private void initialize() {
        subProjectMenuButton =new MenuButton();
        subProjectMenuButton.setText(ProjectRelations.getProjectRelations().size() + " project"+(ProjectRelations.getProjectRelations().size()==1?"":"s"));
        //populateMenu();
        label=new Label("No active mol");
        Button removeButton = new Button("Remove");
        removeButton.setDisable(true);
        removeButton.setOnAction(e->{
            ProjectRelations.findProjectRelations(subProject.get()).remove();
            setSubProject(null);
        });
        Label arrow1 = GlyphsDude.createIconLabel(FontAwesomeIcon.ARROW_CIRCLE_ALT_RIGHT,"","16px", "0pt", ContentDisplay.TOP);
        Label arrow2 = GlyphsDude.createIconLabel(FontAwesomeIcon.ARROW_CIRCLE_ALT_RIGHT,"","16px", "0pt", ContentDisplay.TOP);
        subProjectEntityCombo.setDisable(true);
        mainProjectEntityCombo.setDisable(true);
        subProjectEntityCombo.setOnAction(e-> updateEntities());
        mainProjectEntityCombo.setOnAction(e->updateEntities());
        linkButton.setOnAction(e-> linkEntities(subProjectEntityCombo.getValue(), mainProjectEntityCombo.getValue()));
        alignmentViewer = new AlignmentViewer();
        int row=1;
        grid.add(subProjectMenuButton,0,row);
        grid.add(arrow1,1,row);
        grid.add(label,2,row);
        grid.add(removeButton,3,row++);
        grid.add(subProjectEntityCombo,0,row);
        grid.add(arrow2,1,row);
        grid.add(mainProjectEntityCombo,2,row);
        grid.add(linkButton,3,row++);
        grid.add(alignmentViewer,0,row,5,1);
        for (Node child : grid.getChildren()) {
            GridPane.setFillWidth(child, true);
            ((Control) child).setMaxWidth(Double.MAX_VALUE);
        }


        removeButton.disableProperty().bind(subProjectProperty().isNull());
        subProjectEntityCombo.disableProperty().bind(subProjectProperty().isNull());
        mainProjectEntityCombo.disableProperty().bind(subProjectProperty().isNull());
        alignmentViewer.disableProperty().bind(entitiesLinked);
        subProjectEntityCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Entity object) {
                if (object==null) {
                    return "sub entity";
                }
                return object.getName();
            }

            @Override
            public Entity fromString(String string) {
                return null;
            }
        });
        mainProjectEntityCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Entity object) {
                if (object==null) {
                    return "main entity";
                }
                return object.getName();
            }

            @Override
            public Entity fromString(String string) {
                return null;
            }
        });

        linkButton.disableProperty().bind(Bindings.or(subProjectEntityCombo.getSelectionModel().selectedItemProperty().isNull(), mainProjectEntityCombo.getSelectionModel().selectedItemProperty().isNull()));
        linkButton.textProperty().bind(linkLabelProperty());
        subProjectEntities.add(null);
        subProjectEntityCombo.setItems(subProjectEntities);
        mainProjectEntities.add(null);
        mainProjectEntityCombo.setItems(mainProjectEntities);

        subProjectProperty().addListener(e->{
            subProjectEntities.clear();
            subProjectEntities.add(null);

            if (subProject.get()==null) {
                subProjectMenuButton.setText(ProjectRelations.getProjectRelations().size() + " project"+(ProjectRelations.getProjectRelations().size()==1?"":"s"));
            } else {
                subProjectMenuButton.setText(subProject.get().getName());
                if (subProject.get().getActiveMolecule()!=null) {
                    subProjectEntities.addAll(subProject.get().getActiveMolecule().getEntities());
                    if (subProject.get().getActiveMolecule().getEntities().size() == 1) {
                        subProjectEntityCombo.setValue(subProject.get().getActiveMolecule().getEntities().get(0));
                    } else {
                        subProjectEntityCombo.setValue(null);
                    }
                } else {
                    subProjectEntityCombo.setValue(null);
                }
            }
            populateMenu();
            updateEntities();
        });

        entitiesLinkedProperty().addListener(e -> setLinkLabel(getEntitiesLinked()?"Unlink":"Link"));
    }

    private void updateEntities() {
        BidiMap<Entity, Entity> map = ProjectRelations.getEntityMap(subProject.get());
        if (map==null) {
            setEntitiesLinked(false);
            alignmentViewer.align(subProjectEntityCombo.getValue(), mainProjectEntityCombo.getValue());
            return;
        }
        if (mainProjectEntityCombo.getValue()==null && getKeysByValue(map, subProjectEntityCombo.getValue())!=null) {
            mainProjectEntityCombo.setValue(getKeysByValue(map, subProjectEntityCombo.getValue()));
        }
        if (subProjectEntityCombo.getValue()==null && map.containsKey(mainProjectEntityCombo.getValue())) {
            subProjectEntityCombo.setValue(map.get(mainProjectEntityCombo.getValue()));
        }
        if (map.get(mainProjectEntityCombo.getValue()) == subProjectEntityCombo.getValue()) {
            setEntitiesLinked(true);
            alignmentViewer.alignFromMap(subProjectEntityCombo.getValue(), mainProjectEntityCombo.getValue(),map);
        } else {
            setEntitiesLinked(false);
            alignmentViewer.align(subProjectEntityCombo.getValue(), mainProjectEntityCombo.getValue());
        }
    }

    public static <T, E> T getKeysByValue(Map<T, E> map, E value) {
        return map.entrySet()
                .stream()
                .filter(entry -> Objects.equals(entry.getValue(), value))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    private void linkEntities(Entity subEntity, Entity mainEntity) {
        if (!getEntitiesLinked()) {
            if (!subEntity.getClass().equals(mainEntity.getClass())) {
                GUIUtils.warn("Error","Entities are not of the same type");
            } else {
                //SubProjRelations.findSubProjRelations(subProject.get()).entityMap.putIfAbsent(subProject.get(), new DualHashBidiMap<>());
                BidiMap<Entity,Entity> map = ProjectRelations.getEntityMap(subProject.get());
                map.put(mainEntity, subEntity);
                int subIndex=0;
                int mainIndex=0;
                for (int i = 0; i<alignmentViewer.sequences_sub_main[1].length()&&i<alignmentViewer.sequences_sub_main[0].length(); i++) {

                    char subChar=alignmentViewer.sequences_sub_main[0].charAt(i);
                    char mainChar=alignmentViewer.sequences_sub_main[1].charAt(i);
                    if (subChar!=' ' && mainChar!=' ') {
                        map.put(((Polymer) mainEntity).getResidues().get(mainIndex),((Polymer) subEntity).getResidues().get(subIndex));
                    }
                    if (subChar!=' ') {
                        subIndex++;
                    }
                    if (mainChar!=' ') {
                        mainIndex++;
                    }
                }
            }
        } else {
            ProjectRelations.getEntityMap(subProject.get()).remove(mainProjectEntityCombo.getValue());
            if (mainProjectEntityCombo.getValue() instanceof Polymer) {
                for (Residue residue : ((Polymer) mainProjectEntityCombo.getValue()).getResidues()) {
                    ProjectRelations.getEntityMap(subProject.get()).remove(residue);
                }
            }
        }
        updateEntities();
    }

    private void populateMenu() {
        subProjectMenuButton.getItems().clear();
        List<Path> seen = new ArrayList<>();
        seen.add(ProjectBase.getActive().getDirectory());
        for (Object menu : ProjectRelations.getSubProjMenus(ProjectBase.getActive(),this, seen)) {
            if (menu instanceof Menu) {
                subProjectMenuButton.getItems().add((Menu) menu);
            } else {
                subProjectMenuButton.getItems().add((MenuItem) menu);
            }
        }

        Menu subProjMenu = new Menu("Add New");
        subProjectMenuButton.getItems().add(subProjMenu);
        List<Path> recentProjects= PreferencesController.getRecentProjects();

        for (Path path : recentProjects) {
            if (ProjectRelations.containsSubProjectPath(ProjectBase.getActive(),path)) {
                continue;
            }
            int count = path.getNameCount();
            int first = count - 3;
            first = Math.max(first, 0);
            Path subPath = path.subpath(first, count);
            MenuItem projectMenuItem = new MenuItem(subPath.toString());
            projectMenuItem.setOnAction(e -> {
                setSubProject(ProjectRelations.addSubProject(ProjectBase.getActive(), path));
                //menuButton.disarm();
             });
            subProjMenu.getItems().add(projectMenuItem);
        }
        MenuItem browseProj = new MenuItem("Browse...");
        subProjMenu.getItems().add(browseProj);
        browseProj.setOnAction(e -> {
            loadSubProject();
            //menuButton.disarm();
        });
    }

    private void loadSubProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Project Chooser");
        File directoryFile = chooser.showDialog(null);
        if (directoryFile != null) {
            setSubProject(ProjectRelations.addSubProject(ProjectBase.getActive(), directoryFile.toPath()));
        } else {
            setSubProject(null);
        }
    }

    public void show(double x, double y) {
        populateMenu();
        //if (SubProjRelations.getSubProjRelations().size()>0) {
        label.setText(ProjectBase.getActive().getName());
        mainProjectEntities.clear();
        mainProjectEntities.addAll(ProjectBase.getActive().getActiveMolecule().getEntities());
        if (ProjectBase.getActive().getActiveMolecule().getEntities().size()==1) {
            mainProjectEntityCombo.setValue(ProjectBase.getActive().getActiveMolecule().getEntities().get(0));
        } else {
            mainProjectEntityCombo.setValue(null);
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

    public void updateSubProjMenu(ProjectBase subProj) {
        subProjectMenuButton.setText(subProj.getName());
        subProjectMenuButton.disarm();
        setSubProject(subProj);
    }

    public ProjectBase getSubProject() {
        return subProject.get();
    }

    public ObjectProperty<ProjectBase> subProjectProperty() {
        return subProject;
    }

    public void setSubProject(ProjectBase subProject) {
        this.subProject.set(subProject);
    }

    public boolean getEntitiesLinked() {
        return entitiesLinked.get();
    }

    public BooleanProperty entitiesLinkedProperty() {
        return entitiesLinked;
    }

    public void setEntitiesLinked(boolean entitiesLinked) {
        this.entitiesLinked.set(entitiesLinked);
    }

    public String getLinkLabel() {
        return linkLabel.get();
    }

    public StringProperty linkLabelProperty() {
        return linkLabel;
    }

    public void setLinkLabel(String linkLabel) {
        this.linkLabel.set(linkLabel);
    }
}
