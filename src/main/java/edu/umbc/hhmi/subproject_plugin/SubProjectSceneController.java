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
    private MenuButton menuButton;
    private final ObjectProperty<ProjectBase> subProject = new SimpleObjectProperty<>();
    ChoiceBox<Entity> combo1=new ChoiceBox<>();
    ChoiceBox<Entity> combo2=new ChoiceBox<>();
    Button linkButton=new Button("Link");
    BooleanProperty entitiesLinked =new SimpleBooleanProperty(false);
    StringProperty linkLabel = new SimpleStringProperty("Link");
    ObservableList<Entity> list1 =FXCollections.observableArrayList();
    ObservableList<Entity> list2 =FXCollections.observableArrayList();
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
        menuButton=new MenuButton();
        menuButton.setText(ProjectRelations.getProjectRelations().size() + " project"+(ProjectRelations.getProjectRelations().size()==1?"":"s"));
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
        combo1.setDisable(true);
        combo2.setDisable(true);
        combo1.setOnAction(e-> updateEntities());
        combo2.setOnAction(e->updateEntities());
        linkButton.setOnAction(e-> linkEntities(combo1.getValue(),combo2.getValue()));
        alignmentViewer = new AlignmentViewer();
        grid.add(menuButton,0,0);
        grid.add(arrow1,1,0);
        grid.add(label,2,0);
        grid.add(removeButton,3,0);
        grid.add(combo1,0,1);
        grid.add(arrow2,1,1);
        grid.add(combo2,2,1);
        grid.add(linkButton,3,1);
        grid.add(alignmentViewer,0,2,5,1);
        for (Node child : grid.getChildren()) {
            GridPane.setFillWidth(child, true);
            ((Control) child).setMaxWidth(Double.MAX_VALUE);
        }


        removeButton.disableProperty().bind(subProjectProperty().isNull());
        combo1.disableProperty().bind(subProjectProperty().isNull());
        combo2.disableProperty().bind(subProjectProperty().isNull());
        alignmentViewer.disableProperty().bind(entitiesLinked);
        combo1.setConverter(new StringConverter<>() {
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
        combo2.setConverter(new StringConverter<>() {
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

        linkButton.disableProperty().bind(Bindings.or(combo1.getSelectionModel().selectedItemProperty().isNull(),combo2.getSelectionModel().selectedItemProperty().isNull()));
        linkButton.textProperty().bind(linkLabelProperty());
        list1.add(null);
        combo1.setItems(list1);
        list2.add(null);
        combo2.setItems(list2);

        subProjectProperty().addListener(e->{
            list1.clear();
            list1.add(null);

            if (subProject.get()==null) {
                menuButton.setText(ProjectRelations.getProjectRelations().size() + " project"+(ProjectRelations.getProjectRelations().size()==1?"":"s"));
            } else {
                menuButton.setText(subProject.get().getName());
                if (subProject.get().getActiveMolecule()!=null) {
                    list1.addAll(subProject.get().getActiveMolecule().getEntities());
                    if (subProject.get().getActiveMolecule().getEntities().size() == 1) {
                        combo1.setValue(subProject.get().getActiveMolecule().getEntities().get(0));
                    } else {
                        combo1.setValue(null);
                    }
                } else {
                    combo1.setValue(null);
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
            alignmentViewer.alignSW(combo1.getValue(), combo2.getValue());
            return;
        }
        if (combo2.getValue()==null && getKeysByValue(map,combo1.getValue())!=null) {
            combo2.setValue(getKeysByValue(map,combo1.getValue()));
        }
        if (combo1.getValue()==null && map.containsKey(combo2.getValue())) {
            combo1.setValue(map.get(combo2.getValue()));
        }
        if (map.get(combo2.getValue())==combo1.getValue()) {
            setEntitiesLinked(true);
            alignmentViewer.alignFromMap(combo1.getValue(), combo2.getValue(),map);
        } else {
            setEntitiesLinked(false);
            alignmentViewer.alignSW(combo1.getValue(), combo2.getValue());
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
                for (int i=0;i<alignmentViewer.sequences[1].length()&&i<alignmentViewer.sequences[0].length();i++) {

                    char subChar=alignmentViewer.sequences[0].charAt(i);
                    char mainChar=alignmentViewer.sequences[1].charAt(i);
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
            ProjectRelations.getEntityMap(subProject.get()).remove(combo2.getValue());
            if (combo2.getValue() instanceof Polymer) {
                for (Residue residue : ((Polymer) combo2.getValue()).getResidues()) {
                    ProjectRelations.getEntityMap(subProject.get()).remove(residue);
                }
            }
        }
        updateEntities();
    }

    private void populateMenu() {
        menuButton.getItems().clear();
        List<Path> seen = new ArrayList<>();
        seen.add(ProjectBase.getActive().getDirectory());
        for (Object menu : ProjectRelations.getSubProjMenus(ProjectBase.getActive(),this, seen)) {
            if (menu instanceof Menu) {
                menuButton.getItems().add((Menu) menu);
            } else {
                menuButton.getItems().add((MenuItem) menu);
            }
        }

        Menu subProjMenu = new Menu("Add New");
        menuButton.getItems().add(subProjMenu);
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
        list2.clear();
        list2.addAll(ProjectBase.getActive().getActiveMolecule().getEntities());
        if (ProjectBase.getActive().getActiveMolecule().getEntities().size()==1) {
            combo2.setValue(ProjectBase.getActive().getActiveMolecule().getEntities().get(0));
        } else {
            combo2.setValue(null);
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
        menuButton.setText(subProj.getName());
        menuButton.disarm();
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
