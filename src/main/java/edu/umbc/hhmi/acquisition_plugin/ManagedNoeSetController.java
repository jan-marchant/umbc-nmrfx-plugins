package edu.umbc.hhmi.acquisition_plugin;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import edu.umbc.hhmi.subproject_plugin.ProjectRelations;
import edu.umbc.hhmi.subproject_plugin.SubProjMenu;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.constraints.NoeSet;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.project.SubProject;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.rna.InteractionType;
import org.nmrfx.structure.rna.SSGen;
import org.nmrfx.utils.GUIUtils;
import org.python.modules.math;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.UnaryOperator;

public class ManagedNoeSetController {

    public static ManagedNoeSetController noeSetController;

    @FXML
    static public void showNoeSetup(ActionEvent event) {
        if (noeSetController!=null) {
            noeSetController.getStage().close();
        }
        //if (noeSetController == null) {
        noeSetController = new ManagedNoeSetController();
        //}
        //if (noeSetController != null) {
        noeSetController.show(300,300);
        noeSetController.getStage().toFront();
        //} else {
        //    System.out.println("Couldn't make noeSetup ");
        //}
    }

    Stage stage;
    BorderPane borderPane;
    double xOffset = 50;
    ButtonBase bButton;
    Popup popup;
    MenuButton generate;

    ComboBox<ManagedNoeSet> noeSetCombo = new ComboBox<>();

    public ManagedNoeSetController() {
        stage = new Stage(StageStyle.DECORATED);
        borderPane = new BorderPane();
        Scene scene = new Scene(borderPane);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setTitle("NoeSet Setup");
        stage.setAlwaysOnTop(true);
        initialize();
    }

    public void initialize() {
        stage = new Stage(StageStyle.DECORATED);
        borderPane = new BorderPane();
        Scene scene = new Scene(borderPane);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setTitle("NoeSet Setup");
        stage.setAlwaysOnTop(true);

        Label nameLabel=new Label("Name:");
        String iconSize = "16px";
        String fontSize = "0pt";

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.PLUS_CIRCLE, "", iconSize, fontSize, ContentDisplay.TOP);
        bButton.setOnAction(e -> showPopup());
        bButton.getStyleClass().add("toolButton");
        bButton.setStyle("-fx-background-color: transparent;");

        UnaryOperator<TextFormatter.Change> nameFilter = change -> {
            String newText = change.getControlNewText();
            if (newText.matches("([A-z0-9_\\-])*?")) {
                return change;
            }
            return null;
        };

        TextField editor = new TextField();
        editor.setTextFormatter(new TextFormatter<>(new DefaultStringConverter(),"",nameFilter));
        editor.addEventFilter(KeyEvent.ANY, event -> {
            KeyCode code = event.getCode();
            if (event.getEventType()== KeyEvent.KEY_PRESSED) {
                if (code==KeyCode.ENTER) {
                    addNoeSet(editor.getText());
                    editor.clear();
                    popup.hide();
                }
                if (code==KeyCode.ESCAPE) {
                    editor.clear();
                    popup.hide();
                }
            }
        });
        editor.setPromptText("Name");

        popup = new Popup();
        popup.getContent().add(editor);

        noeSetCombo.setMaxWidth(Double.MAX_VALUE);
        noeSetCombo.getItems().setAll(ManagedNoeSet.getManagedNoeSetsMap().values());
        noeSetCombo.setPromptText("NOE Set:");
        noeSetCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(ManagedNoeSet noeSet) {
                Optional<Map.Entry<String, ManagedNoeSet>> optionalEntry = ManagedNoeSet.getManagedNoeSetsMap().entrySet().stream().filter(ap -> ap.getValue().equals(noeSet)).findFirst();
                return (optionalEntry.map(Map.Entry::getKey).orElse(null));
            }

            @Override
            public ManagedNoeSet fromString(String string) {
                return ManagedNoeSet.getManagedNoeSetsMap().get(string);
            }
        });

        generate = new MenuButton("Generate NOEs");
        Menu attr = new Menu("By Attributes");
        Menu struct = new Menu("From Structure");
        MenuItem sub = new MenuItem("From SubProject NOEs");

        //MenuItem setVienna = new MenuItem("Setup Vienna");
        //MenuItem setShifts = new MenuItem("Predict shifts");
        MenuItem gen = new MenuItem("Generate");
        //setVienna.setOnAction(e -> updateDotBracket(Molecule.getActive()));
        gen.setOnAction(e -> noeSetCombo.getValue().generateNOEsByAttributes());

        sub.setOnAction(ProjectRelations::showSubProjNoeTransfer);


        //attr.getItems().addAll(setVienna,setShifts,gen);
        attr.getItems().addAll(gen);

        MenuItem notYet = new MenuItem("Not Implemented Yet");
        struct.getItems().add(notYet);

        generate.getItems().addAll(attr,struct);
        if (SubProjMenu.isSubProjectPresent()) {
            generate.getItems().add(sub);
        }

        generate.disableProperty().bind(noeSetCombo.getSelectionModel().selectedItemProperty().isNull());
        HBox hBox=new HBox(nameLabel,noeSetCombo,bButton);
        hBox.setAlignment(Pos.CENTER_LEFT);

        Button ok = new Button("Close");
        ok.setOnAction((event) -> stage.close());

        ButtonBar buttonBar = new ButtonBar();
        ButtonBar.setButtonData(ok, ButtonBar.ButtonData.OK_DONE);
        buttonBar.getButtons().addAll(ok);

        borderPane.setTop(hBox);
        borderPane.setCenter(generate);
        borderPane.setBottom(buttonBar);
        stage.setOnCloseRequest(e -> cancel());
    }

    public void show(double x, double y) {
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

    private void addNoeSet(String name) {
        if (ManagedNoeSet.getManagedNoeSetsMap().get(name)!=null) {
            GUIUtils.warn("Error","NOE set "+name+" already exists. Please choose a new name.");
        } else {
            ManagedNoeSet noeSet= ManagedNoeSet.addSet(name);
            noeSetCombo.getItems().add(noeSet);
            noeSetCombo.setValue(noeSet);
        }
    }

    private void showPopup() {
        Bounds userTextFieldBounds = bButton.getBoundsInLocal();
        Point2D popupLocation = bButton.localToScreen(userTextFieldBounds.getMaxX(), userTextFieldBounds.getMinY());
        popup.show(bButton, popupLocation.getX(), popupLocation.getY());
    }

    private void generateNOEsFromStructure(NoeSet noeSet) {
        GUIUtils.warn("Sorry","Not yet implemented");
    }

    void cancel() {
        stage.close();
    }

    public Stage getStage() {
        return stage;
    }

}
