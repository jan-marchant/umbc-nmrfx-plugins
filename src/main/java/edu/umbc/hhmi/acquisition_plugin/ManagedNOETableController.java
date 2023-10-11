package edu.umbc.hhmi.acquisition_plugin;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.PropertySheet;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.SpatialSetGroup;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.properties.ChoiceOperationItem;
import org.nmrfx.utils.properties.DoubleRangeOperationItem;
import org.nmrfx.utils.properties.NvFxPropertyEditorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.List;
import java.util.ResourceBundle;

public class ManagedNOETableController implements Initializable, StageBasedController {

    private static final Logger log = LoggerFactory.getLogger(ManagedNOETableController.class);
    private Stage stage;
    @FXML
    private ToolBar toolBar;
    @FXML
    MasterDetailPane masterDetailPane;
    @FXML
    private TableView<ManagedNoe> tableView;
    private ManagedNoeSet noeSet;

    MenuButton noeSetMenuItem;
    MenuButton peakListMenuButton;
    ObservableMap<String, ManagedNoeSet> noeSetMap;
    MoleculeBase molecule = null;
    PropertySheet propertySheet;
    CheckBox detailsCheckBox;
    DoubleRangeOperationItem refDistanceItem;
    DoubleRangeOperationItem expItem;
    DoubleRangeOperationItem minDisItem;
    DoubleRangeOperationItem maxDisItem;
    DoubleRangeOperationItem fErrorItem;
    ChoiceOperationItem modeItem;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initToolBar();
        tableView = new TableView<>();
        masterDetailPane.setMasterNode(tableView);
        propertySheet = new PropertySheet();
        masterDetailPane.setDetailSide(Side.RIGHT);
        masterDetailPane.setDetailNode(propertySheet);
        masterDetailPane.setShowDetailNode(true);
        masterDetailPane.setDividerPosition(0.7);
        propertySheet.setPrefWidth(400);
        propertySheet.setPropertyEditorFactory(new NvFxPropertyEditorFactory());
        propertySheet.setMode(PropertySheet.Mode.CATEGORY);
        propertySheet.setModeSwitcherVisible(false);
        propertySheet.setSearchBoxVisible(false);

        initTable();

        noeSetMap = FXCollections.observableMap(ManagedNoeSet.getManagedNoeSetsMap());
        MapChangeListener<String, ManagedNoeSet> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends ManagedNoeSet> change) -> updateNoeSetMenu();

        noeSetMap.addListener(mapChangeListener);
        MapChangeListener<String, PeakList> peakmapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> updatePeakListMenu();
        ProjectBase.getActive().addPeakListListener(peakmapChangeListener);

        updateNoeSetMenu();
        updatePeakListMenu();
        masterDetailPane.showDetailNodeProperty().bindBidirectional(detailsCheckBox.selectedProperty());
        List<String> intVolChoice = List.of("Intensity", "Volume");
        modeItem = new ChoiceOperationItem(propertySheet, (a, b, c) -> refresh(), "intensity", intVolChoice, "Exp Calibrate", "Mode", "Reference Distance");
        refDistanceItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                3.0, 1.0, 6.0, false, "Exp Calibrate", "Ref Distance", "Reference Distance");
        expItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                6.0, 1.0, 6.0, false, "Exp Calibrate", "Exp Factor", "Exponent value");
        minDisItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                2.0, 1.0, 3.0, false, "Exp Calibrate", "Min Distance", "Minimum bound");
        maxDisItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                6.0, 3.0, 6.0, false, "Exp Calibrate", "Max Distance", "Maximum bound");
        fErrorItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                0.125, 0.0, 0.2, false, "Exp Calibrate", "Tolerance", "Fractional additional bound");
        propertySheet.getItems().addAll(modeItem, refDistanceItem, expItem, minDisItem, maxDisItem, fErrorItem);
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    private void refresh() {
        tableView.refresh();
    }

    public static ManagedNOETableController create() {
        if (MoleculeFactory.getActive() == null) {
            GUIUtils.warn("NOE Table", "No active molecule");
            return null;
        }
        FXMLLoader loader = new FXMLLoader(ManagedNOETableController.class.getResource("/ManagedNOETableScene.fxml"));
        ManagedNOETableController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.getController();
            controller.stage = stage;
            //stage.setTitle("Experiments");
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;
    }

    void initToolBar() {
        //Button exportButton = new Button("Export");
        //exportButton.setOnAction(e -> exportNMRFxFile());
        //Button clearButton = new Button("Clear");
        //clearButton.setOnAction(e -> clearNOESet());
        //Button calibrateButton = new Button("Calibrate");
        //calibrateButton.setOnAction(e -> calibrate());
        noeSetMenuItem = new MenuButton("NoeSets");
        peakListMenuButton = new MenuButton("PeakLists");
        detailsCheckBox = new CheckBox("Details");
        toolBar.getItems().addAll(noeSetMenuItem, peakListMenuButton, detailsCheckBox);
        updateNoeSetMenu();
    }

    public void updateNoeSetMenu() {
        noeSetMenuItem.getItems().clear();

        for (String noeSetName : ManagedNoeSet.getManagedNoeSetsMap().keySet()) {
            MenuItem menuItem = new MenuItem(noeSetName);
            menuItem.setOnAction(e -> setNoeSet(ManagedNoeSet.getManagedNoeSetsMap().get(noeSetName)));
            noeSetMenuItem.getItems().add(menuItem);
        }
    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();
        for (String peakListName : ProjectBase.getActive().getPeakListNames()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> extractPeakList(PeakList.get(peakListName)));
            peakListMenuButton.getItems().add(menuItem);
        }
    }

    private record ColumnFormatter<S, T>(Format format) implements Callback<TableColumn<S, T>, TableCell<S, T>> {

        @Override
        public TableCell<S, T> call(TableColumn<S, T> arg0) {
            return new TableCell<S, T>() {
                @Override
                protected void updateItem(T item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) {
                        setGraphic(null);
                    } else {
                        setGraphic(new Label(format.format(item)));
                    }
                }
            };
        }
    }

    void initTable() {
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        updateColumns();
        tableView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                if (!tableView.getSelectionModel().getSelectedItems().isEmpty()) {
                    ManagedNoe noe = tableView.getSelectionModel().getSelectedItems().get(0);
                    showPeakInfo(noe);
                }
            }
        });
    }

    void updateColumns() {
        tableView.getColumns().clear();

        TableColumn<ManagedNoe, Integer> idNumCol = new TableColumn<>("id");
        idNumCol.setCellValueFactory(new PropertyValueFactory("ID"));
        idNumCol.setEditable(false);
        idNumCol.setPrefWidth(50);

        TableColumn<ManagedNoe, String> peakListCol = new TableColumn<>("PeakList");
        peakListCol.setCellValueFactory(new PropertyValueFactory("PeakListName"));

        TableColumn<ManagedNoe, Integer> peakNumCol = new TableColumn<>("PeakNum");
        peakNumCol.setCellValueFactory(new PropertyValueFactory("PeakNum"));
        tableView.getColumns().addAll(idNumCol, peakListCol, peakNumCol);

        for (int i = 0; i < 2; i++) {
            final int iGroup = i;
            TableColumn<ManagedNoe, Integer> entityCol = new TableColumn<>("entity" + (iGroup + 1));
            entityCol.setCellValueFactory((TableColumn.CellDataFeatures<ManagedNoe, Integer> p) -> {
                ManagedNoe noe = p.getValue();
                SpatialSetGroup spg = iGroup == 0 ? noe.spg1 : noe.spg2;
                Integer res = spg.getSpatialSet().atom.getTopEntity().getIDNum();
                return new ReadOnlyObjectWrapper(res);
            });
            TableColumn<ManagedNoe, Integer> resCol = new TableColumn<>("res" + (iGroup + 1));
            resCol.setCellValueFactory((TableColumn.CellDataFeatures<ManagedNoe, Integer> p) -> {
                ManagedNoe noe = p.getValue();
                SpatialSetGroup spg = iGroup == 0 ? noe.spg1 : noe.spg2;
                Integer res = spg.getSpatialSet().atom.getResidueNumber();
                return new ReadOnlyObjectWrapper(res);
            });
            TableColumn<ManagedNoe, String> atomCol = new TableColumn<>("aname" + (iGroup + 1));
            atomCol.setCellValueFactory((TableColumn.CellDataFeatures<ManagedNoe, String> p) -> {
                ManagedNoe noe = p.getValue();
                SpatialSetGroup spg = iGroup == 0 ? noe.spg1 : noe.spg2;
                String aname = spg.getSpatialSet().atom.getName();
                return new ReadOnlyObjectWrapper(aname);
            });
            tableView.getColumns().addAll(entityCol, resCol, atomCol);
        }

        TableColumn<ManagedNoe, Float> lowerCol = new TableColumn<>("Lower");
        lowerCol.setCellValueFactory(new PropertyValueFactory("Lower"));
        lowerCol.setCellFactory(new ManagedNOETableController.ColumnFormatter<>(new DecimalFormat(".00")));
        lowerCol.setPrefWidth(75);

        TableColumn<ManagedNoe, Float> upperCol = new TableColumn<>("Upper");
        upperCol.setCellValueFactory(new PropertyValueFactory("Upper"));
        upperCol.setCellFactory(new ManagedNOETableController.ColumnFormatter<>(new DecimalFormat(".00")));
        upperCol.setPrefWidth(75);
        TableColumn<ManagedNoe, Float> ppmCol = new TableColumn<>("PPM");
        ppmCol.setCellValueFactory(new PropertyValueFactory("PpmError"));
        ppmCol.setCellFactory(new ManagedNOETableController.ColumnFormatter<>(new DecimalFormat(".00")));
        ppmCol.setPrefWidth(75);

        TableColumn<ManagedNoe, Float> contribCol = new TableColumn<>("Contrib");
        contribCol.setCellValueFactory(new PropertyValueFactory("Contribution"));
        contribCol.setCellFactory(new ManagedNOETableController.ColumnFormatter<>(new DecimalFormat(".00")));
        contribCol.setPrefWidth(75);

        TableColumn<ManagedNoe, Float> networkCol = new TableColumn<>("Network");
        networkCol.setCellValueFactory(new PropertyValueFactory("NetworkValue"));
        networkCol.setCellFactory(new ManagedNOETableController.ColumnFormatter<>(new DecimalFormat(".00")));
        networkCol.setPrefWidth(75);

        TableColumn<ManagedNoe, String> flagCol = new TableColumn<>("Flags");
        flagCol.setCellValueFactory(new PropertyValueFactory("ActivityFlags"));
        flagCol.setPrefWidth(75);

        tableView.getColumns().addAll(lowerCol, upperCol, ppmCol, contribCol, networkCol, flagCol);

    }

    public void setNoeSet(ManagedNoeSet noeSet) {
        log.info("set noes {}", noeSet != null ? noeSet.getName() : "empty");
        this.noeSet = noeSet;
        if (tableView == null) {
            log.warn("null table");
        } else {
            if (noeSet == null) {
                stage.setTitle("Noes: ");
            } else {

                ObservableList<ManagedNoe> noes = FXCollections.observableList(noeSet.getConstraints());
                log.info("noes {}", noes.size());
                updateColumns();
                tableView.setItems(noes);
                tableView.refresh();
                stage.setTitle("Noes: " + noeSet.getName());
            }
        }

    }

    void extractPeakList(PeakList peakList) {
        System.out.println("Not implemented");
    }

    void showPeakInfo(ManagedNoe noe) {
        Peak peak = noe.getPeak();
        if (peak != null) {
            FXMLController.showPeakAttr();
            FXMLController.getPeakAttrController().gotoPeak(peak);
            FXMLController.getPeakAttrController().getStage().toFront();
        }
    }
}
