package edu.umbc.hhmi.atombrowser_plugin;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.MolFilter;
import org.nmrfx.structure.chemistry.Molecule;

abstract class AtomSelector extends VBox {
    ComboBox<Atom> atomComboBox;
    Button clear;
    ObservableList<Atom> atomList = FXCollections.observableArrayList();
    TextField atomField;
    AutoCompletionBinding<Atom> acb;
    String filterString;
    MolFilter molFilter;
    AtomBrowser atomBrowser;

    public AtomSelector (AtomBrowser atomBrowser, String prompt, boolean includeClear) {
        this.atomBrowser = atomBrowser;
        atomComboBox = new ComboBox<>();
        atomComboBox.setPromptText(prompt);

        atomComboBox.setItems(atomList);
        atomComboBox.setEditable(false);

        atomComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Atom atom) {
                return atom.getShortName();
            }

            @Override
            public Atom fromString(String string) {
                return null;
            }
        });

        clear = new Button("Clear");

        clear.setOnAction(e -> clearAction());

        HBox hBox = new HBox();

        hBox.getChildren().add(atomComboBox);

        if (includeClear) {
            hBox.getChildren().add(clear);
        }

        atomField = new TextField();
        atomField.setPromptText("Search");
        atomField.prefWidthProperty().bind(hBox.widthProperty());
        acb = TextFields.bindAutoCompletion(atomField, atomComboBox.getItems());
        acb.setOnAutoCompleted(event -> {
            Atom atom = event.getCompletion();
            atomField.clear();
            atomComboBox.setValue(atom);
            selectAtom(atom);
        });
        acb.setVisibleRowCount(10);
        setFilterString("*.H*");

        atomField.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode().equals(KeyCode.ENTER)) {
                Atom atom = atomBrowser.mol.getAtom(atomField.getText());
                if (atom!=null) {
                    atomComboBox.setValue(atom);
                    selectAtom(atom);
                }
                atomField.clear();
            } else if (keyEvent.getCode().equals(KeyCode.ESCAPE)) {
                atomField.clear();
            }
        });

        atomComboBox.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (! isShowing) {
                selectAtom(atomComboBox.getValue());
                atomField.clear();
            }
        });

        this.getChildren().addAll(hBox,atomField);
    }

    public abstract void selectAtom (Atom atom);

    public abstract void clearAction();

    public void setFilterString(String string) {
        if (!string.equalsIgnoreCase(filterString)) {
            filterString = string;
            molFilter = new MolFilter(filterString);
            Molecule molecule = Molecule.getActive();
            atomList.clear();
            if (molecule != null) {
                try {
                    Molecule.selectAtomsForTable(molFilter, atomList);
                } catch (InvalidMoleculeException ignore) {}
            }
            atomComboBox.setItems(atomList);
            acb.dispose();
            acb = TextFields.bindAutoCompletion(atomField, atomComboBox.getItems());
            acb.setOnAutoCompleted(event -> {
                Atom atom = event.getCompletion();
                atomField.clear();
                atomComboBox.setValue(atom);
                selectAtom(atom);
            });
            acb.setVisibleRowCount(10);
            if (atomList.contains(atomBrowser.getCurrentAtom())) {
                atomComboBox.setValue(atomBrowser.getCurrentAtom());
                selectAtom(atomBrowser.getCurrentAtom());
            } else {
                atomBrowser.setCurrentAtom(null);
            }
        }
    }
}
