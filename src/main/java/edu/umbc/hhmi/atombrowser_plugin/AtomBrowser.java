/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.umbc.hhmi.atombrowser_plugin;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import edu.umbc.hhmi.subproject_plugin.ProjectRelations;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.apache.commons.collections4.BidiMap;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Entity;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsContextProxy;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.gui.ControllerTool;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class AtomBrowser implements ControllerTool {

    private static final Logger log = LoggerFactory.getLogger(AtomBrowser.class);

    static HashMap<DatasetBase,Double> savedLevels = new HashMap<>();
    ToolBar browserToolBar;
    FXMLController controller;
    Consumer<AtomBrowser> closeAction;
    AtomSelector atomSelector1;
    AtomSelector atomSelector2;

    int centerDim = 0;
    int rangeDim = 1;
    GridPaneCanvas.ORIENTATION orientation = GridPaneCanvas.ORIENTATION.HORIZONTAL;
    ComboBox<GridPaneCanvas.ORIENTATION> orientationComboBox;

    CheckBox aspectCheckBox;
    Slider aspectSlider;
    Label aspectRatioValue;
    CheckBox widthCheckBox;
    Slider widthSlider;
    Label widthValue;

    ObservableList<RangeItem> rangeItems = FXCollections.observableArrayList();
    ObservableList<FilterItem> filterList = FXCollections.observableArrayList();
    ComboBox<RangeItem> rangeSelector;
    List<DrawItem> drawItems = new ArrayList<>();
    ObservableList<LocateItem> locateItems = FXCollections.observableArrayList();
    ComboBox<Nuclei> otherNucleus;
    String xLabel;
    String yLabel;
    Molecule mol;

    ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
    double delta = 0.1;
    boolean scheduled=false;
    private Atom currentAtom=null;

    public AtomBrowser(FXMLController controller, Consumer<AtomBrowser> closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
        if (controller.getActiveChart()!=null) {
            xLabel = controller.getActiveChart().getDimNames().get(0);
            yLabel = controller.getActiveChart().getDimNames().get(1);
        } else {
            xLabel = "1H";
            yLabel = "H";
        }
        int suffix = 1;
        boolean seen;
        do {
            seen = false;
            for (FXMLController test : FXMLController.getControllers()) {
                if (test.getStage().getTitle().equalsIgnoreCase("Browser " + suffix)) {
                    suffix += 1;
                    seen = true;
                }
            }
        } while (seen);

        controller.getStage().setTitle("Browser "+suffix);
    }

    public ToolBar getToolBar() {
        return browserToolBar;
    }

    public void close() {
        closeAction.accept(this);
    }

    public void initialize() {
        initToolbar();

    }
    void initToolbar() {
        browserToolBar = new ToolBar();
        browserToolBar.setPrefWidth(900.0);

        String iconSize = "16px";
        String fontSize = "7pt";
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", iconSize, fontSize, ContentDisplay.TOP);
        Button setupButton = GlyphsDude.createIconButton(FontAwesomeIcon.WRENCH, "Setup", iconSize, fontSize, ContentDisplay.TOP);
        closeButton.setOnAction(e -> close());
        setupButton.setOnAction(e -> new AtomBrowserSetup(this.controller));

        browserToolBar.getItems().add(closeButton);
        browserToolBar.getItems().add(setupButton);
        addFiller(browserToolBar);

        mol = Molecule.getActive();

        if (mol==null) {
            GUIUtils.warn("No active mol","You need to set up a molecule before browsing atoms");
            this.close();
            return;
        }

        atomSelector1 = new AtomSelector(this,"Atom",false) {
            @Override
            public void selectAtom (Atom atom) {
                setAtom(atom);
            }

            @Override
            public void clearAction() {

            }
        };

        atomSelector2 = new AtomSelector(this, "Locate",true) {
            @Override
            public void selectAtom(Atom atom) {
                if (atom!=null) {
                    LocateItem locateItem = new LocateItem(atomBrowser, atom);
                    if (!locateItems.contains(locateItem)) {
                        locateItems.add(locateItem);
                        locateItem.add();
                    } else {
                        locateItems.get(locateItems.indexOf(locateItem)).remove();
                        locateItems.remove(locateItem);
                    }
                }
                Platform.runLater(() -> this.atomComboBox.setValue(null));
            }

            @Override
            public void clearAction() {
                clearLocates();
            }
        };

        atomSelector1.prefWidthProperty().bindBidirectional(atomSelector2.prefWidthProperty());
        browserToolBar.getItems().add(atomSelector1);
        browserToolBar.getItems().add(atomSelector2);

        otherNucleus = new ComboBox<>();
        ObservableList<Nuclei> nucleiList=FXCollections.observableArrayList();
        nucleiList.addAll(Nuclei.H1,Nuclei.C13,Nuclei.N15,Nuclei.F19,Nuclei.P31);
        otherNucleus.setItems(nucleiList);
        otherNucleus.setOnAction(e-> refresh());
        otherNucleus.setValue(Nuclei.H1);
        //toolBar.getItems().add(otherNucleus);
        //addFiller(toolBar);

        VBox vBox2=new VBox();
        browserToolBar.getItems().add(vBox2);
        addFiller(browserToolBar);

        rangeSelector = new ComboBox<>();

        rangeSelector.setItems(rangeItems);
        rangeSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(RangeItem object) {
                return object.getName();
            }

            @Override
            public RangeItem fromString(String string) {
                return null;
            }
        });

        Button restore = new Button("Refresh");
        restore.setOnAction(e -> setAtom(currentAtom));
        restore.setAlignment(Pos.BASELINE_LEFT);

        VBox vBox3 = new VBox(rangeSelector,restore);
        vBox3.setPrefWidth(100);
        browserToolBar.getItems().add(vBox3);
        restore.prefWidthProperty().bind(vBox3.widthProperty());
        rangeSelector.prefWidthProperty().bind(vBox3.widthProperty());
        rangeSelector.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (! isShowing) {
                updateRange();
            }
        });
        //rangeSelector.setOnAction(b -> updateRange());

        double initialAspect=1.0;
        aspectCheckBox = new CheckBox("Aspect Ratio");
        aspectSlider = new Slider();
        aspectRatioValue= new Label(String.valueOf(initialAspect));
        aspectCheckBox.selectedProperty().addListener(e -> updateAspectRatio());
        aspectSlider.setMin(0.1);
        aspectSlider.setMax(10.0);
        aspectSlider.setValue(initialAspect);
        aspectSlider.setBlockIncrement(0.05);
        //aspectSlider.setOnMousePressed(e -> shiftState = e.isShiftDown());
        aspectSlider.valueProperty().addListener(e -> updateAspectRatio());

        aspectSlider.setPrefWidth(100);
        aspectRatioValue.setMinWidth(35);
        aspectRatioValue.setMaxWidth(35);
        HBox hBox = new HBox(aspectSlider,aspectRatioValue);
        VBox vBox4 = new VBox(aspectCheckBox,hBox);

        addFiller(browserToolBar);
        browserToolBar.getItems().add(vBox4);

        double initialWidth=delta*2;
        widthCheckBox = new CheckBox("Strip Width");
        widthSlider = new Slider();
        widthValue= new Label(String.valueOf(initialWidth));
        widthCheckBox.selectedProperty().addListener(e -> updateWidth());
        widthSlider.setMin(0.1);
        widthSlider.setMax(3.0);
        widthSlider.setValue(initialWidth);
        widthSlider.setBlockIncrement(0.05);
        //widthSlider.setOnMousePressed(e -> shiftState = e.isShiftDown());
        widthSlider.valueProperty().addListener(e -> updateWidth());

        widthSlider.setPrefWidth(100);
        widthValue.setMinWidth(35);
        widthValue.setMaxWidth(35);
        HBox hBox2 = new HBox(widthSlider,widthValue);
        VBox vBox5 = new VBox(widthCheckBox,hBox2);

        addFiller(browserToolBar);
        browserToolBar.getItems().add(vBox5);

        orientationComboBox = new ComboBox<>();
        ObservableList<GridPaneCanvas.ORIENTATION> orientations = FXCollections.observableArrayList(GridPaneCanvas.ORIENTATION.values());
        orientationComboBox.setItems(orientations);
        orientationComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(GridPaneCanvas.ORIENTATION object) {
                if (GridPaneCanvas.ORIENTATION.HORIZONTAL.equals(object)) {
                    return "Horiz.";
                } else if (GridPaneCanvas.ORIENTATION.VERTICAL.equals(object)) {
                    return "Vert.";
                } else if (GridPaneCanvas.ORIENTATION.GRID.equals(object)) {
                    return "Grid";
                } else {
                    return "";
                }
            }

            @Override
            public GridPaneCanvas.ORIENTATION fromString(String string) {
                return null;
            }
        });

        orientationComboBox.setValue(orientation);

        orientationComboBox.setOnAction(e -> updateOrientation());

        addFiller(browserToolBar);
        browserToolBar.getItems().add(orientationComboBox);

        addRangeControl("Aro", 6.5, 8.2);
        rangeSelector.setValue(addRangeControl("H1'", 5.1, 6.2));
        addRangeControl("H2'", 3.8, 5.1);
        updateRange();
        controller.getBottomBox().getChildren().add(browserToolBar);
    }

    private void updateOrientation() {
        orientation = orientationComboBox.getValue();
        if (orientation == GridPaneCanvas.ORIENTATION.VERTICAL) {
            centerDim = 1;
            rangeDim = 0;
        } else {
            centerDim = 0;
            rangeDim = 1;
        }
        refresh();
    }

    void updateAspectRatio () {
        double aspectRatio = aspectSlider.getValue();
        aspectRatioValue.setText(String.format("%.2f", aspectRatio));

        if (aspectCheckBox.isSelected()) {
            for (PolyChart applyChart : controller.getCharts()) {
                applyChart.chartProps.setAspect(true);
                applyChart.chartProps.setAspectRatio(aspectRatio);
                //applyChart.refresh();
            }
        } else {
            for (PolyChart applyChart : controller.getCharts()) {
                applyChart.chartProps.setAspect(false);
            }
            updateAllBounds();
            if (controller.getActiveChart()!=null) {
                double newAspectRatio = controller.getActiveChart().chartProps.getAspectRatio();
                if (aspectRatio != newAspectRatio) {
                    if (newAspectRatio <= aspectSlider.getMax() && newAspectRatio >= aspectSlider.getMin()) {
                        aspectSlider.setValue(newAspectRatio);
                    } else if (newAspectRatio > aspectSlider.getMax() && aspectSlider.getValue() != aspectSlider.getMax()) {
                        aspectSlider.setValue(aspectSlider.getMax());
                    } else if (newAspectRatio < aspectSlider.getMin() && aspectSlider.getValue() != aspectSlider.getMin()) {
                        aspectSlider.setValue(aspectSlider.getMin());
                    }
                }
            }
        }
    }

    void updateWidth () {
        double width = widthSlider.getValue();
        widthValue.setText(String.format("%.2f", width));

        if (widthCheckBox.isSelected()) {
            delta = width / 2;
            //refresh();
            for (PolyChart applyChart : controller.getCharts()) {
                double shift = applyChart.getAxis(centerDim).getLowerBound()+applyChart.getAxis(centerDim).getRange()/2;
                applyChart.getAxis(centerDim).setLowerBound(shift - delta);
                applyChart.getAxis(centerDim).setUpperBound(shift + delta);
                applyChart.refresh();
            }
        } else {
            updateAllBounds();
            if (controller.getActiveChart()!=null) {
                double newWidth = controller.getActiveChart().getAxis(centerDim).getRange();
                if (width != newWidth) {
                    if (newWidth <= widthSlider.getMax() && newWidth >= widthSlider.getMin()) {
                        widthSlider.setValue(newWidth);
                    } else if (newWidth > widthSlider.getMax() && widthSlider.getValue() != widthSlider.getMax()) {
                        widthSlider.setValue(widthSlider.getMax());
                    } else if (newWidth < widthSlider.getMin() && widthSlider.getValue() != widthSlider.getMin()) {
                        widthSlider.setValue(widthSlider.getMin());
                    }
                }
            }
        }
    }

    public void addFiller(ToolBar toolBar) {
        Pane filler = new Pane();
        HBox.setHgrow(filler, Priority.ALWAYS);
        filler.setMinWidth(25);
        filler.setMaxWidth(50);
        toolBar.getItems().add(filler);
    }

    public void addDrawItems(Atom atom, ProjectBase project) {
        if (atom.getResonance()!=null) {
            for (PeakDim peakDim : atom.getResonance().getPeakDims()) {
                if (peakDim.getSpectralDimObj().getDimName().equals(xLabel)) {
                    for (PeakDim otherDim : peakDim.getPeak().getPeakDims()) {
                        if (otherDim == peakDim) {
                            continue;
                        }
                        if (otherDim.getSpectralDimObj().getDimName().equals(yLabel)) {
                            boolean seen = false;
                            for (DrawItem item : drawItems) {
                                if (item.addIfMatch(peakDim, otherDim)) {
                                    seen = true;
                                }
                            }
                            if (!seen) {
                                drawItems.add(new DrawItem(peakDim, otherDim, project));
                            }
                        }
                    }
                }
            }
        }
    }

    public void setAtom(Atom atom) {
        LocateItem locateItem = new LocateItem(this, currentAtom);
        if (locateItems.contains(locateItem)) {
            locateItems.get(locateItems.indexOf(locateItem)).remove();
            locateItems.remove(locateItem);
        }
        drawItems.clear();
        //atom guaranteed to be not null
        currentAtom = atom;
        locateItem = new LocateItem(this, atom);
        if (!locateItems.contains(locateItem)) {
            locateItems.add(locateItem);
            locateItem.add();
        }
        addDrawItems(atom, ProjectBase.getActive());
        for (ProjectRelations projectRelation : ProjectRelations.getProjectRelations()) {
            BidiMap<Entity, Entity> map = projectRelation.entityMap;
            if (map.containsKey(atom.getEntity())) {
                Entity otherEntity = map.get(atom.getEntity());
                for (Atom otherAtom : otherEntity.getAtoms()) {
                    if (otherAtom.getName().equals(atom.getName())) {
                        addDrawItems(otherAtom, projectRelation.getSubProject());
                    }
                }
            }
        }
        refresh();

    }

    public void drawLocateItems() {
        for (LocateItem locateItem : locateItems) {
            locateItem.update();
        }
    }

    private void clearLocates() {
        for (LocateItem locateItem : locateItems) {
            locateItem.remove();
        }
        locateItems.clear();
    }

    public void setxLabel(String xLabel) {
        this.xLabel = xLabel;
    }

    public void setyLabel(String yLabel) {
        this.yLabel = yLabel;
    }

    public String getxLabel() {
        return xLabel;
    }

    public String getyLabel() {
        return yLabel;
    }

    public void refresh() {
        //can we make scrollable?
        controller.setBorderState(true);
        controller.setNCharts(0);
        int i = 1;
        Double yMin = Double.MAX_VALUE;
        Double yMax = Double.MIN_VALUE;
        //just filter on active datasets, peakLists, dims, experiment types, subProjects etc. here?
        /*
        for (DrawItem item : drawItems.values()) {
            if (item.getOtherDims().containsKey(otherNucleus.getValue())) {
                for (DrawItem.OtherDim otherDim : item.getOtherDims().get(otherNucleus.getValue())) {
                    if (otherDim.min < yMin) {
                        yMin = otherDim.min;
                    }
                    if (otherDim.max > yMax) {
                        yMax = otherDim.max;
                    }
                }
            }
        }

         */

        PolyChart previousChart = null;
        for (DrawItem item : drawItems) {
            if (item.isAllowed(filterList)) {
                AtomicReference<Double> ppm = new AtomicReference<>(item.getShift());
                if (ppm.get() != null) {
                    if (controller.getCharts().size() < i) {
                        controller.addChart();
                    }
                    PolyChart chart = controller.getCharts().get(i - 1);
                    chart.setActiveChart();
                    chart.chartProps.setTopBorderSize(40);

                    chart.setDataset(item.dataset);
                    chart.getPeakListAttributes().clear();
                    chart.setupPeakListAttributes(item.peakList);
                    for (PeakListAttributes peakListAttributes : chart.getPeakListAttributes()) {
                        peakListAttributes.setLabelType("Label");
                    }

                    DatasetAttributes datasetAttr = chart.getActiveDatasetAttributes().get(0);
                    datasetAttr.setDim(item.centerDimNo, centerDim);
                    datasetAttr.setDim(item.rangeDimNo, rangeDim);

                    GraphicsContext gCC = chart.getCanvas().getGraphicsContext2D();
                    GraphicsContextInterface gC = new GraphicsContextProxy(gCC);
                    item.dataset.setTitle(item.dataset.getName());

                    chart.chartProps.setTitles(true);

                    if (item.getMinRange() < yMin) {
                        yMin = item.getMinRange();
                    }
                    if (item.getMaxRange() > yMax) {
                        yMax = item.getMaxRange();
                    }

                    chart.getAxis(centerDim).setLowerBound(ppm.get() - delta);
                    chart.getAxis(centerDim).setUpperBound(ppm.get() + delta);
                    chart.getAxis(rangeDim).setLowerBound(yMin);
                    chart.getAxis(rangeDim).setUpperBound(yMax);
                    chart.getAxis(rangeDim).lowerBoundProperty().addListener(e -> {
                        if (!scheduled) {
                            service.schedule(() -> Platform.runLater(() -> {
                                updateAllBounds();
                                scheduled = false;
                            }), 1, TimeUnit.MILLISECONDS);

                            scheduled = true;
                        }
                    });
                    chart.getAxis(rangeDim).upperBoundProperty().addListener(e -> {
                        if (!scheduled) {
                            service.schedule(() -> Platform.runLater(() -> {
                                updateAllBounds();
                                scheduled = false;
                            }), 1, TimeUnit.MILLISECONDS);

                            scheduled = true;
                        }
                    });


                    for (int n = 2; n < item.dataset.getNDim(); n++) {
                        int dataDim = datasetAttr.getDim(n);
                        chart.getAxis(n).setLowerBound(item.getMinShift(dataDim));
                        chart.getAxis(n).setUpperBound(item.getMaxShift(dataDim));
                        int finalN = n;
                        chart.getAxis(n).lowerBoundProperty().addListener(e -> {
                            for (PeakDim peakDim : item.dims.get(dataDim)) {
                                if (!peakDim.isFrozen()) {
                                    peakDim.setChemShift((float) chart.getAxis(finalN).getLowerBound());
                                }
                            }
                            if (!scheduled) {
                                service.schedule(() -> Platform.runLater(() -> {
                                    updateAllBounds();
                                    scheduled = false;
                                }), 1, TimeUnit.MILLISECONDS);

                                scheduled = true;
                            }
                        });
                        chart.getAxis(n).upperBoundProperty().addListener(e -> {
                            for (PeakDim peakDim : item.dims.get(dataDim)) {
                                if (!peakDim.isFrozen()) {
                                    peakDim.setChemShift((float) chart.getAxis(finalN).getUpperBound());
                                }
                            }
                            if (!scheduled) {
                                service.schedule(() -> Platform.runLater(() -> {
                                    updateAllBounds();
                                    scheduled = false;
                                }), 1, TimeUnit.MILLISECONDS);

                                scheduled = true;
                            }
                        });

                    }

                    if (previousChart != null) {
                        chart.getAxis(rangeDim).lowerBoundProperty().bindBidirectional(previousChart.getAxis(rangeDim).lowerBoundProperty());
                        chart.getAxis(rangeDim).upperBoundProperty().bindBidirectional(previousChart.getAxis(rangeDim).upperBoundProperty());
                    }

                    item.peakList.registerPeakChangeListener(e -> {
                        boolean dirty = false;
                        Double newPpm = item.getShift();
                        if (newPpm != null && !newPpm.equals(ppm.get())) {
                            double currentDelta = chart.getAxis(centerDim).getRange() / 2;
                            chart.getAxis(centerDim).setLowerBound(newPpm - currentDelta);
                            chart.getAxis(centerDim).setUpperBound(newPpm + currentDelta);
                            ppm.set(newPpm);
                            dirty = true;
                        }

                        for (int n = 2; n < item.dataset.getNDim(); n++) {
                            int dataDim = datasetAttr.getDim(n);
                            Double newMin = item.getMinShift(dataDim);
                            Double newMax = item.getMaxShift(dataDim);
                            if (chart.getAxis(n).getLowerBound() != newMin) {
                                chart.getAxis(n).setLowerBound(item.getMinShift(dataDim));
                                dirty = true;
                            }
                            if (chart.getAxis(n).getUpperBound() != newMax) {
                                chart.getAxis(n).setUpperBound(item.getMaxShift(dataDim));
                                dirty = true;
                            }
                        }

                        if (dirty && !scheduled) {
                            service.schedule(() -> Platform.runLater(() -> {
                                //updateChartBounds(chart);
                                updateAllBounds();
                                drawLocateItems();
                                scheduled = false;
                            }), 1, TimeUnit.MILLISECONDS);
                            scheduled = true;
                        }
                    });
                    chart.getAxis(rangeDim).lowerBoundProperty().addListener(e->updateBounds());
                    chart.getAxis(rangeDim).upperBoundProperty().addListener(e->updateBounds());

                    previousChart = chart;
                    chart.autoScale();
                    for (DatasetAttributes datasetAt : chart.getActiveDatasetAttributes()) {
                        datasetAttr.setLvl(getLevelForDataset(datasetAt));
                    }
                    chart.refresh();
                    i++;
                }
            }
        }
        controller.arrange(orientation);
        //controller.showPeakSlider(false);
        drawLocateItems();
        updateWidth();
        updateAspectRatio();
    }

    private void updateBounds() {
        //called whenever upper of lower bound of rangeDim is changed
        //used to synchronise all strips
    }

    private double getLevelForDataset(DatasetAttributes datasetAt) {
        savedLevels.computeIfAbsent(datasetAt.getDataset(),k -> datasetAt.getLvl());
        return savedLevels.get(datasetAt.getDataset());
    }

    public Atom getCurrentAtom() {
        return currentAtom;
    }

    public void setCurrentAtom(Atom atom) {
        currentAtom = atom;
    }

    public void addFilterItem (Object object, boolean allow) {
        filterList.add(new FilterItem(object,allow));
    }

    public RangeItem addRangeControl(String name, double min, double max) {
        RangeItem rangeItem = new RangeItem(name, min, max);
        rangeItems.add(rangeItem);
        return rangeItem;
    }

    public ObservableList<RangeItem> getRangeItems() {
        return rangeItems;
    }

    public void updateChartBounds(PolyChart chart) {
        /*if (aspectCheckBox.isSelected()) {
            //change delta rather than layout width
            double layoutRatio = chart.getAxis(centerDim).getWidth()/chart.getAxis(rangeDim).getWidth();
            double centerRange = chart.getAxis(centerDim).getRange();
            double centerShift = chart.getAxis(centerDim).getLowerBound()+centerRange/2;
            double rangeRange = chart.getAxis(rangeDim).getRange();
            delta = 0.5*rangeRange*aspectSlider.getValue()*layoutRatio;
            chart.getAxis(centerDim).setLowerBound(centerShift - delta);
            chart.getAxis(centerDim).setUpperBound(centerShift + delta);
        }
         */
        chart.refresh();
    }
    public void updateAllBounds() {
        controller.getCharts().forEach(this::updateChartBounds);
    }

    public void updateRange() {
        RangeItem rangeItem = rangeSelector.getValue();
        if (rangeItem != null) {
            double min = rangeItem.getMin();
            double max = rangeItem.getMax();

            controller.getCharts().forEach(chart -> {
                try {
                    chart.getAxis(rangeDim).setLowerBound(min);
                    chart.getAxis(rangeDim).setUpperBound(max);
                } catch (NumberFormatException ignored) {

                }
            });
        }
    }
}

