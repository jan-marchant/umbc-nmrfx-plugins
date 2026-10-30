package edu.umbc.hhmi.acquisition_plugin;

import org.nmrfx.chemistry.constraints.ManagedNoeSet;
import org.nmrfx.peaks.ManagedList;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.nmrfx.plugin.api.PluginFunction;

import java.util.Set;

public class AcquisitionPlugin implements NMRFxPlugin {
//STARTUP, MENU_FILE, MENU_PLUGINS, STATUS_BAR_TOOLS
    public static Menu acqMenu;

    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {
        return Set.of(EntryPoint.MENU_PLUGINS, EntryPoint.STARTUP);
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        if (entryPoint == EntryPoint.STARTUP) {
            ManagedList.doStartup();
            Condition.doStartup();
            Sample.doStartup();
            Experiment.doStartup();
            ManagedNoeSet.doStartup();
            Acquisition.doStartup();
        }

        if (entryPoint == EntryPoint.MENU_PLUGINS) {
            System.out.println("Adding Acquisition setup to plugins menu");
            acqMenu = new Menu("Acquisition Setup");

            MenuItem acqMenuItem = new MenuItem("Associations");
            acqMenuItem.setOnAction(Acquisition::showAcquisitionList);
            MenuItem sampleMenuItem = new MenuItem("Samples");
            sampleMenuItem.setOnAction(Sample::showSampleList);
            MenuItem conditionMenuItem = new MenuItem("Conditions");
            conditionMenuItem.setOnAction(Condition::showConditionList);
            MenuItem experimentMenuItem = new MenuItem("Experiments");
            experimentMenuItem.setOnAction(Experiment::showExperimentList);
            MenuItem noeSetupMenuItem = new MenuItem("NOE Sets");
            noeSetupMenuItem.setOnAction(ManagedNoeSetController::showNoeSetup);
            MenuItem fixDatasetMenuItem = new MenuItem("Fix dataset");
            fixDatasetMenuItem.setOnAction(Acquisition::fixDataset);
            MenuItem writeallPeaksMenuItem = new MenuItem("Write all peaklists");
            writeallPeaksMenuItem.setOnAction(Acquisition::writeAllPeaklists);


            acqMenu.getItems().addAll(acqMenuItem,sampleMenuItem,conditionMenuItem,experimentMenuItem,noeSetupMenuItem,fixDatasetMenuItem,writeallPeaksMenuItem);
            //what is the point of this plugin function??
            ((Menu) ((PluginFunction) object).guiObject()).getItems().addAll(acqMenu);

            //KeyBindings.registerGlobalKeyAction("ds", this::tweakPeaks);
        }
    }
}
