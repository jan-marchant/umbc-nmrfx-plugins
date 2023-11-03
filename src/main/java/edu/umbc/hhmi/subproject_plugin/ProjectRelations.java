package edu.umbc.hhmi.subproject_plugin;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chemistry.*;
import org.nmrfx.peaks.ManagedList;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.project.ProjectUtilities;
import org.nmrfx.project.SubProject;
import org.nmrfx.star.SaveframeWriter;
import org.nmrfx.utils.GUIUtils;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProjectRelations implements SaveframeWriter, Comparable<ProjectRelations> {
    private static SubProjectSceneController subProjController;
    private static SubProjectNoeController subProjNoeController;
    private static SubProjectAssignmentController subProjAssignmentController;
    private static SubProjectPeakController subProjPeakController;

    private final ProjectBase parentProject;
    private final SubProject subProject;
    public BidiMap<Entity,Entity> entityMap = new DualHashBidiMap<>();

    private final static HashMap<ProjectBase,ObservableList<ProjectRelations>> projectSubProjectMap = new HashMap<>();
    //To allow us to write a header
    private final static HashMap<ProjectBase,Integer> projectSaveFramesAdded = new HashMap<>();
    private final static HashMap<ProjectBase,Integer> projectSaveFramesWritten = new HashMap<>();

    static public Integer getSaveFramesAdded() {
        return getSaveFramesAdded(ProjectBase.getActive());
    }

    static public Integer getSaveFramesWritten() {
        return getSaveFramesWritten(ProjectBase.getActive());
    }

    static public Integer getSaveFramesAdded(ProjectBase project) {
        return projectSaveFramesAdded.computeIfAbsent(project, k -> 0);
    }

    static public Integer getSaveFramesWritten(ProjectBase project) {
        Integer saveFramesWritten = projectSaveFramesWritten.get(project);
        if (saveFramesWritten == null) {
            saveFramesWritten = 0;
            projectSaveFramesAdded.put(project,saveFramesWritten);
        }
        return saveFramesWritten;
    }

    static public ObservableList<ProjectRelations> getProjectRelations() {
        return getProjectRelations(ProjectBase.getActive());
    }

    static public ObservableList<ProjectRelations> getProjectRelations(ProjectBase project) {
        return projectSubProjectMap.computeIfAbsent(project, k -> FXCollections.observableArrayList());
    }

    public static void doStartup() {
        SubProjectSaveframeProcessor subProjSaveFrameProcessor = new SubProjectSaveframeProcessor();
        ProjectBase.addSaveframeProcessor("assembly_subsystems", subProjSaveFrameProcessor);
    }

    public ProjectRelations(ProjectBase parent, SubProject sub) throws Exception {
        parentProject = parent;
        subProject = sub;
        //if (getRecursiveSubProjectList(parentProject).contains(parentProject)) {
        //    throw new Exception("Cannot create recursive subproject loop");
        //}
        getProjectRelations(parent).add(this);
        ProjectBase.getActive().addSaveframe(this);
        ProjectUtilities.sortExtraSaveFrames();
        Integer count = getSaveFramesAdded(parent);
        projectSaveFramesAdded.put(ProjectBase.getActive(),count+1);
    }

    public static List<ProjectBase> getSubProjectList(ProjectBase project) {
        return getProjectRelations(project).stream().map(ProjectRelations::getSubProject).collect(Collectors.toList());
    }

    public static List<ProjectBase> getRecursiveSubProjectList(ProjectBase project) {
        List<ProjectBase> recursive = new ArrayList<>(getSubProjectList(project));
        for (ProjectBase subProj : recursive) {
            recursive.addAll(getRecursiveSubProjectList(subProj));
        }
        return recursive;
    }

    public static ProjectBase findSubProject(Path path) {
        return findSubProject(ProjectBase.getActive(), path);
    }

    public static ProjectBase findSubProject(ProjectBase parent, Path path) {
        for (ProjectRelations subProjRelation : getProjectRelations(parent)) {
            if (subProjRelation.getSubProject().getDirectory().equals(path)) {
                return subProjRelation.getSubProject();
            }
        }
        return null;
    }

    public static ProjectRelations findProjectRelations(ProjectBase sub) {
        //find subProjectRelations between active project and given subProject
        return findProjectRelations(ProjectBase.getActive(),sub);
    }

    public static ProjectRelations findProjectRelations(ProjectBase parent, ProjectBase sub) {
        for (ProjectRelations subProjRelation : getProjectRelations(parent)) {
            if (subProjRelation.getSubProject().equals(sub)) {
                return subProjRelation;
            }
        }
        return null;
    }

    public static BidiMap<Entity, Entity> getEntityMap(ProjectBase sub) {
        return getEntityMap(ProjectBase.getActive(),sub);
    }

    public static BidiMap<Entity, Entity> getEntityMap(ProjectBase parent, ProjectBase sub) {
        ProjectRelations projRel = findProjectRelations(parent, sub);
        return (projRel == null) ? null: projRel.entityMap;
    }

    @FXML
    static void showProjectRelations(ActionEvent event) {
        if (subProjController == null) {
            subProjController = new SubProjectSceneController();
            subProjController.show(400,400);
        }
        if (subProjController != null) {
            subProjController.getStage().show();
            subProjController.getStage().toFront();
        } else {
            System.out.println("Couldn't make SubProjectController ");
        }
    }

    @FXML
    public static void showSubProjNoeTransfer(ActionEvent event) {
        if (subProjNoeController == null) {
            subProjNoeController = new SubProjectNoeController();
            subProjNoeController.show(400,400);
        }
        if (subProjNoeController != null) {
            subProjNoeController.getStage().show();
            subProjNoeController.getStage().toFront();
        } else {
            System.out.println("Couldn't make SubProjectNoeController ");
        }
    }

    @FXML
    public static void showSubProjAssignmentTransfer(ActionEvent actionEvent) {
        if (subProjAssignmentController == null) {
            subProjAssignmentController = new SubProjectAssignmentController();
            subProjAssignmentController.show(400,400);
        }
        if (subProjAssignmentController != null) {
            subProjAssignmentController.getStage().show();
            subProjAssignmentController.getStage().toFront();
        } else {
            System.out.println("Couldn't make SubProjectAssignmentController ");
        }
    }

    @FXML
    public static void showSubProjPeakTransfer(ActionEvent actionEvent) {
        if (subProjPeakController == null) {
            subProjPeakController = new SubProjectPeakController();
            subProjPeakController.show(400,400);
        }
        if (subProjPeakController != null) {
            subProjPeakController.getStage().show();
            subProjPeakController.getStage().toFront();
        } else {
            System.out.println("Couldn't make SubProjectAssignmentController ");
        }
    }

    public void remove() {
        ProjectUtilities.removeExtraSaveFrame(getParentProject(),this);
        Integer count = getSaveFramesAdded(getParentProject());
        projectSaveFramesAdded.put(ProjectBase.getActive(),count-1);
        getProjectRelations(getParentProject()).remove(this);
    }

    @Override
    public void write(Writer chan) throws IOException {
        if (!parentProject.equals(ProjectBase.getActive())) {
            return;
        }
        //TODO: delegate header and sorting to SaveframeWriter interface
        if (getSaveFramesWritten() == 0) {
            chan.write("\n\n");
            chan.write("    ####################################\n");
            chan.write("    #      Associated assemblies       #\n");
            chan.write("    ####################################\n");
            chan.write("\n\n");
        }

        subProject.saveProject();
        Path relativePath;
        try {
            relativePath = parentProject.getDirectory().relativize(subProject.getDirectory());
        } catch (Exception e) {
            relativePath = subProject.getDirectory();
        }
        String label = subProject.getName();
        chan.write("save_" + label + "\n");
        chan.write("_Assembly_subsystem.Sf_category                 ");
        chan.write("assembly_subsystems\n");
        chan.write("_Assembly_subsystem.Sf_framecode                ");
        chan.write("save_" + label + "\n");
        chan.write("_Assembly_subsystem.ID                          ");
        chan.write(getSaveFramesWritten()+"\n");
        chan.write("_Assembly_subsystem.Name                        ");
        chan.write("'"+label+"'\n");
        chan.write("_Assembly_subsystem.Details                     ");
        chan.write("'"+relativePath.toString()+"'\n");

        //fixme: this is not an "official" STAR category.
        // using names for fear that IDs aren't persistent
        // (e.g. if subproject edited).
        chan.write("loop_\n");
        chan.write("_Entity_map.Assembly_subsystem_ID\n");
        chan.write("_Entity_map.Active_system\n");
        chan.write("_Entity_map.Sub_system\n");
        chan.write("\n");

        //All this just to write them in residue order...
        List<Entity> seen=new ArrayList<>();
        for (Entity entity : parentProject.getActiveMolecule().getEntities()) {
            if (entity instanceof Polymer) {
                for (Residue residue : ((Polymer) entity).getResidues()) {
                    if (entityMap.containsKey(residue)) {
                        chan.write(String.format("%d %s %s\n", getSaveFramesWritten(), residue.toString(), entityMap.get(residue).toString()));
                        seen.add(residue);
                    }
                }
            }
        }
        for (Map.Entry<Entity,Entity> entry : entityMap.entrySet()) {
            if (!seen.contains(entry.getKey())) {
                chan.write(String.format("%d %s %s\n", getSaveFramesWritten(), entry.getKey().toString(), entry.getValue().toString()));
            }
        }

        chan.write("stop_\n");
        chan.write("save_\n\n");
        Integer count = getSaveFramesWritten();
        projectSaveFramesWritten.put(ProjectBase.getActive(),count+1);

        if (getSaveFramesWritten().equals(getSaveFramesAdded())) {
            chan.write("\n\n");
            projectSaveFramesWritten.put(ProjectBase.getActive(),0);
        }
    }

    public ProjectBase getSubProject() {
        return subProject;
    }

    @Override
    public int compareTo(ProjectRelations o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
        return getParentProject().getName()+" -> "+getSubProject().getName();
    }

    static public boolean containsSubProjectPath(ProjectBase project,Path path) {
        if (project.getDirectory().equals(path)) {
            return true;
        }
        for (ProjectBase subProject : getSubProjectList(project)) {
            if (containsSubProjectPath(subProject,path)) {
                return true;
            }
        }
        return false;
    }

    public static void addSubProject(String projectName, String projectPath,
                                     List<String> activeEntities, List<String> subEntities) {
        String absolute;
        try {
            File parentDir = new File(ProjectBase.getActive().getProjectDir().toString());
            File childDir = new File(parentDir, projectPath);
            absolute = childDir.getCanonicalPath();
        } catch (Exception e) {
            absolute = projectPath;
        }

        Path projPath= Paths.get(absolute);
        ProjectBase subProj=addSubProject(projectName,ProjectBase.getActive(),projPath);
        //Probably a neater way to do this
        ProjectRelations projectRelations = ProjectRelations.findProjectRelations(subProj);
        if (projectRelations!=null) {
            for (int i = 0; i < activeEntities.size(); i++) {
                projectRelations.entityMap.put(getEntitiesAndResidues(ProjectBase.getActive().getActiveMolecule(), activeEntities.get(i)), getEntitiesAndResidues(subProj.getActiveMolecule(), subEntities.get(i)));
            }
        }
    }

    static public ProjectBase addSubProject(ProjectBase parentProject, Path projectPath) {
        if (projectPath==null) {
            return null;
        }
        ProjectBase found=findSubProject(projectPath);
        if (found!=null) {
            if (parentProject.getDirectory().equals(projectPath)) {
                GUIUtils.warn("Error","Cannot add project as a subProject of itself");
                return null;
            } else {
                return found;
            }
        }
        String projectName = projectPath.getFileName().toString();
        return addSubProject(projectName, parentProject, projectPath);
    }

    static public ProjectBase addSubProject(String name, ProjectBase parentProject, Path path) {
        SubProject subProj;
        try {
            subProj = new SubProject(name);
            parentProject.setActive();
            subProj.loadSubProject(path);
            new ProjectRelations(parentProject, subProj);
        } catch (Exception e) {
            ExceptionDialog dialog = new ExceptionDialog(e);
            dialog.showAndWait();
            subProj=null;
        }
        return subProj;
    }


    static public List<Object> getSubProjMenus(ProjectBase project, SubProjMenu controller, List<Path> seen) {
        List<Object> menus=new ArrayList<>();

        for (ProjectRelations projRelations : getProjectRelations(project)) {
            if (!seen.contains(projRelations.getSubProject().getDirectory())) {
                seen.add(projRelations.getSubProject().getDirectory());
                if (getProjectRelations(projRelations.getSubProject()).size() > 0) {
                    Menu menu = new Menu(projRelations.getSubProject().getName());
                    for (Object subMenu : ProjectRelations.getSubProjMenus(projRelations.getSubProject(), controller, seen)) {
                        if (subMenu instanceof Menu) {
                            menu.getItems().add((Menu) subMenu);
                        } else {
                            menu.getItems().add((MenuItem) subMenu);
                        }
                    }
                    menus.add(menu);
                    if (projRelations.getParentProject() == ProjectBase.getActive()) {
                        menu.setOnAction(e -> controller.setSubProject(projRelations.getSubProject()));
                    }
                } else {
                    MenuItem menu = new MenuItem(projRelations.getSubProject().getName());
                    menus.add(menu);
                    if (projRelations.getParentProject() == ProjectBase.getActive()) {
                        menu.setOnAction(e -> controller.setSubProject(projRelations.getSubProject()));
                    }
                }
            } else {
                MenuItem menu = new MenuItem(projRelations.getSubProject().getName()+" (recursive)");
                menus.add(menu);
            }
        }
        return menus;
    }

    public ProjectBase getParentProject() {
        return parentProject;
    }

    public static Entity getEntitiesAndResidues(MoleculeBase mol, String name) {
        Pattern pattern = Pattern.compile("^([A-z0-9]+)(?::([A-z]+)([0-9]+))?$");
        Matcher matcher = pattern.matcher(name);
        if (!matcher.matches()) {
            return null;
        }
        String entityName=matcher.group(1);
        String resName=matcher.group(2);
        String resNum=matcher.group(3);
        Entity entity=mol.getEntity(entityName);
        if (resNum==null || resNum.isEmpty()) {
            return entity;
        }
        if (entity instanceof Polymer) {
            return ((Polymer) entity).getResidue(resNum);
        }
        return null;
    }

    public static void transferAssignments(int fromSet, int toSet, boolean fromRef, boolean toRef, BidiMap<Entity, Entity> map) {
        if (map != null && map.size()>0) {
            for (Map.Entry<Entity,Entity> entry : map.entrySet()) {
                if (entry.getKey() instanceof Residue parentRes) {
                    if (entry.getValue() instanceof Residue subRes) {
                        for (Atom parentAtom : parentRes.getAtoms()) {
                            Atom subAtom = subRes.getAtom(parentAtom.getName());
                            double fromPPM;
                            try {
                                if (fromRef) {
                                    fromPPM = subAtom.getRefPPM(fromSet).getValue();
                                } else {
                                    fromPPM = subAtom.getPPM(fromSet).getValue();
                                }
                                if (toRef) {
                                    parentAtom.setRefPPM(toSet, fromPPM);
                                } else {
                                    parentAtom.setPPM(toSet, fromPPM);
                                }
                                try {
                                    //for (PeakDim peakDim1 : parentAtom.getResonance().getPeakDims()) {
                                        PeakDim peakDim1 = parentAtom.getResonance().getPeakDims().get(0);
                                        if (peakDim1.getPeakList() instanceof ManagedList list) {
                                            if (list.getRPpmSet() == toSet && toRef) {
                                                if (!peakDim1.isFrozen()) {
                                                    //todo: consider clash detection
                                                    //todo: add user option for this behaviour
                                                    //todo: consider behaviour when PPM set (rather than refPPM)
                                                    peakDim1.setChemShift((float) fromPPM);
                                                }
                                            }
                                        }
                                    //}
                                } catch (Exception ignored) {}
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }
    }

    public static void transferAssignmentsFromPeaklist(PeakList peakList, int toSet, boolean toRef, BidiMap<Entity, Entity> map, boolean requireFrozen) {
        if (map != null && map.size() > 0) {
            for (Peak peak : peakList.peaks()) {
                for (PeakDim peakDim : peak.getPeakDims()) {
                    if (peakDim.isFrozen() || !requireFrozen) {
                        Atom atom = ((AtomResonance) peakDim.getResonance()).getAtom();
                        Float shift = peakDim.getChemShift();
                        for (Map.Entry<Entity, Entity> entry : map.entrySet()) {
                            if (entry.getValue() instanceof Residue subRes) {
                                if (subRes.getAtoms().contains(atom)) {
                                    if (entry.getKey() instanceof Residue parentRes) {
                                        Atom parentAtom = parentRes.getAtom(atom.getName());
                                        if (toRef) {
                                            parentAtom.setRefPPM(toSet, shift);
                                        } else {
                                            parentAtom.setPPM(toSet, shift);
                                        }
                                        try {
                                            //only need to do once - sliding takes care of the rest
                                            //for (PeakDim peakDim1 : parentAtom.getResonance().getPeakDims()) {
                                            PeakDim peakDim1 = parentAtom.getResonance().getPeakDims().get(0);
                                                if (peakDim1.getPeakList() instanceof ManagedList list) {
                                                    //todo: is RPpmSet maintained across saves?
                                                    if (list.getRPpmSet() == toSet && toRef) {
                                                        if (!peakDim1.isFrozen()) {
                                                            //todo: consider clash detection
                                                            //todo: add user option for this behaviour
                                                            //todo: consider behaviour when PPM set (rather than refPPM)
                                                            peakDim1.setChemShift(shift);
                                                        }
                                                    }
                                                }
                                            //}
                                        } catch (Exception ignored) {}

                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
